package com.k.selena.voice

interface SpeechRecognizer {
    fun recognizeForWindow(windowMs: Long): String?
}
