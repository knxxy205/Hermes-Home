package com.example

import android.content.Context
import androidx.test.core.app.ApplicationProvider
import com.example.data.preferences.AppPreferences
import com.example.data.preferences.AppPreferencesStore
import org.junit.Assert.*
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.Shadows.shadowOf
import org.robolectric.annotation.Config

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [28])
class AppPreferencesTest {
    @Test fun savedPreferencesSurviveReopeningAndNotifyOtherReaders() {
        val context = ApplicationProvider.getApplicationContext<Context>()
        context.getSharedPreferences("hermes_app_preferences", Context.MODE_PRIVATE).edit().clear().commit()
        val first = AppPreferencesStore(context)
        val second = AppPreferencesStore(context)
        val next = AppPreferences(terminalFontSize = 19, terminalFont = "fira", extraKeys = false,
            keepScreenOn = true, previewPaste = false, chatFontSize = 18, chatTimestamps = true, chatEnterSends = true,
            appLanguage = "en", filesSort = "modified", showHiddenFiles = true, themeMode = "dark", chatEffort = "high")
        first.update(next)
        shadowOf(android.os.Looper.getMainLooper()).idle()
        assertEquals(next, second.state.value)
        first.close(); second.close()
        AppPreferencesStore(context).use { reopened -> assertEquals(next, reopened.state.value) }
    }

    @Test fun invalidFontValuesAreClampedWithoutChangingOtherPreferences() {
        val normalized = AppPreferences(terminalFontSize = 100, terminalFont = "missing", chatFontSize = -4, keepScreenOn = true, themeMode = "invalid").normalized()
        assertEquals(24, normalized.terminalFontSize)
        assertEquals(12, normalized.chatFontSize)
        assertEquals("jetbrains", normalized.terminalFont)
        assertTrue(normalized.keepScreenOn)
        assertEquals("system", normalized.themeMode)
        assertEquals("", AppPreferences(chatEffort = "unsupported").normalized().chatEffort)
    }

    @Test fun legacySystemFontLoadsAsFiraCode() {
        val context = ApplicationProvider.getApplicationContext<Context>()
        context.getSharedPreferences("hermes_app_preferences", Context.MODE_PRIVATE).edit()
            .clear().putString("terminal_font", "system").putInt("terminal_font_size", 17).commit()
        AppPreferencesStore(context).use { store ->
            assertEquals("fira", store.state.value.terminalFont)
            assertEquals(17, store.state.value.terminalFontSize)
            store.update(store.state.value)
        }
        assertEquals("fira", context.getSharedPreferences("hermes_app_preferences", Context.MODE_PRIVATE).getString("terminal_font", null))
    }
}
