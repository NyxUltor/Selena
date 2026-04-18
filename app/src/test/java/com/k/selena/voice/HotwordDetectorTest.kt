package com.k.selena.voice

import androidx.test.core.app.ApplicationProvider
import org.junit.Assert.assertFalse
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [34])
class HotwordDetectorTest {

    /**
     * When no Picovoice access key is configured, [PorcupineHotwordDetector] must remain
     * inactive: [HotwordDetector.pollForHotword] always returns `false` and the lifecycle
     * methods must not throw.
     */
    @Test
    fun `porcupine is inactive and safe without access key`() {
        val detector = PorcupineHotwordDetector(
            context = ApplicationProvider.getApplicationContext(),
            accessKey = ""
        )
        assertFalse(detector.pollForHotword())
        // Lifecycle methods must be no-ops when engine was not initialised.
        detector.pause()
        detector.resume()
        detector.close()
        // After close, poll must still be safe.
        assertFalse(detector.pollForHotword())
    }

    /**
     * [MockHotwordDetector] triggers every 10 polls. Verify interface contract:
     * the default [pause], [resume], and [close] must be callable without error.
     */
    @Test
    fun `mock detector interface defaults are safe`() {
        val detector = MockHotwordDetector("Selena")
        detector.pause()
        detector.resume()
        detector.close()
        // MockHotwordDetector fires on the 10th poll.
        repeat(9) { assertFalse(detector.pollForHotword()) }
        assert(detector.pollForHotword())
    }
}
