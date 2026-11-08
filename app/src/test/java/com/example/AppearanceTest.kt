package com.example

import android.content.Context
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.luminance
import androidx.compose.ui.test.*
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.unit.dp
import androidx.test.core.app.ApplicationProvider
import com.example.data.hermes.ChatMessage
import com.example.data.hermes.HermesApiClient
import com.example.data.model.ServerConfig
import com.example.data.preferences.AppPreferencesStore
import com.example.data.sftp.SftpManager
import com.example.ui.files.FilesScreen
import com.example.ui.hermes.HermesChatScreen
import com.example.ui.hermes.highlightCode
import com.example.ui.settings.AppPreferencesPanel
import com.example.ui.theme.*
import com.github.takahirom.roborazzi.ExperimentalRoborazziApi
import com.github.takahirom.roborazzi.RoborazziOptions
import com.github.takahirom.roborazzi.RoborazziTaskType
import com.github.takahirom.roborazzi.captureRoboImage
import org.junit.After
import org.junit.Assert.*
import org.junit.Before
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.annotation.Config
import org.robolectric.annotation.GraphicsMode
import java.io.File

@RunWith(NavigationTestRunner::class)
@Config(sdk = [28], qualifiers = "w393dp-h851dp-mdpi")
@GraphicsMode(GraphicsMode.Mode.NATIVE)
class AppearanceTest {
    @get:Rule val compose = createComposeRule()
    private val context get() = ApplicationProvider.getApplicationContext<Context>()
    private val stores = mutableListOf<AppPreferencesStore>()
    private var sftp: SftpManager? = null

    @Before fun resetPreferences() {
        context.getSharedPreferences("hermes_app_preferences", Context.MODE_PRIVATE).edit().clear().commit()
        // An empty saved profile avoids first-run credential migration on a JVM without AndroidKeyStore.
        context.getSharedPreferences("homeserver_encrypted_vault", Context.MODE_PRIVATE).edit().clear()
            .putString("saved_profile_ids", "appearance")
            .putString("active_profile_id", "appearance")
            .putString("profile_appearance_name", "Appearance fixture")
            .commit()
    }

    @After fun closeReaders() {
        stores.forEach { it.close() }
        sftp?.close()
    }

    private fun preferences() = AppPreferencesStore(context).also(stores::add)

    @Test fun themeResolutionAndSemanticContrasts() {
        assertFalse(resolveDarkAppearance("light", true))
        assertTrue(resolveDarkAppearance("dark", false))
        assertFalse(resolveDarkAppearance("system", false))
        assertTrue(resolveDarkAppearance("system", true))
        for (scheme in listOf(LightColorScheme, DarkColorScheme)) {
            val pairs = listOf(
                scheme.onSurface to scheme.surface,
                scheme.onBackground to scheme.background,
                scheme.onSurfaceVariant to scheme.surfaceContainerHighest,
                scheme.onPrimary to scheme.primary,
                scheme.onPrimaryContainer to scheme.primaryContainer,
                scheme.error to scheme.surface,
                scheme.onError to scheme.error
            )
            pairs.forEach { (foreground, background) ->
                assertTrue("Text contrast ${contrast(foreground, background)}", contrast(foreground, background) >= 4.5f)
            }
        }
        for (dark in listOf(false, true)) {
            val background = if (dark) Color(0xFF172233) else Color(0xFFF3F3F3)
            val code = highlightCode("const name = \"Hermes\" // note\n42", "javascript", dark)
            code.spanStyles.forEach { assertTrue(contrast(it.item.color, background) >= 4.5f) }
        }
    }

    @Test fun themeMenuAppliesImmediatelyAndLanguageMenuIsCompact() {
        val store = preferences()
        var observedBackground = Color.Unspecified
        compose.setContent {
            MyApplicationTheme {
                val current by store.state.collectAsState()
                val background = MaterialTheme.colorScheme.background
                SideEffect { observedBackground = background }
                Column(Modifier.fillMaxSize().background(background).verticalScroll(rememberScrollState()).padding(16.dp)) {
                    AppPreferencesPanel(current, store::update)
                }
            }
        }
        compose.onNodeWithTag("app_theme_picker").performClick()
        compose.onNodeWithTag("app_theme_dark").performClick()
        compose.runOnIdle { assertEquals(DarkColorScheme.background, observedBackground) }
        assertEquals("dark", store.state.value.themeMode)
        AppPreferencesStore(context).use { assertEquals("dark", it.state.value.themeMode) }
        compose.onNodeWithTag("app_language_picker").assertTextContains("Bahasa")
        capture("settings-dark")
        compose.onNodeWithTag("app_language_picker").performClick()
        compose.onNodeWithTag("app_language_en").performClick()
        compose.onNodeWithTag("app_language_picker").assertTextContains("Language").assertTextContains("English")
        assertEquals("en", store.state.value.appLanguage)
        compose.onNodeWithTag("app_theme_picker").performClick()
        compose.onNodeWithTag("app_theme_light").performClick()
        compose.runOnIdle { assertEquals(LightColorScheme.background, observedBackground) }
        capture("settings-light")
        compose.onNodeWithTag("app_theme_picker").performClick()
        compose.onNodeWithTag("app_theme_system").performClick()
        assertEquals("system", store.state.value.themeMode)
    }

    @Test fun chatDraftSurvivesThemeChangesAndCodeUsesBothPalettes() {
        val store = preferences()
        val messages = mutableStateListOf(
            ChatMessage(role = "user", content = "Periksa koneksi server."),
            ChatMessage(role = "assistant", content = "Jalankan perintah ini.\n\n```bash\n# cek koneksi\ncurl -I https://example.com\n```"))
        compose.setContent {
            MyApplicationTheme {
                HermesChatScreen(HermesApiClient(), ServerConfig(), messages = messages)
            }
        }
        compose.onNodeWithTag("hermes_chat_input").performTextInput("catatan belum dikirim")
        compose.runOnIdle { store.update(store.state.value.copy(themeMode = "dark")) }
        compose.onNodeWithTag("hermes_chat_input").assertTextEquals("catatan belum dikirim")
        compose.onNodeWithTag("copy_code_button").assertExists()
        capture("chat-dark")
        compose.runOnIdle { store.update(store.state.value.copy(themeMode = "light")) }
        compose.onNodeWithTag("hermes_chat_input").assertTextEquals("catatan belum dikirim")
        capture("chat-light")
    }

    @Test fun filesEmptyStateFollowsTheSameTheme() {
        androidx.work.WorkManager.initialize(context, androidx.work.Configuration.Builder().build())
        val store = preferences()
        val manager = SftpManager().also { sftp = it }
        compose.setContent {
            MyApplicationTheme { FilesScreen(manager, ServerConfig()) }
        }
        compose.runOnIdle { store.update(store.state.value.copy(themeMode = "dark")) }
        compose.onNodeWithText("Hubungkan ke server").assertExists()
        capture("files-dark")
        compose.onNodeWithTag("transfer_history_button").performClick()
        compose.onNodeWithTag("transfer_history_sheet").assertExists()
        capture("transfer-sheet-dark", "transfer_history_sheet")
        compose.onNodeWithTag("close_transfer_history").performClick()
        compose.onNodeWithTag("transfer_history_sheet").assertDoesNotExist()
        compose.runOnIdle { store.update(store.state.value.copy(themeMode = "light")) }
        capture("files-light")
    }

    @Test fun lockScreenWaitsForTheRenamedButtonInBothThemes() {
        val store = preferences()
        var requests = 0
        compose.setContent {
            MyApplicationTheme { VaultLockScreen { requests++ } }
        }
        compose.mainClock.advanceTimeBy(600)
        assertEquals(0, requests)
        compose.onNodeWithTag("unlock_vault_button").assertTextContains("Buka Hermes Home")
        capture("lock-light")
        compose.runOnIdle { store.update(store.state.value.copy(themeMode = "dark")) }
        capture("lock-dark")
        compose.onNodeWithTag("unlock_vault_button").performClick()
        assertEquals(1, requests)
    }

    @OptIn(ExperimentalRoborazziApi::class)
    private fun capture(name: String, tag: String? = null) {
        val directory = File("build/outputs/dark-mode").apply { mkdirs() }
        val node = if (tag == null) compose.onRoot() else compose.onNodeWithTag(tag)
        node.captureRoboImage(File(directory, "$name.png").path,
            roborazziOptions = RoborazziOptions(taskType = RoborazziTaskType.Record))
    }

    private fun contrast(a: Color, b: Color): Float {
        val first = a.luminance(); val second = b.luminance()
        return (maxOf(first, second) + .05f) / (minOf(first, second) + .05f)
    }
}
