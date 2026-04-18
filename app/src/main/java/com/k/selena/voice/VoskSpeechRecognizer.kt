package com.k.selena.voice

import android.content.Context
import android.media.AudioFormat
import android.media.AudioRecord
import android.media.MediaRecorder
import android.util.Log
import org.json.JSONObject
import org.vosk.KaldiRecognizer
import org.vosk.Model
import java.io.File
import java.io.IOException

/**
 * Offline speech recogniser backed by [Vosk](https://alphacephei.com/vosk/).
 *
 * ## Setup
 * 1. Download a small model from https://alphacephei.com/vosk/models
 *    (recommended: `vosk-model-small-en-us-0.22`, ~40 MB).
 * 2. Unzip and copy the **directory** into
 *    `app/src/main/assets/<VOSK_MODEL_NAME>/`
 *    where `VOSK_MODEL_NAME` matches the `buildConfigField` in `build.gradle.kts`.
 * 3. Build and run — the model is synced from assets to internal storage on first install; on
 *    subsequent launches the cached copy is reused.
 *
 * ## Behaviour
 * - Constructor copies the model directory from assets to `filesDir` on first run (skipped when
 *   the destination already exists). If the model is absent from assets, a warning is logged and
 *   every [recognizeForWindow] call returns `null`.
 * - [recognizeForWindow] opens an [AudioRecord], streams raw 16-kHz PCM to a [KaldiRecognizer]
 *   for the requested window duration, then calls [KaldiRecognizer.finalResult] to obtain the
 *   best transcript.
 * - [close] releases the [Model]. A fresh [KaldiRecognizer] is created and destroyed per
 *   recognition window to keep memory pressure low.
 */
class VoskSpeechRecognizer(
    private val context: Context,
    private val modelName: String
) : SpeechRecognizer {

    private var model: Model? = null

    init {
        try {
            val modelDir = syncModelFromAssets()
            if (modelDir != null) {
                model = Model(modelDir.absolutePath)
                Log.i(TAG, "Vosk model loaded from ${modelDir.absolutePath}")
            } else {
                Log.w(
                    TAG,
                    "Vosk model '$modelName' not found in assets — place the model directory " +
                        "at app/src/main/assets/$modelName/. " +
                        "Download models from https://alphacephei.com/vosk/models"
                )
            }
        } catch (e: Exception) {
            Log.e(TAG, "Failed to load Vosk model", e)
        }
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

        val recognizer = KaldiRecognizer(m, SAMPLE_RATE.toFloat())
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

    /**
     * Copies the model directory from assets to [Context.getFilesDir] on first run.
     * Returns the destination [File], or `null` if the model is absent from assets.
     */
    private fun syncModelFromAssets(): File? {
        val destDir = File(context.filesDir, modelName)
        if (destDir.exists() && destDir.isDirectory) {
            return destDir
        }
        val assetList = try {
            context.assets.list(modelName) ?: return null
        } catch (e: IOException) {
            return null
        }
        if (assetList.isEmpty()) return null
        copyAssetDir(modelName, destDir)
        return destDir
    }

    private fun copyAssetDir(assetPath: String, destDir: File) {
        destDir.mkdirs()
        val children = context.assets.list(assetPath) ?: return
        for (child in children) {
            val childAssetPath = "$assetPath/$child"
            val childDest = File(destDir, child)
            if (context.assets.list(childAssetPath)?.isNotEmpty() == true) {
                copyAssetDir(childAssetPath, childDest)
            } else {
                context.assets.open(childAssetPath).use { input ->
                    childDest.outputStream().use { output -> input.copyTo(output) }
                }
            }
        }
    }

    /**
     * Vosk returns JSON like `{"text": "open settings"}`.
     * Returns `null` when the text field is blank or absent.
     */
    private fun parseText(json: String): String? {
        return try {
            JSONObject(json).optString("text", "").trim().ifBlank { null }
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
