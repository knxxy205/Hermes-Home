package com.example

import android.content.ClipboardManager
import android.content.Context
import android.graphics.Bitmap
import android.view.View
import android.view.ViewGroup
import android.view.inputmethod.EditorInfo
import android.view.KeyEvent
import android.view.inspector.WindowInspector
import androidx.test.filters.SdkSuppress
import androidx.compose.ui.test.*
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.test.espresso.Espresso
import androidx.test.espresso.action.GeneralClickAction
import androidx.test.espresso.action.Tap
import androidx.test.espresso.action.Press
import androidx.test.espresso.matcher.ViewMatchers.isAssignableFrom
import androidx.core.view.WindowInsetsCompat
import androidx.test.platform.app.InstrumentationRegistry
import androidx.test.runner.lifecycle.ActivityLifecycleMonitorRegistry
import androidx.test.runner.lifecycle.Stage
import com.example.data.model.ServerConfig
import com.example.data.preferences.AppPreferences
import com.example.data.preferences.AppPreferencesStore
import com.example.data.security.EncryptedStorage
import com.example.data.hermes.ChatMessage
import com.example.data.hermes.HermesChatSession
import com.example.ui.terminal.TerminalView
import com.example.ui.theme.MyApplicationTheme
import org.json.JSONArray
import org.json.JSONObject
import org.junit.Assert.*
import org.junit.Before
import org.junit.After
import org.junit.Rule
import org.junit.Test
import java.io.File
import java.net.ServerSocket
import java.util.concurrent.ConcurrentLinkedQueue
import java.util.concurrent.Executors
import kotlinx.coroutines.launch
import kotlinx.coroutines.cancel

@SdkSuppress(minSdkVersion = 29)
class UsabilityTest {
    @get:Rule val compose = createComposeRule()
    private val instrumentation get() = InstrumentationRegistry.getInstrumentation()
    private val context get() = instrumentation.targetContext

    @Before fun resetPreviewData() {
        check(context.packageName.endsWith(".preview")) { "These fixtures may only reset the separate preview app." }
        assertNoUnresponsiveDialog()
        context.stopService(android.content.Intent(context, com.example.data.ssh.SshConnectionService::class.java))
        com.example.data.ssh.SshSessionHost.close()
        context.getSharedPreferences("homeserver_encrypted_vault", Context.MODE_PRIVATE).edit().clear().commit()
        AppPreferencesStore(context).use { it.update(AppPreferences()) }
        com.example.ui.i18n.AppLanguage.apply("id")
        com.example.data.security.SshTrust.initialize(context)
        context.getSharedPreferences("hermes_transfer_history", Context.MODE_PRIVATE).edit().clear().commit()
    }

    @After fun closeOwnedSessions() {
        context.stopService(android.content.Intent(context, com.example.data.ssh.SshConnectionService::class.java))
        com.example.data.ssh.SshSessionHost.close()
    }

    @Test fun chatSpacingAndCodeOnlyCopyAtPhoneWidth() {
        val messages = listOf(
            ChatMessage(role = "user", content = "cek kecepatan internet"),
            ChatMessage(role = "assistant", content = "Periksa koneksi dari server dengan perintah berikut.\n\n```bash\ncurl -I https://example.com\n```"),
            ChatMessage(role = "user", content = "Tampilkan juga hasilnya supaya bisa saya bandingkan dengan pengujian sebelumnya.")
        )
        EncryptedStorage(context).saveChatSession(HermesChatSession(title = "Cek koneksi", messages = messages))
        AppPreferencesStore(context).use { it.update(AppPreferences(chatTimestamps = true)) }
        compose.setContent { MyApplicationTheme { MainContentScreen() } }
        compose.onNodeWithTag("tab_hermes").performClick()
        compose.onNodeWithTag("chat_message_${messages.first().id}").performScrollTo()
        compose.onAllNodesWithContentDescription("Copy message").assertCountEquals(0)
        compose.onAllNodesWithContentDescription("Salin kode").assertCountEquals(1)
        screenshot("daily-chat-spacing")
        compose.onNodeWithTag("tab_terminal").performClick()
        screenshot("floating-nav-terminal")
        compose.onNodeWithTag("tab_files").performClick()
        screenshot("floating-nav-files")
        compose.onNodeWithTag("tab_settings").performClick()
        screenshot("floating-nav-settings")
    }

    @Test fun encryptedTransferJournalPersistsOnlyCiphertext() {
        val store = com.example.data.transfer.TransferStore(context)
        val batch = com.example.data.transfer.UploadBatch("secure-batch", "work", "profile", "Fixture server", entries = listOf(
            com.example.data.transfer.UploadEntry("content://fixture/sensitive-source", "sensitive-name.txt", "/private-folder")
        ))
        store.add(batch)
        store.close()
        val raw = context.getSharedPreferences("hermes_transfer_history", Context.MODE_PRIVATE).getString("batches", "").orEmpty()
        assertFalse(raw.contains("sensitive-name"))
        assertFalse(raw.contains("sensitive-source"))
        com.example.data.transfer.TransferStore(context).use { reopened -> assertEquals(batch, reopened.find(batch.id)) }
    }

    @Test fun appLanguageSelectionPersistsAndRecreatesWithCorrectLabels() {
        compose.setContent { MyApplicationTheme { MainContentScreen() } }
        compose.onNodeWithTag("tab_settings").performClick()
        compose.onNodeWithTag("app_language_picker").performScrollTo().performClick()
        compose.onNodeWithTag("app_language_en").performClick()
        compose.waitUntil(10_000) { compose.onAllNodesWithText("App preferences").fetchSemanticsNodes().isNotEmpty() }
        compose.onNodeWithText("App preferences").assertExists()
        assertEquals("en", AppPreferencesStore(context).use { it.state.value.appLanguage })
        screenshot("daily-settings-english")
        compose.onNodeWithTag("app_language_picker").performScrollTo().performClick()
        compose.onNodeWithTag("app_language_system").performClick()
        assertEquals("system", AppPreferencesStore(context).use { it.state.value.appLanguage })
    }

    @Test fun realSshSurvivesUiRelockAndRetriesOnlyTransportFailures() {
        val config = ServerConfig("10.0.2.2", 8029, "fixture", "daily-test-password")
        EncryptedStorage(context).saveConfig(config)
        val visible = androidx.compose.runtime.mutableStateOf(true)
        compose.setContent { MyApplicationTheme {
            if (visible.value) MainContentScreen() else androidx.compose.material3.Text("Fixture UI locked")
        } }
        compose.onNodeWithTag("connect_ssh_button").performClick()
        compose.waitUntil(20_000) { compose.onAllNodesWithText("Percayai server").fetchSemanticsNodes().isNotEmpty() || terminalOutput().contains("DAILY_FIXTURE_READY") }
        if (compose.onAllNodesWithText("Percayai server").fetchSemanticsNodes().isNotEmpty()) compose.onNodeWithText("Percayai server").performClick()
        compose.waitUntil(20_000) { terminalOutput().contains("DAILY_FIXTURE_READY") }
        val manager = com.example.data.ssh.SshSessionHost.manager(context)
        val session = manager.activeSession.value
        compose.onNodeWithTag("tab_files").performClick()
        compose.waitUntil(20_000) { compose.onAllNodesWithTag("file_item_uploads").fetchSemanticsNodes().isNotEmpty() }
        compose.runOnIdle { visible.value = false }
        session.sendCommand("while-ui-locked")
        try {
            compose.waitUntil(10_000) { synchronized(session.engine.lock) { session.engine.emulator.screen.transcriptText.contains("ACK:while-ui-locked") } }
        } catch (error: ComposeTimeoutException) {
            throw AssertionError("Hidden SSH state=${session.connectionState.value}; output=" +
                synchronized(session.engine.lock) { session.engine.emulator.screen.transcriptText }, error)
        }
        assertSame(manager, com.example.data.ssh.SshSessionHost.manager(context))
        compose.runOnIdle { visible.value = true }
        compose.onNodeWithTag("tab_terminal").performClick()
        session.sendCommand("drop-transport")
        compose.waitUntil(15_000) { session.reconnectAttempt.value > 0 }
        compose.waitUntil(25_000) { session.connectionState.value is com.example.data.ssh.TerminalConnectionState.Connected }
        session.sendCommand("after-automatic-retry")
        compose.waitUntil(10_000) { terminalOutput().contains("ACK:after-automatic-retry") }
        screenshot("daily-ssh-reconnected")
        session.sendCommand("exit")
        compose.waitUntil(10_000) { session.connectionState.value is com.example.data.ssh.TerminalConnectionState.Disconnected }
        assertEquals(0, session.reconnectAttempt.value)
        session.connect(config)
        compose.waitUntil(20_000) { session.connectionState.value is com.example.data.ssh.TerminalConnectionState.Connected }
        session.sendCommand("drop-transport")
        compose.waitUntil(10_000) { session.reconnectAttempt.value > 0 }
        session.disconnect()
        assertEquals(0, session.reconnectAttempt.value)
        assertTrue(session.connectionState.value is com.example.data.ssh.TerminalConnectionState.Disconnected)
    }

    @Test fun realFilesNavigationUploadsAndRetryKeepPerFileResults() {
        val config = ServerConfig("10.0.2.2", 8029, "fixture", "daily-test-password")
        val storage = EncryptedStorage(context)
        storage.saveConfig(config)
        compose.setContent { MyApplicationTheme { MainContentScreen() } }
        compose.onNodeWithTag("tab_files").performClick()
        compose.waitUntil(20_000) { compose.onAllNodesWithText("Percayai server").fetchSemanticsNodes().isNotEmpty() || compose.onAllNodesWithTag("file_item_readme.txt").fetchSemanticsNodes().isNotEmpty() }
        if (compose.onAllNodesWithText("Percayai server").fetchSemanticsNodes().isNotEmpty()) compose.onNodeWithText("Percayai server").performClick()
        compose.waitUntil(20_000) { compose.onAllNodesWithTag("file_item_readme.txt").fetchSemanticsNodes().isNotEmpty() }
        compose.onNodeWithTag("file_item_.hidden-test").assertDoesNotExist()
        compose.onNodeWithTag("files_sort").performClick()
        compose.onNodeWithText("Tampilkan file tersembunyi").performClick()
        instrumentation.sendKeyDownUpSync(KeyEvent.KEYCODE_BACK)
        compose.onNodeWithTag("file_item_.hidden-test").assertExists()
        screenshot("daily-files-root")
        compose.onNodeWithTag("file_item_uploads").performClick()
        compose.onNodeWithText("Folder ini masih kosong").assertExists()
        compose.onNodeWithTag("files_edit_path").performClick()
        compose.onNodeWithTag("files_path_input").performTextReplacement("/")
        compose.onNodeWithText("Buka", useUnmergedTree = true).performClick()
        compose.waitUntil(20_000) { compose.onAllNodesWithTag("file_item_readme.txt").fetchSemanticsNodes().isNotEmpty() }
        val source = File(context.filesDir, "daily-upload.txt").apply { writeText("phone upload fixture\n") }
        val missing = File(context.filesDir, "daily-missing.txt")
        val batchId = com.example.data.transfer.UploadQueue.enqueue(context, listOf(android.net.Uri.fromFile(source), android.net.Uri.fromFile(missing)), storage.getActiveProfileId(), "/uploads")
        com.example.data.transfer.TransferStore(context).use { store ->
            compose.waitUntil(30_000) { store.find(batchId)?.active == false }
            val batch = requireNotNull(store.find(batchId))
            assertEquals(1, batch.succeeded)
            assertEquals(1, batch.failed)
            assertEquals(21L, batch.entries.first().bytes)
            missing.writeText("retry fixture\n")
            val retryId = com.example.data.transfer.UploadQueue.retry(context, batchId)
            compose.waitUntil(30_000) { store.find(retryId)?.active == false }
            val retry = requireNotNull(store.find(retryId))
            assertEquals(1, retry.entries.size)
            assertEquals("daily-missing.txt", retry.entries.single().name)
            assertEquals(1, retry.succeeded)
            com.example.data.transfer.TransferNavigation.accept(android.content.Intent().putExtra(com.example.data.transfer.TransferNavigation.EXTRA_BATCH, batchId))
            compose.onNodeWithText("Rincian upload").assertExists()
            compose.onNodeWithText("1 selesai · 1 gagal · 0 dibatalkan").assertExists()
            screenshot("daily-upload-results")
        }
    }

    @Test fun photoPickerKeepsDestinationAndRefreshesFolder() {
        val config = ServerConfig("10.0.2.2", 8029, "fixture", "daily-test-password")
        EncryptedStorage(context).saveConfig(config)
        val scope = kotlinx.coroutines.CoroutineScope(kotlinx.coroutines.SupervisorJob() + kotlinx.coroutines.Dispatchers.IO)
        scope.launch { com.example.data.security.SshTrust.pending.collect { it?.answer?.complete(true) } }
        val manager = com.example.data.sftp.SftpManager()
        val model = com.example.ui.files.FilesViewModel(manager)
        val name = "upload-picker-${System.currentTimeMillis()}.jpg"
        val resolver = context.contentResolver
        val photo = requireNotNull(resolver.insert(android.provider.MediaStore.Images.Media.EXTERNAL_CONTENT_URI, android.content.ContentValues().apply {
            put(android.provider.MediaStore.Images.Media.DISPLAY_NAME, name)
            put(android.provider.MediaStore.Images.Media.MIME_TYPE, "image/jpeg")
            put(android.provider.MediaStore.Images.Media.RELATIVE_PATH, "Pictures")
            put(android.provider.MediaStore.Images.Media.IS_PENDING, 1)
        }))
        var completed = false
        try {
            val bitmap = Bitmap.createBitmap(24, 24, Bitmap.Config.ARGB_8888).apply { eraseColor(android.graphics.Color.BLUE) }
            resolver.openOutputStream(photo)!!.use { bitmap.compress(Bitmap.CompressFormat.JPEG, 90, it) }
            bitmap.recycle()
            resolver.update(photo, android.content.ContentValues().apply { put(android.provider.MediaStore.Images.Media.IS_PENDING, 0) }, null, null)
            val expected = resolver.openInputStream(photo)!!.use { it.readBytes() }
            compose.setContent { MyApplicationTheme { com.example.ui.files.FilesScreen(manager, config, model) } }
            compose.runOnIdle { model.navigateTo("/uploads", config) }
            compose.waitUntil(20_000) { !model.uiState.value.isLoading }
            compose.onNodeWithTag("upload_file_fab").performClick()
            clickSystemText("Show roots")
            clickSystemText("Images")
            var hasPicturesFolder = false
            compose.waitUntil(15_000) {
                val roots = instrumentation.uiAutomation.windows.mapNotNull { it.root }
                hasPicturesFolder = roots.any { root -> root.findAccessibilityNodeInfosByText("Pictures").any { it.text?.toString() == "Pictures" && it.isVisibleToUser } }
                hasPicturesFolder || roots.any { root -> root.findAccessibilityNodeInfosByText(name).any { it.text?.toString() == name && it.isVisibleToUser } }
            }
            if (hasPicturesFolder) clickSystemText("Pictures")
            clickSystemText(name)
            compose.waitUntil(15_000) { runCatching { compose.onAllNodesWithText("Unggah 1 file").fetchSemanticsNodes().isNotEmpty() }.getOrDefault(false) }
            compose.runOnIdle { model.navigateTo("/", config) }
            compose.onNodeWithText("Unggah", useUnmergedTree = true).performClick()
            com.example.data.transfer.TransferStore(context).use { store ->
                compose.waitUntil(40_000) { store.read().any { batch -> batch.entries.any { it.name == name } && !batch.active } }
                val batch = store.read().first { batch -> batch.entries.any { it.name == name } }
                assertEquals(batch.toString(), 1, batch.succeeded)
                assertEquals("/uploads", batch.entries.single().destination)
                assertEquals("/", model.currentRemotePath())
                val downloaded = java.io.ByteArrayOutputStream()
                kotlinx.coroutines.runBlocking { manager.downloadToStream("/uploads/$name", downloaded, config).getOrThrow() }
                assertArrayEquals(expected, downloaded.toByteArray())
                compose.runOnIdle { model.navigateTo("/uploads", config) }
                compose.waitUntil(20_000) { model.uiState.value.files.any { it.name == name } }
                compose.onNode(hasScrollToIndexAction()).performScrollToNode(hasTestTag("file_item_$name"))
                compose.onNodeWithTag("file_item_$name").assertIsDisplayed()
                compose.onNodeWithTag("transfer_history_button").performClick()
                compose.onNodeWithTag("transfer_batch_${batch.id}").performSemanticsAction(androidx.compose.ui.semantics.SemanticsActions.OnClick) { it() }
                compose.onNodeWithText("Rincian upload").assertExists()
                compose.mainClock.advanceTimeBy(100)
                screenshot("upload-compact-details")
                compose.onNodeWithContentDescription("Tutup").performClick()
                compose.waitUntil(5_000) { compose.onAllNodesWithText("Rincian upload").fetchSemanticsNodes().isEmpty() }
                completed = true
            }
        } finally {
            if (completed) resolver.delete(photo, null, null)
            manager.close()
            scope.cancel()
        }
    }

    @Test fun preferencesApplyToTheTerminalAndSurviveReopening() {
        compose.setContent { MyApplicationTheme { MainContentScreen() } }
        compose.onNodeWithTag("tab_settings").performClick()
        compose.onNodeWithTag("terminal_font_increase").performClick()
        compose.onNodeWithTag("terminal_font_value").assertTextEquals("13 sp")
        compose.onNodeWithText("Fira Code").performClick().assertIsSelected()
        compose.onNodeWithTag("Layar tetap menyala").performScrollTo().performClick()
        compose.onNodeWithTag("terminal_font_value").performScrollTo()
        screenshot("settings-terminal")
        compose.onNodeWithTag("tab_terminal").performClick()
        compose.runOnIdle {
            val terminal = terminalView()
            assertTrue(terminal.contentDescription.contains("13 sp"))
            assertTrue(terminal.contentDescription.contains("Fira Code"))
            assertTrue(terminal.keepScreenOn)
        }
        compose.onNodeWithTag("tab_files").performClick()
        compose.runOnIdle { assertFalse(terminalView().keepScreenOn) }
        AppPreferencesStore(context).use { assertEquals(13, it.state.value.terminalFontSize); assertEquals("fira", it.state.value.terminalFont); assertTrue(it.state.value.keepScreenOn) }
    }

    @Test fun terminalSelectionSearchCommandsAndSessionsWork() {
        compose.setContent { MyApplicationTheme { MainContentScreen() } }
        compose.onNodeWithTag("connect_ssh_button").performClick()
        compose.waitUntil(15_000) { terminalOutput().contains("Mode simulasi") }
        compose.onNodeWithContentDescription("Susun perintah").performClick()
        compose.onNodeWithTag("terminal_command_draft").performTextInput("echo comfort-check")
        compose.onNodeWithText("Sisipkan ke terminal").performClick()
        compose.runOnIdle { terminalView().onCreateInputConnection(EditorInfo()).performEditorAction(EditorInfo.IME_ACTION_NONE) }
        compose.waitUntil(10_000) { terminalOutput().contains("comfort-check") }
        closeKeyboard()
        compose.onNodeWithContentDescription("Cari output").performClick()
        compose.onNodeWithTag("terminal_search").performTextInput("comfort-check")
        compose.waitUntil(10_000) { compose.onAllNodes(hasText("/", substring = true)).fetchSemanticsNodes().isNotEmpty() }
        screenshot("terminal-search")
        compose.onNodeWithContentDescription("Tutup pencarian").performClick()
        closeKeyboard()
        compose.runOnIdle {
            val terminal = terminalView()
            terminal.session!!.clearBuffer()
            terminal.session!!.engine.append("\u001b[2J\u001b[Hselect target\r\n".toByteArray())
            terminal.scrollToLatest()
            terminal.invalidate()
        }
        Espresso.onView(isAssignableFrom(TerminalView::class.java)).perform(GeneralClickAction(Tap.LONG, { view ->
            val location = IntArray(2); view.getLocationOnScreen(location)
            floatArrayOf(location[0] + 40f, location[1] + 20f)
        }, Press.FINGER))
        screenshot("terminal-selection")
        clickSystemText("Salin")
        compose.runOnIdle {
            val clipboard = context.getSystemService(Context.CLIPBOARD_SERVICE) as ClipboardManager
            assertEquals("select", clipboard.primaryClip!!.getItemAt(0).text.toString())
        }
        compose.onNodeWithTag("new_terminal_session").performClick()
        compose.onNodeWithTag("new_sandbox_session").performClick()
        compose.onNodeWithTag("terminal_session_picker").performClick()
        compose.onNodeWithText("Sesi terminal (2/5)").assertIsDisplayed()
        screenshot("terminal-sessions")
        instrumentation.sendKeyDownUpSync(KeyEvent.KEYCODE_BACK)
        compose.onNodeWithTag("terminal_snippets").performClick()
        compose.onNodeWithTag("add_terminal_snippet").performClick()
        compose.onNodeWithText("Nama perintah").performTextInput("Cek lokal")
        compose.onNodeWithText("Isi perintah").performTextInput("echo saved-check")
        compose.onNodeWithText("Simpan").performClick()
        assertTrue(EncryptedStorage(context).loadCustomSnippets().any { it.name == "Cek lokal" && it.command == "echo saved-check" })
        closeKeyboard()
        compose.onNodeWithTag("terminal_session_picker").performClick()
        compose.onNodeWithContentDescription("Tutup bash 1").performClick()
        compose.onNodeWithText("Tutup sesi", useUnmergedTree = true).performClick()
        compose.onNodeWithTag("terminal_session_picker").assertTextContains("Simulasi 2", substring = true)
        compose.onNodeWithContentDescription("Menu terminal").performClick()
        compose.onNodeWithTag("close_terminal_session").performClick()
        compose.onNodeWithText("Tutup sesi", useUnmergedTree = true).performClick()
        compose.onNodeWithText("Belum ada sesi terminal").assertIsDisplayed()
        screenshot("terminal-no-sessions")
        compose.onNodeWithTag("open_terminal_session").performClick()
        compose.onNodeWithTag("new_sandbox_session").performClick()
        compose.onNodeWithTag("terminal_session_picker").assertTextContains("Simulasi 1", substring = true)
    }

    @Test fun modelPickerChangesTheRequestAndRepliesRenderMarkdown() {
        FixtureServer().use { fixture ->
            EncryptedStorage(context).saveConfig(ServerConfig(hermesBaseUrl = fixture.url, hermesModel = "test/alpha"))
            compose.setContent { MyApplicationTheme { MainContentScreen() } }
            compose.onNodeWithTag("tab_hermes").performClick()
            compose.onNodeWithTag("hermes_chat_input").performTextInput("/model")
            closeKeyboard()
            screenshot("chat-command-draft")
            compose.onNodeWithTag("hermes_send_button").assertIsEnabled().performClick()
            compose.waitUntil(15_000) { compose.onAllNodesWithTag("model_option_test/beta").fetchSemanticsNodes().isNotEmpty() }
            compose.onNodeWithTag("model_search").performTextInput("beta")
            closeKeyboard()
            screenshot("model-picker")
            compose.onNodeWithTag("model_option_test/beta").performClick()
            assertEquals("test/beta", EncryptedStorage(context).loadConfig().hermesModel)
            compose.onNodeWithTag("tab_settings").performClick()
            compose.onNodeWithText("Koneksi & model").performClick()
            compose.onNodeWithTag("settings_list").performScrollToNode(hasTestTag("settings_hermes_model_input"))
            compose.onNodeWithTag("settings_hermes_model_input").assertTextContains("test/beta")
            compose.onNodeWithTag("tab_hermes").performClick()
            compose.onNodeWithTag("hermes_chat_input").performTextInput("Periksa ruang disk")
            closeKeyboard()
            compose.onNodeWithTag("hermes_send_button").assertIsEnabled().performClick()
            compose.waitUntil(15_000) { fixture.requests.any { it.startsWith("POST") } }
            compose.waitUntil(15_000) { compose.onAllNodesWithText("Status disk").fetchSemanticsNodes().isNotEmpty() }
            closeKeyboard()
            compose.onNodeWithText("Status disk").assertExists()
            compose.onNodeWithContentDescription("Salin kode").performScrollTo().performClick()
            screenshot("hermes-reply")
            compose.waitUntil(5_000) {
                var copied: String? = null
                instrumentation.runOnMainSync {
                    val clipboard = context.getSystemService(Context.CLIPBOARD_SERVICE) as ClipboardManager
                    copied = clipboard.primaryClip?.getItemAt(0)?.text?.toString()
                }
                copied == "df -h"
            }
            val request = JSONObject(fixture.requests.first { it.startsWith("POST") }.substringAfter("\r\n\r\n"))
            assertEquals("test/beta", request.getString("model"))
            assertTrue(request.getJSONArray("messages").getJSONObject(0).getString("content").contains("langkah kerja"))
            compose.onNodeWithTag("run_on_server_button").performScrollTo().performClick()
            compose.onNodeWithText("Jalankan", useUnmergedTree = true).performClick()
            val storage = EncryptedStorage(context)
            compose.waitUntil(10_000) { storage.getActiveOrLatestChatSession().messages.any { it.isServerOutput } }
            val output = storage.getActiveOrLatestChatSession().messages.last { it.isServerOutput }
            val outputScope = hasAnyAncestor(hasTestTag("chat_message_${output.id}"))
            compose.onNode(hasTestTag("run_on_server_button") and outputScope).assertDoesNotExist()
            compose.onNode(hasTestTag("open_in_terminal_button") and outputScope).assertDoesNotExist()
            compose.onNode(hasContentDescription("Salin kode") and outputScope).performScrollTo().assertIsDisplayed()
            screenshot("server-result")
        }
    }

    @Test fun newChatDoesNotDuplicateAndDeletingTheActiveChatDoesNotRestoreIt() {
        val storage = EncryptedStorage(context)
        val original = HermesChatSession(title = "Percakapan pertama", messages = listOf(
            ChatMessage(role = "user", content = "Halo"), ChatMessage(role = "assistant", content = "Siap.")
        ))
        storage.saveChatSession(original)
        compose.setContent { MyApplicationTheme { MainContentScreen() } }
        compose.onNodeWithTag("tab_hermes").performClick()
        compose.onNodeWithTag("new_chat_button").performClick()
        val freshId = checkNotNull(storage.getActiveChatSessionId())
        compose.onNodeWithTag("new_chat_button").performClick()
        compose.onNodeWithTag("new_chat_button").performClick()
        assertNotEquals(original.id, freshId)
        assertEquals(freshId, storage.getActiveChatSessionId())
        assertEquals(2, storage.loadChatSessions().size)
        compose.onNodeWithTag("chat_history_button").performClick()
        compose.onNodeWithTag("delete_chat_$freshId").performClick()
        assertFalse(storage.getChatSessionIds().contains(freshId))
        assertNotNull(storage.loadChatSession(original.id))
        assertNotEquals(freshId, storage.getActiveChatSessionId())
    }

    @Test fun htmlAndLegacyExecutionResultsOnlyOfferCopy() {
        val html = ChatMessage(role = "assistant", content = "Siap. Berikut contoh HTML-nya.\n\n```html\n<!DOCTYPE html>\n<html lang=\"id\">\n  <body>Halo, dunia!</body>\n</html>\n```")
        val output = ChatMessage(role = "assistant", content = "⚡ **Hasil Eksekusi di Server:** (`df -h`)\n```bash\nPermission denied, please try again.\nDisconnected from server port 22\n```")
        EncryptedStorage(context).saveChatSession(HermesChatSession(title = "Contoh kode", messages = listOf(html, output)))
        compose.setContent { MyApplicationTheme { MainContentScreen() } }
        compose.onNodeWithTag("tab_hermes").performClick()
        compose.onAllNodesWithTag("run_on_server_button").assertCountEquals(0)
        compose.onAllNodesWithTag("open_in_terminal_button").assertCountEquals(0)
        compose.onNode(hasContentDescription("Salin kode") and hasAnyAncestor(hasTestTag("chat_message_${html.id}"))).performScrollTo()
        screenshot("html-reply")
        compose.onNode(hasContentDescription("Salin kode") and hasAnyAncestor(hasTestTag("chat_message_${output.id}"))).performScrollTo()
        screenshot("legacy-server-result")
    }

    @Test fun modelErrorsRetryAndEmptyCatalogsHaveTheirOwnStates() {
        FixtureServer().use { fixture ->
            fixture.modelsFail = true
            EncryptedStorage(context).saveConfig(ServerConfig(hermesBaseUrl = fixture.url, hermesModel = "test/alpha"))
            compose.setContent { MyApplicationTheme { MainContentScreen() } }
            compose.onNodeWithTag("tab_hermes").performClick()
            compose.onNodeWithTag("chat_model_button").performClick()
            compose.waitUntil(15_000) { compose.onAllNodesWithText("Daftar model gagal dimuat").fetchSemanticsNodes().isNotEmpty() }
            screenshot("model-error")
            fixture.modelsFail = false
            fixture.modelsEmpty = true
            compose.onNodeWithText("Coba lagi").performClick()
            compose.waitUntil(15_000) { compose.onAllNodesWithText("Server belum menyediakan model.").fetchSemanticsNodes().isNotEmpty() }
            fixture.modelsEmpty = false
            compose.onNodeWithContentDescription("Muat ulang model").performClick()
            compose.waitUntil(15_000) { compose.onAllNodesWithTag("model_option_test/alpha").fetchSemanticsNodes().isNotEmpty() }
            compose.onNodeWithTag("model_option_test/alpha").performClick()
            compose.onNodeWithTag("hermes_model_picker").performClick()
            compose.waitUntil(15_000) { compose.onAllNodesWithTag("model_option_test/alpha").fetchSemanticsNodes().isNotEmpty() }
            compose.onNodeWithTag("model_option_test/alpha").performClick()
        }
    }

    @Test fun providersKeepSeparateKeysAndModelsAcrossSaveAndBackup() {
        FixtureServer().use { fixture ->
            val storage = EncryptedStorage(context)
            storage.saveConfig(ServerConfig(hermesBaseUrl = fixture.url, hermesModel = "test/alpha"))
            compose.setContent { MyApplicationTheme { MainContentScreen() } }
            compose.onNodeWithTag("tab_settings").performClick()
            compose.onNodeWithText("Koneksi & model").performClick()
            fun choose(id: String) {
                closeKeyboard()
                compose.onNodeWithTag("settings_list").performScrollToNode(hasTestTag("llm_provider_picker"))
                compose.onNodeWithTag("llm_provider_picker").performScrollTo().performClick()
                compose.onNodeWithTag("llm_provider_$id").performClick()
            }
            choose("google")
            compose.onNodeWithTag("settings_hermes_url_input").assertTextContains("https://generativelanguage.googleapis.com/v1beta/openai")
            compose.onNodeWithTag("settings_list").performScrollToNode(hasTestTag("settings_hermes_key_input"))
            compose.onNodeWithTag("settings_hermes_key_input").performTextInput("google-fixture")
            choose("openrouter")
            compose.onNodeWithTag("settings_hermes_url_input").assertTextContains("https://openrouter.ai/api/v1")
            compose.onNodeWithTag("settings_list").performScrollToNode(hasTestTag("settings_hermes_key_input"))
            compose.onNodeWithTag("settings_hermes_key_input").performTextInput("openrouter-fixture")
            choose("google")
            compose.onNodeWithTag("settings_list").performScrollToNode(hasTestTag("settings_hermes_key_input"))
            compose.onNodeWithContentDescription("Tampilkan API key").performClick()
            compose.onNodeWithTag("settings_hermes_key_input").assertTextContains("google-fixture")
            choose("claude")
            compose.onNodeWithTag("settings_hermes_url_input").assertTextContains("https://api.anthropic.com/v1")
            compose.onNodeWithTag("settings_list").performScrollToNode(hasTestTag("settings_hermes_key_input"))
            compose.onNodeWithTag("settings_hermes_key_input").performTextInput("claude-fixture")
            choose("custom")
            compose.onNodeWithTag("settings_hermes_url_input").assertTextContains(fixture.url)
            compose.onNodeWithTag("settings_list").performScrollToNode(hasTestTag("settings_choose_model"))
            compose.onNodeWithTag("settings_choose_model").performClick()
            compose.waitUntil(15_000) { compose.onAllNodesWithTag("model_option_test/beta").fetchSemanticsNodes().isNotEmpty() }
            compose.onNodeWithTag("model_search").performTextInput("beta")
            closeKeyboard()
            compose.onNodeWithTag("model_option_test/beta").performClick()
            compose.onNodeWithTag("settings_list").performScrollToNode(hasTestTag("settings_test_ai"))
            compose.onNodeWithTag("settings_test_ai").performScrollTo().assertIsDisplayed().performClick()
            compose.waitUntil(15_000) { fixture.requests.any { it.startsWith("POST") } }
            compose.waitUntil(15_000) { compose.onAllNodesWithText("Model test/beta berhasil menjawab", substring = true).fetchSemanticsNodes().isNotEmpty() }
            compose.onNodeWithTag("settings_list").performScrollToNode(hasTestTag("save_settings_button"))
            compose.onNodeWithTag("save_settings_button").performClick()
            val saved = storage.loadConfig()
            assertEquals("test/beta", saved.hermesModel)
            assertEquals("google-fixture", saved.llmProviders["google"]?.apiKey)
            assertEquals("openrouter-fixture", saved.llmProviders["openrouter"]?.apiKey)
            assertEquals("claude-fixture", saved.llmProviders["claude"]?.apiKey)
            val vault = context.getSharedPreferences("homeserver_encrypted_vault", Context.MODE_PRIVATE)
            assertFalse(vault.all.values.any { it.toString().contains("google-fixture") || it.toString().contains("openrouter-fixture") })
            val backup = storage.exportProfilesPayload("fixture-backup-password")
            assertFalse(backup.contains("google-fixture"))
            storage.importProfilesPayload(backup, "fixture-backup-password").getOrThrow()
            assertEquals(saved.llmProviders, storage.loadConfig().llmProviders)
            choose("google")
            screenshot("settings-provider-google")
        }
    }

    private fun terminalView(): TerminalView {
        fun find(view: View): TerminalView? {
            if (view is TerminalView) return view
            if (view is ViewGroup) for (index in 0 until view.childCount) find(view.getChildAt(index))?.let { return it }
            return null
        }
        val activity = ActivityLifecycleMonitorRegistry.getInstance().getActivitiesInStage(Stage.RESUMED).first()
        return checkNotNull(find(activity.window.decorView))
    }
    private fun terminalOutput(): String {
        var text = ""
        instrumentation.runOnMainSync { terminalView().session?.engine?.let { synchronized(it.lock) { text = it.emulator.screen.transcriptText } } }
        return text
    }
    private fun closeKeyboard() {
        assertNoUnresponsiveDialog()
        instrumentation.runOnMainSync {
            val input = context.getSystemService(Context.INPUT_METHOD_SERVICE) as android.view.inputmethod.InputMethodManager
            WindowInspector.getGlobalWindowViews().forEach { root ->
                input.hideSoftInputFromWindow(root.windowToken, 0)
                androidx.core.view.ViewCompat.getWindowInsetsController(root)?.hide(WindowInsetsCompat.Type.ime())
            }
        }
        compose.waitUntil(5_000) {
            var visible = false
            instrumentation.runOnMainSync {
                visible = WindowInspector.getGlobalWindowViews().any { root ->
                    root.rootWindowInsets?.let { WindowInsetsCompat.toWindowInsetsCompat(it).isVisible(WindowInsetsCompat.Type.ime()) } ?: false
                }
            }
            !visible
        }
    }
    private fun clickSystemText(text: String) {
        val automation = instrumentation.uiAutomation
        automation.serviceInfo = automation.serviceInfo.apply {
            flags = flags or android.accessibilityservice.AccessibilityServiceInfo.FLAG_RETRIEVE_INTERACTIVE_WINDOWS
        }
        var bounds: android.graphics.Rect? = null
        var targetNode: android.view.accessibility.AccessibilityNodeInfo? = null
        compose.waitUntil(15_000) {
            val node = automation.windows.asSequence().mapNotNull { it.root }
                .flatMap { it.findAccessibilityNodeInfosByText(text).asSequence() }
                .lastOrNull { (it.text?.toString() == text || it.contentDescription?.toString() == text) && it.isVisibleToUser }
            bounds = node?.let { android.graphics.Rect().also(it::getBoundsInScreen) }
            targetNode = node
            bounds != null
        }
        var clickable = targetNode
        while (clickable != null && !clickable.isClickable) clickable = clickable.parent
        if (clickable?.performAction(android.view.accessibility.AccessibilityNodeInfo.ACTION_CLICK) == true) return
        val target = checkNotNull(bounds)
        val time = android.os.SystemClock.uptimeMillis()
        for (action in listOf(android.view.MotionEvent.ACTION_DOWN, android.view.MotionEvent.ACTION_UP)) {
            val event = android.view.MotionEvent.obtain(time, android.os.SystemClock.uptimeMillis(), action,
                target.exactCenterX(), target.exactCenterY(), 0)
            event.source = android.view.InputDevice.SOURCE_TOUCHSCREEN
            try { check(automation.injectInputEvent(event, true)) } finally { event.recycle() }
        }
    }
    private fun screenshot(name: String) {
        compose.waitForIdle()
        instrumentation.waitForIdleSync()
        android.os.SystemClock.sleep(100)
        val bitmap = checkNotNull(instrumentation.uiAutomation.takeScreenshot())
        val folder = File(context.getExternalFilesDir(null), "usability").apply { mkdirs() }
        File(folder, "$name.png").outputStream().use { bitmap.compress(Bitmap.CompressFormat.PNG, 100, it) }
        bitmap.recycle()
        assertNoUnresponsiveDialog()
    }
    private fun assertNoUnresponsiveDialog() {
        val root = instrumentation.uiAutomation.rootInActiveWindow
        check(root?.findAccessibilityNodeInfosByText("isn't responding").isNullOrEmpty()) { "An Android unresponsive-process dialog blocks the emulator." }
    }
}

/** Local HTTP fixture; no real homeserver or account is used by these tests. */
private class FixtureServer : AutoCloseable {
    private val socket = ServerSocket(0)
    private val executor = Executors.newSingleThreadExecutor()
    val requests = ConcurrentLinkedQueue<String>()
    @Volatile var modelsFail = false
    @Volatile var modelsEmpty = false
    val url = "http://127.0.0.1:${socket.localPort}"
    init {
        executor.execute {
            while (!socket.isClosed) {
                try {
                    socket.accept().use { connection ->
                        connection.soTimeout = 10_000
                        val input = connection.getInputStream()
                        val header = StringBuilder()
                        while (!header.endsWith("\r\n\r\n")) {
                            val next = input.read(); if (next < 0) return@use
                            header.append(next.toChar()); require(header.length < 32768)
                        }
                        val length = Regex("Content-Length: (\\d+)", RegexOption.IGNORE_CASE).find(header)?.groupValues?.get(1)?.toInt() ?: 0
                        val body = ByteArray(length)
                        var offset = 0
                        while (offset < length) { val n = input.read(body, offset, length - offset); if (n < 0) break; offset += n }
                        requests.add(header.toString() + body.toString(Charsets.UTF_8))
                        val response = if (header.startsWith("GET")) (if (modelsEmpty) "{\"data\":[]}" else "{\"data\":[{\"id\":\"test/alpha\"},{\"id\":\"test/beta\"}]}") else
                            JSONObject().put("choices", JSONArray().put(JSONObject().put("message", JSONObject().put("content", "## Status disk\n\nPeriksa **ruang tersedia** dengan perintah berikut.\n\n```bash\ndf -h\n```\n\n- Lihat kolom Use%.\n- Kirim hasilnya untuk diperiksa.")))).toString()
                        val bytes = response.toByteArray()
                        val status = if (modelsFail && header.startsWith("GET")) "503 Unavailable" else "200 OK"
                        connection.getOutputStream().apply { write("HTTP/1.1 $status\r\nContent-Type: application/json\r\nContent-Length: ${bytes.size}\r\nConnection: close\r\n\r\n".toByteArray()); write(bytes); flush() }
                    }
                } catch (_: java.io.IOException) { if (socket.isClosed) break }
            }
        }
    }
    override fun close() { socket.close(); executor.shutdownNow() }
}
