package com.k.selena.voice

import android.media.AudioManager
import android.media.ToneGenerator
import android.util.Log
import com.k.selena.command.CommandRouter
import com.k.selena.command.RouteResult
import com.k.selena.core.SelenaState
import com.k.selena.core.SelenaStateMachine
import java.util.concurrent.Executors
import java.util.concurrent.Future
import java.util.concurrent.atomic.AtomicBoolean

class VoicePipeline(
    private val hotwordDetector: HotwordDetector,
    private val speechRecognizer: SpeechRecognizer,
    private val stateMachine: SelenaStateMachine,
    private val commandRouter: CommandRouter,
    private val commandAnnouncer: CommandAnnouncer? = null,
    private val audioFocusManager: AudioFocusManager? = null
) {
    private val running = AtomicBoolean(false)
    private val executor = Executors.newSingleThreadExecutor()
    private val toneGeneratorLock = Any()
    private var toneGenerator: ToneGenerator? = null
    private var worker: Future<*>? = null

    fun start() {
        if (!running.compareAndSet(false, true)) {
            Log.d(TAG, "Voice loop already running")
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
                runCatching {
                    onHotwordDetected()
                }.onFailure { throwable ->
                    Log.e(TAG, "Voice cycle failed; returning to IDLE", throwable)
                    stateMachine.transitionTo(SelenaState.IDLE, "Voice cycle failure recovery")
                }
            }
        }
    }

    fun stop() {
        running.set(false)
        worker?.cancel(true)
        executor.shutdownNow()
        releaseToneGenerator()
        hotwordDetector.close()
        speechRecognizer.close()
        stateMachine.transitionTo(SelenaState.IDLE, "Voice pipeline stopped")
        Log.i(TAG, "Voice loop stopped")
    }

    private fun onHotwordDetected() {
        // Pause hotword audio capture before opening the ASR microphone window so that
        // the two AudioRecord sessions do not compete for the microphone.
        hotwordDetector.pause()
        // Request audio focus to duck background media during the listening window.
        audioFocusManager?.requestFocus()
        try {
            playBeep(start = true)
            stateMachine.transitionTo(SelenaState.LISTENING, "Hotword detected")
            Log.i(TAG, "Opening listening window for ${LISTEN_WINDOW_MS}ms")
            val recognized = speechRecognizer.recognizeForWindow(LISTEN_WINDOW_MS)
            stateMachine.transitionTo(SelenaState.EXECUTING, "Recognition complete")
            if (!recognized.isNullOrBlank()) {
                Log.i(TAG, "Recognized text=$recognized")
                val result = commandRouter.route(recognized)
                if (result is RouteResult.PendingConfirmation) {
                    handleElevatedConfirmation(result.command)
                }
            } else {
                Log.i(TAG, "Silence detected; closing listening window")
            }
            playBeep(start = false)
            stateMachine.transitionTo(SelenaState.IDLE, "Command handling done")
        } finally {
            // Always resume hotword detection and release audio focus, even if recognition
            // or routing threw.
            audioFocusManager?.abandonFocus()
            hotwordDetector.resume()
        }
    }

    /**
     * Handle an elevated command that requires user confirmation before root execution.
     *
     * Announces the command via TTS, opens a short confirmation window, and only executes
     * via [CommandRouter.executeElevated] if the user speaks an explicit confirmation phrase.
     * Any other response (or silence) cancels the command and returns to IDLE.
     */
    private fun handleElevatedConfirmation(command: String) {
        stateMachine.transitionTo(SelenaState.AWAITING_CONFIRMATION, "Elevated command staged")
        Log.i(TAG, "Awaiting confirmation for elevated command: $command")
        commandAnnouncer?.announce("Confirming: run $command as root?")
        val confirmation = speechRecognizer.recognizeForWindow(CONFIRMATION_WINDOW_MS)
        val normalised = confirmation?.lowercase()?.trim() ?: ""
        if (normalised in CONFIRM_PHRASES) {
            Log.i(TAG, "Elevated command confirmed — executing: $command")
            commandRouter.executeElevated(command)
        } else {
            Log.w(TAG, "Elevated command cancelled — no valid confirmation received " +
                    "(command=$command, response=${confirmation ?: "<silence>"})")
        }
    }

    private fun playBeep(start: Boolean) {
        val tone = if (start) ToneGenerator.TONE_PROP_BEEP else ToneGenerator.TONE_PROP_ACK
        getOrCreateToneGenerator().startTone(tone, BEEP_DURATION_MS.toInt())
    }

    private fun getOrCreateToneGenerator(): ToneGenerator {
        return synchronized(toneGeneratorLock) {
            toneGenerator ?: ToneGenerator(AudioManager.STREAM_NOTIFICATION, 80).also {
                toneGenerator = it
            }
        }
    }

    private fun releaseToneGenerator() {
        synchronized(toneGeneratorLock) {
            toneGenerator?.release()
            toneGenerator = null
        }
    }

    companion object {
        private const val TAG = "VoicePipeline"
        private const val POLL_INTERVAL_MS = 750L
        private const val LISTEN_WINDOW_MS = 2500L
        private const val BEEP_DURATION_MS = 150L

        /** Confirmation window kept intentionally short to reduce latency for legitimate commands. */
        private const val CONFIRMATION_WINDOW_MS = 3000L

        /** Set of normalised phrases that count as an explicit confirmation. */
        private val CONFIRM_PHRASES = setOf("yes", "confirm", "yes confirm", "confirm yes")
    }
}
