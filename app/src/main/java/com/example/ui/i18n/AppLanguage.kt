package com.example.ui.i18n

import android.content.Context
import android.content.res.Configuration
import androidx.appcompat.app.AppCompatDelegate
import androidx.compose.runtime.Composable
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.platform.LocalContext
import androidx.core.os.LocaleListCompat
import java.util.Locale

object AppLanguage {
    fun apply(tag: String) { AppCompatDelegate.setApplicationLocales(LocaleListCompat.forLanguageTags(if (tag == "system") "" else tag)) }
    fun context(context: Context): Context {
        val tag = context.getSharedPreferences("hermes_app_preferences", Context.MODE_PRIVATE).getString("app_language", "id") ?: "id"
        if (tag == "system") return context
        return context.createConfigurationContext(Configuration(context.resources.configuration).apply { setLocale(Locale.forLanguageTag(tag)) })
    }
}

@Composable
fun tr(indonesian: String, english: String): String {
    val system = LocalConfiguration.current.locales[0].language
    val preferred = LocalContext.current.getSharedPreferences("hermes_app_preferences", Context.MODE_PRIVATE).getString("app_language", "id") ?: "id"
    return if ((if (preferred == "system") system else preferred) == "en") english else indonesian
}
