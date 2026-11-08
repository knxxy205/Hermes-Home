package com.example

import android.os.Bundle
import android.widget.Toast
import androidx.activity.compose.BackHandler
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.ime
import androidx.compose.foundation.layout.windowInsetsPadding
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBars
import androidx.compose.foundation.layout.safeDrawingPadding
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.FolderOpen
import androidx.compose.material.icons.filled.Settings
import androidx.compose.material.icons.filled.SmartToy
import androidx.compose.material.icons.filled.Terminal
import androidx.compose.material.icons.filled.Lock
import androidx.compose.material.icons.filled.Security
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.LaunchedEffect
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
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.withTimeoutOrNull
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.scale
import androidx.compose.ui.graphics.Color
import androidx.compose.foundation.layout.heightIn
import androidx.compose.material.icons.filled.Forum
import androidx.compose.material3.Surface
import androidx.activity.SystemBarStyle
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.SpanStyle
import androidx.compose.ui.text.buildAnnotatedString
import androidx.compose.ui.text.withStyle
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.layout.layout
import androidx.compose.ui.layout.onSizeChanged
import androidx.compose.foundation.Image
import androidx.compose.ui.res.painterResource
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
import com.example.ui.theme.IsDarkAppearance
import com.example.ui.theme.resolveDarkAppearance
import kotlinx.coroutines.launch
import com.example.ui.i18n.tr
import androidx.compose.animation.core.updateTransition
import com.example.ui.navigation.LiquidGlassBottomNav
import com.example.ui.navigation.navigationPage
import com.example.ui.navigation.rememberNavigationAnimationsEnabled
import com.example.ui.navigation.navigationContentPadding

enum class AppTab(val title: String, val icon: ImageVector, val tag: String) {
    TERMINAL("Terminal", Icons.Default.Terminal, "tab_terminal"),
    FILES("Files", Icons.Default.FolderOpen, "tab_files"),
    HERMES("Hermes", Icons.Default.Forum, "tab_hermes"),
    SETTINGS("Settings", Icons.Default.Settings, "tab_settings")
}

class MainActivity : androidx.appcompat.app.AppCompatActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        val themeMode = getSharedPreferences("hermes_app_preferences", MODE_PRIVATE).getString("theme_mode", "system") ?: "system"
        delegate.localNightMode = when (themeMode) {
            "light" -> androidx.appcompat.app.AppCompatDelegate.MODE_NIGHT_NO
            "dark" -> androidx.appcompat.app.AppCompatDelegate.MODE_NIGHT_YES
            else -> androidx.appcompat.app.AppCompatDelegate.MODE_NIGHT_FOLLOW_SYSTEM
        }
        super.onCreate(savedInstanceState)
        val language = getSharedPreferences("hermes_app_preferences", MODE_PRIVATE).getString("app_language", "id") ?: "id"
        val wanted = androidx.core.os.LocaleListCompat.forLanguageTags(if (language == "system") "" else language)
        if (androidx.appcompat.app.AppCompatDelegate.getApplicationLocales() != wanted) androidx.appcompat.app.AppCompatDelegate.setApplicationLocales(wanted)
        com.example.data.transfer.TransferNavigation.accept(intent, this)
        com.example.data.security.SshTrust.initialize(applicationContext)
        com.example.data.transfer.UploadCoordinator.observe(applicationContext)
        if (android.os.Build.VERSION.SDK_INT >= 33) setRecentsScreenshotEnabled(false)
        val dark = resolveDarkAppearance(themeMode, resources.configuration.uiMode and android.content.res.Configuration.UI_MODE_NIGHT_MASK == android.content.res.Configuration.UI_MODE_NIGHT_YES)
        val barStyle = if (dark) SystemBarStyle.dark(android.graphics.Color.TRANSPARENT)
            else SystemBarStyle.light(android.graphics.Color.TRANSPARENT, 0xff101722.toInt())
        enableEdgeToEdge(statusBarStyle = barStyle, navigationBarStyle = barStyle)
        requestHighestSupportedRefreshRate()

        // Schedule periodic 1-hour background server health check
        ServerHealthWorker.schedulePeriodicCheck(applicationContext)

        setContent {
            MyApplicationTheme {
                TailHarnessApp(activity = this)
            }
        }
    }

    override fun onNewIntent(intent: android.content.Intent) {
        super.onNewIntent(intent)
        setIntent(intent)
        com.example.data.transfer.TransferNavigation.accept(intent, this)
    }

    @Suppress("DEPRECATION")
    private fun requestHighestSupportedRefreshRate() {
        val currentDisplay = if (android.os.Build.VERSION.SDK_INT >= 30) display else windowManager.defaultDisplay
        val currentMode = currentDisplay?.mode ?: return
        val preferredMode = currentDisplay.supportedModes
            .filter { it.physicalWidth == currentMode.physicalWidth && it.physicalHeight == currentMode.physicalHeight }
            .maxByOrNull { it.refreshRate } ?: return
        if (preferredMode.refreshRate <= 60f) return
        window.attributes = window.attributes.apply {
            preferredDisplayModeId = preferredMode.modeId
            preferredRefreshRate = preferredMode.refreshRate
        }
    }
}

@Composable
fun TailHarnessApp(activity: androidx.fragment.app.FragmentActivity? = null) {
    val context = LocalContext.current
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
    if (isVaultUnlocked) MainContentScreen() else VaultLockScreen {
            if (activity == null) isVaultUnlocked = true
            else BiometricAuthHelper.showBiometricPrompt(
                activity,
                onSuccess = { isVaultUnlocked = true },
                onError = { Toast.makeText(context, it, Toast.LENGTH_SHORT).show() }
            )
    }
}

@Composable
fun VaultLockScreen(onUnlockRequested: () -> Unit) {
    val context = LocalContext.current
    val config = remember { EncryptedStorage(context).loadConfig() }
    val dark = IsDarkAppearance
    DisposableEffect(context, dark) {
        val barStyle = if (dark) SystemBarStyle.dark(android.graphics.Color.TRANSPARENT)
            else SystemBarStyle.light(android.graphics.Color.TRANSPARENT, 0xff101722.toInt())
        (context as? androidx.activity.ComponentActivity)?.enableEdgeToEdge(statusBarStyle = barStyle, navigationBarStyle = barStyle)
        onDispose { }
    }
    BoxWithConstraints(Modifier.fillMaxSize().background(NavyDarkBackground).safeDrawingPadding(), contentAlignment = Alignment.Center) {
        val titleSize = if (maxWidth < 360.dp) 32.sp else 36.sp
        val minimumHeight = maxHeight
        Column(
            Modifier.widthIn(max = 520.dp).fillMaxWidth()
                .verticalScroll(rememberScrollState()).heightIn(min = minimumHeight)
                .padding(horizontal = 26.dp, vertical = 24.dp),
            horizontalAlignment = Alignment.CenterHorizontally
        ) {
            Spacer(Modifier.weight(1.3f))
            Box(Modifier.size(98.dp).clip(RoundedCornerShape(30.dp)).background(com.example.ui.theme.PrimaryBlue)) {
                Image(painterResource(R.drawable.homeserver_mark), "Hermes Home", Modifier.fillMaxSize().scale(1.6f))
            }
            Spacer(Modifier.height(24.dp))
            Text(buildAnnotatedString {
                append("Hermes ")
                withStyle(SpanStyle(color = BurntOrange)) { append("Home") }
            }, color = TextPrimary, fontSize = titleSize,
                fontWeight = FontWeight.ExtraBold, letterSpacing = (-1).sp, textAlign = TextAlign.Center)
            Spacer(Modifier.height(10.dp))
            Text(tr("Server pribadi, dalam genggaman.\nBuka dengan sidik jari atau PIN perangkat.", "Your server, in your hand.\nUnlock with your fingerprint or device PIN."),
                color = TextSecondary, fontSize = 14.sp, lineHeight = 20.sp, textAlign = TextAlign.Center)
            Spacer(Modifier.height(24.dp))
            Row(Modifier.clip(RoundedCornerShape(50)).background(NavyDarkCard).border(1.dp, NavyDarkBorder, RoundedCornerShape(50))
                .padding(horizontal = 18.dp, vertical = 10.dp), verticalAlignment = Alignment.CenterVertically) {
                Icon(Icons.Default.Security, null, tint = TerminalGreen, modifier = Modifier.size(16.dp))
                Spacer(Modifier.width(8.dp))
                Text(if (config.isSshConfigured) config.sshHost else tr("Koneksi privat melalui Tailscale", "Private connection through Tailscale"), color = TextPrimary,
                    fontSize = 15.sp, modifier = Modifier.weight(1f, fill = false), textAlign = TextAlign.Center)
            }
            Spacer(Modifier.height(32.dp))
            Spacer(Modifier.weight(1f))
            Button(onClick = onUnlockRequested, modifier = Modifier.fillMaxWidth().heightIn(min = 56.dp).testTag("unlock_vault_button"),
                shape = RoundedCornerShape(20.dp), colors = ButtonDefaults.buttonColors(containerColor = BurntOrange, contentColor = MaterialTheme.colorScheme.onPrimary)) {
                Icon(Icons.Default.Lock, null, tint = MaterialTheme.colorScheme.onPrimary, modifier = Modifier.size(24.dp))
                Spacer(Modifier.width(8.dp))
                Text(tr("Buka Hermes Home", "Open Hermes Home"), fontWeight = FontWeight.Bold, fontSize = 18.sp, textAlign = TextAlign.Center)
            }
            Spacer(Modifier.height(14.dp))
            Text(tr("Akses lewat jaringan privat", "Access over a private network"), color = TextSecondary,
                fontSize = 12.sp, textAlign = TextAlign.Center)
        }
    }
}

@Composable
fun MainContentScreen() {
    val trustRequest by com.example.data.security.SshTrust.pending.collectAsState()
    trustRequest?.let { request ->
        AlertDialog(
            onDismissRequest = { request.answer.complete(false) },
            title = { Text(tr("Verifikasi identitas server", "Verify server identity")) },
            text = { Text(request.message + tr("\n\nCocokkan fingerprint ini dengan server kamu sebelum mempercayainya.", "\n\nCheck this fingerprint against your server before trusting it.")) },
            confirmButton = { TextButton(onClick = { request.answer.complete(true) }) { Text(tr("Percayai server", "Trust server")) } },
            dismissButton = { TextButton(onClick = { request.answer.complete(false) }) { Text(tr("Batal", "Cancel")) } }
        )
    }
    val context = LocalContext.current
    val coroutineScope = rememberCoroutineScope()

    val encryptedStorage = remember { EncryptedStorage(context) }
    val preferencesStore = remember { com.example.data.preferences.AppPreferencesStore(context) }
    val preferences by preferencesStore.state.collectAsState()
    DisposableEffect(preferencesStore) { onDispose { preferencesStore.close() } }
    var serverConfig by remember { mutableStateOf(encryptedStorage.loadConfig()) }

    val terminalManager = remember { com.example.data.ssh.SshSessionHost.manager(context) }
    val sftpManager = remember { SftpManager() }
    DisposableEffect(sftpManager) { onDispose { sftpManager.close() } }
    val hermesClient = remember { HermesApiClient() }
    val hermesMessages = remember { mutableStateListOf<ChatMessage>() }

    var selectedTab by androidx.compose.runtime.saveable.rememberSaveable { mutableStateOf(AppTab.TERMINAL) }
    val navigationAnimations = rememberNavigationAnimationsEnabled()
    val pageTransition = updateTransition(selectedTab, label = "main-tab-pages")
    var settingsSection by androidx.compose.runtime.saveable.rememberSaveable { mutableStateOf("app") }
    val transferDestination by com.example.data.transfer.TransferNavigation.destination.collectAsState()
    LaunchedEffect(transferDestination) { if (transferDestination != null) selectedTab = AppTab.FILES }
    val density = LocalDensity.current
    val imeInsets = WindowInsets.ime
    val keyboardVisible by remember(density) {
        androidx.compose.runtime.derivedStateOf { imeInsets.getBottom(density) > 0 }
    }
    val terminalActive = selectedTab == AppTab.TERMINAL
    val darkAppearance = IsDarkAppearance
    var navigationHeight by remember { mutableStateOf(84.dp) }
    val activity = context as? androidx.activity.ComponentActivity
    DisposableEffect(terminalActive, darkAppearance) {
        activity?.enableEdgeToEdge(
            statusBarStyle = if (terminalActive || darkAppearance) SystemBarStyle.dark(android.graphics.Color.TRANSPARENT) else SystemBarStyle.light(android.graphics.Color.TRANSPARENT, 0xff101722.toInt()),
            navigationBarStyle = if (terminalActive || darkAppearance) SystemBarStyle.dark(android.graphics.Color.TRANSPARENT) else SystemBarStyle.light(android.graphics.Color.TRANSPARENT, 0xff101722.toInt())
        )
        if (android.os.Build.VERSION.SDK_INT >= 29) {
            activity?.window?.isNavigationBarContrastEnforced = false
        }
        onDispose { }
    }

    // Cross-Integration Handler: Execute CLI command from Hermes directly in Terminal
    val handleExecuteInTerminal: (String) -> Unit = { command ->
        val active = terminalManager.ensureActiveSession()
        selectedTab = AppTab.TERMINAL
        coroutineScope.launch {
            if (active.connectionState.value !is TerminalConnectionState.Connected) {
                val target = active.lastConfig ?: serverConfig
                if (target.isSshConfigured) active.connect(target) else active.startSandboxMode()
            }
            val connected = withTimeoutOrNull(20_000) {
                active.connectionState.first { it is TerminalConnectionState.Connected || it is TerminalConnectionState.Error }
            }
            if (connected is TerminalConnectionState.Connected) {
                active.sendCommand(command)
                Toast.makeText(context, "Perintah dikirim ke Terminal", Toast.LENGTH_SHORT).show()
            } else Toast.makeText(context, "Perintah belum dikirim. Periksa koneksi terminal.", Toast.LENGTH_LONG).show()
        }
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
                useSandboxFallback = !serverConfig.isHermesConfigured,
                responseInstructions = preferences.responseInstructions()
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
        modifier = Modifier.fillMaxSize(),
        containerColor = if (terminalActive) Color(0xff141729) else NavyDarkBackground,
        contentWindowInsets = WindowInsets.statusBars
    ) { innerPadding ->
        Box(
            modifier = Modifier
                .fillMaxSize()
                .padding(innerPadding)
        ) {
            Box(Modifier.fillMaxSize()) {
            // Keep Terminal alive: SSH session, PTY buffer, and scroll NEVER disconnect on tab change
            Box(
                modifier = Modifier
                    .fillMaxSize()
                    .navigationPage(AppTab.TERMINAL, pageTransition, navigationAnimations)
                    .background(Color(0xff141729))
                    .navigationContentPadding(navigationHeight, WindowInsets.ime)
            ) {
                TerminalScreen(
                    terminalManager = terminalManager,
                    serverConfig = serverConfig,
                    encryptedStorage = encryptedStorage,
                    preferences = preferences,
                    onPreferencesChange = preferencesStore::update,
                    onOpenSettings = { settingsSection = "app"; selectedTab = AppTab.SETTINGS },
                    isTabActive = terminalActive
                )
            }

            // Keep Files alive: Directory location and loaded lists NEVER reset on tab change
            Box(
                modifier = Modifier
                    .fillMaxSize()
                    .navigationPage(AppTab.FILES, pageTransition, navigationAnimations)
                    .background(NavyDarkBackground)
                    .windowInsetsPadding(WindowInsets.ime)
            ) {
                FilesScreen(
                    sftpManager = sftpManager,
                    serverConfig = serverConfig,
                    onAskHermesAboutFile = handleAskHermesAboutFile,
                    onOpenSettings = {
                        settingsSection = "connection"
                        selectedTab = AppTab.SETTINGS
                        serverConfig = encryptedStorage.loadConfig()
                    },
                    isTabActive = (selectedTab == AppTab.FILES),
                    preferencesStore = preferencesStore,
                    bottomOverlayInset = if (keyboardVisible) 0.dp else navigationHeight,
                    transferDestination = transferDestination,
                    onTransferDestinationHandled = { com.example.data.transfer.TransferNavigation.consumed() },
                    onSelectTransferProfile = { profileId ->
                        encryptedStorage.loadProfiles().firstOrNull { it.id == profileId }?.let { profile ->
                            encryptedStorage.setActiveProfileId(profile.id)
                            serverConfig = profile.config
                            profile.config
                        }
                    }
                )
            }

            // Keep Hermes Chat alive: Conversation history and user inputs NEVER reset on tab change
            Box(
                modifier = Modifier
                    .fillMaxSize()
                    .navigationPage(AppTab.HERMES, pageTransition, navigationAnimations)
                    .background(NavyDarkBackground)
                    .navigationContentPadding(navigationHeight, WindowInsets.ime)
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
                    messages = hermesMessages,
                    preferences = preferences,
                    onOpenSettings = { settingsSection = "app"; selectedTab = AppTab.SETTINGS },
                    onOpenConnectionSettings = { settingsSection = "connection"; selectedTab = AppTab.SETTINGS },
                    onPreferencesUpdated = preferencesStore::update
                )
            }

            // Keep Settings alive: Form values and test outcomes stay intact
            Box(
                modifier = Modifier
                    .fillMaxSize()
                    .navigationPage(AppTab.SETTINGS, pageTransition, navigationAnimations)
                    .background(NavyDarkBackground)
                    .windowInsetsPadding(WindowInsets.ime)
            ) {
                SettingsScreen(
                    encryptedStorage = encryptedStorage,
                    hermesClient = hermesClient,
                    preferencesStore = preferencesStore,
                    bottomOverlayInset = if (keyboardVisible) 0.dp else navigationHeight,
                    section = settingsSection,
                    onSectionChange = { settingsSection = it },
                    externalConfig = serverConfig,
                    onConfigUpdated = { updatedConfig ->
                        serverConfig = updatedConfig
                    }
                )
            }
            }
            if (!keyboardVisible) TailHarnessBottomNav(
                selectedTab = selectedTab,
                onTabSelected = { selectedTab = it },
                animationsEnabled = navigationAnimations,
                modifier = Modifier.align(Alignment.BottomCenter).onSizeChanged { size ->
                    navigationHeight = with(density) { size.height.toDp() }
                }
            )
        }
    }
}

@Composable
fun TailHarnessBottomNav(
    selectedTab: AppTab,
    onTabSelected: (AppTab) -> Unit,
    modifier: Modifier = Modifier,
    animationsEnabled: Boolean = rememberNavigationAnimationsEnabled()
) {
    LiquidGlassBottomNav(selectedTab, onTabSelected, modifier, animationsEnabled = animationsEnabled)
}

// Retained for test suite compatibility
@Composable
fun Greeting(name: String, modifier: Modifier = Modifier) {
    Text(text = "Hello $name!", modifier = modifier)
}
