package com.k.selena.voice

import android.content.Context
import android.media.AudioFormat
import android.media.AudioRecord
import android.media.MediaRecorder
import android.util.Log
import org.json.JSONObject
import org.vosk.Model
import org.vosk.Recognizer
import org.vosk.android.StorageService
import java.io.IOException

/**
 * Offline speech recogniser backed by [Vosk](https://alphacephei.com/vosk/).
 *
 * ## Setup
 * 1. Download a small model from https://alphacephei.com/vosk/models
 *    (recommended: `vosk-model-small-en-us-0.22`, ~40 MB).
 * 2. Unzip the model and copy the **directory** into
 *    `app/src/main/assets/<VOSK_MODEL_NAME>/`
 *    where `VOSK_MODEL_NAME` matches the `buildConfigField` in `build.gradle.kts`.
 * 3. Build and run — the model is synced from assets to internal storage on first use.
 *
 * ## Behaviour
 * - On construction, [StorageService.sync] copies the model from assets to `filesDir` (no-op
 *   on subsequent runs if the directory already exists).  This is synchronous and fast after the
 *   first install.
 * - [recognizeForWindow] opens an [AudioRecord], streams raw 16-kHz PCM to a [Recognizer] for
 *   the requested window, then calls [Recognizer.finalResult] to get the best transcript.
 * - Returns `null` when the model is not available or recognition yields an empty string.
 * - [close] releases the underlying [Model]. A [Recognizer] is created and closed per
 *   recognition window so that resources are not held between calls.
 */
class VoskSpeechRecognizer(
    private val context: Context,
    private val modelName: String
) : SpeechRecognizer {

    private var model: Model? = null

    init {
        loadModel()
    }

    // -----------------------------------------------------------------------------------------
    // SpeechRecognizer
    // -----------------------------------------------------------------------------------------

    override fun recognizeForWindow(windowMs: Long): String? {
        val m = model ?: run {
            Log.w(TAG, "Vosk model not available; returning null")
            return null
        }

        val bufferSize = AudioRecord.getMinBufferSize(
            SAMPLE_RATE,
            AudioFormat.CHANNEL_IN_MONO,
            AudioFormat.ENCODING_PCM_16BIT
        )
        if (bufferSize == AudioRecord.ERROR_BAD_VALUE || bufferSize == AudioRecord.ERROR) {
            Log.e(TAG, "Cannot determine AudioRecord minimum buffer size")
            return null
        }

        val recorder = AudioRecord(
            MediaRecorder.AudioSource.VOICE_RECOGNITION,
            SAMPLE_RATE,
            AudioFormat.CHANNEL_IN_MONO,
            AudioFormat.ENCODING_PCM_16BIT,
            bufferSize
        )

        if (recorder.state != AudioRecord.STATE_INITIALIZED) {
            Log.e(TAG, "AudioRecord failed to initialise; check RECORD_AUDIO permission")
            recorder.release()
            return null
        }

        val recognizer = Recognizer(m, SAMPLE_RATE.toFloat())
        var recordingStarted = false
        return try {
            recorder.startRecording()
            recordingStarted = true
            Log.i(TAG, "Vosk recognition window open (${windowMs}ms)")

            val buffer = ByteArray(bufferSize)
            val deadline = System.currentTimeMillis() + windowMs

            while (System.currentTimeMillis() < deadline) {
                val read = recorder.read(buffer, 0, buffer.size)
                if (read > 0) {
                    recognizer.acceptWaveForm(buffer, read)
                }
            }

            parseText(recognizer.finalResult).also { text ->
                Log.i(TAG, "Vosk final result: ${text ?: "<silence>"}")
            }
        } catch (e: Exception) {
            Log.e(TAG, "Vosk recognition error", e)
            null
        } finally {
            if (recordingStarted) {
                runCatching { recorder.stop() }
                    .onFailure { Log.w(TAG, "AudioRecord stop failed", it) }
            }
            recorder.release()
            recognizer.close()
            Log.i(TAG, "Vosk recognition window closed")
        }
    }

    override fun close() {
        model?.close()
        model = null
        Log.i(TAG, "Vosk model released")
    }

    // -----------------------------------------------------------------------------------------
    // Internal
    // -----------------------------------------------------------------------------------------

    private fun loadModel() {
        try {
            StorageService.sync(context, modelName, object : StorageService.Callback {
                override fun onSuccess(filesDir: String) {
                    try {
                        model = Model(filesDir)
                        Log.i(TAG, "Vosk model loaded from $filesDir")
                    } catch (e: IOException) {
                        Log.e(TAG, "Failed to load Vosk model from $filesDir", e)
                    }
                }

                override fun onFailure(e: IOException) {
                    Log.e(
                        TAG,
                        "Vosk model sync failed — ensure the model directory " +
                            "'$modelName' is present in app/src/main/assets/. " +
                            "Download models from https://alphacephei.com/vosk/models",
                        e
                    )
                }
            })
        } catch (e: Exception) {
            Log.e(TAG, "Unexpected error starting Vosk model sync", e)
        }
    }

    /**
     * Vosk returns JSON like `{"text": "open settings"}`.
     * Returns `null` when the text field is blank or absent.
     */
    private fun parseText(json: String): String? {
        return try {
            val text = JSONObject(json).optString("text", "").trim()
            text.ifBlank { null }
        } catch (e: Exception) {
            Log.w(TAG, "Could not parse Vosk result JSON: $json")
            null
        }
    }

    companion object {
        private const val TAG = "VoskSpeechRecognizer"
        private const val SAMPLE_RATE = 16_000
    }
}
