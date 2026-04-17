package com.k.selena.system

import android.util.Log

class MagiskRootExecutor : RootCommandExecutor {
    override fun execute(command: String): Int {
        // TODO: Implement Magisk/su execution pathway once root policy + UX is finalized.
        Log.w(TAG, "Root command requested but root execution is scaffold-only: $command")
        return -1
    }

    companion object {
        private const val TAG = "MagiskRootExecutor"
    }
}
