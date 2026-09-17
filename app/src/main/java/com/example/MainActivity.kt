package com.example

import android.os.Bundle
import android.widget.Toast
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.fragment.app.FragmentActivity
import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.togetherWith
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBars
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Fingerprint
import androidx.compose.material.icons.filled.FolderOpen
import androidx.compose.material.icons.filled.Lock
import androidx.compose.material.icons.filled.Security
import androidx.compose.material.icons.filled.Settings
import androidx.compose.material.icons.filled.SmartToy
import androidx.compose.material.icons.filled.Terminal
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateListOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.zIndex
import com.example.data.hermes.ChatMessage
import com.example.data.hermes.HermesApiClient
import com.example.data.security.BiometricAuthHelper
import com.example.data.security.EncryptedStorage
import com.example.data.sftp.SftpManager
import com.example.data.ssh.SshTerminalManager
import com.example.data.ssh.TerminalConnectionState
import com.example.data.worker.ServerHealthWorker
import com.example.ui.files.FilesScreen
import com.example.ui.hermes.HermesChatScreen
import com.example.ui.settings.SettingsScreen
import com.example.ui.terminal.TerminalScreen
import com.example.ui.theme.BurntOrange
import com.example.ui.theme.BurntOrangeBadgeBg
import com.example.ui.theme.BurntOrangeBorder
import com.example.ui.theme.BurntOrangePillBg
import com.example.ui.theme.MyApplicationTheme
import com.example.ui.theme.NavyCardBorder
import com.example.ui.theme.NavyDarkBackground
import com.example.ui.theme.NavyDarkBorder
import com.example.ui.theme.NavyDarkCard
import com.example.ui.theme.NavyDarkSurface
import com.example.ui.theme.TerminalGreen
import com.example.ui.theme.TextPrimary
import com.example.ui.theme.TextSecondary
import kotlinx.coroutines.launch

enum class AppTab(val title: String, val icon: ImageVector, val tag: String) {
    TERMINAL("Terminal", Icons.Default.Terminal, "tab_terminal"),
    FILES("Files", Icons.Default.FolderOpen, "tab_files"),
    HERMES("Hermes", Icons.Default.SmartToy, "tab_hermes"),
    SETTINGS("Settings", Icons.Default.Settings, "tab_settings")
}

class MainActivity : FragmentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()

        // Schedule periodic 1-hour background server health check
        ServerHealthWorker.schedulePeriodicCheck(applicationContext)

        setContent {
            MyApplicationTheme {
                TailHarnessApp(activity = this)
            }
        }
    }
}

@Composable
fun TailHarnessApp(activity: FragmentActivity? = null) {
    val context = LocalContext.current
    val coroutineScope = rememberCoroutineScope()

    var isVaultUnlocked by remember { mutableStateOf(false) }

    // Auto-prompt biometric authentication on startup if activity is present
    LaunchedEffect(activity) {
        if (activity != null && !isVaultUnlocked) {
            BiometricAuthHelper.showBiometricPrompt(
                activity = activity,
                onSuccess = {
                    isVaultUnlocked = true
                },
                onError = { err ->
                    // Keep lock screen visible so user can retry or use PIN
                }
            )
        }
    }

    AnimatedContent(
        targetState = isVaultUnlocked,
        transitionSpec = { fadeIn() togetherWith fadeOut() },
        label = "vault_auth_transition"
    ) { unlocked ->
        if (!unlocked) {
            VaultLockScreen(
                onUnlockRequested = {
                    if (activity != null) {
                        BiometricAuthHelper.showBiometricPrompt(
                            activity = activity,
                            onSuccess = { isVaultUnlocked = true },
                            onError = { errMsg ->
                                Toast.makeText(context, errMsg, Toast.LENGTH_SHORT).show()
                            }
                        )
                    } else {
                        // Fallback in previews or non-activity contexts
                        isVaultUnlocked = true
                    }
                }
            )
        } else {
            MainContentScreen()
        }
    }
}

@Composable
fun VaultLockScreen(
    onUnlockRequested: () -> Unit
) {
    val context = LocalContext.current
    val encryptedStorage = remember { EncryptedStorage(context) }
    val serverConfig = remember { encryptedStorage.loadConfig() }

    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(NavyDarkBackground)
            .padding(24.dp),
        contentAlignment = Alignment.Center
    ) {
        Column(
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.Center,
            modifier = Modifier.fillMaxWidth()
        ) {
            // Shield & Fingerprint Visual Emblem
            Box(
                modifier = Modifier
                    .size(96.dp)
                    .clip(CircleShape)
                    .background(BurntOrangeBadgeBg)
                    .border(2.dp, BurntOrange, CircleShape),
                contentAlignment = Alignment.Center
            ) {
                Icon(
                    imageVector = Icons.Default.Fingerprint,
                    contentDescription = "Biometric Vault",
                    tint = BurntOrange,
                    modifier = Modifier.size(54.dp)
                )
            }

            Spacer(modifier = Modifier.height(24.dp))

            Text(
                text = "Hermes Vault",
                color = TextPrimary,
                fontSize = 24.sp,
                fontWeight = FontWeight.Bold,
                letterSpacing = 0.5.sp
            )

            Spacer(modifier = Modifier.height(8.dp))

            Text(
                text = "Autentikasi biometrik atau PIN/Pola perangkat diperlukan untuk mengakses terminal SSH dan kunci server.",
                color = TextSecondary,
                fontSize = 13.sp,
                lineHeight = 19.sp,
                textAlign = TextAlign.Center,
                modifier = Modifier.padding(horizontal = 16.dp)
            )

            Spacer(modifier = Modifier.height(24.dp))

            // Target Node Card Indicator
            Row(
                modifier = Modifier
                    .clip(RoundedCornerShape(12.dp))
                    .background(NavyDarkCard)
                    .border(1.dp, NavyDarkBorder, RoundedCornerShape(12.dp))
                    .padding(horizontal = 14.dp, vertical = 10.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                Icon(
                    imageVector = Icons.Default.Security,
                    contentDescription = null,
                    tint = TerminalGreen,
                    modifier = Modifier.size(16.dp)
                )
                Spacer(modifier = Modifier.width(8.dp))
                Text(
                    text = if (serverConfig.isSshConfigured) "Node: ${serverConfig.sshHost}" else "Node: Tailscale Encrypted",
                    color = TextPrimary,
                    fontSize = 12.sp,
                    fontFamily = FontFamily.Monospace
                )
            }

            Spacer(modifier = Modifier.height(32.dp))

            Button(
                onClick = onUnlockRequested,
                modifier = Modifier
                    .fillMaxWidth()
                    .height(50.dp)
                    .testTag("unlock_vault_button"),
                shape = RoundedCornerShape(14.dp),
                colors = ButtonDefaults.buttonColors(
                    containerColor = BurntOrange,
                    contentColor = NavyDarkBackground
                )
            ) {
                Icon(
                    imageVector = Icons.Default.Lock,
                    contentDescription = null,
                    tint = NavyDarkBackground,
                    modifier = Modifier.size(18.dp)
                )
                Spacer(modifier = Modifier.width(8.dp))
                Text(
                    text = "Buka Vault (Biometrik / PIN)",
                    fontWeight = FontWeight.Bold,
                    fontSize = 14.sp
                )
            }
        }
    }
}

@Composable
fun MainContentScreen() {
    val context = LocalContext.current
    val coroutineScope = rememberCoroutineScope()

    val encryptedStorage = remember { EncryptedStorage(context) }
    var serverConfig by remember { mutableStateOf(encryptedStorage.loadConfig()) }

    val terminalManager = remember { SshTerminalManager(coroutineScope) }
    val sftpManager = remember { SftpManager() }
    val hermesClient = remember { HermesApiClient() }
    val hermesMessages = remember { mutableStateListOf<ChatMessage>() }

    var selectedTab by remember { mutableStateOf(AppTab.TERMINAL) }

    // Cross-Integration Handler: Execute CLI command from Hermes directly in Terminal
    val handleExecuteInTerminal: (String) -> Unit = { command ->
        val active = terminalManager.activeSession.value
        if (active.connectionState.value !is TerminalConnectionState.Connected) {
            if (serverConfig.isSshConfigured) {
                active.connect(serverConfig)
            } else {
                active.startSandboxMode()
            }
        }
        active.sendCommand(command)
        selectedTab = AppTab.TERMINAL
        Toast.makeText(context, "Perintah dikirim ke Terminal", Toast.LENGTH_SHORT).show()
    }

    // Cross-Integration Handler: Analyze file from SFTP with Hermes Agent
    val handleAskHermesAboutFile: (String, String) -> Unit = { fileName, fileContent ->
        val prompt = "Tolong periksa dan jelaskan berkas '$fileName' berikut:\n\n```\n$fileContent\n```\nJelaskan fungsinya dan beri rekomendasi konfigurasi atau optimasi jika ada."
        val userMsg = ChatMessage(role = "user", content = prompt)
        hermesMessages.add(userMsg)
        selectedTab = AppTab.HERMES
        coroutineScope.launch {
            val result = hermesClient.sendMessage(
                userMessage = prompt,
                history = hermesMessages.dropLast(1),
                config = serverConfig,
                useSandboxFallback = !serverConfig.isHermesConfigured
            )
            result.onSuccess { reply ->
                hermesMessages.add(ChatMessage(role = "assistant", content = reply))
            }.onFailure { err ->
                hermesMessages.add(ChatMessage(
                    role = "assistant",
                    content = "Gagal memproses berkas: ${err.localizedMessage ?: "Unknown error"}",
                    isError = true
                ))
            }
        }
    }

    Scaffold(
        modifier = Modifier.fillMaxSize(),
        containerColor = NavyDarkBackground,
        contentWindowInsets = WindowInsets.statusBars,
        bottomBar = {
            TailHarnessBottomNav(
                selectedTab = selectedTab,
                onTabSelected = { selectedTab = it }
            )
        }
    ) { innerPadding ->
        Box(
            modifier = Modifier
                .fillMaxSize()
                .padding(innerPadding)
        ) {
            // Keep Terminal alive: SSH session, PTY buffer, and scroll NEVER disconnect on tab change
            Box(
                modifier = Modifier
                    .fillMaxSize()
                    .zIndex(if (selectedTab == AppTab.TERMINAL) 1f else 0f)
                    .graphicsLayer {
                        alpha = if (selectedTab == AppTab.TERMINAL) 1f else 0f
                        translationY = if (selectedTab == AppTab.TERMINAL) 0f else 99999f
                    }
            ) {
                TerminalScreen(
                    terminalManager = terminalManager,
                    serverConfig = serverConfig,
                    encryptedStorage = encryptedStorage
                )
            }

            // Keep Files alive: Directory location and loaded lists NEVER reset on tab change
            Box(
                modifier = Modifier
                    .fillMaxSize()
                    .zIndex(if (selectedTab == AppTab.FILES) 1f else 0f)
                    .graphicsLayer {
                        alpha = if (selectedTab == AppTab.FILES) 1f else 0f
                        translationY = if (selectedTab == AppTab.FILES) 0f else 99999f
                    }
            ) {
                FilesScreen(
                    sftpManager = sftpManager,
                    serverConfig = serverConfig,
                    onAskHermesAboutFile = handleAskHermesAboutFile
                )
            }

            // Keep Hermes Chat alive: Conversation history and user inputs NEVER reset on tab change
            Box(
                modifier = Modifier
                    .fillMaxSize()
                    .zIndex(if (selectedTab == AppTab.HERMES) 1f else 0f)
                    .graphicsLayer {
                        alpha = if (selectedTab == AppTab.HERMES) 1f else 0f
                        translationY = if (selectedTab == AppTab.HERMES) 0f else 99999f
                    }
            ) {
                HermesChatScreen(
                    hermesClient = hermesClient,
                    serverConfig = serverConfig,
                    sshTerminalManager = terminalManager,
                    encryptedStorage = encryptedStorage,
                    onConfigUpdated = { updatedConfig ->
                        serverConfig = updatedConfig
                    },
                    onExecuteInTerminal = handleExecuteInTerminal,
                    messages = hermesMessages
                )
            }

            // Keep Settings alive: Form values and test outcomes stay intact
            Box(
                modifier = Modifier
                    .fillMaxSize()
                    .zIndex(if (selectedTab == AppTab.SETTINGS) 1f else 0f)
                    .graphicsLayer {
                        alpha = if (selectedTab == AppTab.SETTINGS) 1f else 0f
                        translationY = if (selectedTab == AppTab.SETTINGS) 0f else 99999f
                    }
            ) {
                SettingsScreen(
                    encryptedStorage = encryptedStorage,
                    hermesClient = hermesClient,
                    onConfigUpdated = { updatedConfig ->
                        serverConfig = updatedConfig
                    }
                )
            }
        }
    }
}

@Composable
fun TailHarnessBottomNav(
    selectedTab: AppTab,
    onTabSelected: (AppTab) -> Unit,
    modifier: Modifier = Modifier
) {
    Box(
        modifier = modifier
            .fillMaxWidth()
            .background(NavyDarkSurface)
            .border(1.dp, NavyDarkBorder)
            .navigationBarsPadding()
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 10.dp, vertical = 8.dp),
            horizontalArrangement = Arrangement.SpaceAround,
            verticalAlignment = Alignment.CenterVertically
        ) {
            for (tab in AppTab.values()) {
                val isSelected = tab == selectedTab

                if (isSelected) {
                    // Active tab: dimmed orange capsule/pill highlight behind icon + label
                    Row(
                        modifier = Modifier
                            .clip(RoundedCornerShape(50))
                            .background(BurntOrangePillBg)
                            .border(1.dp, BurntOrangeBorder, RoundedCornerShape(50))
                            .clickable { onTabSelected(tab) }
                            .padding(horizontal = 14.dp, vertical = 8.dp)
                            .testTag(tab.tag),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.Center
                    ) {
                        Icon(
                            imageVector = tab.icon,
                            contentDescription = tab.title,
                            tint = BurntOrange,
                            modifier = Modifier.size(20.dp)
                        )
                        Spacer(modifier = Modifier.width(8.dp))
                        Text(
                            text = tab.title,
                            color = BurntOrange,
                            fontWeight = FontWeight.Bold,
                            fontSize = 12.sp
                        )
                    }
                } else {
                    // Inactive tab
                    Column(
                        modifier = Modifier
                            .clip(RoundedCornerShape(12.dp))
                            .clickable { onTabSelected(tab) }
                            .padding(horizontal = 14.dp, vertical = 6.dp)
                            .testTag(tab.tag),
                        horizontalAlignment = Alignment.CenterHorizontally,
                        verticalArrangement = Arrangement.Center
                    ) {
                        Icon(
                            imageVector = tab.icon,
                            contentDescription = tab.title,
                            tint = TextSecondary,
                            modifier = Modifier.size(20.dp)
                        )
                        Spacer(modifier = Modifier.height(2.dp))
                        Text(
                            text = tab.title,
                            color = TextSecondary,
                            fontSize = 11.sp,
                            fontWeight = FontWeight.Medium
                        )
                    }
                }
            }
        }
    }
}

// Retained for test suite compatibility
@Composable
fun Greeting(name: String, modifier: Modifier = Modifier) {
    Text(text = "Hello $name!", modifier = modifier)
}
