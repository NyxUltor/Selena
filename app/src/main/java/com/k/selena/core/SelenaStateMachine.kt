package com.k.selena.core

import android.content.Context
import android.util.Log

class SelenaStateMachine(context: Context) {
    private val appContext = context.applicationContext
    private val prefs = appContext.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)

    @Volatile
    var currentState: SelenaState = restoreState()
        private set

    init {
        persistState(currentState)
        Log.i(TAG, "State machine initialized with state=$currentState")
    }

    fun transitionTo(newState: SelenaState, reason: String) {
        val oldState = currentState
        if (oldState == newState) {
            Log.d(TAG, "State unchanged: $oldState (reason=$reason)")
            return
        }
        currentState = newState
        persistState(newState)
        Log.i(TAG, "State transition: $oldState -> $newState (reason=$reason)")
    }

    private fun restoreState(): SelenaState {
        val rawState = prefs.getString(KEY_STATE, SelenaState.IDLE.name)
        val persisted = SelenaState.entries.firstOrNull { it.name == rawState } ?: SelenaState.IDLE
        if (persisted != SelenaState.IDLE) {
            Log.w(TAG, "Recovering from persisted active state=$persisted")
        }
        return persisted
    }

    private fun persistState(state: SelenaState) {
        runCatching {
            prefs.edit().putString(KEY_STATE, state.name).apply()
        }.onFailure { throwable ->
            Log.e(TAG, "Failed to persist state=$state", throwable)
        }
    }

    companion object {
        private const val TAG = "SelenaStateMachine"
        private const val PREFS_NAME = "selena_state"
        private const val KEY_STATE = "current_state"
    }
}
