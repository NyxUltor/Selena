package com.k.selena.system

import android.util.Log

/**
 * Registry of [SelenaHook] instances that the LSPosed module should install on package load.
 *
 * Hooks are registered with [register] and installed collectively via [installAll]. The registry
 * deduplicates by [SelenaHook.id] so the same hook cannot be added twice. This design keeps the
 * main app logic decoupled from the low-level Xposed/LSPosed plumbing — callers register
 * capabilities here, and [LsposedHookEntryPoint] calls [installAll] at the right time.
 */
class HookRegistry {
    private val hooks = mutableListOf<SelenaHook>()

    /**
     * Register [hook] with the registry. If a hook with the same [SelenaHook.id] is already
     * registered the call is silently ignored.
     */
    fun register(hook: SelenaHook) {
        if (hooks.any { it.id == hook.id }) {
            Log.w(TAG, "Hook '${hook.id}' already registered — ignoring duplicate")
            return
        }
        hooks.add(hook)
        Log.d(TAG, "Registered hook '${hook.id}': ${hook.description}")
    }

    /**
     * Install all registered hooks. Each hook's [SelenaHook.install] is called in registration
     * order. Failures are caught and logged so one broken hook does not prevent others from
     * loading.
     */
    fun installAll() {
        if (hooks.isEmpty()) {
            Log.d(TAG, "No hooks registered — nothing to install")
            return
        }
        Log.i(TAG, "Installing ${hooks.size} hook(s)")
        hooks.forEach { hook ->
            runCatching { hook.install() }.onFailure { e ->
                Log.e(TAG, "Failed to install hook '${hook.id}'", e)
            }
        }
    }

    /** Returns an immutable snapshot of the currently registered hook IDs. */
    fun registeredIds(): List<String> = hooks.map { it.id }.toList()

    companion object {
        private const val TAG = "HookRegistry"
    }
}
