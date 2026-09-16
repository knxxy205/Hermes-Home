package com.example.ui.terminal

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
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.Send
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.ContentPaste
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material.icons.filled.Stop
import androidx.compose.material.icons.filled.Terminal
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
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
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.platform.LocalClipboardManager
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.data.model.ServerConfig
import com.example.data.ssh.AnsiTerminalParser
import com.example.data.ssh.SshTerminalManager
import com.example.data.ssh.TerminalConnectionState
import com.example.ui.components.CustomScreenHeader
import com.example.ui.components.PillChip
import com.example.ui.components.ShortcutKeyButton
import com.example.ui.components.StatusBadge
import com.example.ui.theme.BurntOrange
import com.example.ui.theme.BurntOrangeBadgeBg
import com.example.ui.theme.BurntOrangeBorder
import com.example.ui.theme.NavyCardBorder
import com.example.ui.theme.NavyDarkBackground
import com.example.ui.theme.NavyDarkBorder
import com.example.ui.theme.NavyDarkCard
import com.example.ui.theme.NavyDarkSurface
import com.example.ui.theme.TerminalBg
import com.example.ui.theme.TerminalGreen
import com.example.ui.theme.TextPrimary
import com.example.ui.theme.TextSecondary

@Composable
fun TerminalScreen(
    terminalManager: SshTerminalManager,
    serverConfig: ServerConfig,
    modifier: Modifier = Modifier
) {
    val context = LocalContext.current
    val clipboardManager = LocalClipboardManager.current
    val connectionState by terminalManager.connectionState.collectAsState()
    val terminalRawOutput by terminalManager.terminalOutput.collectAsState()
    val verticalScrollState = rememberScrollState()
    val quickCmdScrollState = rememberScrollState()
    val shortcutScrollState = rememberScrollState()

    var inputCommand by remember { mutableStateOf("") }

    // Auto scroll to bottom when terminal output grows
    LaunchedEffect(terminalRawOutput) {
        verticalScrollState.scrollTo(verticalScrollState.maxValue)
    }

    val quickCommands = remember {
        listOf(
            "uname -a",
            "docker ps",
            "df -h",
            "ls -la",
            "pwd",
            "free -h",
            "uptime",
            "systemctl status"
        )
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
                            onClick = { terminalManager.disconnect() },
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
                                    terminalManager.connect(serverConfig)
                                } else {
                                    // Start interactive sandbox demo if not yet configured
                                    terminalManager.startSandboxMode()
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

        // Status badge row
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 20.dp, vertical = 4.dp),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            val (statusText, isSuccess) = when (connectionState) {
                is TerminalConnectionState.Connected -> "SSH CONNECTED" to true
                is TerminalConnectionState.Connecting -> "CONNECTING..." to false
                is TerminalConnectionState.Error -> "CONNECTION ERROR" to false
                TerminalConnectionState.Disconnected -> "STANDBY" to false
            }
            StatusBadge(text = statusText, isSuccess = isSuccess)

            if (!serverConfig.isSshConfigured && connectionState !is TerminalConnectionState.Connected) {
                Text(
                    text = "Tip: Tap 'Sandbox' to test CLI",
                    color = TextSecondary,
                    fontSize = 11.sp,
                    fontFamily = FontFamily.Monospace
                )
            }
        }

        Spacer(modifier = Modifier.height(8.dp))

        // Main Terminal Output Window (Near-black background with bright green/ANSI text)
        Box(
            modifier = Modifier
                .weight(1f)
                .fillMaxWidth()
                .padding(horizontal = 16.dp)
                .clip(RoundedCornerShape(16.dp))
                .background(TerminalBg)
                .border(1.dp, NavyCardBorder, RoundedCornerShape(16.dp))
                .padding(12.dp)
        ) {
            val annotatedOutput = remember(terminalRawOutput) {
                AnsiTerminalParser.parse(terminalRawOutput)
            }

            Column(
                modifier = Modifier
                    .fillMaxSize()
                    .verticalScroll(verticalScrollState)
            ) {
                Text(
                    text = annotatedOutput,
                    fontFamily = FontFamily.Monospace,
                    fontSize = 12.sp,
                    lineHeight = 18.sp,
                    color = TerminalGreen
                )
            }
        }

        Spacer(modifier = Modifier.height(10.dp))

        // Quick Command Chips Row (horizontal scrollable)
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .horizontalScroll(quickCmdScrollState)
                .padding(horizontal = 16.dp),
            horizontalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            for (cmd in quickCommands) {
                PillChip(
                    label = cmd,
                    onClick = {
                        terminalManager.sendCommand(cmd)
                    }
                )
            }
        }

        Spacer(modifier = Modifier.height(8.dp))

        // Shortcut Key Row: Up, Down, Left, Right, ESC, TAB, CTRL+C, ALT (horizontal scrollable)
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .horizontalScroll(shortcutScrollState)
                .padding(horizontal = 16.dp),
            horizontalArrangement = Arrangement.spacedBy(6.dp)
        ) {
            ShortcutKeyButton(label = "↑", onClick = { terminalManager.sendKeyUp() })
            ShortcutKeyButton(label = "↓", onClick = { terminalManager.sendKeyDown() })
            ShortcutKeyButton(label = "←", onClick = { terminalManager.sendKeyLeft() })
            ShortcutKeyButton(label = "→", onClick = { terminalManager.sendKeyRight() })
            ShortcutKeyButton(label = "ESC", onClick = { terminalManager.sendKeyEsc() })
            ShortcutKeyButton(label = "TAB", onClick = { terminalManager.sendKeyTab() })
            ShortcutKeyButton(label = "CTRL+C", onClick = { terminalManager.sendKeyCtrlC() })
            ShortcutKeyButton(label = "ALT", onClick = { terminalManager.sendKeyAlt() })
            ShortcutKeyButton(label = "PASTE", onClick = {
                val clipText = clipboardManager.getText()?.text
                if (!clipText.isNullOrBlank()) {
                    inputCommand = if (inputCommand.isBlank()) clipText else "$inputCommand $clipText"
                    Toast.makeText(context, "Ditempel dari clipboard", Toast.LENGTH_SHORT).show()
                } else {
                    Toast.makeText(context, "Clipboard kosong", Toast.LENGTH_SHORT).show()
                }
            })
            ShortcutKeyButton(label = "CLR", onClick = { terminalManager.clearBuffer() })
        }

        Spacer(modifier = Modifier.height(8.dp))

        // Input Command Row: Modern Pill Layout with Standalone Circular Send Button
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
                    .padding(horizontal = 12.dp, vertical = 6.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text(
                    text = "$ ",
                    color = TerminalGreen,
                    fontWeight = FontWeight.Bold,
                    fontFamily = FontFamily.Monospace,
                    fontSize = 15.sp,
                    modifier = Modifier.padding(start = 2.dp)
                )

                BasicTextField(
                    value = inputCommand,
                    onValueChange = { inputCommand = it },
                    modifier = Modifier
                        .weight(1f)
                        .padding(horizontal = 6.dp, vertical = 4.dp)
                        .testTag("terminal_command_input"),
                    textStyle = MaterialTheme.typography.bodyMedium.copy(
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
                                terminalManager.sendCommand(inputCommand)
                                inputCommand = ""
                            }
                        }
                    ),
                    decorationBox = { innerTextField ->
                        if (inputCommand.isEmpty()) {
                            Text(
                                text = "Ketik perintah...",
                                color = TextSecondary.copy(alpha = 0.55f),
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

            // Standalone Circular Send Button
            IconButton(
                onClick = {
                    if (inputCommand.isNotBlank()) {
                        terminalManager.sendCommand(inputCommand)
                        inputCommand = ""
                    }
                },
                enabled = inputCommand.isNotBlank(),
                modifier = Modifier
                    .size(44.dp)
                    .clip(CircleShape)
                    .background(if (inputCommand.isNotBlank()) BurntOrange else BurntOrange.copy(alpha = 0.25f))
                    .testTag("send_command_button")
            ) {
                Icon(
                    imageVector = Icons.AutoMirrored.Filled.Send,
                    contentDescription = "Send",
                    tint = if (inputCommand.isNotBlank()) NavyDarkBackground else TextSecondary,
                    modifier = Modifier.size(20.dp)
                )
            }
        }

        Spacer(modifier = Modifier.height(4.dp))
    }
}
