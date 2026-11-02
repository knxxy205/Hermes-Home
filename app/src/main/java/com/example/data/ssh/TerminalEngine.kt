package com.example.data.ssh

import com.termux.terminal.TerminalEmulator
import com.termux.terminal.TerminalOutput
import com.termux.terminal.TerminalSessionClient
import com.termux.terminal.TextStyle
import java.lang.reflect.Proxy

/** All emulator access is guarded by this object; network writes only enqueue bytes. */
class TerminalEngine(private val send: (ByteArray) -> Unit) {
    val lock: Any get() = this
    private val client = Proxy.newProxyInstance(
        TerminalSessionClient::class.java.classLoader,
        arrayOf(TerminalSessionClient::class.java)
    ) { _, method, _ -> if (method.name == "getTerminalCursorStyle") 0 else null } as TerminalSessionClient

    val emulator = TerminalEmulator(object : TerminalOutput() {
        override fun write(data: ByteArray, offset: Int, count: Int) = send(data.copyOfRange(offset, offset + count))
        override fun titleChanged(oldTitle: String?, newTitle: String?) {}
        override fun onCopyTextToClipboard(text: String?) {}
        override fun onPasteTextFromClipboard() {}
        override fun onBell() {}
        override fun onColorsChanged() {}
    }, 80, 24, 2000, client)

    init { palette() }

    private fun palette() {
        val colors = intArrayOf(0xff24283b.toInt(), 0xffd77b87.toInt(), 0xff62ae91.toInt(),
            0xffd8b77a.toInt(), 0xff75a7d1.toInt(), 0xffb49ac9.toInt(), 0xff7fb5bc.toInt(), 0xffc6cbd7.toInt())
        colors.forEachIndexed { i, color ->
            emulator.mColors.mCurrentColors[i] = color
            emulator.mColors.mCurrentColors[i + 8] = color
        }
        emulator.mColors.mCurrentColors[TextStyle.COLOR_INDEX_FOREGROUND] = 0xff62ae91.toInt()
        emulator.mColors.mCurrentColors[TextStyle.COLOR_INDEX_BACKGROUND] = 0xff141729.toInt()
        emulator.mColors.mCurrentColors[TextStyle.COLOR_INDEX_CURSOR] = 0xff62ae91.toInt()
        com.termux.terminal.TerminalColors.COLOR_SCHEME.mDefaultColors[TextStyle.COLOR_INDEX_FOREGROUND] = 0xff62ae91.toInt()
        com.termux.terminal.TerminalColors.COLOR_SCHEME.mDefaultColors[TextStyle.COLOR_INDEX_BACKGROUND] = 0xff141729.toInt()
        com.termux.terminal.TerminalColors.COLOR_SCHEME.mDefaultColors[TextStyle.COLOR_INDEX_CURSOR] = 0xff62ae91.toInt()
    }

    fun append(bytes: ByteArray) = synchronized(this) { emulator.append(bytes, bytes.size) }
    fun resize(columns: Int, rows: Int) = synchronized(this) { emulator.resize(columns, rows) }
    fun clear() = synchronized(this) {
        emulator.reset()
        append("\u001b[2J\u001b[H\u001b[3J".toByteArray())
        palette()
    }
    fun paste(text: String) = synchronized(this) { emulator.paste(text) }
    fun key(code: Int, modifiers: Int = 0) = synchronized(this) {
        com.termux.terminal.KeyHandler.getCode(code, modifiers,
            emulator.isCursorKeysApplicationMode, emulator.isKeypadApplicationMode)?.let { send(it.toByteArray()) }
    }
}
