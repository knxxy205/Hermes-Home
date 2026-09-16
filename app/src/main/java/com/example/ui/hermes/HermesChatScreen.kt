package com.example.ui.hermes

import android.content.ClipData
import android.content.ClipboardManager
import android.content.Context
import android.widget.Toast
import androidx.compose.foundation.background
import androidx.compose.foundation.border
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
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.ContentCopy
import androidx.compose.material.icons.filled.ContentPaste
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.material.icons.filled.DeleteSweep
import androidx.compose.material.icons.filled.SmartToy
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
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
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.data.hermes.ChatMessage
import com.example.data.hermes.HermesApiClient
import com.example.data.model.ServerConfig
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

@Composable
fun HermesChatScreen(
    hermesClient: HermesApiClient,
    serverConfig: ServerConfig,
    modifier: Modifier = Modifier,
    messages: SnapshotStateList<ChatMessage> = remember { mutableStateListOf() }
) {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    val listState = rememberLazyListState()

    var inputText by remember { mutableStateOf("") }
    var isSending by remember { mutableStateOf(false) }

    val quickPrompts = remember {
        listOf(
            "Cek status container Docker",
            "Buat script backup rsync harian",
            "Analisis pemakaian storage disk",
            "Periksa load CPU dan RAM server"
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

    fun doSendMessage(prompt: String) {
        if (prompt.isBlank() || isSending) return
        val userMsg = ChatMessage(role = "user", content = prompt.trim())
        messages.add(userMsg)
        inputText = ""
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
            }.onFailure { err ->
                messages.add(
                    ChatMessage(
                        role = "assistant",
                        content = "Gagal menghubungi Hermes Agent:\n${err.localizedMessage ?: "Unknown error"}\n\nPeriksa IP Tailscale dan Model di tab Settings.",
                        isError = true
                    )
                )
            }
        }
    }

    Column(
        modifier = modifier
            .fillMaxSize()
            .background(NavyDarkBackground)
    ) {
        // Screen Header
        CustomScreenHeader(
            icon = Icons.Default.SmartToy,
            title = "Hermes Agent",
            subtitle = if (serverConfig.isHermesConfigured) "${serverConfig.hermesModel} @ ${serverConfig.hermesBaseUrl}" else "Hermes Agent (Standby)",
            trailingContent = {
                IconButton(
                    onClick = {
                        messages.clear()
                        messages.add(
                            ChatMessage(
                                role = "assistant",
                                content = "Riwayat chat telah dibersihkan. Siap menerima perintah baru."
                            )
                        )
                    },
                    modifier = Modifier.testTag("clear_chat_button")
                ) {
                    Icon(
                        Icons.Default.DeleteSweep,
                        contentDescription = "Clear Chat",
                        tint = BurntOrange
                    )
                }
            }
        )

        // Quick Suggestion Chips (horizontal scrollable)
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .horizontalScroll(rememberScrollState())
                .padding(horizontal = 16.dp, vertical = 4.dp),
            horizontalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            for (qp in quickPrompts) {
                PillChip(
                    label = qp,
                    onClick = { doSendMessage(qp) }
                )
            }
        }

        Spacer(modifier = Modifier.height(4.dp))

        // Message List Area
        LazyColumn(
            state = listState,
            modifier = Modifier
                .weight(1f)
                .fillMaxWidth(),
            contentPadding = PaddingValues(horizontal = 16.dp, vertical = 8.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            items(messages, key = { it.id }) { msg ->
                ChatMessageItem(
                    message = msg,
                    onCopy = { copyToClipboard(msg.content) }
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
                            Icon(Icons.Default.SmartToy, contentDescription = null, tint = BurntOrange, modifier = Modifier.size(16.dp))
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

        // Input Row Bar with dedicated Paste and Clear buttons
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 16.dp, vertical = 8.dp)
                .clip(RoundedCornerShape(16.dp))
                .background(NavyDarkCard)
                .border(1.dp, NavyDarkBorder, RoundedCornerShape(16.dp))
                .padding(horizontal = 12.dp, vertical = 6.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            BasicTextField(
                value = inputText,
                onValueChange = { inputText = it },
                modifier = Modifier
                    .weight(1f)
                    .padding(vertical = 4.dp)
                    .testTag("hermes_chat_input"),
                textStyle = MaterialTheme.typography.bodyMedium.copy(
                    color = TextPrimary,
                    fontSize = 14.sp
                ),
                cursorBrush = SolidColor(BurntOrange),
                maxLines = 8,
                decorationBox = { innerTextField ->
                    if (inputText.isEmpty()) {
                        Text(
                            text = "Tanya Hermes Agent (OpenAI-compatible)...",
                            color = TextSecondary.copy(alpha = 0.6f),
                            fontSize = 13.sp
                        )
                    }
                    innerTextField()
                }
            )

            // Clear button when text exists
            if (inputText.isNotEmpty()) {
                IconButton(
                    onClick = { inputText = "" },
                    modifier = Modifier.size(30.dp)
                ) {
                    Icon(
                        imageVector = Icons.Default.Close,
                        contentDescription = "Clear input",
                        tint = TextSecondary,
                        modifier = Modifier.size(16.dp)
                    )
                }
            }

            // Dedicated PASTE button for 1-tap clipboard paste
            IconButton(
                onClick = {
                    val clipboard = context.getSystemService(Context.CLIPBOARD_SERVICE) as ClipboardManager
                    val clip = clipboard.primaryClip?.getItemAt(0)?.text?.toString()
                    if (!clip.isNullOrBlank()) {
                        inputText = if (inputText.isBlank()) clip else "$inputText\n$clip"
                        Toast.makeText(context, "Ditempel dari clipboard", Toast.LENGTH_SHORT).show()
                    } else {
                        Toast.makeText(context, "Clipboard kosong", Toast.LENGTH_SHORT).show()
                    }
                },
                modifier = Modifier
                    .size(34.dp)
                    .clip(RoundedCornerShape(10.dp))
                    .background(NavyDarkSurface)
                    .border(1.dp, BurntOrange.copy(alpha = 0.5f), RoundedCornerShape(10.dp))
                    .testTag("hermes_paste_button")
            ) {
                Icon(
                    imageVector = Icons.Default.ContentPaste,
                    contentDescription = "Paste text",
                    tint = BurntOrange,
                    modifier = Modifier.size(17.dp)
                )
            }

            Spacer(modifier = Modifier.width(6.dp))

            // Send Button
            IconButton(
                onClick = { doSendMessage(inputText) },
                enabled = inputText.isNotBlank() && !isSending,
                modifier = Modifier
                    .size(36.dp)
                    .clip(RoundedCornerShape(10.dp))
                    .background(if (inputText.isNotBlank() && !isSending) BurntOrange else BurntOrange.copy(alpha = 0.3f))
                    .testTag("hermes_send_button")
            ) {
                Icon(
                    imageVector = Icons.AutoMirrored.Filled.Send,
                    contentDescription = "Send",
                    tint = NavyDarkBackground,
                    modifier = Modifier.size(18.dp)
                )
            }
        }

        Spacer(modifier = Modifier.height(6.dp))
    }
}

@Composable
fun ChatMessageItem(
    message: ChatMessage,
    onCopy: () -> Unit
) {
    val isUser = message.role == "user"

    Column(
        modifier = Modifier.fillMaxWidth(),
        horizontalAlignment = if (isUser) Alignment.End else Alignment.Start
    ) {
        Row(
            modifier = Modifier.widthIn(max = 330.dp),
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
                        Icons.Default.SmartToy,
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
                            topStart = 16.dp,
                            topEnd = 16.dp,
                            bottomStart = if (isUser) 16.dp else 4.dp,
                            bottomEnd = if (isUser) 4.dp else 16.dp
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
                            topStart = 16.dp,
                            topEnd = 16.dp,
                            bottomStart = if (isUser) 16.dp else 4.dp,
                            bottomEnd = if (isUser) 4.dp else 16.dp
                        )
                    )
                    .padding(horizontal = 14.dp, vertical = 10.dp)
            ) {
                Column {
                    SelectionContainer {
                        // Render text with code block highlighting if content contains markdown code blocks
                        val content = message.content
                        if (content.contains("```")) {
                            FormattedCodeAndTextMessage(content)
                        } else {
                            Text(
                                text = content,
                                color = if (message.isError) TerminalRed else TextPrimary,
                                fontSize = 14.sp,
                                lineHeight = 20.sp
                            )
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
fun FormattedCodeAndTextMessage(rawContent: String) {
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

                Column(
                    modifier = Modifier
                        .fillMaxWidth()
                        .clip(RoundedCornerShape(8.dp))
                        .background(TerminalBg)
                        .border(1.dp, NavyDarkBorder, RoundedCornerShape(8.dp))
                        .padding(8.dp)
                ) {
                    if (lang.isNotEmpty()) {
                        Text(
                            text = lang.uppercase(),
                            color = BurntOrange,
                            fontSize = 10.sp,
                            fontFamily = FontFamily.Monospace,
                            fontWeight = FontWeight.Bold
                        )
                        Spacer(modifier = Modifier.height(4.dp))
                    }
                    Text(
                        text = code.trim(),
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
