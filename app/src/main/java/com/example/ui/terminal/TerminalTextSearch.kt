package com.example.ui.terminal

import com.example.data.ssh.TerminalEngine
import com.termux.terminal.WcWidth

data class TerminalMatch(val row: Int, val startColumn: Int, val endColumn: Int)

object TerminalTextSearch {
    fun find(engine: TerminalEngine, query: String): List<TerminalMatch> = synchronized(engine.lock) {
        if (query.isBlank()) return@synchronized emptyList()
        val emulator = engine.emulator
        val matches = mutableListOf<TerminalMatch>()
        for (row in -emulator.screen.activeTranscriptRows until emulator.mRows) {
            val text = emulator.screen.getSelectedText(0, row, emulator.mColumns - 1, row)
            var offset = text.indexOf(query, ignoreCase = true)
            while (offset >= 0) {
                fun columns(end: Int): Int = text.substring(0, end).codePoints().toArray().sumOf { WcWidth.width(it).coerceAtLeast(0) }
                matches.add(TerminalMatch(row, columns(offset), (columns(offset + query.length) - 1).coerceAtLeast(columns(offset))))
                if (matches.size >= 1000) return@synchronized matches
                offset = text.indexOf(query, offset + query.length, ignoreCase = true)
            }
        }
        matches
    }
}
