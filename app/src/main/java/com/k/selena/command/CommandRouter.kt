package com.k.selena.command

import android.util.Log
import com.k.selena.system.RootCommandExecutor
import com.k.selena.system.ShellExecutor
import com.k.selena.system.SystemActions

class CommandRouter(
    private val parser: CommandParser = CommandParser(),
    private val systemActions: SystemActions,
    private val shellExecutor: ShellExecutor,
    private val rootExecutor: RootCommandExecutor
) {
    /**
     * Parse [recognizedText] and dispatch the resulting command.
     *
     * Elevated (`sudo`) commands are **not** executed immediately. Instead, a
     * [RouteResult.PendingConfirmation] is returned so the caller can present an explicit
     * confirmation step before calling [executeElevated].
     *
     * @return [RouteResult.Executed] for all non-elevated commands, or
     *         [RouteResult.PendingConfirmation] for elevated commands that require confirmation.
     */
    fun route(recognizedText: String): RouteResult {
        Log.i(TAG, "Received recognized text: $recognizedText")
        val command = parser.parse(recognizedText)
        Log.i(TAG, "Parsed command: $command")
        var result: RouteResult = RouteResult.Executed
        runCatching {
            when (command) {
                is SelenaCommand.OpenApp -> {
                    val opened = systemActions.openApp(command.query)
                    Log.i(TAG, "OpenApp result=$opened query=${command.query}")
                }
                is SelenaCommand.OpenTermux -> {
                    val opened = systemActions.openTermuxWithCommand(command.command)
                    Log.i(TAG, "OpenTermux result=$opened command=${command.command}")
                }
                is SelenaCommand.RunShell -> {
                    if (command.elevated) {
                        // Elevated commands must not execute without explicit confirmation.
                        Log.i(TAG, "Elevated command staged for confirmation: ${command.command}")
                        result = RouteResult.PendingConfirmation(command.command)
                    } else {
                        val exitCode = shellExecutor.execute(command.command)
                        Log.i(TAG, "Shell executed with exitCode=$exitCode command=${command.command}")
                    }
                }
                SelenaCommand.Unknown -> Log.w(TAG, "Unknown command: $recognizedText")
            }
        }.onFailure { throwable ->
            Log.e(TAG, "Command routing failed for text=$recognizedText", throwable)
        }
        return result
    }

    /**
     * Execute [command] with root privileges via the configured [RootCommandExecutor].
     *
     * This method is intentionally separate from [route] and should only be called after the
     * user has explicitly confirmed a previously staged [RouteResult.PendingConfirmation].
     *
     * @return the exit code of the root command, or -1 on failure.
     */
    fun executeElevated(command: String): Int {
        Log.i(TAG, "Executing confirmed elevated command: $command")
        return runCatching {
            val exitCode = rootExecutor.execute(command)
            Log.i(TAG, "Elevated command executed with exitCode=$exitCode command=$command")
            exitCode
        }.getOrElse { throwable ->
            Log.e(TAG, "Elevated command execution failed for command=$command", throwable)
            -1
        }
    }

    companion object {
        private const val TAG = "CommandRouter"
    }
}
