package com.k.selena.voice

import android.util.Log
import java.util.concurrent.atomic.AtomicInteger

class MockSpeechRecognizer : SpeechRecognizer {
    private val index = AtomicInteger(0)
    private val scripted = listOf(
        "open termux and run ls",
        "open settings",
        "sudo run id",
        null
    )

    override fun recognizeForWindow(windowMs: Long): String? {
        // TODO: Replace mock with offline speech recognizer implementation.
        val value = scripted[index.getAndIncrement() % scripted.size]
        Log.i(TAG, "Mock recognized text in ${windowMs}ms window: ${value ?: "<silence>"}")
        return value
    }

    companion object {
        private const val TAG = "MockSpeechRecognizer"
    }
}
