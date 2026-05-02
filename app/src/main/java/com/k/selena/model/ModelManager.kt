package com.k.selena.model

import android.content.Context
import android.util.Log
import java.io.File
import java.io.FileOutputStream
import java.util.zip.ZipInputStream

/**
 * Manages the lifecycle of the Vosk speech recognition model in internal storage.
 *
 * On first launch the model may not be present in internal storage. [isModelAvailable] lets
 * callers check for the model before starting the voice pipeline. [copyFromAssets] handles the
 * one-time setup by unzipping the bundled asset into the app's private files directory, avoiding
 * APK bloat on repeat installations. A future download path can be plugged in at [downloadModel].
 *
 * All model files live under `<filesDir>/vosk-models/<modelName>/` so they are compatible with
 * the [VoskSpeechRecognizer] model path and the [StorageService] sync described in the README.
 */
class ModelManager(context: Context) {
    private val appContext = context.applicationContext
    private val modelsRoot = File(appContext.filesDir, MODELS_DIR)

    /**
     * Return the directory that [modelName] should occupy in internal storage.
     * The directory may or may not exist — call [isModelAvailable] first.
     */
    fun modelDir(modelName: String): File = File(modelsRoot, modelName)

    /**
     * Return `true` when [modelName] exists as a non-empty directory in internal storage,
     * indicating the model is ready to use.
     */
    fun isModelAvailable(modelName: String): Boolean {
        val dir = modelDir(modelName)
        val available = dir.isDirectory && (dir.list()?.isNotEmpty() == true)
        Log.d(TAG, "isModelAvailable($modelName)=$available dir=${dir.absolutePath}")
        return available
    }

    /**
     * Copy and unzip a model that was bundled in the app's `assets/<modelName>/` folder into
     * internal storage. The asset is expected to be a ZIP archive named `<modelName>.zip`.
     *
     * This is a blocking operation and should be called from a background thread.
     *
     * @return `true` on success, `false` if the asset is missing or extraction failed.
     */
    fun copyFromAssets(modelName: String): Boolean {
        val zipAssetPath = "$modelName.zip"
        return try {
            appContext.assets.open(zipAssetPath).use { inputStream ->
                ZipInputStream(inputStream).use { zis ->
                    val destRoot = modelsRoot.also { it.mkdirs() }
                    var entry = zis.nextEntry
                    while (entry != null) {
                        val outFile = File(destRoot, entry.name)
                        if (entry.isDirectory) {
                            outFile.mkdirs()
                        } else {
                            outFile.parentFile?.mkdirs()
                            FileOutputStream(outFile).use { out -> zis.copyTo(out) }
                        }
                        zis.closeEntry()
                        entry = zis.nextEntry
                    }
                }
            }
            Log.i(TAG, "Model '$modelName' extracted from assets to ${modelsRoot.absolutePath}")
            true
        } catch (e: Exception) {
            Log.w(TAG, "Could not copy model '$modelName' from assets: ${e.message}")
            false
        }
    }

    /**
     * Placeholder for a future network download path.
     *
     * When implemented, this should fetch the ZIP archive from [downloadUrl], unzip it into
     * [modelDir], and return `true` on success. The download should be performed on a background
     * thread (e.g., via WorkManager) and progress reported to the user.
     *
     * @return always `false` until implemented.
     */
    @Suppress("UNUSED_PARAMETER")
    fun downloadModel(modelName: String, downloadUrl: String): Boolean {
        Log.w(TAG, "downloadModel not yet implemented for model='$modelName'")
        return false
    }

    companion object {
        private const val TAG = "ModelManager"
        const val MODELS_DIR = "vosk-models"
        const val DEFAULT_MODEL_NAME = "vosk-model-small-en-us-0.22"
        const val DEFAULT_MODEL_URL =
            "https://alphacephei.com/vosk/models/vosk-model-small-en-us-0.22.zip"
    }
}
