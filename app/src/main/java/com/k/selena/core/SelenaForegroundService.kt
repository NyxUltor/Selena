package com.k.selena.core

import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.Service
import android.content.Context
import android.content.Intent
import android.os.Build
import android.os.IBinder
import android.os.PowerManager
import android.util.Log
import androidx.core.app.NotificationCompat
import com.k.selena.R
import com.k.selena.command.CommandRouter
import com.k.selena.system.AndroidSystemActions
import com.k.selena.system.MagiskRootExecutor
import com.k.selena.system.RuntimeShellExecutor
import com.k.selena.voice.AudioRecordSpeechRecognizer
import com.k.selena.voice.MockHotwordDetector
import com.k.selena.voice.VoicePipeline
import java.util.concurrent.atomic.AtomicBoolean

class SelenaForegroundService : Service() {
    private val started = AtomicBoolean(false)
    private lateinit var stateMachine: SelenaStateMachine
    private lateinit var pipeline: VoicePipeline
    private lateinit var wakeLock: PowerManager.WakeLock

    override fun onCreate() {
        super.onCreate()
        stateMachine = SelenaStateMachine()
        val systemActions = AndroidSystemActions(this)
        val rootExecutor = MagiskRootExecutor()
        val commandRouter = CommandRouter(
            systemActions = systemActions,
            shellExecutor = RuntimeShellExecutor(),
            rootExecutor = rootExecutor
        )
        pipeline = VoicePipeline(
            hotwordDetector = MockHotwordDetector("Selena"),
            speechRecognizer = AudioRecordSpeechRecognizer(),
            stateMachine = stateMachine,
            commandRouter = commandRouter
        )
        val powerManager = getSystemService(PowerManager::class.java)
        // PARTIAL_WAKE_LOCK keeps the CPU alive while the screen is off so the voice pipeline
        // can keep running. It is held for the full lifetime of the foreground service and
        // released unconditionally in onDestroy(), making a timeout unnecessary here.
        wakeLock = powerManager.newWakeLock(
            PowerManager.PARTIAL_WAKE_LOCK,
            "Selena::VoicePipelineWakeLock"
        )
        Log.i(TAG, "Foreground service created")
    }

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        startForeground(NOTIFICATION_ID, createNotification())
        if (!wakeLock.isHeld) {
            wakeLock.acquire()
        }
        if (started.compareAndSet(false, true)) {
            Log.i(TAG, "Starting voice pipeline")
            pipeline.start()
        } else {
            Log.d(TAG, "Voice pipeline already running")
        }
        // TODO: Ask user to disable battery optimizations for higher survivability on OEM ROMs.
        //       Intent("android.settings.REQUEST_IGNORE_BATTERY_OPTIMIZATIONS") with package URI.
        return START_STICKY
    }

    override fun onDestroy() {
        Log.i(TAG, "Foreground service destroyed")
        pipeline.stop()
        if (wakeLock.isHeld) {
            wakeLock.release()
        }
        super.onDestroy()
    }

    override fun onBind(intent: Intent?): IBinder? = null

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
