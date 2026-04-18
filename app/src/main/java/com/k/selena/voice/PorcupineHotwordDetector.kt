package com.k.selena.voice

import ai.picovoice.porcupine.Porcupine
import ai.picovoice.porcupine.PorcupineException
import android.content.Context
import android.media.AudioFormat
import android.media.AudioRecord
import android.media.MediaRecorder
import android.util.Log
import java.io.File
import java.util.concurrent.atomic.AtomicBoolean
import kotlin.math.max

/**
 * Real offline hotword detector backed by Picovoice Porcupine.
 *
 * Behaviour:
 * - On construction it tries to build a [Porcupine] instance.
 *   - If a custom keyword file `porcupine_keyword.ppn` is present in `assets/`, that is used
 *     with the configured sensitivity.
 *   - Otherwise the built-in "PORCUPINE" keyword is used as a safe default.
 * - A dedicated background thread opens an [AudioRecord] session and feeds 16-kHz PCM frames
 *   directly to the Porcupine engine. A detected hotword atomically sets a flag.
 * - [pollForHotword] reads and clears that flag — it is always non-blocking.
 * - [pause]/[resume] stop and restart the audio capture thread so that the ASR window can
 *   open its own [AudioRecord] without contention.
 * - [close] tears down everything: capture thread, AudioRecord, and Porcupine engine.
 *
 * Configuration (set in `gradle.properties`):
 *   PICOVOICE_ACCESS_KEY=<your_key>   — obtain free at https://console.picovoice.ai/
 *
 * To use a custom "Selena" wake-word:
 *   1. Train the keyword at https://console.picovoice.ai/ppn
 *   2. Copy the resulting `.ppn` file to `app/src/main/assets/porcupine_keyword.ppn`
 */
class PorcupineHotwordDetector(
    private val context: Context,
    private val accessKey: String,
    private val sensitivity: Float = 0.5f
) : HotwordDetector {

    private val detected = AtomicBoolean(false)
    private val captureRunning = AtomicBoolean(false)
    private var porcupine: Porcupine? = null
    private var audioRecord: AudioRecord? = null
    private var captureThread: Thread? = null

    init {
        if (accessKey.isBlank()) {
            Log.w(TAG, "No Picovoice access key configured; PorcupineHotwordDetector is inactive")
        } else {
            try {
                porcupine = buildPorcupine()
                startCapture()
                Log.i(TAG, "Porcupine hotword engine initialised (frameLength=${porcupine!!.frameLength})")
            } catch (e: PorcupineException) {
                Log.e(TAG, "Porcupine initialisation failed: ${e.message}")
            } catch (e: Exception) {
                Log.e(TAG, "Unexpected error initialising Porcupine", e)
            }
        }
    }

    // -----------------------------------------------------------------------------------------
    // HotwordDetector
    // -----------------------------------------------------------------------------------------

    override fun pollForHotword(): Boolean = detected.getAndSet(false)

    override fun pause() {
        stopCapture()
        Log.d(TAG, "Audio capture paused for ASR window")
    }

    override fun resume() {
        if (porcupine != null) {
            startCapture()
            Log.d(TAG, "Audio capture resumed after ASR window")
        }
    }

    override fun close() {
        stopCapture()
        porcupine?.delete()
        porcupine = null
        Log.i(TAG, "Porcupine engine released")
    }

    // -----------------------------------------------------------------------------------------
    // Internal
    // -----------------------------------------------------------------------------------------

    private fun buildPorcupine(): Porcupine {
        val builder = Porcupine.Builder()
            .setAccessKey(accessKey)
            .setSensitivity(sensitivity)

        val customKeywordAsset = "porcupine_keyword.ppn"
        return if (assetExists(customKeywordAsset)) {
            val destFile = copyAssetToCache(customKeywordAsset)
            Log.i(TAG, "Using custom keyword model: $customKeywordAsset")
            builder.setKeywordPath(destFile.absolutePath).build(context)
        } else {
            Log.i(TAG, "No custom keyword found; using built-in PORCUPINE keyword")
            builder.setKeyword(Porcupine.BuiltInKeyword.PORCUPINE).build(context)
        }
    }

    private fun startCapture() {
        val engine = porcupine ?: return
        if (!captureRunning.compareAndSet(false, true)) return

        val frameLength = engine.frameLength
        val minBufBytes = AudioRecord.getMinBufferSize(
            SAMPLE_RATE,
            AudioFormat.CHANNEL_IN_MONO,
            AudioFormat.ENCODING_PCM_16BIT
        )
        // Buffer must hold at least one full frame; double for headroom.
        val bufBytes = max(minBufBytes, frameLength * 2 * Short.SIZE_BYTES)

        val recorder = AudioRecord(
            MediaRecorder.AudioSource.VOICE_RECOGNITION,
            SAMPLE_RATE,
            AudioFormat.CHANNEL_IN_MONO,
            AudioFormat.ENCODING_PCM_16BIT,
            bufBytes
        )

        if (recorder.state != AudioRecord.STATE_INITIALIZED) {
            Log.e(TAG, "AudioRecord failed to initialise for hotword capture")
            recorder.release()
            captureRunning.set(false)
            return
        }

        audioRecord = recorder
        recorder.startRecording()

        captureThread = Thread({
            val frame = ShortArray(frameLength)
            Log.d(TAG, "Hotword capture thread started")
            while (captureRunning.get()) {
                val read = recorder.read(frame, 0, frameLength)
                if (read == frameLength) {
                    try {
                        val keywordIndex = engine.process(frame)
                        if (keywordIndex >= 0) {
                            Log.i(TAG, "Porcupine hotword detected (keywordIndex=$keywordIndex)")
                            detected.set(true)
                        }
                    } catch (e: PorcupineException) {
                        Log.e(TAG, "Porcupine processing error", e)
                    }
                }
            }
            Log.d(TAG, "Hotword capture thread stopped")
        }, "porcupine-capture")
        captureThread!!.isDaemon = true
        captureThread!!.start()
    }

    private fun stopCapture() {
        captureRunning.set(false)
        captureThread?.interrupt()
        captureThread?.join(JOIN_TIMEOUT_MS)
        captureThread = null
        runCatching { audioRecord?.stop() }
        audioRecord?.release()
        audioRecord = null
    }

    private fun assetExists(name: String): Boolean =
        runCatching { context.assets.open(name).close(); true }.getOrDefault(false)

    private fun copyAssetToCache(name: String): File {
        val dest = File(context.cacheDir, name)
        if (!dest.exists()) {
            context.assets.open(name).use { input ->
                dest.outputStream().use { output -> input.copyTo(output) }
            }
        }
        return dest
    }

    companion object {
        private const val TAG = "PorcupineDetector"
        private const val SAMPLE_RATE = 16_000
        private const val JOIN_TIMEOUT_MS = 1_000L
    }
}
