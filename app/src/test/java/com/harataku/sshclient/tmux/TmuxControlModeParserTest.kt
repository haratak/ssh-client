package com.harataku.sshclient.tmux

import org.junit.Assert.*
import org.junit.Test

class TmuxControlModeParserTest {

    private val parser = TmuxControlModeParser()

    @Test
    fun `parse session-changed`() {
        val event = parser.parseLine("%session-changed \$1 main")
        assertTrue(event is TmuxEvent.SessionChanged)
        val e = event as TmuxEvent.SessionChanged
        assertEquals("\$1", e.sessionId)
        assertEquals("main", e.name)
    }

    @Test
    fun `parse sessions-changed`() {
        val event = parser.parseLine("%sessions-changed")
        assertTrue(event is TmuxEvent.SessionsChanged)
    }

    @Test
    fun `parse window-add`() {
        val event = parser.parseLine("%window-add @0")
        assertTrue(event is TmuxEvent.WindowAdd)
        assertEquals("@0", (event as TmuxEvent.WindowAdd).windowId)
    }

    @Test
    fun `parse window-close`() {
        val event = parser.parseLine("%window-close @1")
        assertTrue(event is TmuxEvent.WindowClose)
        assertEquals("@1", (event as TmuxEvent.WindowClose).windowId)
    }

    @Test
    fun `parse output with octal escapes`() {
        val event = parser.parseLine("%output %0 hello\\033[32mworld")
        assertTrue(event is TmuxEvent.Output)
        val e = event as TmuxEvent.Output
        assertEquals("%0", e.paneId)
        val text = String(e.data)
        assertEquals("hello\u001b[32mworld", text)
    }

    @Test
    fun `parse output with backslash escape`() {
        val event = parser.parseLine("%output %0 path\\\\file")
        assertTrue(event is TmuxEvent.Output)
        val text = String((event as TmuxEvent.Output).data)
        assertEquals("path\\file", text)
    }

    @Test
    fun `parse exit`() {
        val event = parser.parseLine("%exit")
        assertTrue(event is TmuxEvent.Exit)
        assertNull((event as TmuxEvent.Exit).reason)
    }

    @Test
    fun `parse exit with reason`() {
        val event = parser.parseLine("%exit exited")
        assertTrue(event is TmuxEvent.Exit)
        assertEquals("exited", (event as TmuxEvent.Exit).reason)
    }

    @Test
    fun `parse command response block`() {
        assertNull(parser.parseLine("%begin 1234 1"))
        assertNull(parser.parseLine("line1"))
        assertNull(parser.parseLine("line2"))
        val event = parser.parseLine("%end 1234 1")
        assertTrue(event is TmuxEvent.CommandResponse)
        val e = event as TmuxEvent.CommandResponse
        assertEquals(listOf("line1", "line2"), e.lines)
    }

    @Test
    fun `parse error in command block`() {
        assertNull(parser.parseLine("%begin 1234 1"))
        assertNull(parser.parseLine("some output"))
        val event = parser.parseLine("%error 1234 1")
        assertTrue(event is TmuxEvent.Error)
    }

    @Test
    fun `parse unknown line`() {
        val event = parser.parseLine("some random text")
        assertTrue(event is TmuxEvent.Unknown)
    }

    @Test
    fun `parse layout-change`() {
        val event = parser.parseLine("%layout-change @0 abcd,80x24,0,0,0")
        assertTrue(event is TmuxEvent.LayoutChange)
        val e = event as TmuxEvent.LayoutChange
        assertEquals("@0", e.windowId)
    }

    @Test
    fun `decode output octal`() {
        val bytes = TmuxControlModeParser.decodeOutput("\\033[0m")
        assertEquals(0x1B, bytes[0].toInt())
        assertEquals('['.code.toByte(), bytes[1])
        assertEquals('0'.code.toByte(), bytes[2])
        assertEquals('m'.code.toByte(), bytes[3])
    }

    @Test
    fun `decode output newline and tab`() {
        val bytes = TmuxControlModeParser.decodeOutput("a\\nb\\tc")
        assertEquals("a\nb\tc", String(bytes))
    }

    @Test
    fun `reset clears state`() {
        parser.parseLine("%begin 1234 1")
        parser.parseLine("data")
        parser.reset()
        // After reset, should not be in command block
        val event = parser.parseLine("%session-changed \$1 main")
        assertTrue(event is TmuxEvent.SessionChanged)
    }
}
