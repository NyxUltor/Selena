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
        val command = parser.parse(recognizedText)
        Log.i(TAG, "Routing command: $command")
        when (command) {
            is SelenaCommand.OpenApp -> systemActions.openApp(command.query)
            is SelenaCommand.OpenTermux -> systemActions.openTermuxWithCommand(command.command)
            is SelenaCommand.RunShell -> {
                if (command.elevated) {
                    rootExecutor.execute(command.command)
                } else {
                    shellExecutor.execute(command.command)
                }
            }
            SelenaCommand.Unknown -> Log.w(TAG, "Unknown command: $recognizedText")
        }
    }

    companion object {
        private const val TAG = "CommandRouter"
    }
}
