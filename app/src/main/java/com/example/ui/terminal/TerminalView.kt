package com.example.ui.terminal

import android.content.Context
import android.graphics.Canvas
import android.text.InputType
import android.view.GestureDetector
import android.view.KeyEvent
import android.view.MotionEvent
import android.view.View
import android.view.inputmethod.BaseInputConnection
import android.view.inputmethod.EditorInfo
import android.view.inputmethod.InputConnection
import android.view.inputmethod.InputMethodManager
import androidx.core.content.res.ResourcesCompat
import com.example.R
import com.example.data.ssh.SshTerminalSession
import com.termux.terminal.KeyHandler
import com.termux.view.TerminalRenderer
import kotlin.math.roundToInt

/** A cell-based canvas: keyboard animation never lays out the transcript as text. */
class TerminalView(context: Context) : View(context) {
    var session: SshTerminalSession? = null
        set(value) {
            if (field === value) return
            field = value
            topRow = 0
            input?.finishComposingText()
            (context.getSystemService(Context.INPUT_METHOD_SERVICE) as InputMethodManager).restartInput(this)
            resizeTerminal()
            invalidate()
        }
    var ctrl = false
    var onCtrlUsed: () -> Unit = {}
    private var input: InputConnection? = null
    private val inset = 6 * resources.displayMetrics.density
    private val renderer = TerminalRenderer((12 * resources.displayMetrics.scaledDensity).roundToInt(),
        ResourcesCompat.getFont(context, R.font.jetbrains_mono)!!)
    private var topRow = 0
    private var scrollRemainder = 0f
    private var resizeRequest = 0L
    private val applyResize = Runnable {
        val request = resizeRequest
        session?.resize(
            ((width - 2 * inset) / renderer.fontWidth).toInt().coerceAtLeast(20),
            ((height - 2 * inset) / renderer.fontLineSpacing).toInt().coerceAtLeast(4)
        )
        if (request != resizeRequest) resizeTerminal()
    }
    private val gestures = GestureDetector(context, object : GestureDetector.SimpleOnGestureListener() {
        override fun onDown(e: MotionEvent) = true
        override fun onSingleTapUp(e: MotionEvent): Boolean { performClick(); showKeyboard(); return true }
        override fun onScroll(e1: MotionEvent?, e2: MotionEvent, distanceX: Float, distanceY: Float): Boolean {
            val engine = session?.engine ?: return true
            scrollRemainder += distanceY
            val lines = (scrollRemainder / renderer.fontLineSpacing).toInt()
            scrollRemainder -= lines * renderer.fontLineSpacing
            synchronized(engine.lock) {
            topRow = (topRow - lines).coerceIn(-engine.emulator.screen.activeTranscriptRows, 0)
            }
            invalidate()
            return true
        }
        override fun onLongPress(e: MotionEvent) {
            val engine = session?.engine ?: return
        val text = synchronized(engine.lock) { engine.emulator.screen.transcriptText }
            (context.getSystemService(Context.CLIPBOARD_SERVICE) as android.content.ClipboardManager)
                .setPrimaryClip(android.content.ClipData.newPlainText("Terminal", text))
            android.widget.Toast.makeText(context, "Output disalin", android.widget.Toast.LENGTH_SHORT).show()
        }
    })

    init {
        isFocusable = true
        isFocusableInTouchMode = true
        setBackgroundColor(0xff141729.toInt())
        contentDescription = "Terminal SSH. Ketuk untuk mengetik, geser untuk riwayat, tahan untuk menyalin."
    }

    fun showKeyboard() {
        requestFocus()
        (context.getSystemService(Context.INPUT_METHOD_SERVICE) as InputMethodManager)
            .showSoftInput(this, InputMethodManager.SHOW_IMPLICIT)
    }

    override fun onCheckIsTextEditor() = true
    override fun performClick(): Boolean { super.performClick(); return true }
    override fun onTouchEvent(event: MotionEvent): Boolean = gestures.onTouchEvent(event)
    override fun onSizeChanged(w: Int, h: Int, oldw: Int, oldh: Int) = resizeTerminal()
    private fun resizeTerminal() {
        if (width > 0 && height > 0) {
            resizeRequest++
            removeCallbacks(applyResize)
            postDelayed(applyResize, 180)
        }
    }

    override fun onDraw(canvas: Canvas) {
        super.onDraw(canvas)
        val engine = session?.engine ?: return
        canvas.save()
        canvas.translate(inset, inset)
        synchronized(engine.lock) {
            topRow = topRow.coerceIn(-engine.emulator.screen.activeTranscriptRows, 0)
            renderer.render(engine.emulator, canvas, topRow, -1, -1, -1, -1)
        }
        canvas.restore()
    }

    private fun send(text: String) {
        topRow = 0
        if (ctrl && text.isNotEmpty()) {
            val c = text.first().uppercaseChar()
            val control = when (c) { in '@'..'_' -> c.code - 64; ' ' -> 0; '?' -> 127; else -> null }
            if (control != null) session?.sendRawBytes(byteArrayOf(control.toByte()))
            ctrl = false
            onCtrlUsed()
        } else session?.sendRawBytes(text.replace("\r\n", "\r").replace('\n', '\r').toByteArray())
    }

    override fun onCreateInputConnection(outAttrs: EditorInfo): InputConnection {
        outAttrs.inputType = InputType.TYPE_CLASS_TEXT or InputType.TYPE_TEXT_VARIATION_VISIBLE_PASSWORD or InputType.TYPE_TEXT_FLAG_NO_SUGGESTIONS
        outAttrs.imeOptions = EditorInfo.IME_FLAG_NO_EXTRACT_UI or EditorInfo.IME_FLAG_NO_FULLSCREEN or EditorInfo.IME_ACTION_NONE
        outAttrs.initialSelStart = 0
        outAttrs.initialSelEnd = 0
        return object : BaseInputConnection(this, false) {
            // Some keyboards compose despite NO_SUGGESTIONS. Diff only their current
            // composing word; never reset the editor to an invisible sentinel character.
            private var composing = ""
            private fun replaceComposition(text: String) {
                val previous = composing.codePoints().toArray()
                val next = text.codePoints().toArray()
                var common = 0
                while (common < previous.size && common < next.size && previous[common] == next[common]) common++
                if (common < previous.size) {
                    repeat(previous.size - common) { send("\u007f") }
                    if (common < next.size) send(String(next, common, next.size - common))
                } else if (common < next.size) send(String(next, common, next.size - common))
                composing = text
            }
            override fun setComposingText(text: CharSequence, newCursorPosition: Int): Boolean {
                replaceComposition(text.toString()); return true
            }
            override fun commitText(text: CharSequence, newCursorPosition: Int): Boolean {
                replaceComposition(text.toString()); composing = ""; return true
            }
            override fun finishComposingText(): Boolean { composing = ""; return true }
            override fun deleteSurroundingText(beforeLength: Int, afterLength: Int): Boolean {
                repeat(beforeLength.coerceIn(0, 1024)) { send("\u007f") }
                repeat(afterLength.coerceIn(0, 1024)) { send("\u001b[3~") }
                composing = composing.dropLast(beforeLength.coerceAtLeast(0).coerceAtMost(composing.length))
                return true
            }
            override fun deleteSurroundingTextInCodePoints(beforeLength: Int, afterLength: Int) =
                deleteSurroundingText(beforeLength, afterLength)
            override fun getTextBeforeCursor(n: Int, flags: Int): CharSequence = composing.takeLast(n.coerceAtLeast(0))
            override fun getTextAfterCursor(n: Int, flags: Int): CharSequence = ""
            override fun getSelectedText(flags: Int): CharSequence = ""
            override fun performEditorAction(actionCode: Int): Boolean { composing = ""; send("\r"); return true }
            override fun sendKeyEvent(event: KeyEvent): Boolean = this@TerminalView.dispatchKeyEvent(event)
        }.also { input = it }
    }

    override fun onKeyDown(keyCode: Int, event: KeyEvent): Boolean {
        if (keyCode == KeyEvent.KEYCODE_BACK) return super.onKeyDown(keyCode, event)
        var modifiers = 0
        if (event.isCtrlPressed) modifiers = modifiers or KeyHandler.KEYMOD_CTRL
        if (event.isAltPressed) modifiers = modifiers or KeyHandler.KEYMOD_ALT
        if (event.isShiftPressed) modifiers = modifiers or KeyHandler.KEYMOD_SHIFT
        val engine = session?.engine ?: return false
        val code = synchronized(engine.lock) { KeyHandler.getCode(keyCode, modifiers,
            engine.emulator.isCursorKeysApplicationMode, engine.emulator.isKeypadApplicationMode) }
        if (code != null) { session?.sendRawBytes(code.toByteArray()); return true }
        val unicode = event.getUnicodeChar(event.metaState and KeyEvent.META_CTRL_MASK.inv())
        if (unicode > 0) {
            if (event.isCtrlPressed) ctrl = true
            session?.sendRawBytes(((if (event.isAltPressed) "\u001b" else "") + String(Character.toChars(unicode))).toByteArray())
            return true
        }
        return super.onKeyDown(keyCode, event)
    }
}
