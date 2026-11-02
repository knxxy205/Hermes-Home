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
import androidx.compose.runtime.Composable
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
import kotlinx.coroutines.launch
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale
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
    messages: SnapshotStateList<ChatMessage> = remember { mutableStateListOf() }
) {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    var pendingCommand by remember { mutableStateOf<Pair<String, Boolean>?>(null) }
    val listState = rememberLazyListState()

    var inputText by remember { mutableStateOf("") }
    var isSending by remember { mutableStateOf(false) }
    var isRunningServerCommand by remember { mutableStateOf(false) }

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
                        content = "⚡ **Hasil Eksekusi di Server:** (`$command`)\n```bash\n$output\n```"
                    )
                )
                autoSaveCurrentSession()
                listState.animateScrollToItem(messages.lastIndex)
                Toast.makeText(context, "Selesai dieksekusi di server", Toast.LENGTH_SHORT).show()
            }.onFailure { err ->
                messages.add(
                    ChatMessage(
                        role = "assistant",
                        content = "❌ **Gagal mengeksekusi di server:**\n${err.localizedMessage ?: "Unknown error"}",
                        isError = true
                    )
                )
                autoSaveCurrentSession()
                listState.animateScrollToItem(messages.lastIndex)
            }
        }
    }

    fun startNewChatSession() {
        autoSaveCurrentSession()
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
    }

    val slashCommands = remember {
        listOf(
            "/model" to "Deteksi atau ganti model",
            "/models" to "Alias untuk /model",
            "/history" to "Riwayat percakapan",
            "/sesi" to "Alias untuk /history",
            "/status" to "Status koneksi dan model",
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
            listState.animateScrollToItem(messages.size - 1)
        }
    }

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
                messages.add(ChatMessage(
                    role = "assistant",
                    content = "✅ Model Hermes berhasil diubah ke:\n👉 *$actionValue*"
                ))
                autoSaveCurrentSession()
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
        if (prompt.isBlank() || isSending) return
        val rawPrompt = prompt.trim()
        val userMsg = ChatMessage(role = "user", content = rawPrompt)
        messages.add(userMsg)
        autoSaveCurrentSession()
        inputText = ""

        // Process Telegram-like Slash Commands
        if (rawPrompt.startsWith("/")) {
            val parts = rawPrompt.split("\\s+".toRegex())
            val cmd = parts.getOrNull(0)?.lowercase() ?: ""
            val arg = parts.drop(1).joinToString(" ")

            when (cmd) {
                "/model", "/models" -> {
                    if (arg.isNotBlank()) {
                        val targetModel = arg.trim()
                        val updated = serverConfig.copy(hermesModel = targetModel)
                        encryptedStorage?.saveConfig(updated)
                        encryptedStorage?.getActiveProfileId()?.let { pId ->
                            val profiles = encryptedStorage.loadProfiles()
                            val currentP = profiles.find { it.id == pId }
                            if (currentP != null) {
                                encryptedStorage.saveProfile(currentP.copy(config = updated))
                            }
                        }
                        onConfigUpdated?.invoke(updated)
                        messages.add(ChatMessage(
                            role = "assistant",
                            content = "✅ Model Hermes berhasil diubah ke:\n👉 *$targetModel*"
                        ))
                        autoSaveCurrentSession()
                    } else {
                        // Detect models from 9router & LLM server
                        isSending = true
                        scope.launch {
                            val res = hermesClient.fetchAvailableModels(serverConfig)
                            isSending = false
                            res.onSuccess { models ->
                                if (models.isNotEmpty()) {
                                    messages.add(ChatMessage(
                                        role = "assistant",
                                        content = "🤖 *Model Terdeteksi di 9router / Server (${models.size}):*\n\nModel aktif saat ini:\n👉 *${serverConfig.hermesModel.ifBlank { "Belum diset" }}*\n\nKetuk nama model di bawah untuk mengaktifkan:",
                                        interactiveActions = models,
                                        actionType = "select_model"
                                    ))
                                } else {
                                    messages.add(ChatMessage(
                                        role = "assistant",
                                        content = "⚠️ Tidak ada model ditemukan pada endpoint:\n`${serverConfig.hermesBaseUrl}`\n\nPastikan 9router/Ollama aktif atau periksa Base URL di tab Settings.",
                                        isError = true
                                    ))
                                }
                                autoSaveCurrentSession()
                            }.onFailure { err ->
                                messages.add(ChatMessage(
                                    role = "assistant",
                                    content = "❌ Gagal memindai model: ${err.localizedMessage ?: "Unknown error"}\n\nPeriksa koneksi Tailscale / IP server di Settings.",
                                    isError = true
                                ))
                                autoSaveCurrentSession()
                            }
                        }
                    }
                    return
                }

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
                            • Status Mode: ${if (isConfigured) "🟢 Terhubung ke Homeserver" else "🟡 Mode Standby"}
                            • API Key: ${if (serverConfig.hermesApiKey.isNotBlank()) "Tersedia (Bearer Token)" else "Tanpa Key (Lokal)"}
                        """.trimIndent()
                    ))
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
                            • `/model [nama]` - Ganti model AI langsung
                            • `/history` - Buka riwayat & sesi percakapan
                            • `/status` - Cek koneksi, profil, & model aktif
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
                useSandboxFallback = !serverConfig.isHermesConfigured
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
        AlertDialog(
            onDismissRequest = { pendingCommand = null },
            title = { Text("Jalankan perintah di server?") },
            text = { Column {
                Text("Tujuan: ${serverConfig.sshUser}@${serverConfig.sshHost}")
                Text(command, fontFamily = FontFamily.Monospace, modifier = Modifier.padding(top = 12.dp))
            } },
            confirmButton = { TextButton(onClick = {
                pendingCommand = null
                if (interactive) onExecuteInTerminal?.invoke(command) else handleRunOnServer(command)
            }) { Text("Jalankan") } },
            dismissButton = { TextButton(onClick = { pendingCommand = null }) { Text("Batal") } }
        )
    }
    Column(
        modifier = modifier
            .fillMaxSize()
            .background(NavyDarkBackground)
    ) {
        // Screen Header
        CustomScreenHeader(
            icon = Icons.Default.Forum,
            title = "Hermes",
            subtitle = if (currentSessionTitle.isNotBlank() && currentSessionTitle != "Obrolan Baru") {
                "$currentSessionTitle • ${serverConfig.hermesModel}"
            } else if (serverConfig.isHermesConfigured) {
                "${serverConfig.hermesModel} @ ${serverConfig.hermesBaseUrl}"
            } else {
                "Asisten pribadi untuk server kamu"
            },
            trailingContent = {
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(2.dp)
                ) {
                    // New Chat Button
                    IconButton(
                        onClick = { startNewChatSession() },
                        modifier = Modifier
                            .size(34.dp)
                            .testTag("new_chat_button")
                    ) {
                        Icon(
                            Icons.Default.Add,
                            contentDescription = "New Chat",
                            tint = BurntOrange,
                            modifier = Modifier.size(20.dp)
                        )
                    }

                    // History Sessions Button
                    IconButton(
                        onClick = {
                            refreshSavedSessions()
                            showHistorySheet = true
                        },
                        modifier = Modifier
                            .size(34.dp)
                            .testTag("chat_history_button")
                    ) {
                        Icon(
                            Icons.Default.History,
                            contentDescription = "Chat History",
                            tint = BurntOrange,
                            modifier = Modifier.size(20.dp)
                        )
                    }

                    // Clear Current Chat Button
                    IconButton(
                        onClick = {
                            messages.clear()
                            messages.add(
                                ChatMessage(
                                    role = "assistant",
                                    content = "Riwayat chat telah dibersihkan. Siap menerima perintah baru."
                                )
                            )
                            autoSaveCurrentSession()
                        },
                        modifier = Modifier
                            .size(34.dp)
                            .testTag("clear_chat_button")
                    ) {
                        Icon(
                            Icons.Default.DeleteSweep,
                            contentDescription = "Clear Chat",
                            tint = TextSecondary,
                            modifier = Modifier.size(19.dp)
                        )
                    }
                }
            }
        )

        // Telegram-style Slash Command Suggestions Bar
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .horizontalScroll(rememberScrollState())
                .padding(horizontal = 14.dp, vertical = 4.dp),
            horizontalArrangement = Arrangement.spacedBy(6.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            slashCommands.forEach { (cmd, desc) ->
                Row(
                    modifier = Modifier
                        .clip(RoundedCornerShape(12.dp))
                        .background(NavyDarkSurface)
                        .border(1.dp, NavyDarkBorder, RoundedCornerShape(12.dp))
                        .clickable { doSendMessage(cmd) }
                        .padding(horizontal = 12.dp, vertical = 10.dp),
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
            contentPadding = PaddingValues(horizontal = 20.dp, vertical = 20.dp),
            verticalArrangement = Arrangement.spacedBy(20.dp)
        ) {
            items(messages, key = { it.id }) { msg ->
                ChatMessageItem(
                    message = msg,
                    currentModel = serverConfig.hermesModel,
                    onCopy = { copyToClipboard(msg.content) },
                    onActionClick = { actionType, value ->
                        handleInteractiveAction(actionType, value)
                    },
                    onRunOnServer = { cmd -> pendingCommand = cmd to false },
                    onExecuteInTerminal = if (onExecuteInTerminal != null) ({ cmd -> pendingCommand = cmd to true }) else null
                )
            }

            if (isSending) {
                item {
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(vertical = 6.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Box(
                            modifier = Modifier
                                .size(32.dp)
                                .clip(CircleShape)
                                .background(BurntOrangeBadgeBg)
                                .border(1.dp, BurntOrangeBorder, CircleShape),
                            contentAlignment = Alignment.Center
                        ) {
                            Icon(Icons.Default.Forum, contentDescription = null, tint = BurntOrange, modifier = Modifier.size(16.dp))
                        }
                        Spacer(modifier = Modifier.width(10.dp))
                        CircularProgressIndicator(
                            color = BurntOrange,
                            strokeWidth = 2.dp,
                            modifier = Modifier.size(18.dp)
                        )
                        Spacer(modifier = Modifier.width(8.dp))
                        Text(
                            text = "Hermes sedang merespon...",
                            color = TextSecondary,
                            fontSize = 12.sp,
                            fontFamily = FontFamily.Monospace
                        )
                    }
                }
            }
        }

        // Input Row Bar: Modern Pill Layout with Standalone Circular Send Button
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 14.dp, vertical = 6.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            // Pill Container
            Row(
                modifier = Modifier
                    .weight(1f)
                    .clip(RoundedCornerShape(24.dp))
                    .background(NavyDarkCard)
                    .border(1.dp, NavyDarkBorder, RoundedCornerShape(24.dp))
                    .padding(horizontal = 10.dp, vertical = 4.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                // Slash quick toggle button
                Box(
                    modifier = Modifier
                        .size(30.dp)
                        .clip(CircleShape)
                        .background(BurntOrangeBadgeBg)
                        .clickable {
                            inputText = if (inputText.isEmpty()) "/" else ""
                        },
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
                        .padding(horizontal = 8.dp, vertical = 6.dp)
                        .testTag("hermes_chat_input"),
                    textStyle = MaterialTheme.typography.bodyMedium.copy(
                        color = TextPrimary,
                        fontSize = 14.sp
                    ),
                    cursorBrush = SolidColor(BurntOrange),
                    maxLines = 6,
                    decorationBox = { innerTextField ->
                        if (inputText.isEmpty()) {
                            Text(
                                text = "Tanya Hermes atau ketik /...",
                                color = TextSecondary.copy(alpha = 0.55f),
                                fontSize = 13.sp
                            )
                        }
                        innerTextField()
                    }
                )

                // Clean inline Paste button if input is empty
                if (inputText.isEmpty()) {
                    IconButton(
                        onClick = {
                            val clipboard = context.getSystemService(Context.CLIPBOARD_SERVICE) as ClipboardManager
                            val clip = clipboard.primaryClip?.getItemAt(0)?.text?.toString()
                            if (!clip.isNullOrBlank()) {
                                inputText = clip
                                Toast.makeText(context, "Ditempel dari clipboard", Toast.LENGTH_SHORT).show()
                            } else {
                                Toast.makeText(context, "Clipboard kosong", Toast.LENGTH_SHORT).show()
                            }
                        },
                        modifier = Modifier
                            .size(32.dp)
                            .testTag("hermes_paste_button")
                    ) {
                        Icon(
                            imageVector = Icons.Default.ContentPaste,
                            contentDescription = "Paste text",
                            tint = BurntOrange,
                            modifier = Modifier.size(18.dp)
                        )
                    }
                } else {
                    // Clean Clear button if text entered
                    IconButton(
                        onClick = { inputText = "" },
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

            // Standalone Circular Send / Enter Button (Always active solid orange, matching Terminal)
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
                enabled = true,
                modifier = Modifier
                    .size(48.dp)
                    .clip(CircleShape)
                    .background(BurntOrange)
                    .testTag("hermes_send_button")
            ) {
                if (isSending) {
                    CircularProgressIndicator(
                        modifier = Modifier.size(20.dp),
                        color = Color.White,
                        strokeWidth = 2.5.dp
                    )
                } else {
                    Icon(
                        imageVector = Icons.AutoMirrored.Filled.Send,
                        contentDescription = "Send",
                        tint = Color.White,
                        modifier = Modifier.size(20.dp)
                    )
                }
            }
        }

        Spacer(modifier = Modifier.height(6.dp))
    }

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
                            text = "Riwayat Obrolan Hermes",
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
                    text = "Lanjutkan percakapan sebelumnya atau mulai obrolan baru.",
                    color = TextSecondary,
                    fontSize = 12.sp,
                    modifier = Modifier.padding(top = 2.dp, bottom = 14.dp)
                )

                // Start New Chat Button
                Button(
                    onClick = {
                        showHistorySheet = false
                        startNewChatSession()
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
                        tint = Color.White,
                        modifier = Modifier.size(18.dp)
                    )
                    Spacer(modifier = Modifier.width(8.dp))
                    Text(
                        text = "Mulai Obrolan Baru",
                        color = Color.White,
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
                            text = "Belum ada riwayat percakapan tersimpan",
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
                                    modifier = Modifier.size(30.dp)
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
fun ChatMessageItem(
    message: ChatMessage,
    currentModel: String = "",
    onCopy: () -> Unit,
    onActionClick: (String?, String) -> Unit = { _, _ -> },
    onRunOnServer: ((String) -> Unit)? = null,
    onExecuteInTerminal: ((String) -> Unit)? = null
) {
    val isUser = message.role == "user"

    Column(
        modifier = Modifier.fillMaxWidth(),
        horizontalAlignment = if (isUser) Alignment.End else Alignment.Start
    ) {
        Row(
            modifier = Modifier.widthIn(max = 340.dp),
            verticalAlignment = Alignment.Bottom,
            horizontalArrangement = if (isUser) Arrangement.End else Arrangement.Start
        ) {
            if (!isUser) {
                Box(
                    modifier = Modifier
                        .size(30.dp)
                        .clip(CircleShape)
                        .background(BurntOrangeBadgeBg)
                        .border(1.dp, BurntOrangeBorder, CircleShape),
                    contentAlignment = Alignment.Center
                ) {
                    Icon(
                        Icons.Default.Forum,
                        contentDescription = null,
                        tint = if (message.isError) TerminalRed else BurntOrange,
                        modifier = Modifier.size(16.dp)
                    )
                }
                Spacer(modifier = Modifier.width(8.dp))
            }

            Box(
                modifier = Modifier
                    .clip(
                        RoundedCornerShape(
                            topStart = 22.dp,
                            topEnd = 22.dp,
                            bottomStart = if (isUser) 22.dp else 6.dp,
                            bottomEnd = if (isUser) 6.dp else 22.dp
                        )
                    )
                    .background(if (isUser) BurntOrangePillBg else NavyDarkCard)
                    .border(
                        1.dp,
                        when {
                            message.isError -> TerminalRed
                            isUser -> BurntOrangeBorder
                            else -> NavyCardBorder
                        },
                        RoundedCornerShape(
                            topStart = 22.dp,
                            topEnd = 22.dp,
                            bottomStart = if (isUser) 22.dp else 6.dp,
                            bottomEnd = if (isUser) 6.dp else 22.dp
                        )
                    )
                    .padding(horizontal = 16.dp, vertical = 14.dp)
            ) {
                Column {
                    SelectionContainer {
                        // Render text with code block highlighting if content contains markdown code blocks
                        val content = message.content
                        if (content.contains("```")) {
                            FormattedCodeAndTextMessage(
                                rawContent = content,
                                onRunOnServer = onRunOnServer,
                                onExecuteInTerminal = onExecuteInTerminal
                            )
                        } else {
                            Text(
                                text = content,
                                color = if (message.isError) TerminalRed else TextPrimary,
                                fontSize = 14.sp,
                                lineHeight = 23.sp
                            )
                        }
                    }

                    // Interactive Action Buttons (e.g. detected models or profiles)
                    if (message.interactiveActions.isNotEmpty()) {
                        Spacer(modifier = Modifier.height(10.dp))
                        Text(
                            text = if (message.actionType == "select_model") "Pilih model untuk mengaktifkan:" else "Pilih profil jaringan:",
                            color = TextSecondary,
                            fontSize = 11.sp,
                            fontWeight = FontWeight.SemiBold
                        )
                        Spacer(modifier = Modifier.height(6.dp))
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
                                        .padding(horizontal = 10.dp, vertical = 7.dp),
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
                                            text = "AKTIF",
                                            color = BurntOrange,
                                            fontSize = 10.sp,
                                            fontWeight = FontWeight.Bold
                                        )
                                    }
                                }
                            }
                        }
                    }

                    if (!isUser && !message.isError) {
                        Spacer(modifier = Modifier.height(6.dp))
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.End,
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Box(
                                modifier = Modifier
                                    .clip(RoundedCornerShape(6.dp))
                                    .background(NavyDarkSurface)
                                    .border(1.dp, NavyDarkBorder, RoundedCornerShape(6.dp))
                                    .padding(horizontal = 6.dp, vertical = 2.dp)
                            ) {
                                IconButton(
                                    onClick = onCopy,
                                    modifier = Modifier.size(20.dp)
                                ) {
                                    Icon(
                                        Icons.Default.ContentCopy,
                                        contentDescription = "Copy message",
                                        tint = TextSecondary,
                                        modifier = Modifier.size(12.dp)
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
                                            tint = Color.White,
                                            modifier = Modifier.size(12.dp)
                                        )
                                        Spacer(modifier = Modifier.width(3.dp))
                                        Text(
                                            "Run on Server",
                                            color = Color.White,
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
