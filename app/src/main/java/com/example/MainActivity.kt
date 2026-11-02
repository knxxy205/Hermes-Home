package com.example

import android.os.Bundle
import android.widget.Toast
import androidx.activity.compose.BackHandler
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
import androidx.compose.runtime.collectAsState
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.TextButton
import androidx.compose.runtime.DisposableEffect
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
import androidx.compose.ui.graphics.Color
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.heightIn
import androidx.compose.material.icons.filled.Forum
import androidx.compose.material3.Surface
import androidx.activity.SystemBarStyle
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.layout.layout
import androidx.compose.foundation.Image
import androidx.compose.ui.res.painterResource
import androidx.compose.foundation.layout.ime
import androidx.compose.ui.platform.LocalDensity
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
    HERMES("Hermes", Icons.Default.Forum, "tab_hermes"),
    SETTINGS("Settings", Icons.Default.Settings, "tab_settings")
}

class MainActivity : FragmentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        com.example.data.security.SshTrust.initialize(applicationContext)
        if (android.os.Build.VERSION.SDK_INT >= 33) setRecentsScreenshotEnabled(false)
        enableEdgeToEdge(
            statusBarStyle = SystemBarStyle.light(android.graphics.Color.TRANSPARENT, android.graphics.Color.TRANSPARENT),
            navigationBarStyle = SystemBarStyle.light(android.graphics.Color.TRANSPARENT, android.graphics.Color.TRANSPARENT)
        )

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
    var backgroundAt by remember { mutableStateOf(0L) }
    val lifecycleOwner = androidx.lifecycle.compose.LocalLifecycleOwner.current
    DisposableEffect(lifecycleOwner) {
        val observer = androidx.lifecycle.LifecycleEventObserver { _, event ->
            if (event == androidx.lifecycle.Lifecycle.Event.ON_STOP) backgroundAt = android.os.SystemClock.elapsedRealtime()
            if (event == androidx.lifecycle.Lifecycle.Event.ON_START && backgroundAt > 0L &&
                android.os.SystemClock.elapsedRealtime() - backgroundAt >= 60_000L) isVaultUnlocked = false
        }
        lifecycleOwner.lifecycle.addObserver(observer)
        onDispose { lifecycleOwner.lifecycle.removeObserver(observer) }
    }

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
                    .clip(RoundedCornerShape(32.dp))
                    .background(BurntOrangeBadgeBg)
                    .border(1.dp, BurntOrangeBorder, RoundedCornerShape(32.dp)),
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

            Row(verticalAlignment = Alignment.CenterVertically) {
                Box(Modifier.size(76.dp).clip(RoundedCornerShape(20.dp)).background(BurntOrange)) {
                    Image(painterResource(R.drawable.homeserver_mark), "Homeserver Remote", Modifier.fillMaxSize())
                }
                Spacer(Modifier.width(16.dp))
                Column {
                    Text("Homeserver", color = TextPrimary, fontSize = 28.sp, fontWeight = FontWeight.ExtraBold, letterSpacing = (-1).sp)
                    Text("Remote", color = BurntOrange, fontSize = 28.sp, fontWeight = FontWeight.ExtraBold, letterSpacing = (-1).sp)
                }
            }

            Spacer(modifier = Modifier.height(8.dp))

            Text(
                text = "Server pribadi, dalam genggaman. Buka dengan sidik jari atau PIN perangkat untuk melanjutkan.",
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
                    text = if (serverConfig.isSshConfigured) serverConfig.sshHost else "Koneksi privat melalui Tailscale",
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
                    contentColor = Color.White
                )
            ) {
                Icon(
                    imageVector = Icons.Default.Lock,
                    contentDescription = null,
                    tint = Color.White,
                    modifier = Modifier.size(18.dp)
                )
                Spacer(modifier = Modifier.width(8.dp))
                Text(
                    text = "Buka homeserver",
                    fontWeight = FontWeight.Bold,
                    fontSize = 14.sp
                )
            }
        }
    }
}

@Composable
fun MainContentScreen() {
    val trustRequest by com.example.data.security.SshTrust.pending.collectAsState()
    trustRequest?.let { request ->
        AlertDialog(
            onDismissRequest = { request.answer.complete(false) },
            title = { Text("Verifikasi identitas server") },
            text = { Text(request.message + "\n\nCocokkan fingerprint ini dengan server kamu sebelum mempercayainya.") },
            confirmButton = { TextButton(onClick = { request.answer.complete(true) }) { Text("Percayai server") } },
            dismissButton = { TextButton(onClick = { request.answer.complete(false) }) { Text("Batal") } }
        )
    }
    val context = LocalContext.current
    val coroutineScope = rememberCoroutineScope()

    val encryptedStorage = remember { EncryptedStorage(context) }
    var serverConfig by remember { mutableStateOf(encryptedStorage.loadConfig()) }

    val terminalManager = remember { SshTerminalManager(coroutineScope) }
    DisposableEffect(terminalManager) {
        onDispose { terminalManager.sessions.value.forEach { it.destroy() } }
    }
    val sftpManager = remember { SftpManager() }
    DisposableEffect(sftpManager) { onDispose { sftpManager.close() } }
    val hermesClient = remember { HermesApiClient() }
    val hermesMessages = remember { mutableStateListOf<ChatMessage>() }

    var selectedTab by remember { mutableStateOf(AppTab.TERMINAL) }
    val density = LocalDensity.current
    val imeInsets = WindowInsets.ime
    val keyboardVisible by remember(density, imeInsets) { androidx.compose.runtime.derivedStateOf { imeInsets.getBottom(density) > 0 } }
    val terminalActive = selectedTab == AppTab.TERMINAL
    val activity = context as? androidx.activity.ComponentActivity
    DisposableEffect(terminalActive) {
        activity?.enableEdgeToEdge(
            statusBarStyle = if (terminalActive) SystemBarStyle.dark(android.graphics.Color.TRANSPARENT) else SystemBarStyle.light(android.graphics.Color.TRANSPARENT, android.graphics.Color.TRANSPARENT),
            navigationBarStyle = if (terminalActive) SystemBarStyle.dark(0xff141729.toInt()) else SystemBarStyle.light(android.graphics.Color.TRANSPARENT, android.graphics.Color.TRANSPARENT)
        )
        onDispose { }
    }

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

    // If user is on another tab and at root navigation, Back button returns to Terminal tab before exiting app
    BackHandler(enabled = selectedTab != AppTab.TERMINAL) {
        selectedTab = AppTab.TERMINAL
    }

    Scaffold(
        modifier = Modifier.fillMaxSize().imePadding(),
        containerColor = if (terminalActive) Color(0xff141729) else NavyDarkBackground,
        contentWindowInsets = WindowInsets.statusBars,
        bottomBar = {
            if (!keyboardVisible) TailHarnessBottomNav(
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
                    .activeScreen(selectedTab == AppTab.TERMINAL)
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
                    .activeScreen(selectedTab == AppTab.FILES)
            ) {
                FilesScreen(
                    sftpManager = sftpManager,
                    serverConfig = serverConfig,
                    onAskHermesAboutFile = handleAskHermesAboutFile,
                    onOpenSettings = {
                        selectedTab = AppTab.SETTINGS
                        serverConfig = encryptedStorage.loadConfig()
                    },
                    isTabActive = (selectedTab == AppTab.FILES)
                )
            }

            // Keep Hermes Chat alive: Conversation history and user inputs NEVER reset on tab change
            Box(
                modifier = Modifier
                    .fillMaxSize()
                    .activeScreen(selectedTab == AppTab.HERMES)
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
                    .activeScreen(selectedTab == AppTab.SETTINGS)
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

// Preserve screen state and running requests, but never measure or place hidden screens.
// Unlike alpha/translation, this avoids relaying out every form during IME animation.
private fun Modifier.activeScreen(active: Boolean): Modifier = this.then(
    Modifier.layout { measurable, constraints ->
        if (active) {
            val placeable = measurable.measure(constraints)
            layout(placeable.width, placeable.height) { placeable.placeRelative(0, 0) }
        } else {
            layout(0, 0) {}
        }
    }
)

@Composable
fun TailHarnessBottomNav(
    selectedTab: AppTab,
    onTabSelected: (AppTab) -> Unit,
    modifier: Modifier = Modifier
) {
    Surface(
        modifier = modifier
            .fillMaxWidth()
            .padding(horizontal = 16.dp, vertical = 8.dp)
            .navigationBarsPadding(),
        shape = RoundedCornerShape(28.dp),
        color = Color.White,
        shadowElevation = 6.dp,
        border = androidx.compose.foundation.BorderStroke(1.dp, Color(0xffE2E8F0))
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 6.dp, vertical = 6.dp),
            horizontalArrangement = Arrangement.SpaceAround,
            verticalAlignment = Alignment.CenterVertically
        ) {
            for (tab in AppTab.values()) {
                val isSelected = tab == selectedTab

                    Column(
                        modifier = Modifier
                            .weight(1f)
                            .heightIn(min = 56.dp)
                            .clip(RoundedCornerShape(16.dp))
                            .background(if (isSelected) Color(0xff3B82F6) else Color.Transparent)
                            .clickable { onTabSelected(tab) }
                            .padding(vertical = 8.dp)
                            .testTag(tab.tag),
                        horizontalAlignment = Alignment.CenterHorizontally,
                        verticalArrangement = Arrangement.Center
                    ) {
                        Icon(
                            imageVector = tab.icon,
                            contentDescription = tab.title,
                            tint = if (isSelected) Color.White else Color(0xff64748B),
                            modifier = Modifier.size(22.dp)
                        )
                        Spacer(modifier = Modifier.height(2.dp))
                        Text(
                            text = tab.title,
                            color = if (isSelected) Color.White else Color(0xff64748B),
                            fontSize = 11.sp,
                            fontWeight = FontWeight.Medium
                        )
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
