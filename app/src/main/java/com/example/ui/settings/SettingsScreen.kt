package com.example.ui.settings

import android.widget.Toast
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.AutoAwesome
import androidx.compose.material.icons.filled.CheckCircle
import androidx.compose.material.icons.filled.DeleteOutline
import androidx.compose.material.icons.filled.Dns
import androidx.compose.material.icons.filled.Home
import androidx.compose.material.icons.filled.Key
import androidx.compose.material.icons.filled.Lock
import androidx.compose.material.icons.filled.NetworkCheck
import androidx.compose.material.icons.filled.Public
import androidx.compose.material.icons.filled.Save
import androidx.compose.material.icons.filled.Settings
import androidx.compose.material.icons.filled.SmartToy
import androidx.compose.material.icons.filled.Visibility
import androidx.compose.material.icons.filled.VisibilityOff
import androidx.compose.material.icons.filled.VpnKey
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.OutlinedTextFieldDefaults
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.text.input.VisualTransformation
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.data.hermes.HermesApiClient
import com.example.data.model.ConnectionProfile
import com.example.data.model.ServerConfig
import com.example.data.security.EncryptedStorage
import com.example.ui.components.CustomScreenHeader
import com.example.ui.components.PillChip
import com.example.ui.components.StatusBadge
import com.example.ui.components.TailHarnessCard
import com.example.ui.theme.BurntOrange
import com.example.ui.theme.BurntOrangeBadgeBg
import com.example.ui.theme.BurntOrangeBorder
import com.example.ui.theme.NavyCardBorder
import com.example.ui.theme.NavyDarkBackground
import com.example.ui.theme.NavyDarkBorder
import com.example.ui.theme.NavyDarkCard
import com.example.ui.theme.NavyDarkSurface
import com.example.ui.theme.TerminalCyan
import com.example.ui.theme.TerminalGreen
import com.example.ui.theme.TerminalRed
import com.example.ui.theme.TextPrimary
import com.example.ui.theme.TextSecondary
import com.jcraft.jsch.JSch
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

@Composable
fun SettingsScreen(
    encryptedStorage: EncryptedStorage,
    hermesClient: HermesApiClient,
    onConfigUpdated: (ServerConfig) -> Unit,
    modifier: Modifier = Modifier
) {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    val scrollState = rememberScrollState()

    var profiles by remember { mutableStateOf(encryptedStorage.loadProfiles()) }
    var activeProfileId by remember { mutableStateOf(encryptedStorage.getActiveProfileId()) }

    val activeProfile = profiles.find { it.id == activeProfileId } ?: profiles.firstOrNull() ?: ConnectionProfile.defaultHomeProfile()

    var profileName by remember(activeProfileId) { mutableStateOf(activeProfile.name) }
    var host by remember(activeProfileId) { mutableStateOf(activeProfile.config.sshHost) }
    var portText by remember(activeProfileId) { mutableStateOf(activeProfile.config.sshPort.toString()) }
    var user by remember(activeProfileId) { mutableStateOf(activeProfile.config.sshUser) }
    var password by remember(activeProfileId) { mutableStateOf(activeProfile.config.sshPassword) }
    var passwordVisible by remember { mutableStateOf(false) }

    var hermesUrl by remember(activeProfileId) { mutableStateOf(activeProfile.config.hermesBaseUrl) }
    var hermesKey by remember(activeProfileId) { mutableStateOf(activeProfile.config.hermesApiKey) }
    var hermesKeyVisible by remember { mutableStateOf(false) }
    var hermesModel by remember(activeProfileId) { mutableStateOf(activeProfile.config.hermesModel) }

    var isTestingSsh by remember { mutableStateOf(false) }
    var sshTestResult by remember { mutableStateOf<Pair<Boolean, String>?>(null) }

    var isTestingHermes by remember { mutableStateOf(false) }
    var hermesTestResult by remember { mutableStateOf<Pair<Boolean, String>?>(null) }

    var isDetectingModels by remember { mutableStateOf(false) }
    var detectedModels by remember { mutableStateOf<List<String>>(emptyList()) }

    fun buildCurrentConfig(): ServerConfig {
        val parsedPort = portText.toIntOrNull() ?: 22
        return ServerConfig(
            sshHost = host.trim(),
            sshPort = parsedPort,
            sshUser = user.trim(),
            sshPassword = password,
            hermesBaseUrl = hermesUrl.trim(),
            hermesApiKey = hermesKey.trim(),
            hermesModel = hermesModel.trim()
        )
    }

    fun selectProfile(newId: String) {
        if (newId == activeProfileId) return
        // Auto-save current profile draft before switching
        val currentDraft = activeProfile.copy(name = profileName.ifBlank { "Profil $activeProfileId" }, config = buildCurrentConfig())
        encryptedStorage.saveProfile(currentDraft)

        encryptedStorage.setActiveProfileId(newId)
        activeProfileId = newId
        profiles = encryptedStorage.loadProfiles()
        val switched = profiles.find { it.id == newId } ?: profiles.first()
        profileName = switched.name
        host = switched.config.sshHost
        portText = switched.config.sshPort.toString()
        user = switched.config.sshUser
        password = switched.config.sshPassword
        hermesUrl = switched.config.hermesBaseUrl
        hermesKey = switched.config.hermesApiKey
        hermesModel = switched.config.hermesModel
        sshTestResult = null
        hermesTestResult = null
        detectedModels = emptyList()

        onConfigUpdated(switched.config)
        Toast.makeText(context, "Beralih ke profil: ${switched.name}", Toast.LENGTH_SHORT).show()
    }

    fun addNewProfile() {
        // Save current draft first
        val currentDraft = activeProfile.copy(name = profileName.ifBlank { "Profil $activeProfileId" }, config = buildCurrentConfig())
        encryptedStorage.saveProfile(currentDraft)

        val newId = "profile_${System.currentTimeMillis()}"
        val newProfile = ConnectionProfile(
            id = newId,
            name = "Profil Baru (${profiles.size + 1})",
            config = ServerConfig(
                sshHost = "192.168.1.1",
                sshPort = 22,
                sshUser = "admin",
                sshPassword = "",
                hermesBaseUrl = "http://192.168.1.1:11434",
                hermesApiKey = "",
                hermesModel = "hermes-3-llama-3.1-8b"
            )
        )
        encryptedStorage.saveProfile(newProfile)
        encryptedStorage.saveProfileIds(profiles.map { it.id } + newId)
        profiles = encryptedStorage.loadProfiles()
        selectProfile(newId)
        Toast.makeText(context, "Profil baru ditambahkan", Toast.LENGTH_SHORT).show()
    }

    fun deleteCurrentProfile() {
        if (profiles.size <= 1) {
            Toast.makeText(context, "Minimal harus ada satu profil tersisa", Toast.LENGTH_SHORT).show()
            return
        }
        val toDelete = activeProfileId
        val remaining = profiles.filter { it.id != toDelete }
        encryptedStorage.deleteProfile(toDelete)
        val nextId = remaining.first().id
        encryptedStorage.setActiveProfileId(nextId)
        activeProfileId = nextId
        profiles = remaining
        val next = remaining.first()
        profileName = next.name
        host = next.config.sshHost
        portText = next.config.sshPort.toString()
        user = next.config.sshUser
        password = next.config.sshPassword
        hermesUrl = next.config.hermesBaseUrl
        hermesKey = next.config.hermesApiKey
        hermesModel = next.config.hermesModel
        sshTestResult = null
        hermesTestResult = null
        onConfigUpdated(next.config)
        Toast.makeText(context, "Profil dihapus", Toast.LENGTH_SHORT).show()
    }

    fun saveSettings() {
        val config = buildCurrentConfig()
        val updatedProfile = activeProfile.copy(name = profileName.ifBlank { "Profil $activeProfileId" }, config = config)
        encryptedStorage.saveProfile(updatedProfile)
        encryptedStorage.saveConfig(config)
        profiles = encryptedStorage.loadProfiles()
        onConfigUpdated(config)
        Toast.makeText(context, "Kredensial '${updatedProfile.name}' berhasil disimpan ke Keystore!", Toast.LENGTH_SHORT).show()
    }

    Column(
        modifier = modifier
            .fillMaxSize()
            .background(NavyDarkBackground)
    ) {
        CustomScreenHeader(
            icon = Icons.Default.Settings,
            title = "Settings",
            subtitle = "Kredensial & Jaringan Privat Tailscale"
        )

        Column(
            modifier = Modifier
                .weight(1f)
                .fillMaxWidth()
                .verticalScroll(scrollState)
                .padding(horizontal = 16.dp, vertical = 8.dp),
            verticalArrangement = Arrangement.spacedBy(16.dp)
        ) {
            // Profile Selector Card (Rumah LAN vs Di Luar Tailscale)
            TailHarnessCard(
                backgroundColor = NavyDarkCard,
                borderColor = BurntOrange.copy(alpha = 0.5f)
            ) {
                Column(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(14.dp),
                    verticalArrangement = Arrangement.spacedBy(12.dp)
                ) {
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Icon(Icons.Default.Dns, contentDescription = null, tint = BurntOrange, modifier = Modifier.size(20.dp))
                            Spacer(modifier = Modifier.width(8.dp))
                            Text(
                                text = "Profil Koneksi",
                                color = TextPrimary,
                                fontWeight = FontWeight.Bold,
                                fontSize = 15.sp
                            )
                        }
                        StatusBadge(
                            text = if (activeProfile.id == ConnectionProfile.PROFILE_HOME_ID) "LAN RUMAH" else if (activeProfile.id == ConnectionProfile.PROFILE_TAILSCALE_ID) "TAILSCALE" else "KUSTOM",
                            isSuccess = true
                        )
                    }

                    Text(
                        text = "Simpan 2 profil kredensial terpisah: untuk di rumah (LAN) dan di luar via Tailscale VPN. Ganti profil dengan 1 sentuhan:",
                        color = TextSecondary,
                        fontSize = 12.sp,
                        lineHeight = 16.sp
                    )

                    // Profiles Horizontal Chips Bar
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .horizontalScroll(rememberScrollState()),
                        horizontalArrangement = Arrangement.spacedBy(8.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        profiles.forEach { profile ->
                            val isSelected = profile.id == activeProfileId
                            Row(
                                modifier = Modifier
                                    .clip(RoundedCornerShape(12.dp))
                                    .background(if (isSelected) BurntOrangeBadgeBg else NavyDarkSurface)
                                    .border(
                                        width = if (isSelected) 1.5.dp else 1.dp,
                                        color = if (isSelected) BurntOrange else NavyDarkBorder,
                                        shape = RoundedCornerShape(12.dp)
                                    )
                                    .clickable { selectProfile(profile.id) }
                                    .padding(horizontal = 12.dp, vertical = 8.dp),
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                Icon(
                                    imageVector = if (profile.id == ConnectionProfile.PROFILE_HOME_ID) Icons.Default.Home else Icons.Default.Public,
                                    contentDescription = null,
                                    tint = if (isSelected) BurntOrange else TextSecondary,
                                    modifier = Modifier.size(16.dp)
                                )
                                Spacer(modifier = Modifier.width(6.dp))
                                Text(
                                    text = profile.name,
                                    color = if (isSelected) BurntOrange else TextSecondary,
                                    fontWeight = if (isSelected) FontWeight.Bold else FontWeight.Normal,
                                    fontSize = 13.sp
                                )
                            }
                        }

                        // Add Profile Button
                        OutlinedButton(
                            onClick = { addNewProfile() },
                            modifier = Modifier.height(36.dp),
                            shape = RoundedCornerShape(12.dp),
                            colors = ButtonDefaults.outlinedButtonColors(contentColor = BurntOrange),
                            border = BorderStroke(1.dp, BurntOrange.copy(alpha = 0.5f)),
                            contentPadding = PaddingValues(horizontal = 10.dp, vertical = 0.dp)
                        ) {
                            Icon(Icons.Default.Add, contentDescription = null, modifier = Modifier.size(16.dp), tint = BurntOrange)
                            Spacer(modifier = Modifier.width(4.dp))
                            Text("Tambah", fontSize = 12.sp, color = BurntOrange)
                        }
                    }

                    // Edit Active Profile Name Row
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        OutlinedTextField(
                            value = profileName,
                            onValueChange = { profileName = it },
                            label = { Text("Label Profil Aktif", color = TextSecondary) },
                            singleLine = true,
                            colors = OutlinedTextFieldDefaults.colors(
                                focusedBorderColor = BurntOrange,
                                unfocusedBorderColor = NavyDarkBorder,
                                focusedTextColor = TextPrimary,
                                unfocusedTextColor = TextPrimary,
                                focusedLabelColor = BurntOrange
                            ),
                            modifier = Modifier.weight(1f)
                        )

                        if (profiles.size > 1) {
                            Spacer(modifier = Modifier.width(8.dp))
                            IconButton(
                                onClick = { deleteCurrentProfile() },
                                modifier = Modifier
                                    .size(44.dp)
                                    .clip(RoundedCornerShape(12.dp))
                                    .background(NavyDarkSurface)
                                    .border(1.dp, TerminalRed.copy(alpha = 0.5f), RoundedCornerShape(12.dp))
                            ) {
                                Icon(Icons.Default.DeleteOutline, contentDescription = "Hapus Profil", tint = TerminalRed)
                            }
                        }
                    }
                }
            }
            // Security Info Card (Android Keystore + Hardware-backed encryption)
            TailHarnessCard(
                backgroundColor = NavyDarkCard,
                borderColor = NavyCardBorder
            ) {
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(14.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Box(
                        modifier = Modifier
                            .size(42.dp)
                            .clip(RoundedCornerShape(12.dp))
                            .background(BurntOrangeBadgeBg)
                            .border(1.dp, BurntOrangeBorder, RoundedCornerShape(12.dp)),
                        contentAlignment = Alignment.Center
                    ) {
                        Icon(Icons.Default.Lock, contentDescription = null, tint = BurntOrange, modifier = Modifier.size(20.dp))
                    }
                    Spacer(modifier = Modifier.width(12.dp))
                    Column(modifier = Modifier.weight(1f)) {
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Text(
                                text = "Keystore Enkripsi AES-256",
                                color = TextPrimary,
                                fontWeight = FontWeight.Bold,
                                fontSize = 13.sp
                            )
                            Spacer(modifier = Modifier.width(8.dp))
                            StatusBadge(text = "HARDWARE SECURE", isSuccess = true)
                        }
                        Spacer(modifier = Modifier.height(2.dp))
                        Text(
                            text = "Kredensial disimpan terenkripsi di chip keamanan HP. Tidak pernah dikirim ke pihak ketiga selain homeserver Anda.",
                            color = TextSecondary,
                            fontSize = 11.sp,
                            lineHeight = 15.sp
                        )
                    }
                }
            }

            // SECTION 1: HOMESERVER SSH VIA TAILSCALE
            TailHarnessCard(
                backgroundColor = NavyDarkCard,
                borderColor = NavyCardBorder
            ) {
                Column(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(16.dp),
                    verticalArrangement = Arrangement.spacedBy(12.dp)
                ) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Box(
                            modifier = Modifier
                                .size(32.dp)
                                .clip(RoundedCornerShape(8.dp))
                                .background(BurntOrangeBadgeBg)
                                .border(1.dp, BurntOrangeBorder, RoundedCornerShape(8.dp)),
                            contentAlignment = Alignment.Center
                        ) {
                            Icon(Icons.Default.Dns, contentDescription = null, tint = BurntOrange, modifier = Modifier.size(16.dp))
                        }
                        Spacer(modifier = Modifier.width(10.dp))
                        Column {
                            Text(
                                text = "Koneksi SSH Homeserver",
                                color = TextPrimary,
                                fontWeight = FontWeight.Bold,
                                fontSize = 15.sp
                            )
                            Text(
                                text = "Gunakan IP Tailscale (100.x.y.z) atau MagicDNS host",
                                color = TextSecondary,
                                fontSize = 11.sp
                            )
                        }
                    }

                    // Host & Port Row
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.spacedBy(10.dp)
                    ) {
                        OutlinedTextField(
                            value = host,
                            onValueChange = { host = it },
                            label = { Text("IP Tailscale / Host", color = TextSecondary) },
                            placeholder = { Text("100.115.92.40") },
                            singleLine = true,
                            colors = OutlinedTextFieldDefaults.colors(
                                focusedBorderColor = BurntOrange,
                                unfocusedBorderColor = NavyDarkBorder,
                                focusedTextColor = TextPrimary,
                                unfocusedTextColor = TextPrimary,
                                focusedLabelColor = BurntOrange
                            ),
                            modifier = Modifier.weight(0.7f).testTag("settings_host_input")
                        )

                        OutlinedTextField(
                            value = portText,
                            onValueChange = { portText = it },
                            label = { Text("Port", color = TextSecondary) },
                            placeholder = { Text("22") },
                            singleLine = true,
                            keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
                            colors = OutlinedTextFieldDefaults.colors(
                                focusedBorderColor = BurntOrange,
                                unfocusedBorderColor = NavyDarkBorder,
                                focusedTextColor = TextPrimary,
                                unfocusedTextColor = TextPrimary,
                                focusedLabelColor = BurntOrange
                            ),
                            modifier = Modifier.weight(0.3f).testTag("settings_port_input")
                        )
                    }

                    // Username
                    OutlinedTextField(
                        value = user,
                        onValueChange = { user = it },
                        label = { Text("SSH Username", color = TextSecondary) },
                        placeholder = { Text("admin / root") },
                        singleLine = true,
                        colors = OutlinedTextFieldDefaults.colors(
                            focusedBorderColor = BurntOrange,
                            unfocusedBorderColor = NavyDarkBorder,
                            focusedTextColor = TextPrimary,
                            unfocusedTextColor = TextPrimary,
                            focusedLabelColor = BurntOrange
                        ),
                        modifier = Modifier.fillMaxWidth().testTag("settings_user_input")
                    )

                    // Password
                    OutlinedTextField(
                        value = password,
                        onValueChange = { password = it },
                        label = { Text("SSH Password", color = TextSecondary) },
                        singleLine = true,
                        visualTransformation = if (passwordVisible) VisualTransformation.None else PasswordVisualTransformation(),
                        trailingIcon = {
                            IconButton(onClick = { passwordVisible = !passwordVisible }) {
                                Icon(
                                    imageVector = if (passwordVisible) Icons.Default.Visibility else Icons.Default.VisibilityOff,
                                    contentDescription = "Toggle password",
                                    tint = TextSecondary
                                )
                            }
                        },
                        colors = OutlinedTextFieldDefaults.colors(
                            focusedBorderColor = BurntOrange,
                            unfocusedBorderColor = NavyDarkBorder,
                            focusedTextColor = TextPrimary,
                            unfocusedTextColor = TextPrimary,
                            focusedLabelColor = BurntOrange
                        ),
                        modifier = Modifier.fillMaxWidth().testTag("settings_password_input")
                    )

                    // Test SSH Button
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        OutlinedButton(
                            onClick = {
                                val cfg = buildCurrentConfig()
                                if (!cfg.isSshConfigured) {
                                    Toast.makeText(context, "Isi Host, User, dan Password dulu", Toast.LENGTH_SHORT).show()
                                    return@OutlinedButton
                                }
                                isTestingSsh = true
                                sshTestResult = null
                                scope.launch(Dispatchers.IO) {
                                    try {
                                        val jsch = JSch()
                                        val session = jsch.getSession(cfg.sshUser, cfg.sshHost, cfg.sshPort)
                                        session.setPassword(cfg.sshPassword)
                                        session.setConfig("StrictHostKeyChecking", "no")
                                        session.connect(10000)
                                        session.disconnect()
                                        withContext(Dispatchers.Main) {
                                            isTestingSsh = false
                                            sshTestResult = Pair(true, "Koneksi SSH Tailscale Berhasil!")
                                        }
                                    } catch (e: Exception) {
                                        withContext(Dispatchers.Main) {
                                            isTestingSsh = false
                                            sshTestResult = Pair(false, "Gagal: ${e.localizedMessage}")
                                        }
                                    }
                                }
                            },
                            colors = ButtonDefaults.outlinedButtonColors(contentColor = BurntOrange),
                            border = androidx.compose.foundation.BorderStroke(1.dp, BurntOrange),
                            shape = RoundedCornerShape(12.dp)
                        ) {
                            if (isTestingSsh) {
                                CircularProgressIndicator(color = BurntOrange, strokeWidth = 2.dp, modifier = Modifier.size(16.dp))
                                Spacer(modifier = Modifier.width(6.dp))
                            } else {
                                Icon(Icons.Default.NetworkCheck, contentDescription = null, modifier = Modifier.size(16.dp))
                                Spacer(modifier = Modifier.width(6.dp))
                            }
                            Text("Test Koneksi SSH", fontSize = 12.sp, fontWeight = FontWeight.SemiBold)
                        }

                        sshTestResult?.let { (success, msg) ->
                            Text(
                                text = if (success) "✓ Berhasil" else "✗ Gagal",
                                color = if (success) TerminalGreen else TerminalRed,
                                fontWeight = FontWeight.Bold,
                                fontSize = 12.sp,
                                fontFamily = FontFamily.Monospace
                            )
                        }
                    }

                    sshTestResult?.let { (_, msg) ->
                        Text(
                            text = msg,
                            color = TextSecondary,
                            fontSize = 11.sp,
                            fontFamily = FontFamily.Monospace
                        )
                    }
                }
            }

            // SECTION 2: HERMES AI AGENT (OPENAI-COMPATIBLE)
            TailHarnessCard(
                backgroundColor = NavyDarkCard,
                borderColor = NavyCardBorder
            ) {
                Column(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(16.dp),
                    verticalArrangement = Arrangement.spacedBy(12.dp)
                ) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Box(
                            modifier = Modifier
                                .size(32.dp)
                                .clip(RoundedCornerShape(8.dp))
                                .background(BurntOrangeBadgeBg)
                                .border(1.dp, BurntOrangeBorder, RoundedCornerShape(8.dp)),
                            contentAlignment = Alignment.Center
                        ) {
                            Icon(Icons.Default.SmartToy, contentDescription = null, tint = BurntOrange, modifier = Modifier.size(16.dp))
                        }
                        Spacer(modifier = Modifier.width(10.dp))
                        Column {
                            Text(
                                text = "Hermes AI Agent API",
                                color = TextPrimary,
                                fontWeight = FontWeight.Bold,
                                fontSize = 15.sp
                            )
                            Text(
                                text = "Endpoint OpenAI-compatible (Ollama, vLLM, LMDeploy, dll.)",
                                color = TextSecondary,
                                fontSize = 11.sp
                            )
                        }
                    }

                    // Base URL
                    OutlinedTextField(
                        value = hermesUrl,
                        onValueChange = { hermesUrl = it },
                        label = { Text("Base URL API", color = TextSecondary) },
                        placeholder = { Text("http://100.115.92.40:11434") },
                        singleLine = true,
                        colors = OutlinedTextFieldDefaults.colors(
                            focusedBorderColor = BurntOrange,
                            unfocusedBorderColor = NavyDarkBorder,
                            focusedTextColor = TextPrimary,
                            unfocusedTextColor = TextPrimary,
                            focusedLabelColor = BurntOrange
                        ),
                        modifier = Modifier.fillMaxWidth().testTag("settings_hermes_url_input")
                    )

                    // Model Name with Auto-Detect Feature
                    Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
                        OutlinedTextField(
                            value = hermesModel,
                            onValueChange = { hermesModel = it },
                            label = { Text("Nama Model", color = TextSecondary) },
                            placeholder = { Text("hermes-3-llama-3.1-8b / auto-detect") },
                            singleLine = true,
                            trailingIcon = {
                                if (isDetectingModels) {
                                    CircularProgressIndicator(
                                        modifier = Modifier.size(18.dp),
                                        strokeWidth = 2.dp,
                                        color = BurntOrange
                                    )
                                } else {
                                    IconButton(
                                        onClick = {
                                            if (hermesUrl.isBlank()) {
                                                Toast.makeText(context, "Isi Base URL API terlebih dahulu", Toast.LENGTH_SHORT).show()
                                                return@IconButton
                                            }
                                            isDetectingModels = true
                                            scope.launch {
                                                val res = hermesClient.fetchAvailableModels(hermesUrl, hermesKey)
                                                isDetectingModels = false
                                                res.onSuccess { models ->
                                                    if (models.isNotEmpty()) {
                                                        detectedModels = models
                                                        hermesModel = models.first()
                                                        Toast.makeText(context, "Terdeteksi ${models.size} model: ${models.first()}", Toast.LENGTH_LONG).show()
                                                    } else {
                                                        Toast.makeText(context, "Tidak ada model ditemukan pada endpoint ini", Toast.LENGTH_SHORT).show()
                                                    }
                                                }.onFailure { err ->
                                                    Toast.makeText(context, "Gagal deteksi: ${err.localizedMessage}", Toast.LENGTH_LONG).show()
                                                }
                                            }
                                        }
                                    ) {
                                        Icon(
                                            imageVector = Icons.Default.AutoAwesome,
                                            contentDescription = "Deteksi Model Otomatis",
                                            tint = BurntOrange
                                        )
                                    }
                                }
                            },
                            colors = OutlinedTextFieldDefaults.colors(
                                focusedBorderColor = BurntOrange,
                                unfocusedBorderColor = NavyDarkBorder,
                                focusedTextColor = TextPrimary,
                                unfocusedTextColor = TextPrimary,
                                focusedLabelColor = BurntOrange
                            ),
                            modifier = Modifier.fillMaxWidth().testTag("settings_hermes_model_input")
                        )

                        // Auto-Detect Action Button for 9router / Ollama / vLLM
                        OutlinedButton(
                            onClick = {
                                if (hermesUrl.isBlank()) {
                                    Toast.makeText(context, "Isi Base URL API terlebih dahulu", Toast.LENGTH_SHORT).show()
                                    return@OutlinedButton
                                }
                                isDetectingModels = true
                                scope.launch {
                                    val res = hermesClient.fetchAvailableModels(hermesUrl, hermesKey)
                                    isDetectingModels = false
                                    res.onSuccess { models ->
                                        if (models.isNotEmpty()) {
                                            detectedModels = models
                                            if (hermesModel.isBlank() || hermesModel !in models) {
                                                hermesModel = models.first()
                                            }
                                            Toast.makeText(context, "Berhasil deteksi ${models.size} model di 9router/server!", Toast.LENGTH_LONG).show()
                                        } else {
                                            Toast.makeText(context, "Tidak ada model ditemukan pada endpoint ini", Toast.LENGTH_SHORT).show()
                                        }
                                    }.onFailure { err ->
                                        Toast.makeText(context, "Gagal deteksi: ${err.localizedMessage}", Toast.LENGTH_LONG).show()
                                    }
                                }
                            },
                            modifier = Modifier
                                .fillMaxWidth()
                                .height(42.dp),
                            shape = RoundedCornerShape(12.dp),
                            colors = ButtonDefaults.outlinedButtonColors(contentColor = BurntOrange),
                            border = BorderStroke(1.dp, BurntOrange.copy(alpha = 0.6f)),
                            contentPadding = PaddingValues(horizontal = 14.dp, vertical = 0.dp),
                            enabled = !isDetectingModels
                        ) {
                            if (isDetectingModels) {
                                CircularProgressIndicator(
                                    modifier = Modifier.size(16.dp),
                                    strokeWidth = 2.dp,
                                    color = BurntOrange
                                )
                                Spacer(modifier = Modifier.width(8.dp))
                                Text("Memindai 9router / Ollama / vLLM...", fontSize = 12.sp, color = BurntOrange)
                            } else {
                                Icon(Icons.Default.AutoAwesome, contentDescription = null, modifier = Modifier.size(16.dp), tint = BurntOrange)
                                Spacer(modifier = Modifier.width(8.dp))
                                Text("Deteksi Otomatis Model (9router / Ollama)", fontSize = 12.sp, fontWeight = FontWeight.SemiBold, color = BurntOrange)
                            }
                        }

                        Text(
                            text = "Mendukung auto-detect 9router, Ollama (/api/tags), vLLM (/v1/models), LiteLLM, dan reverse proxy.",
                            color = TextSecondary.copy(alpha = 0.7f),
                            fontSize = 11.sp,
                            lineHeight = 15.sp
                        )

                        if (detectedModels.isNotEmpty()) {
                            Text(
                                text = "Model Ditemukan (${detectedModels.size}) - Ketuk untuk memilih:",
                                color = TextPrimary,
                                fontSize = 12.sp,
                                fontWeight = FontWeight.SemiBold
                            )
                            Row(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .horizontalScroll(rememberScrollState()),
                                horizontalArrangement = Arrangement.spacedBy(6.dp)
                            ) {
                                detectedModels.forEach { modelName ->
                                    PillChip(
                                        label = modelName,
                                        onClick = {
                                            hermesModel = modelName
                                            Toast.makeText(context, "Model dipilih: $modelName", Toast.LENGTH_SHORT).show()
                                        },
                                        isSelected = hermesModel == modelName
                                    )
                                }
                            }
                        }
                    }

                    // API Key (Bearer token)
                    OutlinedTextField(
                        value = hermesKey,
                        onValueChange = { hermesKey = it },
                        label = { Text("API Key (Opsional / Bearer Token)", color = TextSecondary) },
                        placeholder = { Text("sk-...") },
                        singleLine = true,
                        visualTransformation = if (hermesKeyVisible) VisualTransformation.None else PasswordVisualTransformation(),
                        trailingIcon = {
                            IconButton(onClick = { hermesKeyVisible = !hermesKeyVisible }) {
                                Icon(
                                    imageVector = if (hermesKeyVisible) Icons.Default.Visibility else Icons.Default.VisibilityOff,
                                    contentDescription = "Toggle API Key",
                                    tint = TextSecondary
                                )
                            }
                        },
                        colors = OutlinedTextFieldDefaults.colors(
                            focusedBorderColor = BurntOrange,
                            unfocusedBorderColor = NavyDarkBorder,
                            focusedTextColor = TextPrimary,
                            unfocusedTextColor = TextPrimary,
                            focusedLabelColor = BurntOrange
                        ),
                        modifier = Modifier.fillMaxWidth().testTag("settings_hermes_key_input")
                    )

                    // Test Hermes Button
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        OutlinedButton(
                            onClick = {
                                val cfg = buildCurrentConfig()
                                isTestingHermes = true
                                hermesTestResult = null
                                scope.launch {
                                    val result = hermesClient.testConnection(cfg)
                                    isTestingHermes = false
                                    result.onSuccess {
                                        hermesTestResult = Pair(true, it)
                                    }.onFailure {
                                        hermesTestResult = Pair(false, "Error: ${it.localizedMessage}")
                                    }
                                }
                            },
                            colors = ButtonDefaults.outlinedButtonColors(contentColor = BurntOrange),
                            border = androidx.compose.foundation.BorderStroke(1.dp, BurntOrange),
                            shape = RoundedCornerShape(12.dp)
                        ) {
                            if (isTestingHermes) {
                                CircularProgressIndicator(color = BurntOrange, strokeWidth = 2.dp, modifier = Modifier.size(16.dp))
                                Spacer(modifier = Modifier.width(6.dp))
                            } else {
                                Icon(Icons.Default.SmartToy, contentDescription = null, modifier = Modifier.size(16.dp))
                                Spacer(modifier = Modifier.width(6.dp))
                            }
                            Text("Test Endpoint Hermes", fontSize = 12.sp, fontWeight = FontWeight.SemiBold)
                        }

                        hermesTestResult?.let { (success, _) ->
                            Text(
                                text = if (success) "✓ Berhasil" else "✗ Gagal",
                                color = if (success) TerminalGreen else TerminalRed,
                                fontWeight = FontWeight.Bold,
                                fontSize = 12.sp,
                                fontFamily = FontFamily.Monospace
                            )
                        }
                    }

                    hermesTestResult?.let { (_, msg) ->
                        Text(
                            text = msg,
                            color = TextSecondary,
                            fontSize = 11.sp,
                            fontFamily = FontFamily.Monospace
                        )
                    }
                }
            }

            // Action Buttons
            Button(
                onClick = { saveSettings() },
                colors = ButtonDefaults.buttonColors(
                    containerColor = BurntOrange,
                    contentColor = NavyDarkBackground
                ),
                shape = RoundedCornerShape(14.dp),
                modifier = Modifier
                    .fillMaxWidth()
                    .height(50.dp)
                    .testTag("save_settings_button")
            ) {
                Icon(Icons.Default.Save, contentDescription = null)
                Spacer(modifier = Modifier.width(8.dp))
                Text("Simpan Kredensial ke Keystore", fontWeight = FontWeight.Bold, fontSize = 15.sp)
            }

            // Quick Preset / Demo Button
            OutlinedButton(
                onClick = {
                    host = "100.115.92.40"
                    portText = "22"
                    user = "admin"
                    password = "REMOVED_CREDENTIAL"
                    hermesUrl = "http://100.115.92.40:11434"
                    hermesKey = "REMOVED_CREDENTIAL"
                    hermesModel = "hermes-3-llama-3.1-8b"
                    saveSettings()
                },
                colors = ButtonDefaults.outlinedButtonColors(contentColor = TextSecondary),
                border = androidx.compose.foundation.BorderStroke(1.dp, NavyDarkBorder),
                shape = RoundedCornerShape(14.dp),
                modifier = Modifier.fillMaxWidth()
            ) {
                Icon(Icons.Default.VpnKey, contentDescription = null, modifier = Modifier.size(16.dp))
                Spacer(modifier = Modifier.width(8.dp))
                Text("Isi Preset Contoh Tailscale", fontSize = 13.sp)
            }

            Spacer(modifier = Modifier.height(16.dp))
        }
    }
}
