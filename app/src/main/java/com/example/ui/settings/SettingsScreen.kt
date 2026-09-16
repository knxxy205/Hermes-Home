package com.example.ui.settings

import android.widget.Toast
import androidx.compose.foundation.background
import androidx.compose.foundation.border
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
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.CheckCircle
import androidx.compose.material.icons.filled.Dns
import androidx.compose.material.icons.filled.Key
import androidx.compose.material.icons.filled.Lock
import androidx.compose.material.icons.filled.NetworkCheck
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
import com.example.data.model.ServerConfig
import com.example.data.security.EncryptedStorage
import com.example.ui.components.CustomScreenHeader
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

    val currentConfig = remember { encryptedStorage.loadConfig() }

    var host by remember { mutableStateOf(currentConfig.sshHost) }
    var portText by remember { mutableStateOf(currentConfig.sshPort.toString()) }
    var user by remember { mutableStateOf(currentConfig.sshUser) }
    var password by remember { mutableStateOf(currentConfig.sshPassword) }
    var passwordVisible by remember { mutableStateOf(false) }

    var hermesUrl by remember { mutableStateOf(currentConfig.hermesBaseUrl) }
    var hermesKey by remember { mutableStateOf(currentConfig.hermesApiKey) }
    var hermesKeyVisible by remember { mutableStateOf(false) }
    var hermesModel by remember { mutableStateOf(currentConfig.hermesModel) }

    var isTestingSsh by remember { mutableStateOf(false) }
    var sshTestResult by remember { mutableStateOf<Pair<Boolean, String>?>(null) }

    var isTestingHermes by remember { mutableStateOf(false) }
    var hermesTestResult by remember { mutableStateOf<Pair<Boolean, String>?>(null) }

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

    fun saveSettings() {
        val config = buildCurrentConfig()
        encryptedStorage.saveConfig(config)
        onConfigUpdated(config)
        Toast.makeText(context, "Kredensial berhasil dienkripsi ke Keystore device!", Toast.LENGTH_SHORT).show()
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

                    // Model Name
                    OutlinedTextField(
                        value = hermesModel,
                        onValueChange = { hermesModel = it },
                        label = { Text("Nama Model", color = TextSecondary) },
                        placeholder = { Text("hermes-3-llama-3.1-8b") },
                        singleLine = true,
                        colors = OutlinedTextFieldDefaults.colors(
                            focusedBorderColor = BurntOrange,
                            unfocusedBorderColor = NavyDarkBorder,
                            focusedTextColor = TextPrimary,
                            unfocusedTextColor = TextPrimary,
                            focusedLabelColor = BurntOrange
                        ),
                        modifier = Modifier.fillMaxWidth().testTag("settings_hermes_model_input")
                    )

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
