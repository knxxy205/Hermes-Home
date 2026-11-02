package com.example.data.ssh

import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.text.SpanStyle
import androidx.compose.ui.text.buildAnnotatedString
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.withStyle

/**
 * Robust ANSI / VT100 Virtual Terminal Screen Buffer for interactive curses applications
 * such as nano, vi, htop, and full-screen shell sessions.
 *
 * Accurately handles:
 * - Direct cursor addressing (\u001B[<row>;<col>H or \u001B[<row>;<col>f)
 * - Alternate screen buffer switching (\u001B[?1049h, \u001B[?1049l, \u001B[?47h)
 * - VT100 Character Set selection (\u001B(B, \u001B(0, \u001B)B, etc.) and DEC box drawing
 * - Application/Numeric Keypad modes (\u001B=, \u001B>)
 * - Cursor save/restore (\u001B7, \u001B8)
 * - Screen clearing (\u001B[2J, \u001B[3J, \u001B[K)
 * - SGR styling (bold, colors, reverse/inverse video \u001B[7m used by nano)
 */
class TerminalScreenBuffer(
    var maxCols: Int = 80,
    var maxRows: Int = 24
) {

    data class Cell(
        var char: Char = ' ',
        var fg: Color = ColorDefaultFg,
        var bg: Color = Color.Transparent,
        var bold: Boolean = false,
        var inverse: Boolean = false
    ) {
        fun reset() {
            char = ' '
            fg = ColorDefaultFg
            bg = Color.Transparent
            bold = false
            inverse = false
        }

        fun copyFrom(other: Cell) {
            char = other.char
            fg = other.fg
            bg = other.bg
            bold = other.bold
            inverse = other.inverse
        }
    }

    companion object {
        val ColorDefaultFg = Color(0xFF62AE91)
        val ColorDefaultBg = Color.Transparent
        val ColorInverseBg = Color(0xFFCBD5E1) // Light gray bg for nano title & shortcuts
        val ColorInverseFg = Color(0xFF05080E) // Dark text for inverse video

        val ColorBlack = Color(0xFF1E293B)
        val ColorRed = Color(0xFFD77B87)
        val ColorGreen = Color(0xFF62AE91)
        val ColorYellow = Color(0xFFD8B77A)
        val ColorBlue = Color(0xFF75A7D1)
        val ColorMagenta = Color(0xFFB49AC9)
        val ColorCyan = Color(0xFF7FB5BC)
        val ColorWhite = Color(0xFFEDEDED)
        val ColorGray = Color(0xFF94A3B8)
    }

    private var isAltScreen = false
    var onInteractiveModeChanged: ((Boolean) -> Unit)? = null

    // Scrollback history for normal non-curses shell output
    private val scrollback = mutableListOf<AnnotatedString>()
    private val maxScrollbackLines = 600

    // Grids for normal and alt screen
    private var normalGrid = Array(maxRows) { Array(maxCols) { Cell() } }
    private var altGrid = Array(maxRows) { Array(maxCols) { Cell() } }

    @Synchronized
    fun resize(columns: Int, rows: Int) {
        if (columns == maxCols && rows == maxRows) return
        val offset = (cursorRow - rows + 1).coerceAtLeast(0)
        if (!isAltScreen) {
            for (r in 0 until offset) scrollback.add(rowToAnnotatedString(normalGrid[r]))
            while (scrollback.size > maxScrollbackLines) scrollback.removeAt(0)
        }
        fun resized(old: Array<Array<Cell>>) = Array(rows) { r ->
            Array(columns) { c -> old.getOrNull(r + offset)?.getOrNull(c)?.copy() ?: Cell() }
        }
        normalGrid = resized(normalGrid)
        altGrid = resized(altGrid)
        maxCols = columns
        maxRows = rows
        cursorRow = (cursorRow - offset).coerceIn(0, rows - 1)
        cursorCol = cursorCol.coerceIn(0, columns - 1)
        savedCursorRow = savedCursorRow.coerceIn(0, rows - 1)
        savedCursorCol = savedCursorCol.coerceIn(0, columns - 1)
    }

    private var cursorRow = 0
    private var cursorCol = 0

    private var savedCursorRow = 0
    private var savedCursorCol = 0

    // Current Style state
    private var currentFg: Color = ColorDefaultFg
    private var currentBg: Color = ColorDefaultBg
    private var currentBold: Boolean = false
    private var currentInverse: Boolean = false
    private var isDecSpecialCharset: Boolean = false

    // State machine
    private enum class State {
        NORMAL,
        ESC,
        CSI,
        CHARSET_G0,
        CHARSET_G1,
        OSC
    }

    private var state = State.NORMAL
    private val csiParams = mutableListOf<Int>()
    private val csiParamBuffer = StringBuilder()
    private var csiPrivate = false

    fun isInteractive(): Boolean = isAltScreen

    @Synchronized
    fun clear() {
        scrollback.clear()
        clearGrid(normalGrid)
        clearGrid(altGrid)
        cursorRow = 0
        cursorCol = 0
        savedCursorRow = 0
        savedCursorCol = 0
        resetSgr()
        isAltScreen = false
        state = State.NORMAL
    }

    private fun clearGrid(grid: Array<Array<Cell>>) {
        for (r in 0 until maxRows) {
            for (c in 0 until maxCols) {
                grid[r][c].reset()
            }
        }
    }

    private fun activeGrid(): Array<Array<Cell>> = if (isAltScreen) altGrid else normalGrid

    @Synchronized
    fun process(text: String) {
        val len = text.length
        var i = 0
        while (i < len) {
            val c = text[i]

            when (state) {
                State.NORMAL -> {
                    when (c) {
                        '\u001B' -> state = State.ESC
                        '\r' -> cursorCol = 0
                        '\n' -> lineFeed()
                        '\b' -> if (cursorCol > 0) cursorCol--
                        '\t' -> {
                            val nextTab = (cursorCol / 8 + 1) * 8
                            cursorCol = minOf(maxCols - 1, nextTab)
                        }
                        '\u0007' -> { /* Bell - ignore */ }
                        else -> {
                            if (c.code >= 32) {
                                val charToWrite = if (isDecSpecialCharset) mapDecSpecial(c) else c
                                writeChar(charToWrite)
                            }
                        }
                    }
                }

                State.ESC -> {
                    when (c) {
                        '[' -> {
                            csiParams.clear()
                            csiParamBuffer.clear()
                            csiPrivate = false
                            state = State.CSI
                        }
                        '(' -> state = State.CHARSET_G0
                        ')' -> state = State.CHARSET_G1
                        ']' -> state = State.OSC
                        '=' -> {
                            // Application keypad mode - consume
                            state = State.NORMAL
                        }
                        '>' -> {
                            // Numeric keypad mode - consume
                            state = State.NORMAL
                        }
                        '7' -> {
                            // Save cursor
                            savedCursorRow = cursorRow
                            savedCursorCol = cursorCol
                            state = State.NORMAL
                        }
                        '8' -> {
                            // Restore cursor
                            cursorRow = savedCursorRow.coerceIn(0, maxRows - 1)
                            cursorCol = savedCursorCol.coerceIn(0, maxCols - 1)
                            state = State.NORMAL
                        }
                        'M' -> {
                            // Reverse index
                            if (cursorRow > 0) cursorRow--
                            state = State.NORMAL
                        }
                        'c' -> {
                            // Terminal reset
                            clear()
                            state = State.NORMAL
                        }
                        else -> {
                            state = State.NORMAL
                        }
                    }
                }

                State.CHARSET_G0 -> {
                    // \u001B(0 = DEC special graphics, \u001B(B = US-ASCII
                    isDecSpecialCharset = (c == '0')
                    state = State.NORMAL
                }

                State.CHARSET_G1 -> {
                    state = State.NORMAL
                }

                State.OSC -> {
                    // Operating System Command (e.g. set title \u001B]0;...\u0007 or \u001B\)
                    if (c == '\u0007' || c == '\u001B') {
                        state = State.NORMAL
                    }
                }

                State.CSI -> {
                    if (csiParams.isEmpty() && csiParamBuffer.isEmpty() && c == '?') {
                        csiPrivate = true
                    } else if (c in '0'..'9') {
                        csiParamBuffer.append(c)
                    } else if (c == ';') {
                        csiParams.add(csiParamBuffer.toString().toIntOrNull() ?: 0)
                        csiParamBuffer.clear()
                    } else if (c in '@'..'~') {
                        // End of CSI sequence
                        if (csiParamBuffer.isNotEmpty()) {
                            csiParams.add(csiParamBuffer.toString().toIntOrNull() ?: 0)
                            csiParamBuffer.clear()
                        }
                        executeCsi(c, csiPrivate, csiParams)
                        state = State.NORMAL
                    }
                }
            }
            i++
        }
    }

    private fun writeChar(c: Char) {
        if (cursorCol >= maxCols) {
            cursorCol = 0
            lineFeed()
        }
        val grid = activeGrid()
        if (cursorRow in 0 until maxRows && cursorCol in 0 until maxCols) {
            grid[cursorRow][cursorCol].apply {
                char = c
                fg = currentFg
                bg = currentBg
                bold = currentBold
                inverse = currentInverse
            }
        }
        cursorCol++
    }

    private fun lineFeed() {
        val grid = activeGrid()
        if (cursorRow < maxRows - 1) {
            cursorRow++
        } else {
            // Scroll up
            if (!isAltScreen) {
                // Save row 0 to scrollback before scrolling
                val lineStr = rowToAnnotatedString(grid[0])
                if (lineStr.text.trim().isNotEmpty()) {
                    scrollback.add(lineStr)
                    if (scrollback.size > maxScrollbackLines) {
                        scrollback.removeAt(0)
                    }
                }
            }
            // Shift lines up
            val recycled = grid[0]
            System.arraycopy(grid, 1, grid, 0, maxRows - 1)
            grid[maxRows - 1] = recycled
            // Clear bottom row
            for (col in 0 until maxCols) {
                grid[maxRows - 1][col].reset()
            }
        }
    }

    private fun executeCsi(cmd: Char, isPrivate: Boolean, params: List<Int>) {
        if (isPrivate) {
            // DEC Private modes
            val mode = params.firstOrNull() ?: 0
            when (cmd) {
                'h' -> {
                    if (mode == 1049 || mode == 47 || mode == 1047) {
                        setAltScreenMode(true)
                    }
                }
                'l' -> {
                    if (mode == 1049 || mode == 47 || mode == 1047) {
                        setAltScreenMode(false)
                    }
                }
            }
            return
        }

        when (cmd) {
            'H', 'f' -> {
                // Cursor position: 1-indexed
                val r = if (params.isNotEmpty() && params[0] > 0) params[0] - 1 else 0
                val c = if (params.size > 1 && params[1] > 0) params[1] - 1 else 0
                cursorRow = r.coerceIn(0, maxRows - 1)
                cursorCol = c.coerceIn(0, maxCols - 1)
            }
            'A' -> {
                val count = if (params.isNotEmpty() && params[0] > 0) params[0] else 1
                cursorRow = (cursorRow - count).coerceAtLeast(0)
            }
            'B' -> {
                val count = if (params.isNotEmpty() && params[0] > 0) params[0] else 1
                cursorRow = (cursorRow + count).coerceAtMost(maxRows - 1)
            }
            'C' -> {
                val count = if (params.isNotEmpty() && params[0] > 0) params[0] else 1
                cursorCol = (cursorCol + count).coerceAtMost(maxCols - 1)
            }
            'D' -> {
                val count = if (params.isNotEmpty() && params[0] > 0) params[0] else 1
                cursorCol = (cursorCol - count).coerceAtLeast(0)
            }
            'G' -> {
                val c = if (params.isNotEmpty() && params[0] > 0) params[0] - 1 else 0
                cursorCol = c.coerceIn(0, maxCols - 1)
            }
            'd' -> {
                val r = if (params.isNotEmpty() && params[0] > 0) params[0] - 1 else 0
                cursorRow = r.coerceIn(0, maxRows - 1)
            }
            'J' -> {
                val mode = params.firstOrNull() ?: 0
                val grid = activeGrid()
                when (mode) {
                    0 -> {
                        // Clear from cursor to end of screen
                        for (c in cursorCol until maxCols) grid[cursorRow][c].reset()
                        for (r in cursorRow + 1 until maxRows) {
                            for (c in 0 until maxCols) grid[r][c].reset()
                        }
                    }
                    1 -> {
                        // Clear from start to cursor
                        for (r in 0 until cursorRow) {
                            for (c in 0 until maxCols) grid[r][c].reset()
                        }
                        for (c in 0..cursorCol) grid[cursorRow][c].reset()
                    }
                    2, 3 -> {
                        // Clear entire screen
                        clearGrid(grid)
                        cursorRow = 0
                        cursorCol = 0
                    }
                }
            }
            'K' -> {
                val mode = params.firstOrNull() ?: 0
                val grid = activeGrid()
                when (mode) {
                    0 -> for (c in cursorCol until maxCols) grid[cursorRow][c].reset()
                    1 -> for (c in 0..cursorCol) grid[cursorRow][c].reset()
                    2 -> for (c in 0 until maxCols) grid[cursorRow][c].reset()
                }
            }
            'X' -> {
                val count = if (params.isNotEmpty() && params[0] > 0) params[0] else 1
                val grid = activeGrid()
                for (c in cursorCol until minOf(maxCols, cursorCol + count)) {
                    grid[cursorRow][c].reset()
                }
            }
            'm' -> {
                handleSgr(params)
            }
        }
    }

    private fun setAltScreenMode(enabled: Boolean) {
        if (isAltScreen != enabled) {
            isAltScreen = enabled
            if (enabled) {
                clearGrid(altGrid)
                cursorRow = 0
                cursorCol = 0
            }
            onInteractiveModeChanged?.invoke(enabled)
        }
    }

    private fun handleSgr(params: List<Int>) {
        if (params.isEmpty()) {
            resetSgr()
            return
        }

        var idx = 0
        while (idx < params.size) {
            when (val code = params[idx]) {
                0 -> resetSgr()
                1 -> currentBold = true
                2, 22 -> currentBold = false
                7 -> currentInverse = true
                27 -> currentInverse = false
                30 -> currentFg = ColorBlack
                31 -> currentFg = ColorRed
                32 -> currentFg = ColorGreen
                33 -> currentFg = ColorYellow
                34 -> currentFg = ColorBlue
                35 -> currentFg = ColorMagenta
                36 -> currentFg = ColorCyan
                37 -> currentFg = ColorWhite
                39 -> currentFg = ColorDefaultFg
                90 -> currentFg = ColorGray
                91 -> currentFg = ColorRed
                92 -> currentFg = ColorGreen
                93 -> currentFg = ColorYellow
                94 -> currentFg = ColorBlue
                95 -> currentFg = ColorMagenta
                96 -> currentFg = ColorCyan
                97 -> currentFg = ColorWhite
                40 -> currentBg = ColorBlack
                41 -> currentBg = ColorRed
                42 -> currentBg = ColorGreen
                43 -> currentBg = ColorYellow
                44 -> currentBg = ColorBlue
                45 -> currentBg = ColorMagenta
                46 -> currentBg = ColorCyan
                47 -> currentBg = ColorWhite
                49 -> currentBg = ColorDefaultBg
            }
            idx++
        }
    }

    private fun resetSgr() {
        currentFg = ColorDefaultFg
        currentBg = ColorDefaultBg
        currentBold = false
        currentInverse = false
    }

    private fun mapDecSpecial(c: Char): Char {
        return when (c) {
            'q' -> '─'
            'x' -> '│'
            'l' -> '┌'
            'k' -> '┐'
            'm' -> '└'
            'j' -> '┘'
            't' -> '├'
            'u' -> '┤'
            'v' -> '┴'
            'w' -> '┬'
            'n' -> '┼'
            'a' -> '▒'
            '`' -> '◆'
            '~' -> '•'
            else -> c
        }
    }

    @Synchronized
    fun render(): AnnotatedString {
        return if (isAltScreen) {
            renderAltScreen()
        } else {
            renderNormalScreen()
        }
    }

    private fun renderAltScreen(): AnnotatedString {
        return buildAnnotatedString {
            for (r in 0 until maxRows) {
                val rowAnnotated = rowToAnnotatedString(altGrid[r])
                append(rowAnnotated)
                if (r < maxRows - 1) {
                    append("\n")
                }
            }
        }
    }

    private fun renderNormalScreen(): AnnotatedString {
        return buildAnnotatedString {
            // Prepend scrollback lines
            for (line in scrollback) {
                append(line)
                append("\n")
            }

            // Find last row with any content in normalGrid
            var lastNonEmptyRow = 0
            for (r in maxRows - 1 downTo 0) {
                if (normalGrid[r].any { it.char != ' ' }) {
                    lastNonEmptyRow = r
                    break
                }
            }

            for (r in 0..lastNonEmptyRow) {
                val rowAnnotated = rowToAnnotatedString(normalGrid[r])
                append(rowAnnotated)
                if (r < lastNonEmptyRow) {
                    append("\n")
                }
            }
        }
    }

    private fun rowToAnnotatedString(row: Array<Cell>): AnnotatedString {
        // Find last non-space cell to avoid excessive trailing spaces
        var lastNonSpace = -1
        for (c in maxCols - 1 downTo 0) {
            if (row[c].char != ' ' || row[c].bg != Color.Transparent || row[c].inverse) {
                lastNonSpace = c
                break
            }
        }

        if (lastNonSpace == -1) {
            return AnnotatedString("")
        }

        return buildAnnotatedString {
            var currentSpanFg: Color? = null
            var currentSpanBg: Color? = null
            var currentSpanBold = false
            var buffer = StringBuilder()

            fun flush() {
                if (buffer.isNotEmpty()) {
                    withStyle(
                        SpanStyle(
                            color = currentSpanFg ?: ColorDefaultFg,
                            background = currentSpanBg ?: Color.Transparent,
                            fontWeight = if (currentSpanBold) FontWeight.Bold else FontWeight.Normal
                        )
                    ) {
                        append(buffer.toString())
                    }
                    buffer.clear()
                }
            }

            for (c in 0..lastNonSpace) {
                val cell = row[c]
                val effectiveFg = if (cell.inverse) ColorInverseFg else cell.fg
                val effectiveBg = if (cell.inverse) ColorInverseBg else cell.bg
                val effectiveBold = cell.bold

                if (effectiveFg != currentSpanFg || effectiveBg != currentSpanBg || effectiveBold != currentSpanBold) {
                    flush()
                    currentSpanFg = effectiveFg
                    currentSpanBg = effectiveBg
                    currentSpanBold = effectiveBold
                }
                buffer.append(cell.char)
            }
            flush()
        }
    }
}
