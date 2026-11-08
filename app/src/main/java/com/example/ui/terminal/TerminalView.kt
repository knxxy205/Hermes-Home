package com.example.ui.terminal

import android.content.ClipData
import android.content.ClipboardManager
import android.content.Context
import android.graphics.Canvas
import android.graphics.Paint
import android.graphics.Rect
import android.text.InputType
import android.view.*
import android.view.inputmethod.BaseInputConnection
import android.view.inputmethod.EditorInfo
import android.view.inputmethod.InputConnection
import android.view.inputmethod.InputMethodManager
import android.widget.OverScroller
import androidx.core.content.res.ResourcesCompat
import com.example.R
import com.example.data.preferences.AppPreferences
import com.example.data.ssh.SshTerminalSession
import com.termux.terminal.KeyHandler
import com.termux.view.TerminalRenderer
import kotlin.math.roundToInt
import kotlin.math.floor
import kotlin.math.ceil

class TerminalView(context: Context) : View(context) {
    var session: SshTerminalSession? = null
        set(value) {
            if (field === value) return
            finishSelection()
            field?.let { viewports[it.id] = Viewport(topRow, pixelOffset) }
            field = value
            val viewport = value?.let { viewports[it.id] }
            topRow = viewport?.row ?: 0
            pixelOffset = viewport?.offset ?: 0f
            input?.finishComposingText()
            inputManager.restartInput(this)
            resizeTerminal()
            publishViewport()
            invalidate()
        }
    var ctrl = false
    var alt = false
    var onModifiersUsed: () -> Unit = {}
    var onViewportChanged: (Boolean) -> Unit = {}
    var onFontSizeChanged: (Int) -> Unit = {}
    var onPasteRequested: (String) -> Unit = {}
    private val inputManager get() = context.getSystemService(Context.INPUT_METHOD_SERVICE) as InputMethodManager
    private var input: InputConnection? = null
    private val inset = 6 * resources.displayMetrics.density
    private var preferences = AppPreferences()
    private var preferencesActive: Boolean? = null
    private var fontSize = 12f
    private var renderer = makeRenderer()
    private val scroller = OverScroller(context)
    private var topRow = 0
    private var pixelOffset = 0f
    private data class Viewport(val row: Int, val offset: Float)
    private val viewports = mutableMapOf<String, Viewport>()
    private var publishedScrolling = false
    private data class Cell(val row: Int, val column: Int) : Comparable<Cell> {
        override fun compareTo(other: Cell) = compareValuesBy(this, other, Cell::row, Cell::column)
    }
    private var start: Cell? = null
    private var end: Cell? = null
    private var draggingStart = false
    private var selectionMode: ActionMode? = null
    private var searchMatch: TerminalMatch? = null
    private var drawnRevision = Long.MIN_VALUE
    private val handlePaint = Paint(Paint.ANTI_ALIAS_FLAG).apply { color = 0xFF90CAF9.toInt() }
    private val applyResize = Runnable {
        session?.resize(((width - 2 * inset) / renderer.fontWidth).toInt().coerceAtLeast(20),
            ((height - 2 * inset) / renderer.fontLineSpacing).toInt().coerceAtLeast(4))
    }

    private fun makeRenderer() = TerminalRenderer((fontSize * resources.displayMetrics.scaledDensity).roundToInt(),
        ResourcesCompat.getFont(context, if (preferences.terminalFont == "fira") R.font.fira_code else R.font.jetbrains_mono)!!)

    fun applyPreferences(value: AppPreferences, active: Boolean) {
        if (preferences == value && preferencesActive == active) return
        preferencesActive = active
        keepScreenOn = active && value.keepScreenOn
        contentDescription = "Terminal SSH, ${if (value.terminalFont == "fira") "Fira Code" else "JetBrains Mono"}, ${value.terminalFontSize} sp. Ketuk untuk mengetik, geser untuk riwayat, tahan untuk memilih teks, cubit untuk ukuran huruf."
        if (preferences.terminalFontSize != value.terminalFontSize || preferences.terminalFont != value.terminalFont) {
            fontSize = value.terminalFontSize.toFloat()
            preferences = value
            renderer = makeRenderer()
            pixelOffset = 0f
            finishSelection()
            resizeTerminal()
            invalidate()
        } else preferences = value
    }

    private val scaleDetector = ScaleGestureDetector(context, object : ScaleGestureDetector.SimpleOnScaleGestureListener() {
        override fun onScaleBegin(detector: ScaleGestureDetector): Boolean { finishSelection(); scroller.forceFinished(true); pixelOffset = 0f; return true }
        override fun onScale(detector: ScaleGestureDetector): Boolean {
            fontSize = (fontSize * detector.scaleFactor).coerceIn(10f, 24f)
            renderer = makeRenderer()
            resizeTerminal()
            invalidate()
            return true
        }
        override fun onScaleEnd(detector: ScaleGestureDetector) { onFontSizeChanged(fontSize.roundToInt()) }
    })

    private val gestures = GestureDetector(context, object : GestureDetector.SimpleOnGestureListener() {
        override fun onDown(e: MotionEvent): Boolean { scroller.forceFinished(true); return true }
        override fun onSingleTapUp(e: MotionEvent): Boolean { performClick(); showKeyboard(); return true }
        override fun onScroll(e1: MotionEvent?, e2: MotionEvent, distanceX: Float, distanceY: Float): Boolean {
            if (scaleDetector.isInProgress) return true
            scrollToPixel(topRow * renderer.fontLineSpacing + pixelOffset + distanceY)
            return true
        }
        override fun onFling(e1: MotionEvent?, e2: MotionEvent, velocityX: Float, velocityY: Float): Boolean {
            val history = session?.engine?.let { synchronized(it.lock) { it.emulator.screen.activeTranscriptRows } } ?: 0
            scroller.fling(0, (topRow * renderer.fontLineSpacing + pixelOffset).roundToInt(), 0, -velocityY.toInt(), 0, 0, -history * renderer.fontLineSpacing, 0)
            postInvalidateOnAnimation()
            return true
        }
        override fun onLongPress(e: MotionEvent) {
            val engine = session?.engine ?: return
            val cell = cellAt(e.x, e.y)
            synchronized(engine.lock) {
                val screen = engine.emulator.screen
                fun nonBlank(column: Int) = screen.getSelectedText(column, cell.row, column, cell.row).isNotBlank()
                var left = cell.column
                var right = cell.column
                while (left > 0 && nonBlank(left - 1)) left--
                while (right < engine.emulator.mColumns - 1 && nonBlank(right + 1)) right++
                start = Cell(cell.row, left); end = Cell(cell.row, right)
            }
            searchMatch = null
            selectionMode = startActionMode(selectionCallback, ActionMode.TYPE_FLOATING)
            performHapticFeedback(HapticFeedbackConstants.LONG_PRESS)
            invalidate()
        }
    })

    private val selectionCallback = object : ActionMode.Callback2() {
        override fun onCreateActionMode(mode: ActionMode, menu: Menu): Boolean {
            menu.add(0, android.R.id.copy, 0, "Salin").setShowAsAction(MenuItem.SHOW_AS_ACTION_IF_ROOM)
            menu.add(0, android.R.id.paste, 1, "Tempel")
            menu.add(0, android.R.id.selectAll, 2, "Pilih semua")
            return true
        }
        override fun onPrepareActionMode(mode: ActionMode, menu: Menu) = false
        override fun onActionItemClicked(mode: ActionMode, item: MenuItem): Boolean {
            when (item.itemId) {
                android.R.id.copy -> { copySelection(); mode.finish() }
                android.R.id.paste -> { requestPaste(); mode.finish() }
                android.R.id.selectAll -> {
                    session?.engine?.let { synchronized(it.lock) {
                        start = Cell(-it.emulator.screen.activeTranscriptRows, 0)
                        end = Cell(it.emulator.mRows - 1, it.emulator.mColumns - 1)
                    } }
                    mode.invalidateContentRect(); invalidate()
                }
                else -> return false
            }
            return true
        }
        override fun onDestroyActionMode(mode: ActionMode) { selectionMode = null; start = null; end = null; invalidate() }
        override fun onGetContentRect(mode: ActionMode, view: View, outRect: Rect) {
            val cell = end ?: start ?: return
            val x = (inset + cell.column * renderer.fontWidth).toInt().coerceIn(0, width)
            val y = (inset + (cell.row - topRow + 1) * renderer.fontLineSpacing - pixelOffset).toInt().coerceIn(0, height)
            outRect.set(x, (y - renderer.fontLineSpacing).coerceAtLeast(0), (x + renderer.fontWidth.toInt()).coerceAtMost(width), y)
        }
    }

    init {
        isFocusable = true; isFocusableInTouchMode = true
        setBackgroundColor(0xFF141729.toInt())
        contentDescription = "Terminal SSH. Ketuk untuk mengetik, geser untuk riwayat, tahan untuk memilih teks, cubit untuk ukuran huruf."
    }

    private fun cellAt(x: Float, y: Float): Cell {
        val engine = session?.engine ?: return Cell(0, 0)
        return synchronized(engine.lock) {
            Cell((topRow + floor((y - inset + pixelOffset) / renderer.fontLineSpacing).toInt()).coerceIn(-engine.emulator.screen.activeTranscriptRows, engine.emulator.mRows - 1),
                ((x - inset) / renderer.fontWidth).toInt().coerceIn(0, engine.emulator.mColumns - 1))
        }
    }

    private fun finishSelection() { selectionMode?.finish(); start = null; end = null; searchMatch = null }
    fun copySelection() {
        val engine = session?.engine ?: return
        val a = start ?: return
        val b = end ?: return
        val first = minOf(a, b); val last = maxOf(a, b)
        val text = synchronized(engine.lock) { engine.emulator.screen.getSelectedText(first.column, first.row, last.column, last.row) }
        (context.getSystemService(Context.CLIPBOARD_SERVICE) as ClipboardManager).setPrimaryClip(ClipData.newPlainText("Terminal", text))
    }
    fun requestPaste() {
        val clipboard = context.getSystemService(Context.CLIPBOARD_SERVICE) as ClipboardManager
        clipboard.primaryClip?.getItemAt(0)?.coerceToText(context)?.toString()?.let(onPasteRequested)
    }
    fun showKeyboard() { requestFocus(); inputManager.showSoftInput(this, InputMethodManager.SHOW_IMPLICIT) }
    fun hideKeyboard() { inputManager.hideSoftInputFromWindow(windowToken, 0) }
    fun scrollToLatest() { finishSelection(); scroller.forceFinished(true); scrollToRow(0) }
    fun highlight(match: TerminalMatch?) {
        finishSelection(); searchMatch = match
        if (match != null) scrollToRow(match.row - 2)
        invalidate()
    }
    fun invalidateForRevision(revision: Long) {
        if (drawnRevision == revision) return
        drawnRevision = revision
        postInvalidateOnAnimation()
    }
    private fun publishViewport() {
        val scrolling = topRow < 0
        if (publishedScrolling != scrolling) {
            publishedScrolling = scrolling
            onViewportChanged(scrolling)
        }
    }
    private fun scrollToRow(row: Int) {
        scrollToPixel(row * renderer.fontLineSpacing.toFloat())
    }
    private fun scrollToPixel(position: Float) {
        val engine = session?.engine ?: return
        val history = synchronized(engine.lock) { engine.emulator.screen.activeTranscriptRows }
        val pixel = position.coerceIn(-history * renderer.fontLineSpacing.toFloat(), 0f)
        val row = floor(pixel / renderer.fontLineSpacing).toInt()
        val offset = pixel - row * renderer.fontLineSpacing
        if (topRow == row && pixelOffset == offset) return
        topRow = row
        pixelOffset = offset
        publishViewport(); postInvalidateOnAnimation()
    }
    override fun computeScroll() {
        if (scroller.computeScrollOffset()) { scrollToPixel(scroller.currY.toFloat()); postInvalidateOnAnimation() }
    }
    override fun onCheckIsTextEditor() = true
    override fun performClick(): Boolean { super.performClick(); return true }
    override fun onTouchEvent(event: MotionEvent): Boolean {
        scaleDetector.onTouchEvent(event)
        if (event.pointerCount > 1 || scaleDetector.isInProgress) return true
        if (selectionMode != null) {
            when (event.actionMasked) {
                MotionEvent.ACTION_DOWN -> {
                    val cell = cellAt(event.x, event.y)
                    fun distance(point: Cell?) = point?.let { kotlin.math.abs(it.row - cell.row) * 1000 + kotlin.math.abs(it.column - cell.column) } ?: Int.MAX_VALUE
                    draggingStart = distance(start) < distance(end)
                }
                MotionEvent.ACTION_MOVE -> {
                    if (event.y < inset) scrollToRow(topRow - 1) else if (event.y > height - inset) scrollToRow(topRow + 1)
                    if (draggingStart) start = cellAt(event.x, event.y) else end = cellAt(event.x, event.y)
                    selectionMode?.invalidateContentRect(); invalidate()
                }
            }
            return true
        }
        gestures.onTouchEvent(event)
        return true
    }
    override fun onSizeChanged(w: Int, h: Int, oldw: Int, oldh: Int) { finishSelection(); resizeTerminal() }
    private fun resizeTerminal() { if (width > 0 && height > 0) { removeCallbacks(applyResize); postOnAnimation(applyResize) } }
    override fun onDetachedFromWindow() { removeCallbacks(applyResize); finishSelection(); super.onDetachedFromWindow() }

    override fun onDraw(canvas: Canvas) {
        super.onDraw(canvas)
        val engine = session?.engine ?: return
        canvas.save()
        synchronized(engine.lock) {
            val scrolled = engine.emulator.scrollCounter
            if (topRow < 0) topRow -= scrolled
            start = start?.let { it.copy(row = it.row - scrolled) }
            end = end?.let { it.copy(row = it.row - scrolled) }
            if (scrolled > 0) searchMatch = null
            engine.emulator.clearScrollCounter()
            val oldest = -engine.emulator.screen.activeTranscriptRows
            topRow = topRow.coerceIn(oldest, 0)
            if (topRow == 0) pixelOffset = 0f
            canvas.translate(inset, inset - pixelOffset)
            if ((start?.row ?: 0) < oldest || (end?.row ?: 0) < oldest) finishSelection()
            val a = start; val b = end
            val first = if (a != null && b != null) minOf(a, b) else null
            val last = if (a != null && b != null) maxOf(a, b) else null
            val match = searchMatch
            renderer.render(engine.emulator, canvas, topRow, first?.row ?: match?.row ?: -1,
                last?.row ?: match?.row ?: -1, first?.column ?: match?.startColumn ?: -1, last?.column ?: match?.endColumn ?: -1,
                ceil((height - 2 * inset + pixelOffset) / renderer.fontLineSpacing).toInt().coerceAtLeast(1))
            listOfNotNull(first, last).forEach { cell ->
                val y = (cell.row - topRow + 1) * renderer.fontLineSpacing.toFloat()
                if (y in 0f..height.toFloat()) canvas.drawCircle((cell.column + 0.5f) * renderer.fontWidth, y, 5 * resources.displayMetrics.density, handlePaint)
            }
        }
        canvas.restore()
    }

    private fun send(text: String) {
        scrollToLatest()
        var value = text.replace("\r\n", "\r").replace('\n', '\r')
        if (ctrl && value.isNotEmpty()) {
            controlCode(value.first())?.let { value = it.toChar().toString() }
        }
        if (alt) value = "\u001b$value"
        session?.sendRawBytes(value.toByteArray(Charsets.UTF_8))
        if (ctrl || alt) { ctrl = false; alt = false; onModifiersUsed() }
    }
    private fun controlCode(char: Char): Int? = when (val c = char.uppercaseChar()) {
        in '@'..'_' -> c.code - 64
        ' ' -> 0
        '?' -> 127
        else -> null
    }

    override fun onCreateInputConnection(outAttrs: EditorInfo): InputConnection {
        outAttrs.inputType = InputType.TYPE_CLASS_TEXT or InputType.TYPE_TEXT_VARIATION_VISIBLE_PASSWORD or InputType.TYPE_TEXT_FLAG_NO_SUGGESTIONS
        outAttrs.imeOptions = EditorInfo.IME_FLAG_NO_EXTRACT_UI or EditorInfo.IME_FLAG_NO_FULLSCREEN or EditorInfo.IME_ACTION_NONE
        outAttrs.initialSelStart = 0; outAttrs.initialSelEnd = 0
        return object : BaseInputConnection(this, false) {
            private var composing = ""
            private fun replaceComposition(text: String) {
                val previous = composing.codePoints().toArray(); val next = text.codePoints().toArray()
                var common = 0
                while (common < previous.size && common < next.size && previous[common] == next[common]) common++
                repeat(previous.size - common) { send("\u007f") }
                if (common < next.size) send(String(next, common, next.size - common))
                composing = text
            }
            override fun setComposingText(text: CharSequence, newCursorPosition: Int): Boolean { replaceComposition(text.toString()); return true }
            override fun commitText(text: CharSequence, newCursorPosition: Int): Boolean { replaceComposition(text.toString()); composing = ""; return true }
            override fun finishComposingText(): Boolean { composing = ""; return true }
            override fun deleteSurroundingText(beforeLength: Int, afterLength: Int): Boolean {
                repeat(beforeLength.coerceIn(0, 1024)) { send("\u007f") }
                repeat(afterLength.coerceIn(0, 1024)) { send("\u001b[3~") }
                composing = composing.dropLast(beforeLength.coerceIn(0, composing.length)); return true
            }
            override fun deleteSurroundingTextInCodePoints(beforeLength: Int, afterLength: Int): Boolean {
                repeat(beforeLength.coerceIn(0, 1024)) { send("\u007f") }
                repeat(afterLength.coerceIn(0, 1024)) { send("\u001b[3~") }
                val count = beforeLength.coerceIn(0, composing.codePointCount(0, composing.length))
                composing = composing.substring(0, composing.offsetByCodePoints(composing.length, -count))
                return true
            }
            override fun getTextBeforeCursor(n: Int, flags: Int): CharSequence = composing.takeLast(n.coerceAtLeast(0))
            override fun getTextAfterCursor(n: Int, flags: Int): CharSequence = ""
            override fun getSelectedText(flags: Int): CharSequence = ""
            override fun performEditorAction(actionCode: Int): Boolean { composing = ""; send("\r"); return true }
            override fun sendKeyEvent(event: KeyEvent): Boolean = dispatchKeyEvent(event)
        }.also { input = it }
    }
    override fun onKeyDown(keyCode: Int, event: KeyEvent): Boolean {
        if (keyCode == KeyEvent.KEYCODE_BACK) {
            if (selectionMode != null) { finishSelection(); return true }
            return super.onKeyDown(keyCode, event)
        }
        if (event.isCtrlPressed && event.isShiftPressed && keyCode == KeyEvent.KEYCODE_C) { copySelection(); return true }
        if (event.isCtrlPressed && event.isShiftPressed && keyCode == KeyEvent.KEYCODE_V) { requestPaste(); return true }
        var modifiers = 0
        if (event.isCtrlPressed || ctrl) modifiers = modifiers or KeyHandler.KEYMOD_CTRL
        if (event.isAltPressed || alt) modifiers = modifiers or KeyHandler.KEYMOD_ALT
        if (event.isShiftPressed) modifiers = modifiers or KeyHandler.KEYMOD_SHIFT
        val engine = session?.engine ?: return false
        val code = synchronized(engine.lock) { KeyHandler.getCode(keyCode, modifiers, engine.emulator.isCursorKeysApplicationMode, engine.emulator.isKeypadApplicationMode) }
        if (code != null) { scrollToLatest(); session?.sendRawBytes(code.toByteArray()); ctrl = false; alt = false; onModifiersUsed(); return true }
        val unicode = event.getUnicodeChar(event.metaState and KeyEvent.META_CTRL_MASK.inv() and KeyEvent.META_ALT_MASK.inv())
        if (unicode > 0) {
            ctrl = ctrl || event.isCtrlPressed; alt = alt || event.isAltPressed
            send(String(Character.toChars(unicode))); return true
        }
        return super.onKeyDown(keyCode, event)
    }
}
