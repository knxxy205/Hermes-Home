package com.example.data.preferences

import android.content.Context
import android.content.SharedPreferences
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow

data class AppPreferences(
    val terminalFontSize: Int = 12,
    val terminalFont: String = "jetbrains",
    val extraKeys: Boolean = true,
    val keepScreenOn: Boolean = false,
    val previewPaste: Boolean = true,
    val chatFontSize: Int = 15,
    val chatTimestamps: Boolean = false,
    val chatEnterSends: Boolean = false,
    val answerStyle: String = "steps",
    val chatEffort: String = "",
    val hermesInstructions: String = "",
    val appLanguage: String = "id",
    val filesSort: String = "name",
    val showHiddenFiles: Boolean = false,
    val themeMode: String = "system"
) {
    fun normalized() = copy(
        terminalFontSize = terminalFontSize.coerceIn(10, 24),
        terminalFont = when (terminalFont) {
            "system", "fira" -> "fira"
            else -> "jetbrains"
        },
        chatFontSize = chatFontSize.coerceIn(12, 22),
        answerStyle = answerStyle.takeIf { it in listOf("concise", "steps", "detailed") } ?: "steps",
        chatEffort = chatEffort.takeIf { it in listOf("", "low", "medium", "high") } ?: "",
        hermesInstructions = hermesInstructions.take(2000),
        appLanguage = appLanguage.takeIf { it in listOf("system", "id", "en") } ?: "id",
        themeMode = themeMode.takeIf { it in listOf("system", "light", "dark") } ?: "system",
        filesSort = filesSort.takeIf { it in listOf("name", "modified", "size") } ?: "name"
    )

    fun responseInstructions(): String = when (answerStyle) {
        "concise" -> "Berikan jawaban ringkas. Mulai dari jawaban utama, lalu langkah yang perlu dilakukan."
        "detailed" -> "Jelaskan jawaban secara terperinci, termasuk alasan, langkah, dan cara memeriksa hasilnya."
        else -> "Susun jawaban menjadi langkah kerja yang mudah diikuti. Berikan perintah seperlunya dan cara memeriksa hasilnya."
    } + if (hermesInstructions.isBlank()) "" else "\nPreferensi pengguna:\n$hermesInstructions"
}

/** Appearance preferences are separate from the encrypted connection vault. */
class AppPreferencesStore(context: Context) : AutoCloseable {
    private val prefs = context.applicationContext.getSharedPreferences("hermes_app_preferences", Context.MODE_PRIVATE)
    private val mutableState = MutableStateFlow(read())
    val state = mutableState.asStateFlow()
    private val listener = SharedPreferences.OnSharedPreferenceChangeListener { _, _ -> mutableState.value = read() }

    init { prefs.registerOnSharedPreferenceChangeListener(listener) }

    private fun read() = AppPreferences(
        terminalFontSize = prefs.getInt("terminal_font_size", 12),
        terminalFont = prefs.getString("terminal_font", "jetbrains") ?: "jetbrains",
        extraKeys = prefs.getBoolean("extra_keys", true),
        keepScreenOn = prefs.getBoolean("keep_screen_on", false),
        previewPaste = prefs.getBoolean("preview_paste", true),
        chatFontSize = prefs.getInt("chat_font_size", 15),
        chatTimestamps = prefs.getBoolean("chat_timestamps", false),
        chatEnterSends = prefs.getBoolean("chat_enter_sends", false),
        answerStyle = prefs.getString("answer_style", "steps") ?: "steps",
        chatEffort = prefs.getString("chat_effort", "") ?: "",
        hermesInstructions = prefs.getString("hermes_instructions", "") ?: "",
        appLanguage = prefs.getString("app_language", "id") ?: "id",
        themeMode = prefs.getString("theme_mode", "system") ?: "system",
        filesSort = prefs.getString("files_sort", "name") ?: "name",
        showHiddenFiles = prefs.getBoolean("show_hidden_files", false)
    ).normalized()

    fun update(value: AppPreferences) {
        val next = value.normalized()
        prefs.edit()
            .putInt("terminal_font_size", next.terminalFontSize)
            .putString("terminal_font", next.terminalFont)
            .putBoolean("extra_keys", next.extraKeys)
            .putBoolean("keep_screen_on", next.keepScreenOn)
            .putBoolean("preview_paste", next.previewPaste)
            .putInt("chat_font_size", next.chatFontSize)
            .putBoolean("chat_timestamps", next.chatTimestamps)
            .putBoolean("chat_enter_sends", next.chatEnterSends)
            .putString("answer_style", next.answerStyle)
            .putString("chat_effort", next.chatEffort)
            .putString("hermes_instructions", next.hermesInstructions)
            .putString("app_language", next.appLanguage)
            .putString("theme_mode", next.themeMode)
            .putString("files_sort", next.filesSort)
            .putBoolean("show_hidden_files", next.showHiddenFiles)
            .apply()
        mutableState.value = next
    }

    override fun close() { prefs.unregisterOnSharedPreferenceChangeListener(listener) }
}
