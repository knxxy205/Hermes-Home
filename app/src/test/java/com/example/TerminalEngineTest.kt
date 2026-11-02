package com.example

import com.example.data.ssh.TerminalEngine
import com.termux.terminal.TextStyle
import org.junit.Assert.*
import org.junit.Test

class TerminalEngineTest {
    private fun TerminalEngine.feed(text: String) = append(text.toByteArray())

    @Test fun columnsTabsAndCrLfStayAligned() {
        val engine = TerminalEngine {}
        engine.resize(80, 24)
        engine.feed("Name\tSize\r\nroot\t12G\r\nhome\t44G")
        val lines = engine.emulator.screen.transcriptText.lines()
        assertEquals("Name    Size", lines[0])
        assertEquals("root    12G", lines[1])
        assertEquals("home    44G", lines[2])
    }

    @Test fun splitUtf8WideAndCombiningCharactersKeepCellPositions() {
        val engine = TerminalEngine {}
        "界e\u0301🙂".toByteArray().forEach { engine.append(byteArrayOf(it)) }
        assertEquals(5, engine.emulator.cursorCol)
        assertTrue(engine.emulator.screen.transcriptText.contains("界e\u0301🙂"))
    }

    @Test fun fullLineFollowedByCrLfDoesNotInsertBlankLine() {
        val engine = TerminalEngine {}
        engine.resize(5, 4)
        engine.feed("12345\r\nabc")
        assertEquals("12345\nabc", engine.emulator.screen.transcriptText.trimEnd())
    }

    @Test fun alternateScreenRestoresShellAndResizeKeepsHistory() {
        val engine = TerminalEngine {}
        engine.feed("root@server:~$ ")
        engine.feed("\u001b[?1049h\u001b[2J\u001b[Hnano")
        assertTrue(engine.emulator.isAlternateBufferActive)
        engine.feed("\u001b[?1049l")
        assertTrue(engine.emulator.screen.transcriptText.contains("root@server"))
        repeat(60) { engine.feed("\r\nrow-$it") }
        engine.resize(40, 10)
        engine.resize(80, 24)
        val output = engine.emulator.screen.transcriptText
        assertTrue(output.contains("row-0"))
        assertTrue(output.contains("row-59"))
    }

    @Test fun ansiTrueColorAndBracketedPasteAreSupported() {
        val sent = java.io.ByteArrayOutputStream()
        val engine = TerminalEngine { sent.write(it) }
        engine.feed("\u001b[38;2;12;34;56mA\u001b[0m\u001b[?2004h")
        assertEquals(0xff0c2238.toInt(), TextStyle.decodeForeColor(engine.emulator.screen.getStyleAt(0, 0)))
        engine.paste("echo hello")
        assertEquals("\u001b[200~echo hello\u001b[201~", sent.toString("UTF-8"))
    }
}
