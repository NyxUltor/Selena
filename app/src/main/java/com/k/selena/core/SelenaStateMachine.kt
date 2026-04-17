package com.k.selena.core

import android.util.Log

class SelenaStateMachine {
    @Volatile
    var currentState: SelenaState = SelenaState.IDLE
        private set

    fun transitionTo(newState: SelenaState, reason: String) {
        val oldState = currentState
        if (oldState == newState) {
            Log.d(TAG, "State unchanged: $oldState (reason=$reason)")
            return
        }
        currentState = newState
        Log.i(TAG, "State transition: $oldState -> $newState (reason=$reason)")
    }

    companion object {
        private const val TAG = "SelenaStateMachine"
    }
}
