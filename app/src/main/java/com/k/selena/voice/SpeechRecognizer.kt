package com.k.selena.voice

interface SpeechRecognizer : AutoCloseable {
    /**
     * Open the microphone for [windowMs] milliseconds, feed the captured PCM audio to the
     * underlying recognition engine, and return the recognised text. Returns `null` on silence,
     * engine unavailability, or a recognition error.
     */
    fun recognizeForWindow(windowMs: Long): String?

    /** Release all engine resources. Default is a no-op. */
    override fun close(): Unit = Unit
}
