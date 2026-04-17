package com.k.selena.command

import com.k.selena.system.RootCommandExecutor
import com.k.selena.system.ShellExecutor
import com.k.selena.system.SystemActions
import org.junit.Assert.assertEquals
import org.junit.Test

class CommandRouterTest {
    @Test
    fun `routes elevated shell commands to root executor`() {
        val actions = FakeActions()
        val shell = FakeShell()
        val root = FakeRoot()
        val router = CommandRouter(systemActions = actions, shellExecutor = shell, rootExecutor = root)

        router.route("sudo run id")

        assertEquals("id", root.lastCommand)
        assertEquals(null, shell.lastCommand)
    }

    private class FakeActions : SystemActions {
        override fun openApp(query: String): Boolean = true
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
