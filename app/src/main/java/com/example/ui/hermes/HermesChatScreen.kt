package com.example.ui.hermes

import android.content.ClipData
import android.content.ClipboardManager
import android.content.Context
import android.widget.Toast
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
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
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.selection.SelectionContainer
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.Send
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.ContentCopy
import androidx.compose.material.icons.filled.ContentPaste
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.DeleteSweep
import androidx.compose.material.icons.filled.Dns
import androidx.compose.material.icons.filled.History
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material.icons.filled.SmartToy
import androidx.compose.material.icons.filled.Forum
import androidx.compose.material.icons.filled.Terminal
import androidx.compose.material3.Button
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.TextButton
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.Text
import androidx.compose.material3.rememberModalBottomSheetState
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material.icons.filled.MoreVert
import androidx.compose.material.icons.filled.ExpandMore
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.input.key.onPreviewKeyEvent
import androidx.compose.ui.input.key.Key
import androidx.compose.ui.input.key.KeyEventType
import androidx.compose.ui.input.key.key
import androidx.compose.ui.input.key.type
import androidx.compose.ui.input.key.isShiftPressed
import com.example.data.preferences.AppPreferences
import androidx.compose.runtime.Composable
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.tween
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateListOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.runtime.snapshots.SnapshotStateList
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalSoftwareKeyboardController
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.data.hermes.ChatMessage
import com.example.data.hermes.HermesApiClient
import com.example.data.hermes.HermesChatSession
import com.example.data.model.ConnectionProfile
import com.example.data.model.ServerConfig
import com.example.data.security.EncryptedStorage
import com.example.ui.components.CustomScreenHeader
import com.example.ui.components.PillChip
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
import com.example.ui.theme.UserMessageSurface
import kotlinx.coroutines.launch
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale
import com.example.ui.i18n.tr
import java.util.UUID

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun HermesChatScreen(
    hermesClient: HermesApiClient,
    serverConfig: ServerConfig,
    sshTerminalManager: com.example.data.ssh.SshTerminalManager? = null,
    encryptedStorage: EncryptedStorage? = null,
    onConfigUpdated: ((ServerConfig) -> Unit)? = null,
    onExecuteInTerminal: ((String) -> Unit)? = null,
    modifier: Modifier = Modifier,
    messages: SnapshotStateList<ChatMessage> = remember { mutableStateListOf() },
    preferences: AppPreferences = AppPreferences(),
    onOpenSettings: () -> Unit = {},
    onOpenConnectionSettings: () -> Unit = onOpenSettings,
    onPreferencesUpdated: (AppPreferences) -> Unit = {}
) {
    val context = LocalContext.current
    val keyboardController = LocalSoftwareKeyboardController.current
    val scope = rememberCoroutineScope()
    var pendingCommand by remember { mutableStateOf<Pair<String, Boolean>?>(null) }
    val listState = rememberLazyListState()

    var inputText by androidx.compose.runtime.saveable.rememberSaveable { mutableStateOf("") }
    var isSending by remember { mutableStateOf(false) }
    var isRunningServerCommand by remember { mutableStateOf(false) }
    var sessionLoaded by remember { mutableStateOf(false) }
    var modelPicker by remember { mutableStateOf(false) }
    var modelQuery by remember { mutableStateOf("") }
    var confirmClear by remember { mutableStateOf(false) }
    var commandMenu by remember { mutableStateOf(false) }
    LaunchedEffect(modelPicker) { if (modelPicker) keyboardController?.hide() }

    var currentSessionId by remember {
        mutableStateOf(encryptedStorage?.getActiveChatSessionId() ?: UUID.randomUUID().toString())
    }
    var currentSessionTitle by remember { mutableStateOf("Obrolan Baru") }
    var showHistorySheet by remember { mutableStateOf(false) }
    var savedSessions by remember { mutableStateOf<List<HermesChatSession>>(emptyList()) }

    fun refreshSavedSessions() {
        if (encryptedStorage != null) {
            savedSessions = encryptedStorage.loadChatSessions()
        }
    }

    fun autoSaveCurrentSession() {
        if (encryptedStorage == null) return
        val firstUserMsg = messages.firstOrNull { it.role == "user" }?.content?.trim()
        val derivedTitle = when {
            firstUserMsg != null -> {
                val clean = firstUserMsg.lines().firstOrNull() ?: firstUserMsg
                if (clean.length > 28) clean.take(28) + "..." else clean
            }
            currentSessionTitle.isNotBlank() && currentSessionTitle != "Obrolan Baru" -> currentSessionTitle
            else -> "Obrolan Baru"
        }
        currentSessionTitle = derivedTitle
        val session = HermesChatSession(
            id = currentSessionId,
            title = derivedTitle,
            updatedAt = System.currentTimeMillis(),
            messages = messages.toList()
        )
        encryptedStorage.saveChatSession(session)
        refreshSavedSessions()
    }

    fun handleRunOnServer(command: String) {
        if (isRunningServerCommand) return
        isRunningServerCommand = true
        scope.launch {
            Toast.makeText(context, "Mengeksekusi perintah di server via SSH...", Toast.LENGTH_SHORT).show()
            val result = sshTerminalManager?.executeCommand(serverConfig, command)
                ?: Result.failure(Exception("SSH Manager belum tersedia"))

            isRunningServerCommand = false
            result.onSuccess { output ->
                messages.add(
                    ChatMessage(
                        role = "assistant",
                        content = "**Hasil dari server**\n\nPerintah: `$command`\n\n```text\n$output\n```",
                        isServerOutput = true
                    )
                )
                autoSaveCurrentSession()
                listState.animateScrollToItem(messages.lastIndex)
                Toast.makeText(context, "Selesai dieksekusi di server", Toast.LENGTH_SHORT).show()
            }.onFailure { err ->
                messages.add(
                    ChatMessage(
                        role = "assistant",
                        content = "Gagal mengeksekusi di server:\n${err.localizedMessage ?: "Unknown error"}",
                        isError = true,
                        isServerOutput = true
                    )
                )
                autoSaveCurrentSession()
                listState.animateScrollToItem(messages.lastIndex)
            }
        }
    }

    fun startNewChatSession(discardCurrent: Boolean = false) {
        if (!discardCurrent && currentSessionTitle == "Obrolan Baru" && messages.none { it.role == "user" }) {
            inputText = ""
            commandMenu = false
            pendingCommand = null
            showHistorySheet = false
            return
        }
        if (!discardCurrent) autoSaveCurrentSession()
        val newSession = HermesChatSession(
            title = "Obrolan Baru",
            messages = listOf(
                ChatMessage(
                    role = "assistant",
                    content = "Halo! Saya Hermes Agent di homeserver Anda. Ada yang bisa saya bantu untuk administrasi server atau automasi hari ini?"
                )
            )
        )
        encryptedStorage?.saveChatSession(newSession)
        currentSessionId = newSession.id
        currentSessionTitle = newSession.title
        inputText = ""
        commandMenu = false
        pendingCommand = null
        messages.clear()
        messages.addAll(newSession.messages)
        refreshSavedSessions()
        Toast.makeText(context, "Memulai obrolan baru", Toast.LENGTH_SHORT).show()
    }

    fun switchSession(session: HermesChatSession) {
        autoSaveCurrentSession()
        currentSessionId = session.id
        currentSessionTitle = session.title
        encryptedStorage?.setActiveChatSessionId(session.id)
        messages.clear()
        messages.addAll(session.messages)
        showHistorySheet = false
        Toast.makeText(context, "Melanjutkan: ${session.title}", Toast.LENGTH_SHORT).show()
    }

    // Initial load from persistent storage
    LaunchedEffect(Unit) {
        if (encryptedStorage != null) {
            val session = encryptedStorage.getActiveOrLatestChatSession()
            currentSessionId = session.id
            currentSessionTitle = session.title
            if (messages.isEmpty()) {
                messages.addAll(session.messages)
            }
            refreshSavedSessions()
        } else if (messages.isEmpty()) {
            messages.add(
                ChatMessage(
                    role = "assistant",
                    content = "Halo! Saya Hermes Agent di homeserver Anda. Ada yang bisa saya bantu untuk administrasi server atau automasi hari ini?"
                )
            )
        }
        sessionLoaded = true
    }

    val slashCommands = remember {
        listOf(
            "/model" to "Deteksi atau ganti model",
            "/models" to "Alias untuk /model",
            "/history" to "Riwayat percakapan",
            "/sesi" to "Alias untuk /history",
            "/status" to "Status koneksi dan model",
            "/effort" to "Atur effort: low, medium, high, atau default",
            "/profile" to "Pilih profil jaringan",
            "/profiles" to "Alias untuk /profile",
            "/help" to "Daftar perintah",
            "/start" to "Alias untuk /help",
            "/clear" to "Bersihkan obrolan",
            "/reset" to "Alias untuk /clear"
        )
    }

    // Scroll to bottom when messages list updates
    LaunchedEffect(messages.size) {
        if (messages.isNotEmpty()) {
            val lastVisible = listState.layoutInfo.visibleItemsInfo.lastOrNull()?.index ?: messages.lastIndex
            if (lastVisible >= messages.lastIndex - 2 || messages.last().role == "user") listState.animateScrollToItem(messages.lastIndex)
        }
    }
    LaunchedEffect(currentSessionId) { if (messages.isNotEmpty()) listState.scrollToItem(messages.lastIndex) }

    fun copyToClipboard(text: String) {
        val clipboard = context.getSystemService(Context.CLIPBOARD_SERVICE) as ClipboardManager
        val clip = ClipData.newPlainText("Hermes Response", text)
        clipboard.setPrimaryClip(clip)
        Toast.makeText(context, "Disalin ke clipboard", Toast.LENGTH_SHORT).show()
    }

    fun handleInteractiveAction(actionType: String?, actionValue: String) {
        when (actionType) {
            "select_model" -> {
                val updated = serverConfig.copy(hermesModel = actionValue)
                encryptedStorage?.saveConfig(updated)
                encryptedStorage?.getActiveProfileId()?.let { pId ->
                    val profiles = encryptedStorage.loadProfiles()
                    val currentP = profiles.find { it.id == pId }
                    if (currentP != null) {
                        encryptedStorage.saveProfile(currentP.copy(config = updated))
                    }
                }
                onConfigUpdated?.invoke(updated)
                Toast.makeText(context, "Model aktif: $actionValue", Toast.LENGTH_SHORT).show()
            }
            "select_profile" -> {
                val profiles = encryptedStorage?.loadProfiles() ?: emptyList()
                val target = profiles.find { it.name == actionValue }
                if (target != null) {
                    encryptedStorage?.setActiveProfileId(target.id)
                    onConfigUpdated?.invoke(target.config)
                    messages.add(ChatMessage(
                        role = "assistant",
                        content = "✅ Berhasil beralih ke profil: *${target.name}*\n• Host SSH: `${target.config.sshHost}`\n• Hermes URL: `${target.config.hermesBaseUrl}`\n• Model AI: *${target.config.hermesModel}*"
                    ))
                    autoSaveCurrentSession()
                }
            }
        }
    }

    fun doSendMessage(prompt: String) {
        if (prompt.isBlank() || isSending || isRunningServerCommand || !sessionLoaded) return
        val rawPrompt = prompt.trim()
        val modelCommand = rawPrompt.substringBefore(' ').lowercase()
        if (modelCommand == "/model" || modelCommand == "/models") {
            val modelName = rawPrompt.substringAfter(' ', "").trim()
            inputText = ""
            modelQuery = modelName
            modelPicker = true
            return
        }
        val userMsg = ChatMessage(role = "user", content = rawPrompt)
        messages.add(userMsg)
        autoSaveCurrentSession()
        inputText = ""

        // Process Telegram-like Slash Commands
        if (rawPrompt.startsWith("/")) {
            val parts = rawPrompt.split("\\s+".toRegex())
            val cmd = parts.getOrNull(0)?.lowercase() ?: ""

            when (cmd) {
                "/history", "/sesi" -> {
                    refreshSavedSessions()
                    showHistorySheet = true
                    messages.add(ChatMessage(
                        role = "assistant",
                        content = "📜 Membuka riwayat obrolan Hermes (${savedSessions.size} sesi tersimpan). Anda dapat memilih percakapan sebelumnya untuk dilanjutkan."
                    ))
                    autoSaveCurrentSession()
                    return
                }

                "/status" -> {
                    val activeProfileName = encryptedStorage?.loadProfiles()?.find { it.id == encryptedStorage.getActiveProfileId() }?.name ?: "Default"
                    val isConfigured = serverConfig.isHermesConfigured
                    messages.add(ChatMessage(
                        role = "assistant",
                        content = """
                            ⚡ *Status Hermes Agent & Jaringan*
                            • Profil Aktif: *$activeProfileName*
                            • SSH Host: `${serverConfig.sshHost}:${serverConfig.sshPort}`
                            • Base URL: `${serverConfig.hermesBaseUrl}`
                            • Model AI: *${serverConfig.hermesModel.ifBlank { "Belum diset" }}*
                            • Effort: *${preferences.chatEffort.ifBlank { "Default" }}*
                            • Konfigurasi: ${if (isConfigured) "Endpoint dan model tersimpan" else "Belum lengkap"}
                            • API Key: ${if (serverConfig.hermesApiKey.isNotBlank()) "Tersedia (Bearer Token)" else "Tanpa Key (Lokal)"}
                        """.trimIndent()
                    ))
                    autoSaveCurrentSession()
                    return
                }

                "/effort" -> {
                    val requested = parts.getOrNull(1)?.lowercase().orEmpty()
                    val normalized = when (requested) {
                        "default", "auto", "off", "reset" -> ""
                        "low", "medium", "high" -> requested
                        else -> null
                    }
                    if (normalized == null) {
                        messages.add(ChatMessage(role = "assistant", content = "Effort saat ini: **${preferences.chatEffort.ifBlank { "Default" }}**. Gunakan `/effort low`, `/effort medium`, `/effort high`, atau `/effort default`."))
                    } else {
                        onPreferencesUpdated(preferences.copy(chatEffort = normalized))
                        messages.add(ChatMessage(role = "assistant", content = "Effort chat diatur ke **${normalized.ifBlank { "Default" }}**."))
                    }
                    autoSaveCurrentSession()
                    return
                }

                "/profile", "/profiles" -> {
                    val profiles = encryptedStorage?.loadProfiles() ?: emptyList()
                    val activeId = encryptedStorage?.getActiveProfileId()
                    val activeName = profiles.find { it.id == activeId }?.name ?: "Default"
                    messages.add(ChatMessage(
                        role = "assistant",
                        content = "🌐 *Profil Koneksi:* Saat ini aktif di *$activeName*.\n\nKetuk profil di bawah untuk beralih jaringan:",
                        interactiveActions = profiles.map { it.name },
                        actionType = "select_profile"
                    ))
                    autoSaveCurrentSession()
                    return
                }

                "/clear", "/reset" -> {
                    messages.clear()
                    messages.add(ChatMessage(
                        role = "assistant",
                        content = "Riwayat chat telah dibersihkan. Siap menerima instruksi baru."
                    ))
                    autoSaveCurrentSession()
                    return
                }

                "/help", "/start" -> {
                    messages.add(ChatMessage(
                        role = "assistant",
                        content = """
                            📋 *Perintah Telegram Hermes:*
                            • `/model` - Deteksi otomatis & pilih model di 9router/server
                            • `/model [nama]` - Cari dan pilih model dari server
                            • `/history` - Buka riwayat & sesi percakapan
                            • `/status` - Cek koneksi, profil, & model aktif
                            • `/effort low|medium|high` - Atur reasoning effort, `/effort default` untuk mengikuti model
                            • `/profile` - Ganti profil (Rumah / Tailscale)
                            • `/clear` - Bersihkan obrolan saat ini
                            • `/help` - Tampilkan bantuan ini
                        """.trimIndent()
                    ))
                    autoSaveCurrentSession()
                    return
                }
            }
        }

        // Standard LLM Message
        isSending = true
        scope.launch {
            val result = hermesClient.sendMessage(
                userMessage = userMsg.content,
                history = messages.dropLast(1),
                config = serverConfig,
                useSandboxFallback = !serverConfig.isHermesConfigured,
                responseInstructions = preferences.responseInstructions(),
                reasoningEffort = preferences.chatEffort.ifBlank { null }
            )
            isSending = false
            result.onSuccess { reply ->
                messages.add(ChatMessage(role = "assistant", content = reply))
                autoSaveCurrentSession()
            }.onFailure { err ->
                messages.add(
                    ChatMessage(
                        role = "assistant",
                        content = "Gagal menghubungi Hermes Agent:\n${err.localizedMessage ?: "Unknown error"}\n\nPeriksa IP Tailscale dan Model di tab Settings atau gunakan /model.",
                        isError = true
                    )
                )
                autoSaveCurrentSession()
            }
        }
    }

    pendingCommand?.let { (command, interactive) ->
        val activeTerminal = sshTerminalManager?.activeSession?.value
        val targetConfig = if (interactive) activeTerminal?.lastConfig ?: serverConfig else serverConfig
        val simulated = !targetConfig.isSshConfigured || (interactive && activeTerminal?.connectionState?.value is com.example.data.ssh.TerminalConnectionState.Connected && activeTerminal.lastConfig == null)
        AlertDialog(
            onDismissRequest = { pendingCommand = null },
            title = { Text(if (interactive) tr("Jalankan perintah di terminal?", "Run this command in the terminal?") else tr("Jalankan perintah di server?", "Run this command on the server?")) },
            text = { Column {
                Text(if (simulated) tr("Tujuan: simulasi lokal", "Destination: local simulation") else tr("Tujuan:", "Destination:") + " ${targetConfig.sshUser}@${targetConfig.sshHost}:${targetConfig.sshPort}")
                Text(command, fontFamily = FontFamily.Monospace, modifier = Modifier.padding(top = 12.dp))
            } },
            confirmButton = { TextButton(onClick = {
                pendingCommand = null
                if (interactive) onExecuteInTerminal?.invoke(command) else handleRunOnServer(command)
            }) { Text(tr("Jalankan", "Run")) } },
            dismissButton = { TextButton(onClick = { pendingCommand = null }) { Text(tr("Batal", "Cancel")) } }
        )
    }
    Column(
        modifier = modifier
            .fillMaxSize()
            .background(NavyDarkBackground)
    ) {
        val busy = isSending || isRunningServerCommand || !sessionLoaded
        HermesChatHeader(currentSessionTitle, serverConfig.hermesModel, !busy,
            onModel = { modelQuery = ""; modelPicker = true },
            onNew = { startNewChatSession() },
            onHistory = { refreshSavedSessions(); showHistorySheet = true },
            onClear = { confirmClear = true })

        if (commandMenu || inputText.startsWith("/")) Row(
            modifier = Modifier
                .fillMaxWidth()
                .horizontalScroll(rememberScrollState())
                .padding(horizontal = 14.dp, vertical = 4.dp),
            horizontalArrangement = Arrangement.spacedBy(6.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            slashCommands.filter { (cmd, _) -> cmd !in listOf("/models", "/sesi", "/profiles", "/start", "/reset") && (inputText == "/" || !inputText.startsWith("/") || cmd.startsWith(inputText.substringBefore(' '))) }.forEach { (cmd, desc) ->
                Row(
                    modifier = Modifier
                        .clip(RoundedCornerShape(12.dp))
                        .background(NavyDarkSurface)
                        .border(1.dp, NavyDarkBorder, RoundedCornerShape(12.dp))
                        .clickable(enabled = !busy, role = Role.Button) { commandMenu = false; doSendMessage(cmd) }
                        .heightIn(min = 48.dp).padding(horizontal = 12.dp, vertical = 10.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Text(
                        text = cmd,
                        color = BurntOrange,
                        fontWeight = FontWeight.Bold,
                        fontFamily = FontFamily.Monospace,
                        fontSize = 12.sp
                    )
                    Spacer(modifier = Modifier.width(4.dp))
                    Text(
                        text = desc,
                        color = TextSecondary,
                        fontSize = 11.sp
                    )
                }
            }
        }

        Spacer(modifier = Modifier.height(2.dp))

        // Message List Area
        LazyColumn(
            state = listState,
            modifier = Modifier
                .weight(1f)
                .fillMaxWidth(),
            contentPadding = PaddingValues(horizontal = 16.dp, vertical = 20.dp),
            verticalArrangement = Arrangement.spacedBy(28.dp)
        ) {
            items(messages, key = { it.id }, contentType = { it.role }) { msg ->
                ChatMessageItem(
                    message = msg,
                    currentModel = serverConfig.hermesModel,
                    fontSize = preferences.chatFontSize,
                    showTime = preferences.chatTimestamps,
                    onActionClick = { actionType, value ->
                        if (!busy) {
                            if (actionType == "select_model") { modelQuery = value; modelPicker = true }
                            else handleInteractiveAction(actionType, value)
                        }
                    },
                    onRunOnServer = if (!busy) ({ cmd -> pendingCommand = cmd to false }) else null,
                    onExecuteInTerminal = if (!busy && onExecuteInTerminal != null) ({ cmd -> pendingCommand = cmd to true }) else null
                )
            }

            if (isSending) {
                item(key = "thinking", contentType = "thinking") {
                    HermesThinkingIndicator()
                }
            }
        }

        val olderMessages by remember { androidx.compose.runtime.derivedStateOf {
            messages.isNotEmpty() && (listState.layoutInfo.visibleItemsInfo.lastOrNull()?.index ?: 0) < messages.lastIndex - 1
        } }
        if (olderMessages) TextButton(onClick = { scope.launch { listState.animateScrollToItem(messages.lastIndex) } }, modifier = Modifier.fillMaxWidth()) { Text(tr("Pesan terbaru", "Latest messages"), color = BurntOrange) }
        Text(tr("Pesan untuk Hermes", "Message Hermes"), color = TextSecondary, fontSize = 11.sp, modifier = Modifier.padding(start = 20.dp, top = 2.dp))
        if (isRunningServerCommand) Text(tr("Perintah sedang berjalan…", "Command running…"), color = TextSecondary, fontSize = 12.sp, modifier = Modifier.padding(horizontal = 20.dp, vertical = 4.dp))
        // Keep editing available while a reply is loading; sending waits for completion.
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 12.dp, vertical = 4.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            // Pill Container
            Row(
                modifier = Modifier
                    .weight(1f)
                    .clip(RoundedCornerShape(24.dp))
                    .background(NavyDarkCard)
                    .border(1.dp, MaterialTheme.colorScheme.outline, RoundedCornerShape(24.dp))
                    .padding(horizontal = 4.dp, vertical = 2.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                // Slash quick toggle button
                Box(
                    modifier = Modifier
                        .size(44.dp)
                        .clip(CircleShape)
                        .clickable(role = Role.Button) {
                            commandMenu = !commandMenu
                        }.semantics { contentDescription = "Perintah chat" },
                    contentAlignment = Alignment.Center
                ) {
                    Text(
                        text = "/",
                        color = BurntOrange,
                        fontWeight = FontWeight.Bold,
                        fontFamily = FontFamily.Monospace,
                        fontSize = 15.sp
                    )
                }

                BasicTextField(
                    value = inputText,
                    onValueChange = { inputText = it },
                    modifier = Modifier
                        .weight(1f)
                        .padding(horizontal = 6.dp, vertical = 8.dp)
                        .testTag("hermes_chat_input")
                        .semantics { contentDescription = "Pesan untuk Hermes" }
                        .onPreviewKeyEvent { event ->
                            if (preferences.chatEnterSends && event.key == Key.Enter && event.type == KeyEventType.KeyDown && !event.isShiftPressed) {
                                if (!busy) doSendMessage(inputText)
                                true
                            } else false
                        },
                    keyboardOptions = KeyboardOptions(imeAction = if (preferences.chatEnterSends) ImeAction.Send else ImeAction.Default),
                    keyboardActions = KeyboardActions(onSend = { if (!busy) doSendMessage(inputText) }),
                    textStyle = MaterialTheme.typography.bodyMedium.copy(
                        color = TextPrimary,
                        fontSize = preferences.chatFontSize.sp
                    ),
                    cursorBrush = SolidColor(BurntOrange),
                    maxLines = 4,
                    decorationBox = { innerTextField ->
                        if (inputText.isEmpty()) {
                            Text(
                                text = tr("Tulis pesan…", "Write a message…"),
                                color = TextSecondary,
                                fontSize = 13.sp
                            )
                        }
                        innerTextField()
                    }
                )

            IconButton(
                onClick = {
                    if (!isSending) {
                        if (inputText.isNotBlank()) {
                            doSendMessage(inputText)
                        } else {
                            Toast.makeText(context, "Ketik pesan untuk Hermes", Toast.LENGTH_SHORT).show()
                        }
                    }
                },
                enabled = inputText.isNotBlank() && !busy,
                modifier = Modifier
                    .size(44.dp)
                    .clip(CircleShape)
                    .background(if (inputText.isNotBlank() && !busy) BurntOrange else NavyDarkSurface)
                    .testTag("hermes_send_button")
            ) {
                if (isSending) {
                    CircularProgressIndicator(
                        modifier = Modifier.size(20.dp),
                        color = BurntOrange,
                        strokeWidth = 2.5.dp
                    )
                } else {
                    Icon(
                        imageVector = Icons.AutoMirrored.Filled.Send,
                        contentDescription = "Send",
                        tint = if (inputText.isNotBlank() && !busy) MaterialTheme.colorScheme.onPrimary else TextSecondary,
                        modifier = Modifier.size(20.dp)
                    )
                }
            }
            }
        }

        Spacer(modifier = Modifier.height(6.dp))
    }

    if (modelPicker) ModelPickerSheet(hermesClient, serverConfig,
        onSelect = { handleInteractiveAction("select_model", it); modelPicker = false },
        onDismiss = { modelPicker = false }, onOpenSettings = { modelPicker = false; onOpenConnectionSettings() }, initialQuery = modelQuery)
    if (confirmClear) AlertDialog(onDismissRequest = { confirmClear = false }, title = { Text(tr("Bersihkan obrolan ini?", "Clear this chat?")) },
        text = { Text(tr("Pesan pada percakapan ini akan dihapus. Percakapan lain tetap tersimpan.", "Messages in this conversation will be removed. Other conversations are kept.")) },
        confirmButton = { TextButton(onClick = { messages.clear(); autoSaveCurrentSession(); confirmClear = false }) { Text(tr("Bersihkan", "Clear")) } },
        dismissButton = { TextButton(onClick = { confirmClear = false }) { Text(tr("Batal", "Cancel")) } })

    // Modal Bottom Sheet: History & Saved Chat Sessions
    if (showHistorySheet) {
        ModalBottomSheet(
            onDismissRequest = { showHistorySheet = false },
            sheetState = rememberModalBottomSheetState(),
            containerColor = NavyDarkBackground,
            dragHandle = {
                Box(
                    modifier = Modifier
                        .padding(vertical = 10.dp)
                        .width(40.dp)
                        .height(4.dp)
                        .clip(RoundedCornerShape(2.dp))
                        .background(NavyDarkBorder)
                )
            }
        ) {
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 18.dp, vertical = 8.dp)
                    .navigationBarsPadding()
            ) {
                // Header
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Icon(
                            Icons.Default.History,
                            contentDescription = null,
                            tint = BurntOrange,
                            modifier = Modifier.size(22.dp)
                        )
                        Spacer(modifier = Modifier.width(8.dp))
                        Text(
                            text = tr("Riwayat obrolan Hermes", "Hermes chat history"),
                            color = TextPrimary,
                            fontSize = 17.sp,
                            fontWeight = FontWeight.Bold
                        )
                    }
                    IconButton(
                        onClick = { showHistorySheet = false },
                        modifier = Modifier.size(32.dp)
                    ) {
                        Icon(
                            Icons.Default.Close,
                            contentDescription = "Close",
                            tint = TextSecondary,
                            modifier = Modifier.size(18.dp)
                        )
                    }
                }

                Text(
                    text = tr("Lanjutkan percakapan sebelumnya atau mulai obrolan baru.", "Continue a conversation or start a new chat."),
                    color = TextSecondary,
                    fontSize = 12.sp,
                    modifier = Modifier.padding(top = 2.dp, bottom = 14.dp)
                )

                // Start New Chat Button
                Button(
                    onClick = {
                        showHistorySheet = false
                                                    startNewChatSession(discardCurrent = true)
                    },
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(44.dp)
                        .testTag("start_new_chat_button"),
                    shape = RoundedCornerShape(12.dp),
                    colors = ButtonDefaults.buttonColors(containerColor = BurntOrange)
                ) {
                    Icon(
                        Icons.Default.Add,
                        contentDescription = null,
                        tint = MaterialTheme.colorScheme.onPrimary,
                        modifier = Modifier.size(18.dp)
                    )
                    Spacer(modifier = Modifier.width(8.dp))
                    Text(
                        text = tr("Mulai obrolan baru", "Start new chat"),
                        color = MaterialTheme.colorScheme.onPrimary,
                        fontWeight = FontWeight.Bold,
                        fontSize = 13.sp
                    )
                }

                Spacer(modifier = Modifier.height(14.dp))

                // Saved Sessions List
                if (savedSessions.isEmpty()) {
                    Box(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(vertical = 36.dp),
                        contentAlignment = Alignment.Center
                    ) {
                        Text(
                            text = tr("Belum ada riwayat percakapan tersimpan", "No saved conversations yet"),
                            color = TextSecondary,
                            fontSize = 13.sp
                        )
                    }
                } else {
                    Text(
                        text = "PERCAKAPAN TERSIMPAN (${savedSessions.size})",
                        color = TextSecondary,
                        fontSize = 11.sp,
                        fontWeight = FontWeight.Bold,
                        fontFamily = FontFamily.Monospace,
                        modifier = Modifier.padding(bottom = 8.dp)
                    )

                    LazyColumn(
                        modifier = Modifier
                            .fillMaxWidth()
                            .heightIn(max = 380.dp),
                        verticalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        items(savedSessions, key = { it.id }) { session ->
                            val isCurrent = session.id == currentSessionId
                            val dateStr = remember(session.updatedAt) {
                                val sdf = SimpleDateFormat("dd MMM, HH:mm", Locale.getDefault())
                                sdf.format(Date(session.updatedAt))
                            }
                            val messageCount = session.messages.size

                            Row(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .clip(RoundedCornerShape(12.dp))
                                    .background(if (isCurrent) BurntOrangeBadgeBg else NavyDarkCard)
                                    .border(
                                        width = 1.dp,
                                        color = if (isCurrent) BurntOrange else NavyDarkBorder,
                                        shape = RoundedCornerShape(12.dp)
                                    )
                                    .clickable { switchSession(session) }
                                    .testTag("chat_session_${session.id}")
                                    .padding(horizontal = 12.dp, vertical = 10.dp),
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                Box(
                                    modifier = Modifier
                                        .size(34.dp)
                                        .clip(CircleShape)
                                        .background(if (isCurrent) BurntOrange else NavyDarkSurface),
                                    contentAlignment = Alignment.Center
                                ) {
                                    Icon(
                                        Icons.Default.SmartToy,
                                        contentDescription = null,
                                        tint = if (isCurrent) NavyDarkBackground else BurntOrange,
                                        modifier = Modifier.size(18.dp)
                                    )
                                }

                                Spacer(modifier = Modifier.width(10.dp))

                                Column(modifier = Modifier.weight(1f)) {
                                    Row(verticalAlignment = Alignment.CenterVertically) {
                                        Text(
                                            text = session.title,
                                            color = TextPrimary,
                                            fontWeight = FontWeight.SemiBold,
                                            fontSize = 13.sp,
                                            maxLines = 1,
                                            modifier = Modifier.weight(1f, fill = false)
                                        )
                                        if (isCurrent) {
                                            Spacer(modifier = Modifier.width(6.dp))
                                            Text(
                                                text = "AKTIF",
                                                color = BurntOrange,
                                                fontWeight = FontWeight.Bold,
                                                fontSize = 9.sp
                                            )
                                        }
                                    }
                                    Spacer(modifier = Modifier.height(2.dp))
                                    Text(
                                        text = "$dateStr • $messageCount pesan",
                                        color = TextSecondary,
                                        fontSize = 11.sp
                                    )
                                }

                                // Delete Session button
                                IconButton(
                                    onClick = {
                                        encryptedStorage?.deleteChatSession(session.id)
                                        refreshSavedSessions()
                                        if (isCurrent) {
                                            startNewChatSession()
                                        }
                                    },
                                    modifier = Modifier.size(44.dp).testTag("delete_chat_${session.id}")
                                ) {
                                    Icon(
                                        Icons.Default.Delete,
                                        contentDescription = "Hapus Sesi",
                                        tint = TextSecondary.copy(alpha = 0.6f),
                                        modifier = Modifier.size(16.dp)
                                    )
                                }
                            }
                        }
                    }
                }

                Spacer(modifier = Modifier.height(16.dp))
            }
        }
    }
}

@Composable
private fun HermesThinkingIndicator() {
    Row(Modifier.fillMaxWidth().padding(vertical = 4.dp), verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(8.dp)) {
        CircularProgressIndicator(Modifier.size(16.dp), strokeWidth = 2.dp, color = BurntOrange)
        Text(tr("Menunggu jawaban Hermes…", "Waiting for Hermes…"), color = TextSecondary, fontSize = 12.sp)
    }
}

@Composable
fun ChatMessageItem(
    message: ChatMessage,
    currentModel: String = "",
    onActionClick: (String?, String) -> Unit = { _, _ -> },
    onRunOnServer: ((String) -> Unit)? = null,
    onExecuteInTerminal: ((String) -> Unit)? = null,
    fontSize: Int = 15,
    showTime: Boolean = false
) {
    val isUser = message.role == "user"
    val bubbleShape = RoundedCornerShape(
        topStart = 22.dp,
        topEnd = 22.dp,
        bottomStart = if (isUser) 22.dp else 7.dp,
        bottomEnd = if (isUser) 7.dp else 22.dp
    )

    Column(
        modifier = Modifier.fillMaxWidth().testTag("chat_message_${message.id}"),
        horizontalAlignment = if (isUser) Alignment.End else Alignment.Start
    ) {
        Row(
            modifier = Modifier.fillMaxWidth(),
            verticalAlignment = Alignment.Bottom,
            horizontalArrangement = if (isUser) Arrangement.End else Arrangement.Start
        ) {
            Box(
                modifier = Modifier
                    .then(if (isUser) Modifier.widthIn(max = 300.dp).clip(bubbleShape).background(UserMessageSurface) else Modifier.fillMaxWidth())
                    .padding(horizontal = if (isUser) 20.dp else 0.dp, vertical = if (isUser) 16.dp else 0.dp)
            ) {
                Column {
                    if (message.isError || showTime) {
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Text(
                                text = (if (message.isError) tr("Perlu diperiksa", "Needs attention") else if (isUser) tr("Kamu", "You") else "Hermes") +
                                    if (showTime) "  ${SimpleDateFormat("HH:mm", Locale.getDefault()).format(Date(message.timestamp))}" else "",
                                color = if (message.isError) TerminalRed else TextSecondary,
                                fontSize = 11.sp,
                                fontWeight = FontWeight.Bold,
                                letterSpacing = 0.sp
                            )
                        }
                        Spacer(Modifier.height(if (isUser) 12.dp else 8.dp))
                    }
                    SelectionContainer {
                        HermesMessageBody(message.content, fontSize,
                            textColor = if (message.isError) TerminalRed else TextPrimary,
                            onRun = if (message.canOfferCommandActions) onRunOnServer else null,
                            onTerminal = if (message.canOfferCommandActions) onExecuteInTerminal else null,
                            fillWidth = !isUser)
                    }

                    // Interactive Action Buttons (e.g. detected models or profiles)
                    if (message.interactiveActions.isNotEmpty()) {
                        Spacer(modifier = Modifier.height(10.dp))
                        Text(
                            text = if (message.actionType == "select_model") tr("Pilih model untuk mengaktifkan:", "Select a model to activate:") else tr("Pilih profil jaringan:", "Select a network profile:"),
                            color = TextSecondary,
                            fontSize = 11.sp,
                            fontWeight = FontWeight.SemiBold
                        )
                        Spacer(modifier = Modifier.height(2.dp))
                        Column(verticalArrangement = Arrangement.spacedBy(5.dp)) {
                            message.interactiveActions.forEach { actionLabel ->
                                val isSelected = actionLabel == currentModel
                                Row(
                                    modifier = Modifier
                                        .fillMaxWidth()
                                        .clip(RoundedCornerShape(10.dp))
                                        .background(if (isSelected) BurntOrangeBadgeBg else NavyDarkSurface)
                                        .border(
                                            width = 1.dp,
                                            color = if (isSelected) BurntOrange else NavyDarkBorder,
                                            shape = RoundedCornerShape(10.dp)
                                        )
                                        .clickable { onActionClick(message.actionType, actionLabel) }
                                        .heightIn(min = 48.dp).padding(horizontal = 10.dp, vertical = 7.dp),
                                    verticalAlignment = Alignment.CenterVertically
                                ) {
                                    Icon(
                                        imageVector = if (message.actionType == "select_model") Icons.Default.SmartToy else Icons.Default.Dns,
                                        contentDescription = null,
                                        tint = if (isSelected) BurntOrange else TextSecondary,
                                        modifier = Modifier.size(15.dp)
                                    )
                                    Spacer(modifier = Modifier.width(8.dp))
                                    Text(
                                        text = actionLabel,
                                        color = if (isSelected) BurntOrange else TextPrimary,
                                        fontWeight = if (isSelected) FontWeight.Bold else FontWeight.Medium,
                                        fontSize = 12.sp,
                                        fontFamily = FontFamily.Monospace,
                                        modifier = Modifier.weight(1f)
                                    )
                                    if (isSelected) {
                                        Text(
                                            text = tr("AKTIF", "ACTIVE"),
                                            color = BurntOrange,
                                            fontSize = 10.sp,
                                            fontWeight = FontWeight.Bold
                                        )
                                    }
                                }
                            }
                        }
                    }

                }
            }
        }
    }
}

@Composable
fun FormattedCodeAndTextMessage(
    rawContent: String,
    onRunOnServer: ((String) -> Unit)? = null,
    onExecuteInTerminal: ((String) -> Unit)? = null
) {
    val context = LocalContext.current
    val segments = remember(rawContent) {
        rawContent.split("```")
    }

    Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
        for (i in segments.indices) {
            val part = segments[i]
            if (part.isBlank()) continue

            if (i % 2 == 1) {
                // Code block (inside ``` ... ```)
                val lines = part.lines()
                val lang = lines.firstOrNull()?.trim() ?: ""
                val code = if (lang.isNotEmpty() && !lang.contains(" ")) {
                    lines.drop(1).joinToString("\n")
                } else part
                val cleanCode = code.trim()

                Column(
                    modifier = Modifier
                        .fillMaxWidth()
                        .clip(RoundedCornerShape(8.dp))
                        .background(NavyDarkSurface)
                        .border(1.dp, NavyDarkBorder, RoundedCornerShape(8.dp))
                        .padding(8.dp)
                ) {
                    // Header bar with Language tag and Action Buttons
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(bottom = 6.dp),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.SpaceBetween
                    ) {
                        Text(
                            text = if (lang.isNotEmpty()) lang.uppercase() else "BASH / CLI",
                            color = BurntOrange,
                            fontSize = 11.sp,
                            fontFamily = FontFamily.Monospace,
                            fontWeight = FontWeight.Bold
                        )

                        Row(
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.spacedBy(4.dp)
                        ) {
                            // Copy Code button
                            Box(
                                modifier = Modifier
                                    .clip(RoundedCornerShape(6.dp))
                                    .background(NavyDarkSurface)
                                    .border(1.dp, NavyDarkBorder, RoundedCornerShape(6.dp))
                                    .clickable {
                                        val clipboard = context.getSystemService(Context.CLIPBOARD_SERVICE) as ClipboardManager
                                        clipboard.setPrimaryClip(ClipData.newPlainText("Code", cleanCode))
                                        Toast.makeText(context, "Kode disalin", Toast.LENGTH_SHORT).show()
                                    }
                                    .padding(horizontal = 6.dp, vertical = 3.dp)
                            ) {
                                Row(verticalAlignment = Alignment.CenterVertically) {
                                    Icon(
                                        Icons.Default.ContentCopy,
                                        contentDescription = "Salin Kode",
                                        tint = TextSecondary,
                                        modifier = Modifier.size(11.dp)
                                    )
                                    Spacer(modifier = Modifier.width(3.dp))
                                    Text("Salin", color = TextSecondary, fontSize = 10.sp)
                                }
                            }

                            // Run on Server button (Executes directly via SSH and streams back output)
                            if (onRunOnServer != null) {
                                Box(
                                    modifier = Modifier
                                        .clip(RoundedCornerShape(6.dp))
                                        .background(BurntOrange)
                                        .clickable {
                                            onRunOnServer(cleanCode)
                                        }
                                        .padding(horizontal = 7.dp, vertical = 3.dp)
                                        .testTag("run_on_server_button")
                                ) {
                                    Row(verticalAlignment = Alignment.CenterVertically) {
                                        Icon(
                                            Icons.Default.PlayArrow,
                                            contentDescription = "Run on Server",
                                            tint = MaterialTheme.colorScheme.onPrimary,
                                            modifier = Modifier.size(12.dp)
                                        )
                                        Spacer(modifier = Modifier.width(3.dp))
                                        Text(
                                            "Run on Server",
                                            color = MaterialTheme.colorScheme.onPrimary,
                                            fontSize = 10.sp,
                                            fontWeight = FontWeight.Bold
                                        )
                                    }
                                }
                            }

                            // Optional Run in Interactive Terminal button
                            if (onExecuteInTerminal != null) {
                                Box(
                                    modifier = Modifier
                                        .clip(RoundedCornerShape(6.dp))
                                        .background(BurntOrangePillBg)
                                        .border(1.dp, BurntOrangeBorder, RoundedCornerShape(6.dp))
                                        .clickable {
                                            onExecuteInTerminal(cleanCode)
                                        }
                                        .padding(horizontal = 6.dp, vertical = 3.dp)
                                ) {
                                    Row(verticalAlignment = Alignment.CenterVertically) {
                                        Icon(
                                            Icons.Default.Terminal,
                                            contentDescription = "Terminal",
                                            tint = BurntOrange,
                                            modifier = Modifier.size(11.dp)
                                        )
                                        Spacer(modifier = Modifier.width(2.dp))
                                        Text(
                                            "Terminal",
                                            color = BurntOrange,
                                            fontSize = 10.sp
                                        )
                                    }
                                }
                            }
                        }
                    }

                    Text(
                        text = cleanCode,
                        color = TerminalGreen,
                        fontFamily = FontFamily.Monospace,
                        fontSize = 12.sp,
                        lineHeight = 16.sp
                    )
                }
            } else {
                // Normal prose text
                Text(
                    text = part.trim(),
                    color = TextPrimary,
                    fontSize = 14.sp,
                    lineHeight = 20.sp
                )
            }
        }
    }
}
