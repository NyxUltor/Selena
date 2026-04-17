package com.k.selena.voice

import android.content.Context
import android.media.AudioManager
import android.media.ToneGenerator
import android.util.Log
import com.k.selena.command.CommandRouter
import com.k.selena.core.SelenaState
import com.k.selena.core.SelenaStateMachine
import java.util.concurrent.Executors
import java.util.concurrent.Future
import java.util.concurrent.atomic.AtomicBoolean

class VoicePipeline(
    context: Context,
    private val hotwordDetector: HotwordDetector,
    private val speechRecognizer: SpeechRecognizer,
    private val stateMachine: SelenaStateMachine,
    private val commandRouter: CommandRouter
) {
    private val running = AtomicBoolean(false)
    private val executor = Executors.newSingleThreadExecutor()
    private var worker: Future<*>? = null

    fun start() {
        if (!running.compareAndSet(false, true)) {
            return
        }
        worker = executor.submit {
            Log.i(TAG, "Voice loop started")
            while (running.get()) {
                try {
                    Thread.sleep(POLL_INTERVAL_MS)
                } catch (_: InterruptedException) {
                    Thread.currentThread().interrupt()
                    break
                }
                if (!hotwordDetector.pollForHotword()) {
                    continue
                }
                onHotwordDetected()
            }
        }
    }

    fun stop() {
        running.set(false)
        worker?.cancel(true)
        executor.shutdownNow()
        stateMachine.transitionTo(SelenaState.IDLE, "Voice pipeline stopped")
        Log.i(TAG, "Voice loop stopped")
    }

    private fun onHotwordDetected() {
        playBeep(start = true)
        stateMachine.transitionTo(SelenaState.LISTENING, "Hotword detected")
        Log.i(TAG, "Opening listening window")
        val recognized = speechRecognizer.recognizeForWindow(LISTEN_WINDOW_MS)
        stateMachine.transitionTo(SelenaState.EXECUTING, "Recognition complete")
        if (!recognized.isNullOrBlank()) {
            commandRouter.route(recognized)
        } else {
            Log.i(TAG, "Silence detected; closing listening window")
        }
        playBeep(start = false)
        stateMachine.transitionTo(SelenaState.IDLE, "Command handling done")
    }

    private fun playBeep(start: Boolean) {
        val tone = if (start) ToneGenerator.TONE_PROP_BEEP else ToneGenerator.TONE_PROP_ACK
        ToneGenerator(AudioManager.STREAM_NOTIFICATION, 80).startTone(tone, 120)
    }

    companion object {
        private const val TAG = "VoicePipeline"
        private const val POLL_INTERVAL_MS = 750L
        private const val LISTEN_WINDOW_MS = 2500L
    }
}
