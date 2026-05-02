package com.k.selena.voice

import android.content.Context
import android.speech.tts.TextToSpeech
import android.util.Log
import java.util.Locale

/**
 * [CommandAnnouncer] implementation backed by Android's [TextToSpeech] engine.
 *
 * Initialisation is asynchronous; if [announce] is called before the engine is ready the
 * utterance is silently dropped (the confirmation timeout window will expire and the command
 * will be safely cancelled). Call [shutdown] when the owning component is destroyed.
 */
class TtsCommandAnnouncer(context: Context) : CommandAnnouncer {
    private val appContext = context.applicationContext
    @Volatile private var tts: TextToSpeech? = null
    @Volatile private var ready = false

    init {
        tts = TextToSpeech(appContext) { status ->
            if (status == TextToSpeech.SUCCESS) {
                val langResult = tts?.setLanguage(Locale.US)
                ready = langResult != TextToSpeech.LANG_MISSING_DATA &&
                        langResult != TextToSpeech.LANG_NOT_SUPPORTED
                if (!ready) {
                    Log.w(TAG, "TTS language not supported; announcements will be silent")
                } else {
                    Log.i(TAG, "TTS engine initialised successfully")
                }
            } else {
                Log.w(TAG, "TTS init failed with status=$status; announcements will be silent")
            }
        }
    }

    override fun announce(text: String) {
        if (!ready) {
            Log.w(TAG, "TTS not ready; dropping announcement: $text")
            return
        }
        tts?.speak(text, TextToSpeech.QUEUE_FLUSH, null, UTTERANCE_ID)
            ?: Log.w(TAG, "TTS engine null; dropping announcement: $text")
    }

    /** Release TTS engine resources. Must be called when the owning component is destroyed. */
    fun shutdown() {
        ready = false
        tts?.stop()
        tts?.shutdown()
        tts = null
        Log.i(TAG, "TTS engine shut down")
    }

    companion object {
        private const val TAG = "TtsCommandAnnouncer"
        private const val UTTERANCE_ID = "selena_confirmation"
    }
}
