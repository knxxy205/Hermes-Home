package com.example

import com.example.data.ssh.TerminalEngine
import com.example.ui.terminal.TerminalTextSearch
import org.junit.Assert.*
import org.junit.Test

class TerminalTextSearchTest {
    @Test fun searchUsesRenderedTextAndFindsCaseInsensitiveOccurrences() {
        val engine = TerminalEngine {}
        engine.append("\u001b[31mError\u001b[0m: error\r\nready".toByteArray())
        val matches = TerminalTextSearch.find(engine, "ERROR")
        assertEquals(2, matches.size)
        assertEquals(0, matches[0].startColumn)
        assertEquals(4, matches[0].endColumn)
        assertEquals(7, matches[1].startColumn)
        assertTrue(TerminalTextSearch.find(engine, "[31m").isEmpty())
    }

    @Test fun searchCoordinatesUseTerminalCellsForWideAndCombiningCharacters() {
        val engine = TerminalEngine {}
        engine.append("界e\u0301🙂 target".toByteArray())
        val result = TerminalTextSearch.find(engine, "target").single()
        assertEquals(6, result.startColumn)
        assertEquals(11, result.endColumn)
    }

    @Test fun historyIsSearchedAndEmptyQueriesDoNotHighlightEverything() {
        val engine = TerminalEngine {}
        engine.resize(30, 4)
        engine.append(("old marker\r\n" + (1..20).joinToString("\r\n") { "line $it" }).toByteArray())
        assertTrue(TerminalTextSearch.find(engine, "old marker").single().row < 0)
        assertTrue(TerminalTextSearch.find(engine, "  ").isEmpty())
    }
}
