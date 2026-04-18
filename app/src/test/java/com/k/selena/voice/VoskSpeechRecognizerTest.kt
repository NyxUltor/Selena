package com.k.selena.voice

import androidx.test.core.app.ApplicationProvider
import org.junit.Assert.assertNull
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [34])
class VoskSpeechRecognizerTest {

    /**
     * When no Vosk model is present in assets, [VoskSpeechRecognizer] must gracefully return
     * `null` for every recognition window rather than throwing.
     */
    @Test
    fun `returns null when model is absent from assets`() {
        val recognizer = VoskSpeechRecognizer(
            context = ApplicationProvider.getApplicationContext(),
            modelName = "vosk-model-does-not-exist"
        )
        // Model is absent — must not throw; must return null.
        val result = recognizer.recognizeForWindow(windowMs = 100)
        assertNull(result)
        recognizer.close()
    }
}
