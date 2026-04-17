package com.k.selena.system

import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.util.Log

class AndroidSystemActions(private val context: Context) : SystemActions {
    override fun openApp(query: String): Boolean {
        val packageName = query.trim()
        if (packageName.isBlank()) return false
        val packageManager = context.packageManager

        val launchIntent = packageManager.getLaunchIntentForPackage(packageName)
            ?: findAppByLabel(packageName, packageManager)
        if (launchIntent == null) {
            Log.w(TAG, "No launchable app found for query=$query")
            return false
        }
        launchIntent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
        context.startActivity(launchIntent)
        Log.i(TAG, "Opened app for query=$query")
        return true
    }

    override fun openTermuxWithCommand(command: String?): Boolean {
        val actionIntent = Intent("com.termux.app.RUN_COMMAND").apply {
            setPackage(TERMUX_PACKAGE)
            putExtra("com.termux.RUN_COMMAND_PATH", "/data/data/com.termux/files/usr/bin/sh")
            putExtra("com.termux.RUN_COMMAND_ARGUMENTS", arrayOf("-c", command ?: ""))
            putExtra("com.termux.RUN_COMMAND_WORKDIR", "/data/data/com.termux/files/home")
            putExtra("com.termux.RUN_COMMAND_BACKGROUND", false)
        }
        return try {
            actionIntent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
            context.startActivity(actionIntent)
            Log.i(TAG, "Sent Termux run command intent")
            true
        } catch (e: Exception) {
            val fallback = context.packageManager.getLaunchIntentForPackage(TERMUX_PACKAGE)
            if (fallback == null) {
                Log.w(TAG, "Termux not installed")
                false
            } else {
                fallback.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
                context.startActivity(fallback)
                Log.i(TAG, "Opened Termux fallback launcher")
                true
            }
        }
    }

    private fun findAppByLabel(query: String, packageManager: PackageManager): Intent? {
        val installed = packageManager.getInstalledApplications(PackageManager.GET_META_DATA)
        val match = installed.firstOrNull { app ->
            packageManager.getApplicationLabel(app).toString().equals(query, ignoreCase = true)
        } ?: return null
        return packageManager.getLaunchIntentForPackage(match.packageName)
    }

    companion object {
        private const val TAG = "AndroidSystemActions"
        private const val TERMUX_PACKAGE = "com.termux"
    }
}
