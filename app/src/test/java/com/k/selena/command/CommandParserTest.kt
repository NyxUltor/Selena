package com.k.selena.command

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class CommandParserTest {
    private val parser = CommandParser()

    @Test
    fun `parses open app command`() {
        val parsed = parser.parse("open settings")
        assertEquals(SelenaCommand.OpenApp("settings"), parsed)
    }

    @Test
    fun `parses sudo shell command`() {
        val parsed = parser.parse("sudo run id")
        assertEquals(SelenaCommand.RunShell("id", elevated = true), parsed)
    }

    @Test
    fun `parses termux command`() {
        val parsed = parser.parse("open termux and run ls")
        assertEquals(SelenaCommand.OpenTermux("ls"), parsed)
    }

    @Test
    fun `parses open app command with mixed casing`() {
        val parsed = parser.parse("Open Settings")
        assertEquals(SelenaCommand.OpenApp("Settings"), parsed)
    }

    @Test
    fun `parses termux command with mixed casing`() {
        val parsed = parser.parse("Open Termux and RUN ls -la")
        assertEquals(SelenaCommand.OpenTermux("ls -la"), parsed)
    }

    @Test
    fun `parses execute command with mixed casing`() {
        val parsed = parser.parse("SuDo ExEcUtE id")
        assertEquals(SelenaCommand.RunShell("id", elevated = true), parsed)
    }

    @Test
    fun `returns unknown for blank text`() {
        val parsed = parser.parse(" ")
        assertTrue(parsed is SelenaCommand.Unknown)
    }
}
