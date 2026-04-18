package com.k.selena.system

import android.util.Log

/**
 * Executes commands with root privileges via Magisk's `su` binary.
 *
 * Requires a rooted device with Magisk installed and the Selena app granted superuser
 * permissions in the Magisk Manager app. On non-rooted devices the command will fail and
 * exit code -1 is returned.
 */
class MagiskRootExecutor : RootCommandExecutor {
    override fun execute(command: String): Int {
        if (command.isBlank()) return -1
        return try {
            val process = ProcessBuilder("su", "-c", command)
                .redirectErrorStream(true)
                .start()
            // Drain stdout/stderr to prevent the child process blocking on a full pipe buffer.
            val output = process.inputStream.bufferedReader().readText()
            val exitCode = process.waitFor()
            if (output.isNotBlank()) {
                Log.d(TAG, "Root command output: $output")
            }
            Log.i(TAG, "Root command executed with exitCode=$exitCode")
            exitCode
        } catch (e: Exception) {
            Log.e(TAG, "Root command failed (device may not be rooted or su is not available)", e)
            -1
        }
    }

    companion object {
        private const val TAG = "MagiskRootExecutor"
    }
}
