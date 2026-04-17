package com.k.selena.command

class CommandParser {
    fun parse(text: String): SelenaCommand {
        val raw = text.trim()
        if (raw.isEmpty()) return SelenaCommand.Unknown

        val lower = raw.lowercase()
        if (lower.startsWith("open termux")) {
            val command = extractTermuxCommand(raw, lower)
            return SelenaCommand.OpenTermux(command)
        }
        if (lower.startsWith("open ")) {
            return SelenaCommand.OpenApp(stripPrefix(raw, OPEN_PREFIX).trim())
        }

        val elevated = lower.startsWith("sudo ")
        val shellPayload = when {
            lower.startsWith(SUDO_RUN_PREFIX) -> stripPrefix(raw, SUDO_RUN_PREFIX).trim()
            lower.startsWith(SUDO_EXECUTE_PREFIX) -> stripPrefix(raw, SUDO_EXECUTE_PREFIX).trim()
            lower.startsWith(RUN_PREFIX) -> stripPrefix(raw, RUN_PREFIX).trim()
            lower.startsWith(EXECUTE_PREFIX) -> stripPrefix(raw, EXECUTE_PREFIX).trim()
            elevated -> stripPrefix(raw, SUDO_PREFIX).trim()
            else -> raw
        }
        return if (shellPayload.isBlank()) SelenaCommand.Unknown
        else SelenaCommand.RunShell(shellPayload, elevated = elevated)
    }

    private fun extractTermuxCommand(raw: String, lower: String): String? {
        val afterPrefixIndex = OPEN_TERMUX_PREFIX.length
        val runIndex = lower.indexOf(RUN_PREFIX, startIndex = afterPrefixIndex)
        if (runIndex >= 0) {
            val contentStart = (runIndex + RUN_PREFIX.length).coerceAtMost(raw.length)
            return raw.substring(contentStart).trim().ifBlank { null }
        }
        val executeIndex = lower.indexOf(EXECUTE_PREFIX, startIndex = afterPrefixIndex)
        if (executeIndex >= 0) {
            val contentStart = (executeIndex + EXECUTE_PREFIX.length).coerceAtMost(raw.length)
            return raw.substring(contentStart).trim().ifBlank { null }
        }
        return null
    }

    private fun stripPrefix(raw: String, prefix: String): String =
        if (raw.length >= prefix.length) raw.substring(prefix.length) else ""

    companion object {
        private const val OPEN_TERMUX_PREFIX = "open termux"
        private const val OPEN_PREFIX = "open "
        private const val SUDO_PREFIX = "sudo "
        private const val RUN_PREFIX = "run "
        private const val EXECUTE_PREFIX = "execute "
        private const val SUDO_RUN_PREFIX = "sudo run "
        private const val SUDO_EXECUTE_PREFIX = "sudo execute "
    }
}
