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
    fun `returns unknown for blank text`() {
        val parsed = parser.parse(" ")
        assertTrue(parsed is SelenaCommand.Unknown)
    }
}
