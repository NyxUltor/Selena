package com.k.selena.system

import android.util.Log

/**
 * LSPosed module entry point. On package load [onPackageLoaded] is called with the target
 * package name; registered hooks are then installed via [HookRegistry.installAll].
 *
 * Add hooks by calling [registry].[HookRegistry.register] from application initialisation code
 * or directly in this object before [onPackageLoaded] is triggered.
 */
object LsposedHookEntryPoint {
    private const val TAG = "LsposedHookEntryPoint"

    private val registry = HookRegistry()

    /** Expose the registry so external code can register hooks before package load. */
    fun registry(): HookRegistry = registry

    /**
     * Called when LSPosed loads a target package. Delegates hook installation to [registry].
     *
     * @param packageName the fully-qualified name of the package being loaded.
     */
    fun onPackageLoaded(packageName: String) {
        Log.i(TAG, "Package loaded: $packageName — installing ${registry.registeredIds().size} hook(s)")
        registry.installAll()
    }
}

