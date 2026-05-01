package com.k.selena.voice

/**
 * Abstraction over an audio feedback mechanism used to read back staged commands to the user
 * before they are confirmed for execution.
 *
 * Implementations are expected to be non-blocking: [announce] enqueues speech and returns
 * immediately. A null/no-op implementation is acceptable when no audio output is available.
 */
interface CommandAnnouncer {
    /**
     * Announce [text] to the user asynchronously.
     * Implementations must not throw; errors should be handled internally.
     */
    fun announce(text: String)
}
