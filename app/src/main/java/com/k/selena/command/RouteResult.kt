package com.k.selena.command

/**
 * Result returned by [CommandRouter.route] to signal whether a command was executed
 * immediately or requires explicit user confirmation before execution.
 */
sealed class RouteResult {
    /** The command was dispatched and executed without further interaction required. */
    data object Executed : RouteResult()

    /**
     * An elevated (root) command was recognised. Execution is intentionally deferred until
     * the user explicitly confirms via [CommandRouter.executeElevated].
     *
     * @param command the shell command payload (without the `sudo` prefix) that is pending.
     */
    data class PendingConfirmation(val command: String) : RouteResult()
}
