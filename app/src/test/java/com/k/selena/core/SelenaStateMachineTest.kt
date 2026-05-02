package com.k.selena.core

import androidx.test.core.app.ApplicationProvider
import org.junit.Assert.assertEquals
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [34])
class SelenaStateMachineTest {
    private val machine = SelenaStateMachine(ApplicationProvider.getApplicationContext())

    @Test
    fun `initial state is IDLE`() {
        assertEquals(SelenaState.IDLE, machine.currentState)
    }

    @Test
    fun `transitions from IDLE to LISTENING`() {
        machine.transitionTo(SelenaState.LISTENING, "hotword detected")
        assertEquals(SelenaState.LISTENING, machine.currentState)
    }

    @Test
    fun `transitions through full IDLE-LISTENING-EXECUTING-IDLE cycle`() {
        machine.transitionTo(SelenaState.LISTENING, "hotword")
        machine.transitionTo(SelenaState.EXECUTING, "speech captured")
        machine.transitionTo(SelenaState.IDLE, "command done")
        assertEquals(SelenaState.IDLE, machine.currentState)
    }

    @Test
    fun `no-op transition when target equals current state`() {
        machine.transitionTo(SelenaState.IDLE, "redundant")
        assertEquals(SelenaState.IDLE, machine.currentState)
    }

    @Test
    fun `transitions to AWAITING_CONFIRMATION for elevated commands`() {
        machine.transitionTo(SelenaState.LISTENING, "hotword")
        machine.transitionTo(SelenaState.EXECUTING, "speech captured")
        machine.transitionTo(SelenaState.AWAITING_CONFIRMATION, "elevated command staged")
        assertEquals(SelenaState.AWAITING_CONFIRMATION, machine.currentState)
    }

    @Test
    fun `transitions from AWAITING_CONFIRMATION back to IDLE after cancellation`() {
        machine.transitionTo(SelenaState.AWAITING_CONFIRMATION, "elevated command staged")
        machine.transitionTo(SelenaState.IDLE, "confirmation cancelled")
        assertEquals(SelenaState.IDLE, machine.currentState)
    }
}
