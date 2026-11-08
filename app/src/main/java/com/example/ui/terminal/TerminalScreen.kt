package com.example.ui.terminal

import android.content.ClipData
import android.content.ClipboardManager
import android.content.Context
import android.view.KeyEvent
import android.widget.Toast
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.automirrored.filled.ArrowForward
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.viewinterop.AndroidView
import com.example.data.model.ConnectionProfile
import com.example.data.model.CustomSnippet
import com.example.data.model.ServerConfig
import com.example.data.preferences.AppPreferences
import com.example.data.security.EncryptedStorage
import com.example.data.ssh.SshTerminalManager
import com.example.data.ssh.SshTerminalSession
import com.example.data.ssh.TerminalConnectionState
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import com.example.ui.i18n.tr
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.compose.LocalLifecycleOwner
import androidx.lifecycle.repeatOnLifecycle
import kotlinx.coroutines.flow.collect

private val CanvasColor = Color(0xFF141729)
private val BarColor = Color(0xFF1B1E33)
private val Green = Color(0xFF83CBB0)
private val Subtle = Color(0xFFB6BBCA)

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun TerminalScreen(
    terminalManager: SshTerminalManager,
    serverConfig: ServerConfig,
    encryptedStorage: EncryptedStorage? = null,
    modifier: Modifier = Modifier,
    preferences: AppPreferences = AppPreferences(),
    onPreferencesChange: (AppPreferences) -> Unit = {},
    onOpenSettings: () -> Unit = {},
    isTabActive: Boolean = true
) {
    MaterialTheme(colorScheme = MaterialTheme.colorScheme) {
    val context = LocalContext.current
    val session by terminalManager.activeSession.collectAsState()
    val sessions by terminalManager.sessions.collectAsState()
    val title by session.title.collectAsState()
    val state by session.connectionState.collectAsState()
    val reconnectAttempt by session.reconnectAttempt.collectAsState()
    var view by remember { mutableStateOf<TerminalView?>(null) }
    var ctrl by remember(session.id) { mutableStateOf(false) }
    var alt by remember(session.id) { mutableStateOf(false) }
    var scrolled by remember(session.id) { mutableStateOf(false) }
    var menu by remember { mutableStateOf(false) }
    var showSessions by remember { mutableStateOf(false) }
    var showHosts by remember { mutableStateOf(false) }
    var profiles by remember { mutableStateOf(emptyList<ConnectionProfile>()) }
    var renameTarget by remember { mutableStateOf<SshTerminalSession?>(null) }
    var renameText by remember { mutableStateOf("") }
    var closeTarget by remember { mutableStateOf<SshTerminalSession?>(null) }
    var clearOutput by remember { mutableStateOf(false) }
    var showSnippets by remember { mutableStateOf(false) }
    var snippetQuery by remember { mutableStateOf("") }
    var snippets by remember { mutableStateOf(encryptedStorage?.loadCustomSnippets() ?: CustomSnippet.getDefaultSnippets()) }
    var editingSnippet by remember { mutableStateOf<CustomSnippet?>(null) }
    var editSnippet by remember { mutableStateOf(false) }
    var snippetName by remember { mutableStateOf("") }
    var snippetCommand by remember { mutableStateOf("") }
    var draftTarget by remember { mutableStateOf<SshTerminalSession?>(null) }
    var draft by remember { mutableStateOf("") }
    var pasteDraft by remember { mutableStateOf(false) }
    var search by remember(session.id) { mutableStateOf(false) }
    var query by remember(session.id) { mutableStateOf("") }
    var matches by remember(session.id) { mutableStateOf(emptyList<TerminalMatch>()) }
    var matchIndex by remember(session.id) { mutableStateOf(0) }
    var refreshSearch by remember { mutableIntStateOf(0) }

    fun copy(text: String) {
        (context.getSystemService(Context.CLIPBOARD_SERVICE) as ClipboardManager).setPrimaryClip(ClipData.newPlainText("Terminal", text))
        Toast.makeText(context, "Disalin", Toast.LENGTH_SHORT).show()
    }
    fun paste(text: String) {
        if (state !is TerminalConnectionState.Connected) {
            Toast.makeText(context, "Hubungkan sesi sebelum menempel teks", Toast.LENGTH_SHORT).show()
        } else if (preferences.previewPaste || text.contains('\n') || text.contains('\r')) {
            draftTarget = session; draft = text; pasteDraft = true
        } else { view?.scrollToLatest(); session.engine.paste(text) }
    }
    fun newSession() { view?.hideKeyboard(); profiles = encryptedStorage?.loadProfiles().orEmpty(); showHosts = true }
    fun sendKey(code: Int) {
        val modifiers = (if (ctrl) com.termux.terminal.KeyHandler.KEYMOD_CTRL else 0) or (if (alt) com.termux.terminal.KeyHandler.KEYMOD_ALT else 0)
        view?.scrollToLatest(); session.engine.key(code, modifiers)
        ctrl = false; alt = false
    }
    LaunchedEffect(query, session.id, refreshSearch) {
        matches = withContext(Dispatchers.Default) { TerminalTextSearch.find(session.engine, query) }
        matchIndex = 0
        view?.highlight(matches.firstOrNull())
    }
    Column(modifier.fillMaxSize().background(CanvasColor)) {
        Row(Modifier.fillMaxWidth().heightIn(min = 56.dp).padding(start = 12.dp, end = 4.dp), verticalAlignment = Alignment.CenterVertically) {
            Column(Modifier.weight(1f).clickable { showSessions = true }.padding(vertical = 8.dp).testTag("terminal_session_picker")) {
                Text(title, color = Color.White, fontSize = 15.sp, fontWeight = FontWeight.SemiBold, maxLines = 1, overflow = TextOverflow.Ellipsis)
                Text(when (val connection = state) {
                    is TerminalConnectionState.Connected -> if (session.lastConfig == null) tr("Simulasi lokal", "Local simulation") else "${connection.user}@${connection.host}"
                    is TerminalConnectionState.Connecting -> if (reconnectAttempt > 0) tr("Menghubungkan ulang ($reconnectAttempt/6)…", "Reconnecting ($reconnectAttempt/6)…") else tr("Menghubungkan…", "Connecting…")
                    is TerminalConnectionState.Error -> tr("Koneksi gagal", "Connection failed")
                    else -> if (sessions.isEmpty()) tr("Belum ada sesi", "No sessions") else tr("Belum terhubung", "Disconnected")
                }, color = if (state is TerminalConnectionState.Connected) Green else Subtle, fontSize = 12.sp, maxLines = 1, overflow = TextOverflow.Ellipsis)
            }
            IconButton(enabled = sessions.isNotEmpty(), onClick = { search = !search; if (!search) { query = ""; view?.highlight(null) } }) { Icon(Icons.Default.Search, tr("Cari output", "Search output"), tint = if (sessions.isNotEmpty()) Green else Subtle.copy(alpha = 0.45f)) }
            IconButton(onClick = { newSession() }, enabled = sessions.size < 5, modifier = Modifier.testTag("new_terminal_session")) { Icon(Icons.Default.Add, tr("Sesi baru", "New session"), tint = if (sessions.size < 5) Green else Subtle) }
            Box {
            IconButton(enabled = sessions.isNotEmpty(), onClick = { menu = true }) { Icon(Icons.Default.MoreVert, tr("Menu terminal", "Terminal menu"), tint = if (sessions.isNotEmpty()) Subtle else Subtle.copy(alpha = 0.45f)) }
                DropdownMenu(menu, { menu = false }) {
                    DropdownMenuItem(text = { Text(tr("Kelola sesi", "Manage sessions")) }, onClick = { menu = false; view?.hideKeyboard(); showSessions = true })
                    DropdownMenuItem(text = { Text(tr("Pengaturan terminal", "Terminal settings")) }, onClick = { menu = false; view?.hideKeyboard(); onOpenSettings() })
                    DropdownMenuItem(text = { Text(tr("Ubah nama sesi", "Rename session")) }, enabled = sessions.isNotEmpty(), onClick = { menu = false; renameTarget = session; renameText = title })
                    DropdownMenuItem(text = { Text(tr("Salin seluruh output", "Copy all output")) }, enabled = sessions.isNotEmpty(), onClick = { menu = false; copy(synchronized(session.engine.lock) { session.engine.emulator.screen.transcriptText }) })
                    DropdownMenuItem(text = { Text(tr("Bersihkan output", "Clear output")) }, enabled = sessions.isNotEmpty(), onClick = { menu = false; clearOutput = true })
                    DropdownMenuItem(text = { Text(tr("Putuskan koneksi", "Disconnect")) }, enabled = state is TerminalConnectionState.Connected || state is TerminalConnectionState.Connecting, onClick = { menu = false; session.disconnect() })
                    DropdownMenuItem(text = { Text(tr("Tutup sesi", "Close session")) }, enabled = sessions.isNotEmpty(), onClick = { menu = false; view?.hideKeyboard(); closeTarget = session }, modifier = Modifier.testTag("close_terminal_session"))
                }
            }
        }
        if (sessions.size > 1) Row(Modifier.fillMaxWidth().horizontalScroll(rememberScrollState()).background(BarColor).padding(horizontal = 8.dp), horizontalArrangement = Arrangement.spacedBy(6.dp)) {
            sessions.forEachIndexed { index, item ->
                val sessionTitle by item.title.collectAsState()
                FilterChip(selected = item.id == session.id, onClick = { terminalManager.selectSession(index) }, label = { Text(sessionTitle, maxLines = 1, overflow = TextOverflow.Ellipsis) }, modifier = Modifier.widthIn(max = 180.dp),
                    colors = FilterChipDefaults.filterChipColors(containerColor = BarColor, labelColor = Subtle, selectedContainerColor = Color(0xFF26483F), selectedLabelColor = Green))
            }
        }
        if (sessions.isNotEmpty() && state !is TerminalConnectionState.Connected) Row(Modifier.fillMaxWidth().padding(horizontal = 12.dp), verticalAlignment = Alignment.CenterVertically) {
            Text((state as? TerminalConnectionState.Error)?.message ?: tr("Buka koneksi SSH atau coba simulasi lokal.", "Connect to SSH or try a local simulation."), color = Subtle, fontSize = 12.sp, modifier = Modifier.weight(1f))
            TextButton(enabled = state !is TerminalConnectionState.Connecting, onClick = {
                val config = session.lastConfig ?: serverConfig
                if (config.isSshConfigured) {
                    terminalManager.onHostConnectionRequested()
                    session.connect(config)
                } else session.startSandboxMode()
            }, modifier = Modifier.testTag("connect_ssh_button")) { Text(if (state is TerminalConnectionState.Error) tr("Coba lagi", "Try again") else tr("Hubungkan", "Connect"), color = Green) }
        }
        if (search) Column(Modifier.fillMaxWidth().background(BarColor).padding(horizontal = 12.dp)) {
            OutlinedTextField(query, { query = it }, label = { Text(tr("Cari dalam output", "Search terminal output")) }, singleLine = true,
                colors = OutlinedTextFieldDefaults.colors(focusedTextColor = Color.White, unfocusedTextColor = Color.White, focusedLabelColor = Green, unfocusedLabelColor = Subtle, focusedBorderColor = Green, cursorColor = Green),
                modifier = Modifier.fillMaxWidth().testTag("terminal_search"))
            Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
                Text(if (query.isBlank()) tr("Cari teks pada sesi ini", "Search text in this session") else if (matches.isEmpty()) tr("Tidak ada hasil", "No matches") else "${matchIndex + 1} / ${matches.size}${if (matches.size == 1000) "+" else ""}", color = Subtle, fontSize = 12.sp, modifier = Modifier.weight(1f))
                IconButton(enabled = matches.isNotEmpty(), onClick = { matchIndex = (matchIndex - 1 + matches.size) % matches.size; view?.highlight(matches[matchIndex]) }) { Icon(Icons.Default.KeyboardArrowUp, "Hasil sebelumnya", tint = Green) }
                IconButton(enabled = matches.isNotEmpty(), onClick = { matchIndex = (matchIndex + 1) % matches.size; view?.highlight(matches[matchIndex]) }) { Icon(Icons.Default.KeyboardArrowDown, "Hasil berikutnya", tint = Green) }
                IconButton(onClick = { refreshSearch++ }) { Icon(Icons.Default.Refresh, "Perbarui pencarian", tint = Green) }
                IconButton(onClick = { search = false; query = ""; view?.highlight(null) }) { Icon(Icons.Default.Close, "Tutup pencarian", tint = Green) }
            }
        }
        Box(Modifier.weight(1f).fillMaxWidth()) {
            if (sessions.isEmpty()) Column(Modifier.align(Alignment.Center).padding(24.dp), horizontalAlignment = Alignment.CenterHorizontally) {
                Text(tr("Belum ada sesi terminal", "No terminal sessions"), color = Color.White, fontSize = 18.sp, fontWeight = FontWeight.SemiBold)
                Text(tr("Buka host tersimpan atau mulai simulasi lokal.", "Open a saved host or start a local simulation."), color = Subtle, fontSize = 13.sp, modifier = Modifier.padding(vertical = 12.dp))
                TextButton(onClick = { newSession() }, modifier = Modifier.testTag("open_terminal_session")) { Text(tr("Buka sesi", "Open session"), color = Green) }
            } else {
            TerminalCanvas(session, preferences, isTabActive, ctrl, alt,
                onModifiersUsed = { ctrl = false; alt = false }, onView = { view = it },
                onScrolled = { scrolled = it }, onFontSize = { onPreferencesChange(preferences.copy(terminalFontSize = it)) },
                onPaste = ::paste, modifier = Modifier.fillMaxSize())
            if (scrolled) FilledTonalButton(onClick = { view?.scrollToLatest() }, modifier = Modifier.align(Alignment.BottomEnd).padding(8.dp).testTag("terminal_latest")) {
                Text(tr("Output terbaru", "Latest output"))
            }
            }
        }
        if (sessions.isNotEmpty()) {
        if (preferences.extraKeys) Row(Modifier.fillMaxWidth().background(BarColor).horizontalScroll(rememberScrollState()).padding(horizontal = 4.dp), verticalAlignment = Alignment.CenterVertically) {
            TerminalKey("Ctrl", ctrl) { ctrl = !ctrl }
            TerminalKey("Esc") { sendKey(KeyEvent.KEYCODE_ESCAPE) }
            TerminalKey("Tab") { sendKey(KeyEvent.KEYCODE_TAB) }
            TerminalKey("←") { sendKey(KeyEvent.KEYCODE_DPAD_LEFT) }
            TerminalKey("↑") { sendKey(KeyEvent.KEYCODE_DPAD_UP) }
            TerminalKey("↓") { sendKey(KeyEvent.KEYCODE_DPAD_DOWN) }
            TerminalKey("→") { sendKey(KeyEvent.KEYCODE_DPAD_RIGHT) }
            TerminalKey("Alt", alt) { alt = !alt }
            TerminalKey("Home") { sendKey(KeyEvent.KEYCODE_MOVE_HOME) }
            TerminalKey("End") { sendKey(KeyEvent.KEYCODE_MOVE_END) }
        }
        Row(Modifier.fillMaxWidth().background(BarColor), verticalAlignment = Alignment.CenterVertically) {
            TextButton(onClick = { session.sendKeyCtrlC() }, modifier = Modifier.weight(1f)) { Text("Ctrl+C", color = Green) }
            TextButton(onClick = { view?.requestPaste() }, modifier = Modifier.weight(1f)) { Text(tr("Tempel", "Paste"), color = Green) }
            TextButton(onClick = { showSnippets = true }, modifier = Modifier.weight(1f).testTag("terminal_snippets")) { Text(tr("Perintah", "Commands"), color = Green) }
            IconButton(onClick = { draftTarget = session; draft = ""; pasteDraft = false }) { Icon(Icons.Default.Edit, tr("Susun perintah", "Compose command"), tint = Green) }
            IconButton(onClick = { view?.showKeyboard() }, modifier = Modifier.testTag("terminal_keyboard")) { Icon(Icons.Default.Keyboard, tr("Buka keyboard", "Open keyboard"), tint = Green) }
        }
        }
    }

    if (showHosts) ModalBottomSheet(onDismissRequest = { showHosts = false }, sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true)) {
        Column(Modifier.fillMaxWidth().navigationBarsPadding().padding(horizontal = 20.dp)) {
            Text(tr("Buka sesi baru", "Open a new session"), style = MaterialTheme.typography.titleLarge, fontWeight = FontWeight.Bold)
            Text(tr("Pilih host untuk sesi ini. Sesi lain tetap berjalan.", "Choose a host. Other sessions keep running."), style = MaterialTheme.typography.bodyMedium, modifier = Modifier.padding(vertical = 8.dp))
            LazyColumn(Modifier.heightIn(max = 360.dp)) {
                items(profiles, key = { it.id }) { profile ->
                    ListItem(headlineContent = { Text(profile.name) }, supportingContent = { Text(if (profile.config.isSshConfigured) "${profile.config.sshUser}@${profile.config.sshHost}:${profile.config.sshPort}" else "Lengkapi koneksi host di Settings") },
                        modifier = Modifier.clickable(enabled = profile.config.isSshConfigured) { terminalManager.createSession(profile.config, profile.name); showHosts = false })
                }
                item { TextButton(onClick = { terminalManager.createSession(title = "Simulasi ${sessions.size + 1}").startSandboxMode(); showHosts = false }, modifier = Modifier.fillMaxWidth().testTag("new_sandbox_session")) { Text(tr("Buka simulasi lokal", "Open local simulation")) } }
                item { TextButton(onClick = { showHosts = false; onOpenSettings() }, modifier = Modifier.fillMaxWidth()) { Text(tr("Atur host di Settings", "Configure a host in Settings")) } }
            }
        }
    }
    if (showSessions) ModalBottomSheet(onDismissRequest = { showSessions = false }, sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true)) {
        Column(Modifier.fillMaxWidth().navigationBarsPadding().padding(horizontal = 20.dp)) {
            Text(tr("Sesi terminal (${sessions.size}/5)", "Terminal sessions (${sessions.size}/5)"), style = MaterialTheme.typography.titleLarge)
            LazyColumn(Modifier.heightIn(max = 400.dp)) {
                items(sessions, key = { it.id }) { item ->
                    val itemTitle by item.title.collectAsState()
                    val connection by item.connectionState.collectAsState()
                    ListItem(headlineContent = { Text(itemTitle, fontWeight = if (item.id == session.id) FontWeight.Bold else FontWeight.Normal) },
                        supportingContent = { Text(when (connection) { is TerminalConnectionState.Connected -> if (item.lastConfig == null) "Simulasi lokal" else item.lastConfig!!.sshHost; is TerminalConnectionState.Connecting -> "Menghubungkan…"; else -> "Tidak terhubung" }) },
                        leadingContent = { if (item.id == session.id) Icon(Icons.Default.Check, "Sesi aktif") },
                        trailingContent = { Row {
                            IconButton(onClick = { renameTarget = item; renameText = itemTitle }) { Icon(Icons.Default.Edit, tr("Ubah nama $itemTitle", "Rename $itemTitle")) }
                            IconButton(onClick = { closeTarget = item }) { Icon(Icons.Default.Close, tr("Tutup $itemTitle", "Close $itemTitle")) }
                        } }, modifier = Modifier.clickable { terminalManager.selectSession(sessions.indexOf(item)); showSessions = false })
                }
            }
            TextButton(enabled = sessions.size < 5, onClick = { showSessions = false; newSession() }, modifier = Modifier.fillMaxWidth()) { Text(if (sessions.size < 5) tr("Tambah sesi", "Add session") else tr("Maksimal lima sesi", "Maximum five sessions")) }
            TextButton(enabled = sessions.isNotEmpty(), onClick = { session.disconnect(); showSessions = false }, modifier = Modifier.testTag("disconnect_ssh_button")) { Text(tr("Putuskan sesi aktif", "Disconnect active session")) }
        }
    }
    renameTarget?.let { target -> AlertDialog(onDismissRequest = { renameTarget = null }, title = { Text(tr("Nama sesi", "Session name")) },
        text = { OutlinedTextField(renameText, { renameText = it.take(60) }, label = { Text(tr("Nama", "Name")) }, singleLine = true) },
        confirmButton = { TextButton(enabled = renameText.isNotBlank(), onClick = { target.setTitle(renameText.trim()); renameTarget = null }) { Text(tr("Simpan", "Save")) } },
        dismissButton = { TextButton(onClick = { renameTarget = null }) { Text(tr("Batal", "Cancel")) } }) }
    closeTarget?.let { target -> AlertDialog(onDismissRequest = { closeTarget = null }, title = { Text(tr("Tutup sesi?", "Close session?")) }, text = { Text(if (sessions.size == 1) tr("Koneksi sesi ini akan diputus. Kamu bisa membuka sesi baru setelahnya.", "This session will disconnect. You can open a new session afterward.") else tr("Koneksi sesi ini akan diputus. Sesi lain tetap berjalan.", "This session will disconnect. Other sessions keep running.")) },
        confirmButton = { TextButton(onClick = { val index = terminalManager.sessions.value.indexOfFirst { it.id == target.id }; terminalManager.closeSession(index); closeTarget = null; showSessions = false }) { Text(tr("Tutup sesi", "Close session")) } },
        dismissButton = { TextButton(onClick = { closeTarget = null }) { Text(tr("Batal", "Cancel")) } }) }
    if (clearOutput) AlertDialog(onDismissRequest = { clearOutput = false }, title = { Text(tr("Bersihkan output sesi?", "Clear session output?")) }, text = { Text(tr("Riwayat tampilan terminal ini akan dihapus. Proses di server tetap berjalan.", "The terminal display history will be cleared. Server processes keep running.")) },
        confirmButton = { TextButton(onClick = { session.clearBuffer(); view?.scrollToLatest(); clearOutput = false }) { Text(tr("Bersihkan", "Clear")) } }, dismissButton = { TextButton(onClick = { clearOutput = false }) { Text(tr("Batal", "Cancel")) } })
    draftTarget?.let { target -> AlertDialog(onDismissRequest = { draftTarget = null }, title = { Text(if (pasteDraft) tr("Tinjau teks clipboard", "Review clipboard text") else tr("Susun perintah", "Compose command")) },
        text = { Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
            val targetTitle by target.title.collectAsState()
            Text(tr("Sesi: $targetTitle", "Session: $targetTitle"), fontSize = 12.sp)
            Text(if (draft.contains('\n') || draft.contains('\r')) tr("Teks berisi beberapa baris. Shell dapat menjalankan baris tersebut saat ditempel.", "This text contains multiple lines. The shell may execute them when pasted.") else tr("Tekan Enter di terminal untuk menjalankan setelah teks disisipkan.", "Press Enter in the terminal to run the inserted text."), fontSize = 13.sp)
            OutlinedTextField(draft, { draft = it }, label = { Text(tr("Perintah atau teks", "Command or text")) }, minLines = 3, maxLines = 8, textStyle = MaterialTheme.typography.bodyMedium.copy(fontFamily = FontFamily.Monospace), modifier = Modifier.testTag("terminal_command_draft"))
        } }, confirmButton = {
            val targetState by target.connectionState.collectAsState()
            TextButton(enabled = draft.isNotEmpty() && targetState is TerminalConnectionState.Connected, onClick = { target.engine.paste(draft); draftTarget = null; view?.showKeyboard() }) { Text(tr("Sisipkan ke terminal", "Insert into terminal")) }
        },
        dismissButton = { TextButton(onClick = { draftTarget = null }) { Text(tr("Batal", "Cancel")) } }) }
    if (showSnippets) ModalBottomSheet(onDismissRequest = { showSnippets = false }, sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true)) {
        Column(Modifier.fillMaxWidth().navigationBarsPadding().padding(horizontal = 20.dp)) {
            Text(tr("Perintah tersimpan", "Saved commands"), style = MaterialTheme.typography.titleLarge)
            Text(tr("Pilih perintah untuk meninjaunya sebelum disisipkan.", "Choose a command to review before inserting it."), fontSize = 13.sp, modifier = Modifier.padding(vertical = 8.dp))
            OutlinedTextField(snippetQuery, { snippetQuery = it }, label = { Text(tr("Cari perintah", "Search commands")) }, singleLine = true, modifier = Modifier.fillMaxWidth())
            val filtered = snippets.filter { it.name.contains(snippetQuery, true) || it.command.contains(snippetQuery, true) }
            LazyColumn(Modifier.heightIn(max = 340.dp)) {
                if (filtered.isEmpty()) item { Text(tr("Tidak ada perintah yang cocok.", "No matching commands."), modifier = Modifier.padding(vertical = 20.dp)) }
                items(filtered, key = { it.id }) { snippet ->
                    ListItem(headlineContent = { Text(snippet.name) }, supportingContent = { Text(snippet.command, fontFamily = FontFamily.Monospace, maxLines = 2, overflow = TextOverflow.Ellipsis) },
                        trailingContent = { if (!snippet.isDefault) Row {
                            IconButton(onClick = { editingSnippet = snippet; snippetName = snippet.name; snippetCommand = snippet.command; editSnippet = true; showSnippets = false }) { Icon(Icons.Default.Edit, "Ubah ${snippet.name}") }
                            IconButton(onClick = { encryptedStorage?.deleteCustomSnippet(snippet.id); snippets = snippets.filter { it.id != snippet.id } }) { Icon(Icons.Default.Delete, "Hapus ${snippet.name}") }
                        } }, modifier = Modifier.clickable { draftTarget = session; draft = snippet.command; pasteDraft = false; showSnippets = false })
                }
            }
            TextButton(onClick = { editingSnippet = null; snippetName = ""; snippetCommand = ""; editSnippet = true; showSnippets = false }, modifier = Modifier.fillMaxWidth().testTag("add_terminal_snippet")) { Text(tr("Tambah perintah", "Add command")) }
        }
    }
    if (editSnippet) AlertDialog(onDismissRequest = { editSnippet = false }, title = { Text(if (editingSnippet == null) tr("Tambah perintah", "Add command") else tr("Ubah perintah", "Edit command")) },
        text = { Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
            OutlinedTextField(snippetName, { snippetName = it }, label = { Text(tr("Nama perintah", "Command name")) }, singleLine = true)
            OutlinedTextField(snippetCommand, { snippetCommand = it }, label = { Text(tr("Isi perintah", "Command text")) }, minLines = 2, maxLines = 5)
        } }, confirmButton = { TextButton(enabled = snippetName.isNotBlank() && snippetCommand.isNotBlank(), onClick = {
            val next = (editingSnippet ?: CustomSnippet(name = snippetName, command = snippetCommand, category = "Umum", isDefault = false)).copy(name = snippetName.trim(), command = snippetCommand)
            snippets = snippets.filter { it.id != next.id } + next
            encryptedStorage?.saveAllSnippets(snippets)
            editSnippet = false
        }) { Text(tr("Simpan", "Save")) } }, dismissButton = { TextButton(onClick = { editSnippet = false }) { Text(tr("Batal", "Cancel")) } })
    }
}

@Composable
private fun TerminalCanvas(session: SshTerminalSession, preferences: AppPreferences, active: Boolean, ctrl: Boolean, alt: Boolean,
    onModifiersUsed: () -> Unit, onView: (TerminalView) -> Unit, onScrolled: (Boolean) -> Unit,
    onFontSize: (Int) -> Unit, onPaste: (String) -> Unit, modifier: Modifier) {
    var terminalView by remember { mutableStateOf<TerminalView?>(null) }
    val owner = LocalLifecycleOwner.current
    val currentView = terminalView
    LaunchedEffect(session, active, currentView, owner) {
        if (!active || currentView == null) return@LaunchedEffect
        owner.lifecycle.repeatOnLifecycle(Lifecycle.State.STARTED) {
            session.revision.collect { revision -> currentView.invalidateForRevision(revision) }
        }
    }
    AndroidView(factory = { TerminalView(it).also { created -> terminalView = created; onView(created) } }, modifier = modifier.testTag("terminal_canvas"), update = {
        it.session = session; it.ctrl = ctrl; it.alt = alt
        it.onModifiersUsed = onModifiersUsed; it.onViewportChanged = onScrolled
        it.onFontSizeChanged = onFontSize; it.onPasteRequested = onPaste
        it.applyPreferences(preferences, active)
    })
}

@Composable
private fun TerminalKey(label: String, selected: Boolean = false, onClick: () -> Unit) {
    TextButton(onClick, modifier = Modifier.widthIn(min = 48.dp).heightIn(min = 48.dp),
        colors = ButtonDefaults.textButtonColors(contentColor = Green, containerColor = if (selected) Color(0xFF35564C) else Color.Transparent), contentPadding = PaddingValues(horizontal = 10.dp)) {
        Text(label, fontSize = 13.sp, fontWeight = if (selected) FontWeight.Bold else FontWeight.Normal)
    }
}
