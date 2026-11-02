package com.example

import com.example.data.ssh.TerminalScreenBuffer
import org.junit.Assert.*
import org.junit.Test

class TerminalBufferTest {
    @Test fun fullWidthLineDoesNotAddBlankRowBeforeCrLf() {
        val terminal = TerminalScreenBuffer(5, 4)
        terminal.process("12345\r\nabc")
        assertEquals("12345\nabc", terminal.render().text)
    }
    @Test fun carriageReturnPreservesAlignedTable() {
        val terminal = TerminalScreenBuffer(60, 10)
        terminal.process("Filesystem  Size  Used  Avail\r\n/dev/sda2   234G   98G   124G\r\n")
        assertEquals("Filesystem  Size  Used  Avail\n/dev/sda2   234G   98G   124G", terminal.render().text.trimEnd())
    }

    @Test fun ansiSequenceCanSpanNetworkChunks() {
        val terminal = TerminalScreenBuffer()
        terminal.process("\u001b[3")
        terminal.process("2mhello\u001b[0m")
        assertEquals("hello", terminal.render().text)
        assertTrue(terminal.render().spanStyles.isNotEmpty())
    }

    @Test fun resizingKeepsVisibleCellsAndCursorInBounds() {
        val terminal = TerminalScreenBuffer(80, 24)
        terminal.process("hello")
        terminal.resize(40, 12)
        terminal.process(" world")
        assertEquals("hello world", terminal.render().text)
        terminal.resize(80, 24)
        assertEquals("hello world", terminal.render().text)
    }
}
