package com.example.ui.settings

import android.widget.Toast
import androidx.compose.foundation.BorderStroke
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
import androidx.compose.material.icons.filled.DeleteOutline
import androidx.compose.material.icons.filled.Dns
import androidx.compose.material.icons.filled.Home
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
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
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
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
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
import com.example.ui.theme.TerminalGreen
import com.example.ui.theme.TerminalRed
import com.example.ui.theme.TextPrimary
import com.example.ui.theme.TextSecondary

@Composable
fun SettingsScreen(
    encryptedStorage: EncryptedStorage,
    hermesClient: HermesApiClient,
    onConfigUpdated: (ServerConfig) -> Unit,
    modifier: Modifier = Modifier,
    viewModel: SettingsViewModel = viewModel(
        factory = SettingsViewModel.provideFactory(encryptedStorage, hermesClient)
    )
) {
    val context = LocalContext.current
    val scrollState = rememberScrollState()
    val uiState by viewModel.uiState.collectAsStateWithLifecycle()

    LaunchedEffect(uiState.statusMessage) {
        uiState.statusMessage?.let { msg ->
            Toast.makeText(context, msg, Toast.LENGTH_SHORT).show()
            viewModel.clearStatusMessage()
        }
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
                            Icon(
                                Icons.Default.Dns,
                                contentDescription = null,
                                tint = BurntOrange,
                                modifier = Modifier.size(20.dp)
                            )
                            Spacer(modifier = Modifier.width(8.dp))
                            Text(
                                text = "Profil Koneksi",
                                color = TextPrimary,
                                fontWeight = FontWeight.Bold,
                                fontSize = 15.sp
                            )
                        }
                        StatusBadge(
                            text = if (uiState.activeProfile.id == ConnectionProfile.PROFILE_HOME_ID) "LAN RUMAH"
                            else if (uiState.activeProfile.id == ConnectionProfile.PROFILE_TAILSCALE_ID) "TAILSCALE"
                            else "KUSTOM",
                            isSuccess = true
                        )
                    }

                    Text(
                        text = "Simpan profil kredensial terpisah: untuk di rumah (LAN) dan di luar via Tailscale VPN. Ganti profil dengan 1 sentuhan:",
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
                        uiState.profiles.forEach { profile ->
                            val isSelected = profile.id == uiState.activeProfileId
                            Row(
                                modifier = Modifier
                                    .clip(RoundedCornerShape(12.dp))
                                    .background(if (isSelected) BurntOrangeBadgeBg else NavyDarkSurface)
                                    .border(
                                        width = if (isSelected) 1.5.dp else 1.dp,
                                        color = if (isSelected) BurntOrange else NavyDarkBorder,
                                        shape = RoundedCornerShape(12.dp)
                                    )
                                    .clickable { viewModel.selectProfile(profile.id, onConfigUpdated) }
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
                            onClick = { viewModel.addNewProfile(onConfigUpdated) },
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
                            value = uiState.profileName,
                            onValueChange = { viewModel.updateProfileName(it) },
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

                        if (uiState.profiles.size > 1) {
                            Spacer(modifier = Modifier.width(8.dp))
                            IconButton(
                                onClick = { viewModel.deleteCurrentProfile(onConfigUpdated) },
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
                                text = "Keystore Enkripsi AES-256-GCM",
                                color = TextPrimary,
                                fontWeight = FontWeight.Bold,
                                fontSize = 13.sp
                            )
                            Spacer(modifier = Modifier.width(8.dp))
                            StatusBadge(text = "HARDWARE SECURE", isSuccess = true)
                        }
                        Spacer(modifier = Modifier.height(2.dp))
                        Text(
                            text = "Kredensial disimpan terenkripsi di chip keamanan Android KeyStore. Tidak ada cleartext atau Base64 fallback.",
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
                            value = uiState.host,
                            onValueChange = { viewModel.updateHost(it) },
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
                            value = uiState.portText,
                            onValueChange = { viewModel.updatePort(it) },
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
                        value = uiState.user,
                        onValueChange = { viewModel.updateUser(it) },
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
                        value = uiState.password,
                        onValueChange = { viewModel.updatePassword(it) },
                        label = { Text("SSH Password", color = TextSecondary) },
                        singleLine = true,
                        visualTransformation = if (uiState.passwordVisible) VisualTransformation.None else PasswordVisualTransformation(),
                        trailingIcon = {
                            IconButton(onClick = { viewModel.togglePasswordVisibility() }) {
                                Icon(
                                    imageVector = if (uiState.passwordVisible) Icons.Default.Visibility else Icons.Default.VisibilityOff,
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
                            onClick = { viewModel.testSshConnection() },
                            colors = ButtonDefaults.outlinedButtonColors(contentColor = BurntOrange),
                            border = BorderStroke(1.dp, BurntOrange),
                            shape = RoundedCornerShape(12.dp)
                        ) {
                            if (uiState.isTestingSsh) {
                                CircularProgressIndicator(color = BurntOrange, strokeWidth = 2.dp, modifier = Modifier.size(16.dp))
                                Spacer(modifier = Modifier.width(6.dp))
                            } else {
                                Icon(Icons.Default.NetworkCheck, contentDescription = null, modifier = Modifier.size(16.dp))
                                Spacer(modifier = Modifier.width(6.dp))
                            }
                            Text("Test Koneksi SSH", fontSize = 12.sp, fontWeight = FontWeight.SemiBold)
                        }

                        uiState.sshTestResult?.let { (success, _) ->
                            Text(
                                text = if (success) "✓ Berhasil" else "✗ Gagal",
                                color = if (success) TerminalGreen else TerminalRed,
                                fontWeight = FontWeight.Bold,
                                fontSize = 12.sp,
                                fontFamily = FontFamily.Monospace
                            )
                        }
                    }

                    uiState.sshTestResult?.let { (_, msg) ->
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
                                text = "Endpoint OpenAI-compatible (Ollama, vLLM, LMDeploy, 9router)",
                                color = TextSecondary,
                                fontSize = 11.sp
                            )
                        }
                    }

                    // Base URL
                    OutlinedTextField(
                        value = uiState.hermesUrl,
                        onValueChange = { viewModel.updateHermesUrl(it) },
                        label = { Text("Base URL API", color = TextSecondary) },
                        placeholder = { Text("https://100.115.92.40:11434") },
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
                            value = uiState.hermesModel,
                            onValueChange = { viewModel.updateHermesModel(it) },
                            label = { Text("Nama Model", color = TextSecondary) },
                            placeholder = { Text("hermes-3-llama-3.1-8b / auto-detect") },
                            singleLine = true,
                            trailingIcon = {
                                if (uiState.isDetectingModels) {
                                    CircularProgressIndicator(
                                        modifier = Modifier.size(18.dp),
                                        strokeWidth = 2.dp,
                                        color = BurntOrange
                                    )
                                } else {
                                    IconButton(onClick = { viewModel.autoDetectModels() }) {
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
                            onClick = { viewModel.autoDetectModels() },
                            modifier = Modifier
                                .fillMaxWidth()
                                .height(42.dp),
                            shape = RoundedCornerShape(12.dp),
                            colors = ButtonDefaults.outlinedButtonColors(contentColor = BurntOrange),
                            border = BorderStroke(1.dp, BurntOrange.copy(alpha = 0.6f)),
                            contentPadding = PaddingValues(horizontal = 14.dp, vertical = 0.dp),
                            enabled = !uiState.isDetectingModels
                        ) {
                            if (uiState.isDetectingModels) {
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

                        if (uiState.detectedModels.isNotEmpty()) {
                            Text(
                                text = "Model Ditemukan (${uiState.detectedModels.size}) - Ketuk untuk memilih:",
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
                                uiState.detectedModels.forEach { modelName ->
                                    PillChip(
                                        label = modelName,
                                        onClick = {
                                            viewModel.updateHermesModel(modelName)
                                            Toast.makeText(context, "Model dipilih: $modelName", Toast.LENGTH_SHORT).show()
                                        },
                                        isSelected = uiState.hermesModel == modelName
                                    )
                                }
                            }
                        }
                    }

                    // API Key (Bearer token)
                    OutlinedTextField(
                        value = uiState.hermesKey,
                        onValueChange = { viewModel.updateHermesKey(it) },
                        label = { Text("API Key (Opsional / Bearer Token)", color = TextSecondary) },
                        placeholder = { Text("sk-...") },
                        singleLine = true,
                        visualTransformation = if (uiState.hermesKeyVisible) VisualTransformation.None else PasswordVisualTransformation(),
                        trailingIcon = {
                            IconButton(onClick = { viewModel.toggleHermesKeyVisibility() }) {
                                Icon(
                                    imageVector = if (uiState.hermesKeyVisible) Icons.Default.Visibility else Icons.Default.VisibilityOff,
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
                            onClick = { viewModel.testHermesEndpoint() },
                            colors = ButtonDefaults.outlinedButtonColors(contentColor = BurntOrange),
                            border = BorderStroke(1.dp, BurntOrange),
                            shape = RoundedCornerShape(12.dp)
                        ) {
                            if (uiState.isTestingHermes) {
                                CircularProgressIndicator(color = BurntOrange, strokeWidth = 2.dp, modifier = Modifier.size(16.dp))
                                Spacer(modifier = Modifier.width(6.dp))
                            } else {
                                Icon(Icons.Default.SmartToy, contentDescription = null, modifier = Modifier.size(16.dp))
                                Spacer(modifier = Modifier.width(6.dp))
                            }
                            Text("Test Endpoint Hermes", fontSize = 12.sp, fontWeight = FontWeight.SemiBold)
                        }

                        uiState.hermesTestResult?.let { (success, _) ->
                            Text(
                                text = if (success) "✓ Berhasil" else "✗ Gagal",
                                color = if (success) TerminalGreen else TerminalRed,
                                fontWeight = FontWeight.Bold,
                                fontSize = 12.sp,
                                fontFamily = FontFamily.Monospace
                            )
                        }
                    }

                    uiState.hermesTestResult?.let { (_, msg) ->
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
                onClick = { viewModel.saveSettings(onConfigUpdated) },
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
                onClick = { viewModel.applyPresetTailscale(onConfigUpdated) },
                colors = ButtonDefaults.outlinedButtonColors(contentColor = TextSecondary),
                border = BorderStroke(1.dp, NavyDarkBorder),
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
