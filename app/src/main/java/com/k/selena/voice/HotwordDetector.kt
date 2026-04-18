package com.k.selena.voice

interface HotwordDetector : AutoCloseable {
    /**
     * Returns `true` if the hotword was detected since the last poll.
     * Implementations must be non-blocking; any heavyweight processing runs on a
     * separate internal thread and only the flag is sampled here.
     */
    fun pollForHotword(): Boolean

    /**
     * Temporarily pause audio capture. Called by [VoicePipeline] before it opens its
     * own [android.media.AudioRecord] for ASR so that two capture sessions do not
     * compete for the microphone. Default is a no-op for mock implementations.
     */
    fun pause(): Unit = Unit

    /**
     * Resume audio capture after the ASR window has closed. Default is a no-op.
     */
    fun resume(): Unit = Unit

    /** Release all engine resources. Default is a no-op. */
    override fun close(): Unit = Unit
}
