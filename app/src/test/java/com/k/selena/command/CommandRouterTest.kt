package com.k.selena.command

import com.k.selena.system.RootCommandExecutor
import com.k.selena.system.ShellExecutor
import com.k.selena.system.SystemActions
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class CommandRouterTest {
    @Test
    fun `elevated shell command is staged for confirmation and not immediately executed`() {
        val actions = FakeActions()
        val shell = FakeShell()
        val root = FakeRoot()
        val router = CommandRouter(systemActions = actions, shellExecutor = shell, rootExecutor = root)

        val result = router.route("sudo run id")

        // Elevated commands must NOT execute immediately.
        assertNull("Root executor must not be called before explicit confirmation", root.lastCommand)
        assertNull("Shell executor must not be called for elevated commands", shell.lastCommand)
        assertTrue("Route result must be PendingConfirmation", result is RouteResult.PendingConfirmation)
        assertEquals("id", (result as RouteResult.PendingConfirmation).command)
    }

    @Test
    fun `elevated command executes via executeElevated after confirmation`() {
        val actions = FakeActions()
        val shell = FakeShell()
        val root = FakeRoot()
        val router = CommandRouter(systemActions = actions, shellExecutor = shell, rootExecutor = root)

        // Stage the command.
        val result = router.route("sudo run id")
        assertTrue(result is RouteResult.PendingConfirmation)
        assertNull("Root executor must not be called before confirmation", root.lastCommand)

        // Explicitly confirm and execute.
        router.executeElevated((result as RouteResult.PendingConfirmation).command)

        assertEquals("id", root.lastCommand)
        assertNull(shell.lastCommand)
    }

    @Test
    fun `routes non elevated shell commands to regular shell executor`() {
        val actions = FakeActions()
        val shell = FakeShell()
        val root = FakeRoot()
        val router = CommandRouter(systemActions = actions, shellExecutor = shell, rootExecutor = root)

        val result = router.route("run whoami")

        assertEquals("whoami", shell.lastCommand)
        assertNull(root.lastCommand)
        assertTrue("Non-elevated commands should return Executed", result is RouteResult.Executed)
    }

    @Test
    fun `open app command returns Executed`() {
        val actions = FakeActions()
        val shell = FakeShell()
        val root = FakeRoot()
        val router = CommandRouter(systemActions = actions, shellExecutor = shell, rootExecutor = root)

        val result = router.route("open settings")

        assertTrue(result is RouteResult.Executed)
        assertEquals("settings", actions.lastQuery)
    }

    @Test
    fun `sudo bare command without run keyword is staged for confirmation`() {
        val actions = FakeActions()
        val shell = FakeShell()
        val root = FakeRoot()
        val router = CommandRouter(systemActions = actions, shellExecutor = shell, rootExecutor = root)

        val result = router.route("sudo reboot")

        assertNull(root.lastCommand)
        assertTrue(result is RouteResult.PendingConfirmation)
        assertEquals("reboot", (result as RouteResult.PendingConfirmation).command)
    }

    private class FakeActions : SystemActions {
        var lastQuery: String? = null
        override fun openApp(query: String): Boolean {
            lastQuery = query
            return true
        }
        override fun openTermuxWithCommand(command: String?): Boolean = true
    }

    private class FakeShell : ShellExecutor {
        var lastCommand: String? = null
        override fun execute(command: String): Int {
            lastCommand = command
            return 0
        }
    }

    private class FakeRoot : RootCommandExecutor {
        var lastCommand: String? = null
        override fun execute(command: String): Int {
            lastCommand = command
            return 0
        }
    }
}
