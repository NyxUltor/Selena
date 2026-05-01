package com.k.selena.voice

import androidx.test.core.app.ApplicationProvider
import com.k.selena.command.CommandRouter
import com.k.selena.command.RouteResult
import com.k.selena.core.SelenaState
import com.k.selena.core.SelenaStateMachine
import com.k.selena.system.RootCommandExecutor
import com.k.selena.system.ShellExecutor
import com.k.selena.system.SystemActions
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import java.util.concurrent.CopyOnWriteArrayList
import java.util.concurrent.atomic.AtomicReference

/**
 * Tests for the safe sudo confirmation flow inside [VoicePipeline].
 *
 * These tests use fakes for all I/O dependencies so the pipeline can run synchronously
 * without real microphone access or TTS hardware.
 */
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [34])
class VoicePipelineConfirmationTest {

    // ── CommandRouter helpers ──────────────────────────────────────────────────────────────────

    @Test
    fun `elevated command route returns PendingConfirmation and does not call root executor`() {
        val root = FakeRoot()
        val router = buildRouter(root = root)

        val result = router.route("sudo run id")

        assertTrue(result is RouteResult.PendingConfirmation)
        assertEquals("id", (result as RouteResult.PendingConfirmation).command)
        assertNull("Root executor must not be called before confirmation", root.lastCommand)
    }

    @Test
    fun `non-elevated command route returns Executed and calls shell executor`() {
        val shell = FakeShell()
        val router = buildRouter(shell = shell)

        val result = router.route("run whoami")

        assertTrue(result is RouteResult.Executed)
        assertEquals("whoami", shell.lastCommand)
    }

    // ── VoicePipeline confirmation flow ───────────────────────────────────────────────────────

    @Test
    fun `elevated command is executed after yes confirmation`() {
        val root = FakeRoot()
        val router = buildRouter(root = root)
        val stateMachine = SelenaStateMachine(ApplicationProvider.getApplicationContext())
        val announcements = CopyOnWriteArrayList<String>()
        val announcer = FakeAnnouncer(announcements)

        // First recognition: elevated command; second recognition: confirmation "yes".
        val speechRecognizer = ScriptedSpeechRecognizer("sudo run id", "yes")
        val pipeline = buildPipeline(
            router = router,
            speechRecognizer = speechRecognizer,
            stateMachine = stateMachine,
            announcer = announcer
        )

        pipeline.start()
        Thread.sleep(PIPELINE_SETTLE_MS)
        pipeline.stop()

        assertEquals("id", root.lastCommand)
        assertTrue(
            "TTS should announce the command before asking for confirmation",
            announcements.any { it.contains("id") }
        )
    }

    @Test
    fun `elevated command is cancelled when user says no`() {
        val root = FakeRoot()
        val router = buildRouter(root = root)
        val stateMachine = SelenaStateMachine(ApplicationProvider.getApplicationContext())

        val speechRecognizer = ScriptedSpeechRecognizer("sudo run rm -rf", "no")
        val pipeline = buildPipeline(
            router = router,
            speechRecognizer = speechRecognizer,
            stateMachine = stateMachine
        )

        pipeline.start()
        Thread.sleep(PIPELINE_SETTLE_MS)
        pipeline.stop()

        assertNull("Root executor must not be called when the user says no", root.lastCommand)
    }

    @Test
    fun `elevated command is cancelled on silence in confirmation window`() {
        val root = FakeRoot()
        val router = buildRouter(root = root)
        val stateMachine = SelenaStateMachine(ApplicationProvider.getApplicationContext())

        // Second recognition returns null (silence).
        val speechRecognizer = ScriptedSpeechRecognizer("sudo run id", null)
        val pipeline = buildPipeline(
            router = router,
            speechRecognizer = speechRecognizer,
            stateMachine = stateMachine
        )

        pipeline.start()
        Thread.sleep(PIPELINE_SETTLE_MS)
        pipeline.stop()

        assertNull("Root executor must not be called on silence", root.lastCommand)
    }

    @Test
    fun `state machine is in AWAITING_CONFIRMATION when elevated command is being confirmed`() {
        val stateMachine = SelenaStateMachine(ApplicationProvider.getApplicationContext())
        // Capture the state at the moment the root executor is called (inside executeElevated,
        // which runs while the state machine is still in AWAITING_CONFIRMATION).
        val stateAtExecution = AtomicReference<SelenaState>()
        val root = object : RootCommandExecutor {
            @Volatile var lastCommand: String? = null
            override fun execute(command: String): Int {
                lastCommand = command
                stateAtExecution.set(stateMachine.currentState)
                return 0
            }
        }
        val router = buildRouter(root = root)

        val speechRecognizer = ScriptedSpeechRecognizer("sudo run id", "confirm")
        val pipeline = buildPipeline(
            router = router,
            speechRecognizer = speechRecognizer,
            stateMachine = stateMachine
        )

        pipeline.start()
        Thread.sleep(PIPELINE_SETTLE_MS)
        pipeline.stop()

        assertEquals(
            "Root executor must be called while state is AWAITING_CONFIRMATION",
            SelenaState.AWAITING_CONFIRMATION,
            stateAtExecution.get()
        )
        assertEquals(SelenaState.IDLE, stateMachine.currentState)
    }

    // ── Helpers ───────────────────────────────────────────────────────────────────────────────

    private fun buildRouter(
        shell: FakeShell = FakeShell(),
        root: RootCommandExecutor = FakeRoot()
    ): CommandRouter = CommandRouter(
        systemActions = FakeActions(),
        shellExecutor = shell,
        rootExecutor = root
    )

    private fun buildPipeline(
        router: CommandRouter,
        speechRecognizer: SpeechRecognizer,
        stateMachine: SelenaStateMachine,
        announcer: CommandAnnouncer? = null
    ): VoicePipeline = VoicePipeline(
        hotwordDetector = ImmediateHotwordDetector(),
        speechRecognizer = speechRecognizer,
        stateMachine = stateMachine,
        commandRouter = router,
        commandAnnouncer = announcer,
        audioFocusManager = null
    )

    // ── Fakes ─────────────────────────────────────────────────────────────────────────────────

    /** Fires on the very first [pollForHotword] call then stays quiet. */
    private class ImmediateHotwordDetector : HotwordDetector {
        @Volatile private var fired = false
        override fun pollForHotword(): Boolean = if (!fired) { fired = true; true } else false
        override fun pause() {}
        override fun resume() {}
        override fun close() {}
    }

    /** Returns scripted responses in sequence (thread-safe queue). */
    private class ScriptedSpeechRecognizer(vararg responses: String?) : SpeechRecognizer {
        private val queue = ArrayDeque(responses.toList())
        override fun recognizeForWindow(windowMs: Long): String? =
            synchronized(queue) { queue.removeFirstOrNull() }
    }

    private class FakeAnnouncer(private val log: CopyOnWriteArrayList<String>) : CommandAnnouncer {
        override fun announce(text: String) { log.add(text) }
    }

    private class FakeActions : SystemActions {
        override fun openApp(query: String): Boolean = true
        override fun openTermuxWithCommand(command: String?): Boolean = true
    }

    private class FakeShell : ShellExecutor {
        @Volatile var lastCommand: String? = null
        override fun execute(command: String): Int { lastCommand = command; return 0 }
    }

    private class FakeRoot : RootCommandExecutor {
        @Volatile var lastCommand: String? = null
        override fun execute(command: String): Int { lastCommand = command; return 0 }
    }

    companion object {
        /** Give the pipeline thread enough time to complete one hotword → recognise → confirm cycle. */
        private const val PIPELINE_SETTLE_MS = 2000L
    }
}
