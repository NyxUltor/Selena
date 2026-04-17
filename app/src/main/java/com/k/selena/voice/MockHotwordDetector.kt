package com.k.selena.voice

import android.util.Log
import java.util.concurrent.atomic.AtomicInteger

class MockHotwordDetector(private val hotword: String) : HotwordDetector {
    private val pollCount = AtomicInteger(0)

    override fun pollForHotword(): Boolean {
        // TODO: Integrate a real offline hotword engine (e.g., Porcupine or custom model) here.
        val count = pollCount.incrementAndGet()
        val detected = count % 10 == 0
        if (detected) {
            Log.i(TAG, "Mock hotword detected: $hotword")
        } else {
            Log.v(TAG, "Mock hotword not detected")
        }
        return detected
    }

    companion object {
        private const val TAG = "MockHotwordDetector"
    }
}
