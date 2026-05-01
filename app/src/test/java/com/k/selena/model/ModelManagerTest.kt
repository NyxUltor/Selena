package com.k.selena.model

import androidx.test.core.app.ApplicationProvider
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import java.io.File

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [34])
class ModelManagerTest {

    private val manager = ModelManager(ApplicationProvider.getApplicationContext())

    @Test
    fun `isModelAvailable returns false when model directory does not exist`() {
        assertFalse(manager.isModelAvailable("vosk-model-does-not-exist"))
    }

    @Test
    fun `isModelAvailable returns false for empty model directory`() {
        val dir = manager.modelDir("empty-model")
        dir.mkdirs()
        try {
            assertFalse(
                "An empty directory should not be considered an available model",
                manager.isModelAvailable("empty-model")
            )
        } finally {
            dir.deleteRecursively()
        }
    }

    @Test
    fun `isModelAvailable returns true when model directory has files`() {
        val dir = manager.modelDir("fake-model")
        dir.mkdirs()
        val sentinel = File(dir, "conf").also { it.mkdirs() }
        try {
            assertTrue(
                "A non-empty directory should be reported as an available model",
                manager.isModelAvailable("fake-model")
            )
        } finally {
            dir.deleteRecursively()
            sentinel.deleteRecursively()
        }
    }

    @Test
    fun `copyFromAssets returns false gracefully when asset zip is absent`() {
        val result = manager.copyFromAssets("vosk-model-does-not-exist")
        assertFalse("copyFromAssets must return false when the zip asset is missing", result)
    }

    @Test
    fun `downloadModel returns false as it is not yet implemented`() {
        val result = manager.downloadModel(
            "vosk-model-small-en-us-0.22",
            ModelManager.DEFAULT_MODEL_URL
        )
        assertFalse("downloadModel should return false until implemented", result)
    }
}
