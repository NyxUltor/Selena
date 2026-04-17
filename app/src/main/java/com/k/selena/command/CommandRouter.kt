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
    fun route(recognizedText: String) {
        Log.i(TAG, "Received recognized text: $recognizedText")
        val command = parser.parse(recognizedText)
        Log.i(TAG, "Parsed command: $command")
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
                        val exitCode = rootExecutor.execute(command.command)
                        Log.i(TAG, "Elevated shell executed with exitCode=$exitCode command=${command.command}")
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
    }

    companion object {
        private const val TAG = "CommandRouter"
    }
}
