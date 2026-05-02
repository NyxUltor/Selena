package com.k.selena.system

/**
 * Represents a single system capability that can be injected by the LSPosed module.
 *
 * Implement this interface for each feature hook you want Selena to register. The [install]
 * method is called once per package load by [HookRegistry.installAll] and should be idempotent.
 */
interface SelenaHook {
    /** Unique identifier for this hook, used for deduplication and logging. */
    val id: String

    /** Human-readable description of what this hook does. */
    val description: String

    /**
     * Install the hook for the current package load context.
     *
     * Implementations should be safe to call even when the target package is not the expected
     * one — guard with a package name check at the top of this method.
     */
    fun install()
}
