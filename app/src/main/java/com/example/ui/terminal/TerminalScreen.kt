package com.example.ui.terminal

import android.widget.Toast
import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.combinedClickable
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.text.selection.SelectionContainer
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.Send
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.BookmarkBorder
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.ContentPaste
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.Edit
import androidx.compose.material.icons.filled.FlashOn
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material.icons.filled.RestartAlt
import androidx.compose.material.icons.filled.Stop
import androidx.compose.material.icons.filled.Terminal
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.OutlinedTextFieldDefaults
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.platform.LocalClipboardManager
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.data.model.CustomSnippet
import com.example.data.model.ServerConfig
import com.example.data.security.EncryptedStorage
import com.example.data.ssh.SshTerminalManager
import com.example.data.ssh.TerminalConnectionState
import com.example.ui.components.CustomScreenHeader
import com.example.ui.components.PillChip
import com.example.ui.components.ShortcutKeyButton
import com.example.ui.components.StatusBadge
import com.example.ui.theme.BurntOrange
import com.example.ui.theme.BurntOrangeBadgeBg
import com.example.ui.theme.BurntOrangeBorder
import com.example.ui.theme.BurntOrangePillBg
import com.example.ui.theme.NavyCardBorder
import com.example.ui.theme.NavyDarkBackground
import com.example.ui.theme.NavyDarkBorder
import com.example.ui.theme.NavyDarkCard
import com.example.ui.theme.NavyDarkSurface
import com.example.ui.theme.TerminalBg
import com.example.ui.theme.TerminalCyan
import com.example.ui.theme.TerminalGreen
import com.example.ui.theme.TerminalRed
import com.example.ui.theme.TextPrimary
import com.example.ui.theme.TextSecondary

@OptIn(ExperimentalFoundationApi::class)
@Composable
fun TerminalScreen(
    terminalManager: SshTerminalManager,
    serverConfig: ServerConfig,
    encryptedStorage: EncryptedStorage? = null,
    modifier: Modifier = Modifier
) {
    val context = LocalContext.current
    val clipboardManager = LocalClipboardManager.current

    val sessions by terminalManager.sessions.collectAsState()
    val activeSessionIndex by terminalManager.activeSessionIndex.collectAsState()
    val activeSession by terminalManager.activeSession.collectAsState()

    val connectionState by activeSession.connectionState.collectAsState()
    val terminalAnnotatedOutput by activeSession.terminalAnnotatedOutput.collectAsState()
    val isInteractiveMode by activeSession.isInteractiveMode.collectAsState()

    var inputCommand by remember { mutableStateOf("") }
    var isCtrlActive by remember { mutableStateOf(false) }
    var isWordWrapEnabled by remember { mutableStateOf(true) }

    // Custom snippets state
    var snippets by remember {
        mutableStateOf(encryptedStorage?.loadCustomSnippets() ?: CustomSnippet.getDefaultSnippets())
    }
    var showAddSnippetDialog by remember { mutableStateOf(false) }
    var snippetToAction by remember { mutableStateOf<CustomSnippet?>(null) }

    // New Snippet form fields
    var newSnippetName by remember { mutableStateOf("") }
    var newSnippetCommand by remember { mutableStateOf("") }
    var newSnippetCategory by remember { mutableStateOf("Umum") }

    val verticalScrollState = rememberScrollState()
    val terminalHorizontalScrollState = rememberScrollState()
    val tabsScrollState = rememberScrollState()
    val quickCmdScrollState = rememberScrollState()
    val shortcutScrollState = rememberScrollState()

    // Auto-scroll to bottom of active session output
    LaunchedEffect(terminalAnnotatedOutput) {
        verticalScrollState.scrollTo(verticalScrollState.maxValue)
    }

    val hostSubtitle = when (val state = connectionState) {
        is TerminalConnectionState.Connected -> "${state.user}@${state.host}"
        is TerminalConnectionState.Connecting -> "Connecting to ${serverConfig.sshHost}..."
        is TerminalConnectionState.Error -> "Offline - ${state.message.take(30)}..."
        TerminalConnectionState.Disconnected ->
            if (serverConfig.isSshConfigured) "${serverConfig.sshUser}@${serverConfig.sshHost}:${serverConfig.sshPort}"
            else "Homeserver not configured"
    }

    Column(
        modifier = modifier
            .fillMaxSize()
            .background(NavyDarkBackground)
    ) {
        // Custom Header with Badge Icon
        CustomScreenHeader(
            icon = Icons.Default.Terminal,
            title = "Terminal",
            subtitle = hostSubtitle,
            trailingContent = {
                when (connectionState) {
                    is TerminalConnectionState.Connected -> {
                        Button(
                            onClick = { activeSession.disconnect() },
                            colors = ButtonDefaults.buttonColors(
                                containerColor = BurntOrangeBadgeBg,
                                contentColor = BurntOrange
                            ),
                            shape = RoundedCornerShape(12.dp),
                            border = androidx.compose.foundation.BorderStroke(1.dp, BurntOrangeBorder),
                            contentPadding = PaddingValues(horizontal = 12.dp, vertical = 6.dp),
                            modifier = Modifier.testTag("disconnect_ssh_button")
                        ) {
                            Icon(Icons.Default.Stop, contentDescription = null, modifier = Modifier.size(16.dp))
                            Spacer(modifier = Modifier.width(4.dp))
                            Text("Disconnect", fontSize = 12.sp, fontWeight = FontWeight.Bold)
                        }
                    }
                    is TerminalConnectionState.Connecting -> {
                        CircularProgressIndicator(
                            color = BurntOrange,
                            strokeWidth = 2.dp,
                            modifier = Modifier.size(24.dp)
                        )
                    }
                    else -> {
                        Button(
                            onClick = {
                                if (serverConfig.isSshConfigured) {
                                    activeSession.connect(serverConfig)
                                } else {
                                    activeSession.startSandboxMode()
                                }
                            },
                            colors = ButtonDefaults.buttonColors(
                                containerColor = BurntOrange,
                                contentColor = NavyDarkBackground
                            ),
                            shape = RoundedCornerShape(12.dp),
                            contentPadding = PaddingValues(horizontal = 12.dp, vertical = 6.dp),
                            modifier = Modifier.testTag("connect_ssh_button")
                        ) {
                            Icon(Icons.Default.PlayArrow, contentDescription = null, modifier = Modifier.size(16.dp))
                            Spacer(modifier = Modifier.width(4.dp))
                            Text(
                                if (serverConfig.isSshConfigured) "Connect" else "Sandbox",
                                fontSize = 12.sp,
                                fontWeight = FontWeight.Bold
                            )
                        }
                    }
                }
            }
        )

        // Terminal Multi-Session Tabs Bar (Fitur 4)
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .background(NavyDarkSurface)
                .border(1.dp, NavyDarkBorder)
                .horizontalScroll(tabsScrollState)
                .padding(horizontal = 14.dp, vertical = 6.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            sessions.forEachIndexed { index, sess ->
                val isCurrent = index == activeSessionIndex
                val sessionTitle by sess.title.collectAsState()
                val sessionConnState by sess.connectionState.collectAsState()
                val dotColor = when (sessionConnState) {
                    is TerminalConnectionState.Connected -> TerminalGreen
                    is TerminalConnectionState.Connecting -> BurntOrange
                    is TerminalConnectionState.Error -> TerminalRed
                    else -> TextSecondary.copy(alpha = 0.5f)
                }

                Row(
                    modifier = Modifier
                        .clip(RoundedCornerShape(8.dp))
                        .background(if (isCurrent) BurntOrangeBadgeBg else NavyDarkBackground)
                        .border(
                            width = 1.dp,
                            color = if (isCurrent) BurntOrange else NavyDarkBorder,
                            shape = RoundedCornerShape(8.dp)
                        )
                        .clickable { terminalManager.selectSession(index) }
                        .padding(horizontal = 10.dp, vertical = 6.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    // Session Status Dot
                    Box(
                        modifier = Modifier
                            .size(7.dp)
                            .clip(CircleShape)
                            .background(dotColor)
                    )
                    Spacer(modifier = Modifier.width(6.dp))
                    Text(
                        text = "#${index + 1} $sessionTitle",
                        color = if (isCurrent) BurntOrange else TextPrimary,
                        fontWeight = if (isCurrent) FontWeight.Bold else FontWeight.Medium,
                        fontSize = 12.sp,
                        fontFamily = FontFamily.Monospace
                    )

                    // Close Session button (if more than 1 session)
                    if (sessions.size > 1) {
                        Spacer(modifier = Modifier.width(8.dp))
                        Box(
                            modifier = Modifier
                                .size(16.dp)
                                .clip(CircleShape)
                                .clickable {
                                    terminalManager.closeSession(index)
                                    Toast.makeText(context, "Sesi #${index + 1} ditutup", Toast.LENGTH_SHORT).show()
                                },
                            contentAlignment = Alignment.Center
                        ) {
                            Icon(
                                imageVector = Icons.Default.Close,
                                contentDescription = "Tutup Sesi",
                                tint = if (isCurrent) BurntOrange else TextSecondary,
                                modifier = Modifier.size(11.dp)
                            )
                        }
                    }
                }
            }

            // New Session Button (+)
            if (sessions.size < 5) {
                Row(
                    modifier = Modifier
                        .clip(RoundedCornerShape(8.dp))
                        .background(BurntOrangePillBg)
                        .border(1.dp, BurntOrangeBorder, RoundedCornerShape(8.dp))
                        .clickable {
                            terminalManager.createSession(serverConfig)
                            Toast.makeText(context, "Sesi terminal baru #${sessions.size + 1} dibuka", Toast.LENGTH_SHORT).show()
                        }
                        .padding(horizontal = 10.dp, vertical = 6.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Icon(
                        Icons.Default.Add,
                        contentDescription = "New Session",
                        tint = BurntOrange,
                        modifier = Modifier.size(14.dp)
                    )
                    Spacer(modifier = Modifier.width(4.dp))
                    Text(
                        "Sesi Baru",
                        color = BurntOrange,
                        fontSize = 11.sp,
                        fontWeight = FontWeight.Bold
                    )
                }
            }
        }

        // Status badge row
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 20.dp, vertical = 4.dp),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            val (statusText, isSuccess) = when {
                isInteractiveMode -> "NANO ACTIVE" to true
                connectionState is TerminalConnectionState.Connected -> "SSH CONNECTED" to true
                connectionState is TerminalConnectionState.Connecting -> "CONNECTING..." to false
                connectionState is TerminalConnectionState.Error -> "CONNECTION ERROR" to false
                else -> "STANDBY" to false
            }
            StatusBadge(text = statusText, isSuccess = isSuccess)

            if (isInteractiveMode) {
                Text(
                    text = "^O Simpan • ENTER OK • ^X Keluar",
                    color = BurntOrange,
                    fontSize = 11.sp,
                    fontFamily = FontFamily.Monospace,
                    fontWeight = FontWeight.Bold
                )
            } else if (!serverConfig.isSshConfigured && connectionState !is TerminalConnectionState.Connected) {
                Text(
                    text = "Tip: Tap 'Sandbox' to test CLI",
                    color = TextSecondary,
                    fontSize = 11.sp,
                    fontFamily = FontFamily.Monospace
                )
            }
        }

        Spacer(modifier = Modifier.height(2.dp))

        // Main Terminal Output Window (Termius Dark #0A0A0A canvas - Full-width edge-to-edge)
        Box(
            modifier = Modifier
                .weight(1f)
                .fillMaxWidth()
                .background(Color(0xFF0A0A0A))
        ) {
            Box(
                modifier = Modifier
                    .fillMaxSize()
                    .verticalScroll(verticalScrollState)
                    .padding(4.dp)
            ) {
                SelectionContainer {
                    Text(
                        text = terminalAnnotatedOutput,
                        fontFamily = FontFamily.Monospace,
                        fontSize = 13.sp,
                        lineHeight = 20.sp,
                        color = Color(0xFFA8D08D),
                        softWrap = true,
                        modifier = Modifier.fillMaxWidth()
                    )
                }
            }
        }

        Spacer(modifier = Modifier.height(4.dp))

        // Quick Action Keys & Snippets (Consolidated compact 36.dp single horizontal row)
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .horizontalScroll(shortcutScrollState)
                .padding(horizontal = 8.dp),
            horizontalArrangement = Arrangement.spacedBy(6.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            // Essential keyboard keys
            ShortcutKeyButton(label = "ESC", onClick = { activeSession.sendKeyEsc() })
            ShortcutKeyButton(label = "TAB", onClick = { activeSession.sendKeyTab() })
            ShortcutKeyButton(
                label = if (isCtrlActive) "CTRL [ON]" else "CTRL",
                isActive = isCtrlActive,
                onClick = {
                    isCtrlActive = !isCtrlActive
                    if (isCtrlActive) {
                        Toast.makeText(context, "CTRL aktif: Ketik huruf apa saja (misal 'o' atau 'x')", Toast.LENGTH_SHORT).show()
                    }
                }
            )
            ShortcutKeyButton(label = "^C", onClick = { activeSession.sendKeyCtrlC() })
            ShortcutKeyButton(
                label = "ENTER ↵",
                isActive = false,
                onClick = {
                    activeSession.sendKeyEnter()
                    Toast.makeText(context, "↵ Enter dikirim", Toast.LENGTH_SHORT).show()
                }
            )

            // Dedicated Nano Action Buttons
            if (isInteractiveMode) {
                ShortcutKeyButton(
                    label = "^O",
                    isActive = true,
                    onClick = {
                        activeSession.sendKeyCtrlO()
                        Toast.makeText(context, "Ctrl+O dikirim (Write Out / Save)", Toast.LENGTH_SHORT).show()
                    }
                )
                ShortcutKeyButton(
                    label = "^X",
                    isActive = true,
                    onClick = {
                        activeSession.sendKeyCtrlX()
                        Toast.makeText(context, "Ctrl+X dikirim (Exit)", Toast.LENGTH_SHORT).show()
                    }
                )
            }

            ShortcutKeyButton(label = "↑", onClick = { activeSession.sendKeyUp() })
            ShortcutKeyButton(label = "↓", onClick = { activeSession.sendKeyDown() })
            ShortcutKeyButton(label = "←", onClick = { activeSession.sendKeyLeft() })
            ShortcutKeyButton(label = "→", onClick = { activeSession.sendKeyRight() })
            ShortcutKeyButton(label = "CLEAR", onClick = { activeSession.clearBuffer() })

            // Snippet Buttons (Sys Info, df -h, etc.)
            for (snip in snippets) {
                Row(
                    modifier = Modifier
                        .height(36.dp)
                        .clip(RoundedCornerShape(8.dp))
                        .background(if (snip.isDefault) Color(0xFF1E2636) else BurntOrangeBadgeBg.copy(alpha = 0.5f))
                        .border(
                            width = 1.dp,
                            color = if (snip.isDefault) Color(0xFF2D394E) else BurntOrangeBorder,
                            shape = RoundedCornerShape(8.dp)
                        )
                        .combinedClickable(
                            onClick = {
                                activeSession.sendCommand(snip.command)
                                Toast.makeText(context, "Menjalankan: ${snip.name}", Toast.LENGTH_SHORT).show()
                            },
                            onLongClick = {
                                snippetToAction = snip
                            }
                        )
                        .padding(horizontal = 10.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    if (!snip.isDefault) {
                        Icon(
                            Icons.Default.FlashOn,
                            contentDescription = null,
                            tint = BurntOrange,
                            modifier = Modifier.size(12.dp)
                        )
                        Spacer(modifier = Modifier.width(4.dp))
                    }
                    Text(
                        text = snip.name,
                        color = if (snip.isDefault) TextPrimary else BurntOrange,
                        fontFamily = FontFamily.Monospace,
                        fontSize = 11.sp,
                        fontWeight = FontWeight.SemiBold
                    )
                }
            }

            // "+ Tambah" Action Button for Custom Snippet
            Row(
                modifier = Modifier
                    .height(36.dp)
                    .clip(RoundedCornerShape(8.dp))
                    .background(BurntOrangePillBg)
                    .border(1.dp, BurntOrangeBorder, RoundedCornerShape(8.dp))
                    .clickable {
                        newSnippetName = ""
                        newSnippetCommand = ""
                        showAddSnippetDialog = true
                    }
                    .padding(horizontal = 10.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                Icon(
                    Icons.Default.Add,
                    contentDescription = "Tambah Snippet",
                    tint = BurntOrange,
                    modifier = Modifier.size(13.dp)
                )
                Spacer(modifier = Modifier.width(4.dp))
                Text(
                    "Tambah",
                    color = BurntOrange,
                    fontSize = 11.sp,
                    fontWeight = FontWeight.Bold
                )
            }
        }

        Spacer(modifier = Modifier.height(6.dp))

        // Command Input Field and Circular Send Button
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 16.dp, vertical = 4.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            // The unified input container
            Row(
                modifier = Modifier
                    .weight(1f)
                    .clip(RoundedCornerShape(24.dp))
                    .background(NavyDarkCard)
                    .border(
                        1.dp,
                        if (isCtrlActive) BurntOrange else NavyCardBorder,
                        RoundedCornerShape(24.dp)
                    )
                    .padding(horizontal = 14.dp, vertical = 6.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text(
                    text = if (isCtrlActive) "CTRL+" else "$ ",
                    color = if (isCtrlActive) BurntOrange else TerminalGreen,
                    fontFamily = FontFamily.Monospace,
                    fontWeight = FontWeight.Bold,
                    fontSize = 14.sp
                )

                Spacer(modifier = Modifier.width(6.dp))

                BasicTextField(
                    value = inputCommand,
                    onValueChange = { newVal ->
                        if (isCtrlActive && newVal.length > inputCommand.length) {
                            val newChar = newVal.last()
                            if (newChar.isLetter()) {
                                activeSession.sendKeyCtrl(newChar)
                                Toast.makeText(context, "Ctrl+${newChar.uppercaseChar()} dikirim", Toast.LENGTH_SHORT).show()
                                isCtrlActive = false
                                return@BasicTextField
                            }
                        }
                        inputCommand = newVal
                    },
                    modifier = Modifier
                        .weight(1f)
                        .testTag("command_input_field"),
                    textStyle = androidx.compose.ui.text.TextStyle(
                        color = TextPrimary,
                        fontFamily = FontFamily.Monospace,
                        fontSize = 14.sp
                    ),
                    cursorBrush = SolidColor(BurntOrange),
                    maxLines = 5,
                    keyboardOptions = KeyboardOptions(imeAction = ImeAction.Send),
                    keyboardActions = KeyboardActions(
                        onSend = {
                            if (inputCommand.isNotBlank()) {
                                activeSession.sendCommand(inputCommand)
                                inputCommand = ""
                            } else {
                                activeSession.sendKeyEnter()
                            }
                        }
                    ),
                    decorationBox = { innerTextField ->
                        if (inputCommand.isEmpty()) {
                            Text(
                                text = if (isCtrlActive) "Ketik huruf untuk Ctrl+... (o=Simpan, x=Keluar)" else "Ketik perintah...",
                                color = if (isCtrlActive) BurntOrange.copy(alpha = 0.8f) else TextSecondary.copy(alpha = 0.55f),
                                fontFamily = FontFamily.Monospace,
                                fontSize = 13.sp
                            )
                        }
                        innerTextField()
                    }
                )

                // Clean inline Paste button if input is empty
                if (inputCommand.isEmpty()) {
                    IconButton(
                        onClick = {
                            val clipText = clipboardManager.getText()?.text
                            if (!clipText.isNullOrBlank()) {
                                inputCommand = clipText
                                Toast.makeText(context, "Ditempel dari clipboard", Toast.LENGTH_SHORT).show()
                            } else {
                                Toast.makeText(context, "Clipboard kosong", Toast.LENGTH_SHORT).show()
                            }
                        },
                        modifier = Modifier
                            .size(32.dp)
                            .testTag("terminal_paste_button")
                    ) {
                        Icon(
                            imageVector = Icons.Default.ContentPaste,
                            contentDescription = "Paste from clipboard",
                            tint = BurntOrange,
                            modifier = Modifier.size(18.dp)
                        )
                    }
                } else {
                    // Clean Clear button if text entered
                    IconButton(
                        onClick = { inputCommand = "" },
                        modifier = Modifier.size(32.dp)
                    ) {
                        Icon(
                            imageVector = Icons.Default.Close,
                            contentDescription = "Clear input",
                            tint = TextSecondary,
                            modifier = Modifier.size(18.dp)
                        )
                    }
                }
            }

            Spacer(modifier = Modifier.width(8.dp))

            // Standalone Circular Send / Enter Button (Always active!)
            IconButton(
                onClick = {
                    if (inputCommand.isNotBlank()) {
                        activeSession.sendCommand(inputCommand)
                        inputCommand = ""
                    } else {
                        activeSession.sendKeyEnter()
                        Toast.makeText(context, "↵ Enter", Toast.LENGTH_SHORT).show()
                    }
                },
                enabled = true,
                modifier = Modifier
                    .size(44.dp)
                    .clip(CircleShape)
                    .background(BurntOrange)
                    .testTag("send_command_button")
            ) {
                Icon(
                    imageVector = Icons.AutoMirrored.Filled.Send,
                    contentDescription = if (inputCommand.isNotBlank()) "Send" else "Enter",
                    tint = NavyDarkBackground,
                    modifier = Modifier.size(20.dp)
                )
            }
        }

        Spacer(modifier = Modifier.height(4.dp))
    }

    // Modal Dialog: Add Custom Snippet (Fitur 3)
    if (showAddSnippetDialog) {
        AlertDialog(
            onDismissRequest = { showAddSnippetDialog = false },
            containerColor = NavyDarkCard,
            titleContentColor = TextPrimary,
            title = {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Icon(Icons.Default.BookmarkBorder, contentDescription = null, tint = BurntOrange, modifier = Modifier.size(22.dp))
                    Spacer(modifier = Modifier.width(8.dp))
                    Text("Tambah Perintah Favorit", fontWeight = FontWeight.Bold, fontSize = 17.sp)
                }
            },
            text = {
                Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                    Text(
                        "Simpan perintah bash yang sering digunakan untuk eksekusi 1-klik di terminal.",
                        color = TextSecondary,
                        fontSize = 12.sp
                    )

                    OutlinedTextField(
                        value = newSnippetName,
                        onValueChange = { newSnippetName = it },
                        label = { Text("Label Tombol (contoh: Nginx Restart)") },
                        singleLine = true,
                        colors = OutlinedTextFieldDefaults.colors(
                            focusedBorderColor = BurntOrange,
                            unfocusedBorderColor = NavyDarkBorder,
                            focusedTextColor = TextPrimary,
                            unfocusedTextColor = TextPrimary
                        ),
                        modifier = Modifier.fillMaxWidth()
                    )

                    OutlinedTextField(
                        value = newSnippetCommand,
                        onValueChange = { newSnippetCommand = it },
                        label = { Text("Perintah Bash CLI") },
                        placeholder = { Text("sudo systemctl restart nginx") },
                        singleLine = false,
                        maxLines = 3,
                        colors = OutlinedTextFieldDefaults.colors(
                            focusedBorderColor = BurntOrange,
                            unfocusedBorderColor = NavyDarkBorder,
                            focusedTextColor = TextPrimary,
                            unfocusedTextColor = TextPrimary
                        ),
                        modifier = Modifier.fillMaxWidth()
                    )

                    // Quick Template Suggestions
                    Text("Template Populer:", color = TextSecondary, fontSize = 11.sp, fontWeight = FontWeight.Bold)
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .horizontalScroll(rememberScrollState()),
                        horizontalArrangement = Arrangement.spacedBy(6.dp)
                    ) {
                        val templates = listOf(
                            "Docker Restart" to "docker compose restart",
                            "Flush RAM" to "sudo sync && echo 3 | sudo tee /proc/sys/vm/drop_caches",
                            "Cek Port" to "sudo ss -tulpn",
                            "Ping Gateway" to "ping -c 4 1.1.1.1",
                            "Storage Detail" to "ncdu / -x || df -h"
                        )
                        for ((tLabel, tCmd) in templates) {
                            Box(
                                modifier = Modifier
                                    .clip(RoundedCornerShape(8.dp))
                                    .background(NavyDarkSurface)
                                    .border(1.dp, NavyDarkBorder, RoundedCornerShape(8.dp))
                                    .clickable {
                                        newSnippetName = tLabel
                                        newSnippetCommand = tCmd
                                    }
                                    .padding(horizontal = 8.dp, vertical = 4.dp)
                            ) {
                                Text(tLabel, color = BurntOrange, fontSize = 11.sp)
                            }
                        }
                    }
                }
            },
            confirmButton = {
                Button(
                    onClick = {
                        if (newSnippetName.isNotBlank() && newSnippetCommand.isNotBlank()) {
                            val newSnip = CustomSnippet(
                                name = newSnippetName.trim(),
                                command = newSnippetCommand.trim(),
                                category = newSnippetCategory,
                                isDefault = false
                            )
                            encryptedStorage?.addCustomSnippet(newSnip)
                            snippets = encryptedStorage?.loadCustomSnippets() ?: (snippets + newSnip)
                            showAddSnippetDialog = false
                            Toast.makeText(context, "Tombol pintasan '${newSnip.name}' disimpan", Toast.LENGTH_SHORT).show()
                        } else {
                            Toast.makeText(context, "Mohon lengkapi nama dan perintah", Toast.LENGTH_SHORT).show()
                        }
                    },
                    colors = ButtonDefaults.buttonColors(containerColor = BurntOrange)
                ) {
                    Text("Simpan", color = NavyDarkBackground, fontWeight = FontWeight.Bold)
                }
            },
            dismissButton = {
                TextButton(onClick = { showAddSnippetDialog = false }) {
                    Text("Batal", color = TextSecondary)
                }
            }
        )
    }

    // Modal Dialog: Snippet Actions (Long-press)
    val actionSnip = snippetToAction
    if (actionSnip != null) {
        AlertDialog(
            onDismissRequest = { snippetToAction = null },
            containerColor = NavyDarkCard,
            titleContentColor = TextPrimary,
            title = {
                Text(actionSnip.name, fontWeight = FontWeight.Bold)
            },
            text = {
                Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    Text("Perintah: ${actionSnip.command}", color = TerminalGreen, fontFamily = FontFamily.Monospace, fontSize = 12.sp)

                    Spacer(modifier = Modifier.height(4.dp))

                    Button(
                        onClick = {
                            activeSession.sendCommand(actionSnip.command)
                            snippetToAction = null
                            Toast.makeText(context, "Menjalankan ${actionSnip.name}", Toast.LENGTH_SHORT).show()
                        },
                        colors = ButtonDefaults.buttonColors(containerColor = BurntOrange),
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        Icon(Icons.Default.PlayArrow, contentDescription = null, tint = NavyDarkBackground, modifier = Modifier.size(16.dp))
                        Spacer(modifier = Modifier.width(6.dp))
                        Text("Jalankan Sekarang", color = NavyDarkBackground, fontWeight = FontWeight.Bold)
                    }

                    Button(
                        onClick = {
                            inputCommand = actionSnip.command
                            snippetToAction = null
                        },
                        colors = ButtonDefaults.buttonColors(containerColor = NavyDarkSurface),
                        border = androidx.compose.foundation.BorderStroke(1.dp, NavyDarkBorder),
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        Icon(Icons.Default.Edit, contentDescription = null, tint = TextPrimary, modifier = Modifier.size(16.dp))
                        Spacer(modifier = Modifier.width(6.dp))
                        Text("Salin ke Kolom Input", color = TextPrimary)
                    }

                    if (!actionSnip.isDefault) {
                        Button(
                            onClick = {
                                encryptedStorage?.deleteCustomSnippet(actionSnip.id)
                                snippets = encryptedStorage?.loadCustomSnippets() ?: snippets.filter { it.id != actionSnip.id }
                                snippetToAction = null
                                Toast.makeText(context, "Snippet dihapus", Toast.LENGTH_SHORT).show()
                            },
                            colors = ButtonDefaults.buttonColors(containerColor = TerminalRed.copy(alpha = 0.15f)),
                            border = androidx.compose.foundation.BorderStroke(1.dp, TerminalRed),
                            modifier = Modifier.fillMaxWidth()
                        ) {
                            Icon(Icons.Default.Delete, contentDescription = null, tint = TerminalRed, modifier = Modifier.size(16.dp))
                            Spacer(modifier = Modifier.width(6.dp))
                            Text("Hapus Pintasan", color = TerminalRed, fontWeight = FontWeight.Bold)
                        }
                    }
                }
            },
            confirmButton = {},
            dismissButton = {
                TextButton(onClick = { snippetToAction = null }) {
                    Text("Tutup", color = TextSecondary)
                }
            }
        )
    }
}
