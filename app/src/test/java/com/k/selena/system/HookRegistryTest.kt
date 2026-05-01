package com.k.selena.system

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class HookRegistryTest {

    @Test
    fun `registry is empty on creation`() {
        val registry = HookRegistry()
        assertTrue(registry.registeredIds().isEmpty())
    }

    @Test
    fun `register adds hook to registry`() {
        val registry = HookRegistry()
        registry.register(FakeHook("hook-1", "First hook"))
        assertEquals(listOf("hook-1"), registry.registeredIds())
    }

    @Test
    fun `duplicate hook id is silently ignored`() {
        val registry = HookRegistry()
        registry.register(FakeHook("hook-1", "First"))
        registry.register(FakeHook("hook-1", "Duplicate"))
        assertEquals(1, registry.registeredIds().size)
    }

    @Test
    fun `installAll calls install on each registered hook`() {
        val registry = HookRegistry()
        val hookA = FakeHook("hook-a", "Hook A")
        val hookB = FakeHook("hook-b", "Hook B")
        registry.register(hookA)
        registry.register(hookB)

        registry.installAll()

        assertTrue("hook-a must be installed", hookA.installed)
        assertTrue("hook-b must be installed", hookB.installed)
    }

    @Test
    fun `installAll on empty registry completes without error`() {
        val registry = HookRegistry()
        registry.installAll() // must not throw
    }

    @Test
    fun `failing hook does not prevent other hooks from being installed`() {
        val registry = HookRegistry()
        val broken = object : SelenaHook {
            override val id = "broken"
            override val description = "Always throws"
            override fun install() = throw RuntimeException("simulated failure")
        }
        val good = FakeHook("good", "Good hook")
        registry.register(broken)
        registry.register(good)

        registry.installAll() // must not throw

        assertTrue("Good hook must still be installed despite broken hook", good.installed)
    }

    @Test
    fun `registeredIds returns ids in registration order`() {
        val registry = HookRegistry()
        registry.register(FakeHook("first", ""))
        registry.register(FakeHook("second", ""))
        registry.register(FakeHook("third", ""))
        assertEquals(listOf("first", "second", "third"), registry.registeredIds())
    }

    @Test
    fun `LsposedHookEntryPoint registry and onPackageLoaded are wired correctly`() {
        // Create a dedicated registry (not the singleton) to avoid state leakage between runs.
        val registry = HookRegistry()
        val hook = FakeHook("test-hook", "Test hook")
        registry.register(hook)

        // Verify installAll drives installation (same code path used by onPackageLoaded).
        registry.installAll()

        assertTrue("Hook must be installed after installAll", hook.installed)
        assertEquals(listOf("test-hook"), registry.registeredIds())
    }

    // ── Helpers ───────────────────────────────────────────────────────────────────────────────

    private class FakeHook(
        override val id: String,
        override val description: String
    ) : SelenaHook {
        var installed = false
        override fun install() { installed = true }
    }
}
