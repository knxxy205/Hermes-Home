package com.example.ui.terminal

import android.view.KeyEvent
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalClipboardManager
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.viewinterop.AndroidView
import com.example.data.model.CustomSnippet
import com.example.data.model.ServerConfig
import com.example.data.security.EncryptedStorage
import com.example.data.ssh.SshTerminalManager
import com.example.data.ssh.SshTerminalSession
import com.example.data.ssh.TerminalConnectionState

private val CanvasColor = Color(0xff141729)
private val BarColor = Color(0xff1b1e33)
private val Green = Color(0xff62ae91)

@Composable
fun TerminalScreen(
    terminalManager: SshTerminalManager,
    serverConfig: ServerConfig,
    encryptedStorage: EncryptedStorage? = null,
    modifier: Modifier = Modifier
) {
    val session by terminalManager.activeSession.collectAsState()
    val sessions by terminalManager.sessions.collectAsState()
    val state by session.connectionState.collectAsState()
    val clipboard = LocalClipboardManager.current
    var terminalView by remember { mutableStateOf<TerminalView?>(null) }
    var ctrl by remember(session.id) { mutableStateOf(false) }
    var showSessions by remember { mutableStateOf(false) }
    var showSnippets by remember { mutableStateOf(false) }
    var addSnippet by remember { mutableStateOf(false) }
    var name by remember { mutableStateOf("") }
    var command by remember { mutableStateOf("") }
    var snippets by remember { mutableStateOf(encryptedStorage?.loadCustomSnippets() ?: CustomSnippet.getDefaultSnippets()) }

    Column(modifier.fillMaxSize().background(CanvasColor)) {
        if (state !is TerminalConnectionState.Connected) {
            Row(Modifier.fillMaxWidth().padding(horizontal = 12.dp), verticalAlignment = Alignment.CenterVertically) {
                Text(when (val s = state) {
                    is TerminalConnectionState.Connecting -> "Menghubungkan…"
                    is TerminalConnectionState.Error -> s.message
                    else -> "Ketuk Hubungkan untuk membuka terminal"
                }, color = Color(0xffb6bbca), fontSize = 12.sp, modifier = Modifier.weight(1f))
                TextButton(enabled = state !is TerminalConnectionState.Connecting, onClick = {
                    if (serverConfig.isSshConfigured) session.connect(serverConfig) else session.startSandboxMode()
                }, modifier = Modifier.testTag("connect_ssh_button")) { Text("Hubungkan", color = Green) }
            }
        }
        TerminalCanvas(session, ctrl, { ctrl = false }, { terminalView = it }, Modifier.weight(1f).fillMaxWidth())
        Row(Modifier.fillMaxWidth().height(48.dp).padding(horizontal = 8.dp), verticalAlignment = Alignment.CenterVertically) {
            IconButton(onClick = { terminalView?.showKeyboard() }) {
                Icon(Icons.Default.Keyboard, "Buka keyboard", tint = Green)
            }
            Row(Modifier.weight(1f).height(36.dp).clip(RoundedCornerShape(12.dp))
                .background(Color(0xff183b39)).clickable { showSessions = true }.padding(horizontal = 12.dp),
                verticalAlignment = Alignment.CenterVertically) {
                Text(if (serverConfig.isSshConfigured) "${serverConfig.sshUser}@${serverConfig.sshHost}" else "Sandbox",
                    color = Green, fontSize = 13.sp, maxLines = 1, modifier = Modifier.weight(1f))
                Icon(Icons.Default.ExpandMore, "Pilih sesi", tint = Green, modifier = Modifier.size(18.dp))
            }
            IconButton(onClick = { terminalManager.createSession(serverConfig) }) { Icon(Icons.Default.Add, "Sesi baru", tint = Green) }
            IconButton(onClick = { showSnippets = true }) { Icon(Icons.Default.GridView, "Perintah tersimpan", tint = Green) }
        }
        Row(Modifier.fillMaxWidth().background(BarColor).horizontalScroll(rememberScrollState()), verticalAlignment = Alignment.CenterVertically) {
            TerminalKey(if (ctrl) "ctrl •" else "ctrl") { ctrl = !ctrl }
            TerminalKey("esc") { session.engine.key(KeyEvent.KEYCODE_ESCAPE) }
            TerminalKey("tab") { session.engine.key(KeyEvent.KEYCODE_TAB) }
            TerminalKey("←") { session.engine.key(KeyEvent.KEYCODE_DPAD_LEFT) }
            TerminalKey("↑") { session.engine.key(KeyEvent.KEYCODE_DPAD_UP) }
            TerminalKey("↓") { session.engine.key(KeyEvent.KEYCODE_DPAD_DOWN) }
            TerminalKey("→") { session.engine.key(KeyEvent.KEYCODE_DPAD_RIGHT) }
            TerminalKey("^C") { session.sendKeyCtrlC() }
            TerminalKey("Tempel") { clipboard.getText()?.text?.let { session.engine.paste(it) } }
            TerminalKey("↵") { session.sendKeyEnter() }
        }
    }
    if (showSessions) AlertDialog(onDismissRequest = { showSessions = false }, title = { Text("Sesi terminal") }, text = {
        Column {
            sessions.forEachIndexed { index, item ->
                val title by item.title.collectAsState()
                Row(verticalAlignment = Alignment.CenterVertically) {
                    TextButton(onClick = { terminalManager.selectSession(index); showSessions = false }, modifier = Modifier.weight(1f)) { Text(title) }
                    if (sessions.size > 1) IconButton(onClick = { terminalManager.closeSession(index) }) { Icon(Icons.Default.Close, "Tutup sesi") }
                }
            }
            TextButton(onClick = { session.disconnect(); showSessions = false }, modifier = Modifier.testTag("disconnect_ssh_button")) { Text("Putuskan koneksi aktif") }
        }
    }, confirmButton = { TextButton(onClick = { showSessions = false }) { Text("Tutup") } })
    if (showSnippets) AlertDialog(onDismissRequest = { showSnippets = false }, title = { Text("Perintah tersimpan") }, text = {
        androidx.compose.foundation.lazy.LazyColumn {
            items(snippets.size) { index ->
                val snippet = snippets[index]
                Row(verticalAlignment = Alignment.CenterVertically) {
                    TextButton(onClick = { session.sendCommand(snippet.command); showSnippets = false }, modifier = Modifier.weight(1f)) { Text(snippet.name) }
                    if (!snippet.isDefault) IconButton(onClick = {
                        encryptedStorage?.deleteCustomSnippet(snippet.id)
                        snippets = snippets.filter { it.id != snippet.id }
                    }) { Icon(Icons.Default.Delete, "Hapus perintah") }
                }
            }
        }
    }, confirmButton = { TextButton(onClick = { addSnippet = true; showSnippets = false }) { Text("Tambah") } },
        dismissButton = { TextButton(onClick = { showSnippets = false }) { Text("Tutup") } })
    if (addSnippet) AlertDialog(onDismissRequest = { addSnippet = false }, title = { Text("Tambah perintah") }, text = {
        Column {
            OutlinedTextField(name, { name = it }, label = { Text("Nama") })
            OutlinedTextField(command, { command = it }, label = { Text("Perintah") })
        }
    }, confirmButton = { TextButton(enabled = name.isNotBlank() && command.isNotBlank(), onClick = {
        val snippet = CustomSnippet(name = name.trim(), command = command.trim(), category = "Umum", isDefault = false)
        encryptedStorage?.addCustomSnippet(snippet)
        snippets = snippets + snippet
        name = ""; command = ""; addSnippet = false
    }) { Text("Simpan") } }, dismissButton = { TextButton(onClick = { addSnippet = false }) { Text("Batal") } })
}

@Composable
private fun TerminalCanvas(session: SshTerminalSession, ctrl: Boolean, onCtrlUsed: () -> Unit,
                           onView: (TerminalView) -> Unit, modifier: Modifier) {
    // Only this small scope observes output; toolbar and navigation do not recompose per packet.
    val revision by session.revision.collectAsState()
    AndroidView(factory = { TerminalView(it).also(onView) }, modifier = modifier.testTag("terminal_canvas"), update = {
        it.session = session
        it.ctrl = ctrl
        it.onCtrlUsed = onCtrlUsed
        @Suppress("UNUSED_VARIABLE") val frame = revision
        it.postInvalidateOnAnimation()
    })
}

@Composable
private fun TerminalKey(label: String, onClick: () -> Unit) {
    Box(Modifier.height(44.dp).widthIn(min = 48.dp).clickable(onClick = onClick).padding(horizontal = 12.dp), contentAlignment = Alignment.Center) {
        Text(label, color = Green, fontSize = 14.sp)
    }
}
