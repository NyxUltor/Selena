package com.k.selena.core

import org.junit.Assert.assertEquals
import org.junit.Test

class SelenaStateMachineTest {
    private val machine = SelenaStateMachine()

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
}
