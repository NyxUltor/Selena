package com.k.selena.voice

import android.content.Context
import android.media.AudioAttributes
import android.media.AudioFocusRequest
import android.media.AudioManager
import android.util.Log

/**
 * Requests and releases audio focus so that background media is ducked while Selena is
 * actively listening for commands.
 *
 * [AudioFocusRequest] (API 26) is used unconditionally because Selena's `minSdk` is 26.
 * If focus cannot be obtained the service degrades gracefully — listening continues without
 * ducking. Call [abandonFocus] when the listening window closes.
 */
class AudioFocusManager(context: Context) {
    private val audioManager =
        context.applicationContext.getSystemService(Context.AUDIO_SERVICE) as AudioManager

    private val focusChangeListener = AudioManager.OnAudioFocusChangeListener { focusChange ->
        Log.d(TAG, "Audio focus change: $focusChange")
    }

    private val focusRequest: AudioFocusRequest = AudioFocusRequest.Builder(
        AudioManager.AUDIOFOCUS_GAIN_TRANSIENT_MAY_DUCK
    )
        .setAudioAttributes(
            AudioAttributes.Builder()
                .setUsage(AudioAttributes.USAGE_ASSISTANT)
                .setContentType(AudioAttributes.CONTENT_TYPE_SPEECH)
                .build()
        )
        .setAcceptsDelayedFocusGain(false)
        .setWillPauseWhenDucked(false)
        .setOnAudioFocusChangeListener(focusChangeListener)
        .build()

    private var focusHeld = false

    /**
     * Request transient audio focus with ducking.
     *
     * @return `true` if focus was granted, `false` if denied (operation continues without ducking).
     */
    fun requestFocus(): Boolean {
        if (focusHeld) return true
        val result = audioManager.requestAudioFocus(focusRequest)
        focusHeld = result == AudioManager.AUDIOFOCUS_REQUEST_GRANTED
        if (!focusHeld) {
            Log.w(TAG, "Audio focus request denied — listening continues without ducking")
        } else {
            Log.d(TAG, "Audio focus granted")
        }
        return focusHeld
    }

    /**
     * Abandon previously held audio focus. Safe to call when focus was never acquired.
     */
    fun abandonFocus() {
        if (!focusHeld) return
        audioManager.abandonAudioFocusRequest(focusRequest)
        focusHeld = false
        Log.d(TAG, "Audio focus abandoned")
    }

    companion object {
        private const val TAG = "AudioFocusManager"
    }
}
