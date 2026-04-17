package com.k.selena.system

import android.util.Log

class RuntimeShellExecutor : ShellExecutor {
    override fun execute(command: String): Int {
        if (command.isBlank()) return -1
        return try {
            val process = ProcessBuilder("sh", "-c", command)
                .redirectErrorStream(true) // merge stderr into stdout
                .start()
            // Drain stdout to prevent the child process blocking on a full pipe buffer.
            process.inputStream.bufferedReader().readText()
            val exitCode = process.waitFor()
            Log.i(TAG, "Shell command executed with exitCode=$exitCode")
            exitCode
        } catch (e: Exception) {
            Log.e(TAG, "Shell command failed", e)
            -1
        }
    }

    companion object {
        private const val TAG = "RuntimeShellExecutor"
    }
}
