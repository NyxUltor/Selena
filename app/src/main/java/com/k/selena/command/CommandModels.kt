package com.k.selena.command

sealed class SelenaCommand {
    data class OpenApp(val query: String) : SelenaCommand()
    data class RunShell(val command: String, val elevated: Boolean) : SelenaCommand()
    data class OpenTermux(val command: String?) : SelenaCommand()
    data object Unknown : SelenaCommand()
}
