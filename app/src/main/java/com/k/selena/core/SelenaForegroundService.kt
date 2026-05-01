package com.k.selena.core

import android.Manifest
import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.Service
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.net.Uri
import android.os.Build
import android.os.IBinder
import android.os.PowerManager
import android.util.Log
import androidx.core.content.ContextCompat
import androidx.core.app.NotificationCompat
import com.k.selena.BuildConfig
import com.k.selena.R
import com.k.selena.command.CommandRouter
import com.k.selena.model.ModelManager
import com.k.selena.system.AndroidSystemActions
import com.k.selena.system.MagiskRootExecutor
import com.k.selena.system.RuntimeShellExecutor
import com.k.selena.voice.AudioFocusManager
import com.k.selena.voice.MockHotwordDetector
import com.k.selena.voice.PorcupineHotwordDetector
import com.k.selena.voice.TtsCommandAnnouncer
import com.k.selena.voice.VoicePipeline
import com.k.selena.voice.VoskSpeechRecognizer
import java.util.concurrent.atomic.AtomicBoolean

class SelenaForegroundService : Service() {
    private val started = AtomicBoolean(false)
    private lateinit var stateMachine: SelenaStateMachine
    private lateinit var pipeline: VoicePipeline
    private lateinit var wakeLock: PowerManager.WakeLock
    private var ttsAnnouncer: TtsCommandAnnouncer? = null

    override fun onCreate() {
        super.onCreate()
        stateMachine = SelenaStateMachine(this)

        // Model management: log availability so future UI/download logic can act on it.
        val modelManager = ModelManager(this)
        if (!modelManager.isModelAvailable(BuildConfig.VOSK_MODEL_NAME)) {
            Log.w(TAG, "Vosk model '${BuildConfig.VOSK_MODEL_NAME}' not found in internal storage. " +
                    "Attempting to copy from assets…")
            val copied = modelManager.copyFromAssets(BuildConfig.VOSK_MODEL_NAME)
            if (!copied) {
                Log.w(TAG, "Model could not be copied from assets. ASR will be unavailable " +
                        "until the model is installed at: " +
                        "${modelManager.modelDir(BuildConfig.VOSK_MODEL_NAME).absolutePath}")
            }
        }

        val systemActions = AndroidSystemActions(this)
        val rootExecutor = MagiskRootExecutor()
        val commandRouter = CommandRouter(
            systemActions = systemActions,
            shellExecutor = RuntimeShellExecutor(),
            rootExecutor = rootExecutor
        )

        // TTS announcer for root-command confirmation read-back.
        val announcer = TtsCommandAnnouncer(this).also { ttsAnnouncer = it }

        val hotwordDetector = if (BuildConfig.PICOVOICE_ACCESS_KEY.isNotBlank()) {
            Log.i(TAG, "Using PorcupineHotwordDetector")
            PorcupineHotwordDetector(
                context = this,
                accessKey = BuildConfig.PICOVOICE_ACCESS_KEY,
                sensitivity = BuildConfig.HOTWORD_SENSITIVITY
            )
        } else {
            Log.w(TAG, "PICOVOICE_ACCESS_KEY not set — falling back to MockHotwordDetector")
            MockHotwordDetector(BuildConfig.HOTWORD)
        }

        val speechRecognizer = VoskSpeechRecognizer(
            context = this,
            modelName = BuildConfig.VOSK_MODEL_NAME
        )

        pipeline = VoicePipeline(
            hotwordDetector = hotwordDetector,
            speechRecognizer = speechRecognizer,
            stateMachine = stateMachine,
            commandRouter = commandRouter,
            commandAnnouncer = announcer,
            audioFocusManager = AudioFocusManager(this)
        )
        val powerManager = getSystemService(PowerManager::class.java)
        // PARTIAL_WAKE_LOCK keeps the CPU alive while the screen is off so the voice pipeline
        // can keep running. It is held for the full lifetime of the foreground service and
        // released unconditionally in onDestroy(), making a timeout unnecessary here.
        wakeLock = powerManager.newWakeLock(
            PowerManager.PARTIAL_WAKE_LOCK,
            "Selena::VoicePipelineWakeLock"
        )
        Log.i(TAG, "Foreground service created with restored state=${stateMachine.currentState}")
    }

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        startForeground(NOTIFICATION_ID, createNotification())
        if (!wakeLock.isHeld) {
            wakeLock.acquire()
        }
        if (started.compareAndSet(false, true)) {
            if (stateMachine.currentState != SelenaState.IDLE) {
                stateMachine.transitionTo(SelenaState.IDLE, "Service restart recovery")
            }
            if (hasRecordAudioPermission()) {
                Log.i(TAG, "Starting voice pipeline")
                pipeline.start()
            } else {
                Log.w(TAG, "RECORD_AUDIO not granted; service remains active in IDLE mode")
                stateMachine.transitionTo(SelenaState.IDLE, "Microphone permission unavailable")
            }
            requestBatteryOptimizationExemption()
        } else {
            Log.d(TAG, "Voice pipeline already running")
        }
        return START_STICKY
    }

    override fun onDestroy() {
        Log.i(TAG, "Foreground service destroyed")
        pipeline.stop()
        ttsAnnouncer?.shutdown()
        if (wakeLock.isHeld) {
            wakeLock.release()
        }
        super.onDestroy()
    }

    override fun onBind(intent: Intent?): IBinder? = null

    private fun hasRecordAudioPermission(): Boolean =
        ContextCompat.checkSelfPermission(
            this,
            Manifest.permission.RECORD_AUDIO
        ) == PackageManager.PERMISSION_GRANTED

    /**
     * Request that the OS exempts Selena from battery optimisations. On stock Android this shows
     * a system dialog; on OEM ROMs the user may still need to allow auto-start manually.
     * The permission [android.Manifest.permission.REQUEST_IGNORE_BATTERY_OPTIMIZATIONS] is
     * declared in the manifest, so the intent is safe to fire without a try/catch.
     */
    private fun requestBatteryOptimizationExemption() {
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.M) return
        val pm = getSystemService(PowerManager::class.java)
        if (pm.isIgnoringBatteryOptimizations(packageName)) return
        try {
            val intent = Intent(android.provider.Settings.ACTION_REQUEST_IGNORE_BATTERY_OPTIMIZATIONS).apply {
                data = Uri.parse("package:$packageName")
                addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
            }
            startActivity(intent)
            Log.i(TAG, "Launched battery optimisation exemption dialog")
        } catch (e: Exception) {
            Log.w(TAG, "Could not launch battery optimisation dialog", e)
        }
    }

    private fun createNotification(): Notification {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            val channel = NotificationChannel(
                CHANNEL_ID,
                getString(R.string.service_channel_name),
                NotificationManager.IMPORTANCE_LOW
            )
            val manager = getSystemService(NotificationManager::class.java)
            manager.createNotificationChannel(channel)
        }

        return NotificationCompat.Builder(this, CHANNEL_ID)
            .setContentTitle(getString(R.string.service_notification_title))
            .setContentText(getString(R.string.service_notification_text))
            .setSmallIcon(android.R.drawable.ic_btn_speak_now)
            .setOngoing(true)
            .setForegroundServiceBehavior(NotificationCompat.FOREGROUND_SERVICE_IMMEDIATE)
            .build()
    }

    companion object {
        private const val TAG = "SelenaService"
        private const val CHANNEL_ID = "selena_foreground_channel"
        private const val NOTIFICATION_ID = 1001

        fun createIntent(context: Context): Intent = Intent(context, SelenaForegroundService::class.java)
    }
}
