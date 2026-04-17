package com.k.selena.command

class CommandParser {
    fun parse(text: String): SelenaCommand {
        val raw = text.trim()
        if (raw.isEmpty()) return SelenaCommand.Unknown

        val lower = raw.lowercase()
        if (lower.startsWith("open termux")) {
            val command = raw.substringAfter("run", "").trim().ifBlank { null }
            return SelenaCommand.OpenTermux(command)
        }
        if (lower.startsWith("open ")) {
            return SelenaCommand.OpenApp(raw.substringAfter("open ").trim())
        }

        val elevated = lower.startsWith("sudo ")
        val shellPayload = when {
            lower.startsWith("sudo run ") -> raw.substringAfter("sudo run ").trim()
            lower.startsWith("sudo execute ") -> raw.substringAfter("sudo execute ").trim()
            lower.startsWith("run ") -> raw.substringAfter("run ").trim()
            lower.startsWith("execute ") -> raw.substringAfter("execute ").trim()
            elevated -> raw.substringAfter("sudo ").trim()
            else -> raw
        }
        return if (shellPayload.isBlank()) SelenaCommand.Unknown
        else SelenaCommand.RunShell(shellPayload, elevated = elevated)
    }
}
