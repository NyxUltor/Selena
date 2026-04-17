package com.k.selena.core

import android.Manifest
import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.Service
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.os.Build
import android.os.IBinder
import android.util.Log
import androidx.core.content.ContextCompat
import androidx.core.app.NotificationCompat
import com.k.selena.BuildConfig
import com.k.selena.R
import com.k.selena.command.CommandRouter
import com.k.selena.system.AndroidSystemActions
import com.k.selena.system.MagiskRootExecutor
import com.k.selena.system.RuntimeShellExecutor
import com.k.selena.voice.MockHotwordDetector
import com.k.selena.voice.MockSpeechRecognizer
import com.k.selena.voice.VoicePipeline
import java.util.concurrent.atomic.AtomicBoolean

class SelenaForegroundService : Service() {
    private val started = AtomicBoolean(false)
    private lateinit var stateMachine: SelenaStateMachine
    private lateinit var pipeline: VoicePipeline

    override fun onCreate() {
        super.onCreate()
        stateMachine = SelenaStateMachine(this)
        val systemActions = AndroidSystemActions(this)
        val rootExecutor = MagiskRootExecutor()
        val commandRouter = CommandRouter(
            systemActions = systemActions,
            shellExecutor = RuntimeShellExecutor(),
            rootExecutor = rootExecutor
        )
        pipeline = VoicePipeline(
            hotwordDetector = MockHotwordDetector(BuildConfig.HOTWORD),
            speechRecognizer = MockSpeechRecognizer(),
            stateMachine = stateMachine,
            commandRouter = commandRouter
        )
        Log.i(TAG, "Foreground service created with restored state=${stateMachine.currentState}")
    }

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        startForeground(NOTIFICATION_ID, createNotification())
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
        } else {
            Log.d(TAG, "Voice pipeline already running")
        }
        // TODO: Ask user to disable battery optimizations for higher survivability on OEM ROMs.
        return START_STICKY
    }

    override fun onDestroy() {
        Log.i(TAG, "Foreground service destroyed")
        pipeline.stop()
        super.onDestroy()
    }

    override fun onBind(intent: Intent?): IBinder? = null

    private fun hasRecordAudioPermission(): Boolean =
        ContextCompat.checkSelfPermission(
            this,
            Manifest.permission.RECORD_AUDIO
        ) == PackageManager.PERMISSION_GRANTED

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
