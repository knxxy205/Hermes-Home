package com.example.ui.settings

import android.content.ClipData
import android.content.ClipboardManager
import android.content.Context
import android.content.Intent
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
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.AutoAwesome
import androidx.compose.material.icons.filled.ContentCopy
import androidx.compose.material.icons.filled.ContentPaste
import androidx.compose.material.icons.filled.DeleteOutline
import androidx.compose.material.icons.filled.Dns
import androidx.compose.material.icons.filled.FileDownload
import androidx.compose.material.icons.filled.FileUpload
import androidx.compose.material.icons.filled.Home
import androidx.compose.material.icons.filled.Lock
import androidx.compose.material.icons.filled.NetworkCheck
import androidx.compose.material.icons.filled.Public
import androidx.compose.material.icons.filled.Save
import androidx.compose.material.icons.filled.Settings
import androidx.compose.material.icons.filled.Share
import androidx.compose.material.icons.filled.SmartToy
import androidx.compose.material.icons.filled.Visibility
import androidx.compose.material.icons.filled.VisibilityOff
import androidx.compose.material.icons.filled.VpnKey
import androidx.compose.material.icons.filled.Warning
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.OutlinedTextFieldDefaults
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.runtime.rememberCoroutineScope
import kotlinx.coroutines.launch
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
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
    val scrollState = rememberLazyListState()
    val uiState by viewModel.uiState.collectAsStateWithLifecycle()

    var showExportDialog by remember { mutableStateOf(false) }
    var showBackupPasswordDialog by remember { mutableStateOf(false) }
    var backupPassword by remember { mutableStateOf("") }
    var backupBusy by remember { mutableStateOf(false) }
    val scope = rememberCoroutineScope()
    var exportPayloadText by remember { mutableStateOf("") }
    var showImportDialog by remember { mutableStateOf(false) }
    var importInputText by remember { mutableStateOf("") }
    var showImportConfirmDialog by remember { mutableStateOf(false) }
    var showAddProfileDialog by remember { mutableStateOf(false) }
    var newProfileName by remember { mutableStateOf("") }
    var newProfileHost by remember { mutableStateOf("") }
    var newProfilePort by remember { mutableStateOf("22") }
    var newProfileUser by remember { mutableStateOf("") }
    var newProfilePassword by remember { mutableStateOf("") }
    var newProfilePasswordVisible by remember { mutableStateOf(false) }

    LaunchedEffect(uiState.statusMessage) {
        uiState.statusMessage?.let { msg ->
            Toast.makeText(context, msg, Toast.LENGTH_SHORT).show()
            viewModel.clearStatusMessage()
        }
    }

    if (showAddProfileDialog) {
        val validPort = newProfilePort.toIntOrNull()?.let { it in 1..65535 } == true
        val canSave = newProfileName.isNotBlank() && newProfileHost.isNotBlank() &&
            newProfileUser.isNotBlank() && validPort
        AlertDialog(
            onDismissRequest = { showAddProfileDialog = false },
            containerColor = Color.White,
            titleContentColor = Color(0xff1e293b),
            textContentColor = Color(0xff64748b),
            shape = RoundedCornerShape(28.dp),
            title = { Text("Tambah host", fontWeight = FontWeight.Bold) },
            text = {
                Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                Text("Simpan detail server baru sebagai profil koneksi.", color = Color(0xff64748b), fontSize = 13.sp)
                    OutlinedTextField(
                        value = newProfileName, onValueChange = { newProfileName = it },
                        label = { Text("Nama profil") }, singleLine = true,
                        colors = OutlinedTextFieldDefaults.colors(
                            focusedBorderColor = Color(0xff3b82f6), unfocusedBorderColor = Color(0xffe2e8f0),
                            focusedTextColor = Color(0xff1e293b), unfocusedTextColor = Color(0xff1e293b),
                            focusedLabelColor = Color(0xff3b82f6)
                        ), modifier = Modifier.fillMaxWidth()
                    )
                    OutlinedTextField(
                        value = newProfileHost, onValueChange = { newProfileHost = it },
                        label = { Text("Host atau alamat IP") }, placeholder = { Text("contoh: server.tailnet") },
                        singleLine = true, keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Uri),
                        colors = OutlinedTextFieldDefaults.colors(
                            focusedBorderColor = Color(0xff3b82f6), unfocusedBorderColor = Color(0xffe2e8f0),
                            focusedTextColor = Color(0xff1e293b), unfocusedTextColor = Color(0xff1e293b),
                            focusedLabelColor = Color(0xff3b82f6)
                        ), modifier = Modifier.fillMaxWidth()
                    )
                    Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                        OutlinedTextField(
                            value = newProfileUser, onValueChange = { newProfileUser = it },
                            label = { Text("Username") }, singleLine = true,
                            colors = OutlinedTextFieldDefaults.colors(
                                focusedBorderColor = Color(0xff3b82f6), unfocusedBorderColor = Color(0xffe2e8f0),
                                focusedTextColor = Color(0xff1e293b), unfocusedTextColor = Color(0xff1e293b),
                                focusedLabelColor = Color(0xff3b82f6)
                            ), modifier = Modifier.weight(1f)
                        )
                        OutlinedTextField(
                            value = newProfilePort, onValueChange = { newProfilePort = it.filter(Char::isDigit).take(5) },
                            label = { Text("Port") }, singleLine = true,
                            keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
                            colors = OutlinedTextFieldDefaults.colors(
                                focusedBorderColor = Color(0xff3b82f6), unfocusedBorderColor = Color(0xffe2e8f0),
                                focusedTextColor = Color(0xff1e293b), unfocusedTextColor = Color(0xff1e293b),
                                focusedLabelColor = Color(0xff3b82f6)
                            ), modifier = Modifier.width(104.dp)
                        )
                    }
                    OutlinedTextField(
                        value = newProfilePassword, onValueChange = { newProfilePassword = it },
                        label = { Text("Password SSH (opsional untuk disimpan nanti)") }, singleLine = true,
                        visualTransformation = if (newProfilePasswordVisible) VisualTransformation.None else PasswordVisualTransformation(),
                        trailingIcon = {
                            IconButton(onClick = { newProfilePasswordVisible = !newProfilePasswordVisible }) {
                                Icon(if (newProfilePasswordVisible) Icons.Default.Visibility else Icons.Default.VisibilityOff,
                                    contentDescription = if (newProfilePasswordVisible) "Sembunyikan password" else "Tampilkan password",
                                    tint = Color(0xff64748b))
                            }
                        },
                        colors = OutlinedTextFieldDefaults.colors(
                            focusedBorderColor = Color(0xff3b82f6), unfocusedBorderColor = Color(0xffe2e8f0),
                            focusedTextColor = Color(0xff1e293b), unfocusedTextColor = Color(0xff1e293b),
                            focusedLabelColor = Color(0xff3b82f6)
                        ), modifier = Modifier.fillMaxWidth()
                    )
                }
            },
            confirmButton = {
                Button(
                    enabled = canSave,
                    onClick = {
                        val saved = viewModel.saveNewProfile(
                            newProfileName, newProfileHost, newProfilePort, newProfileUser, newProfilePassword, onConfigUpdated
                        )
                        if (saved) {
                            showAddProfileDialog = false
                            newProfileName = ""; newProfileHost = ""; newProfilePort = "22"
                            newProfileUser = ""; newProfilePassword = ""; newProfilePasswordVisible = false
                        }
                    },
                    colors = ButtonDefaults.buttonColors(
                        containerColor = Color(0xff3b82f6), disabledContainerColor = Color(0xffe8edf5),
                        disabledContentColor = Color(0xff94a3b8)
                    )
                ) { Text("Simpan") }
            },
            dismissButton = {
                TextButton(onClick = { showAddProfileDialog = false }) { Text("Batal", color = Color(0xff64748b)) }
            }
        )
    }

    if (showBackupPasswordDialog) {
        AlertDialog(
            onDismissRequest = { if (!backupBusy) { showBackupPasswordDialog = false; backupPassword = "" } },
            title = { Text("Enkripsi backup") },
            text = {
                Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
                    Text("Gunakan password minimal 12 karakter. Password ini diperlukan untuk memulihkan backup di perangkat lain.")
                    OutlinedTextField(value = backupPassword, onValueChange = { backupPassword = it },
                        label = { Text("Password backup") }, visualTransformation = PasswordVisualTransformation())
                }
            },
            confirmButton = {
                TextButton(enabled = backupPassword.length >= 12 && !backupBusy, onClick = {
                    backupBusy = true
                    scope.launch {
                        val result = withContext(Dispatchers.IO) { runCatching { viewModel.exportProfiles(backupPassword) } }
                        backupBusy = false
                        result.onSuccess { exportPayloadText = it; showBackupPasswordDialog = false; showExportDialog = true; backupPassword = "" }
                            .onFailure { Toast.makeText(context, "Backup gagal: ${it.localizedMessage}", Toast.LENGTH_LONG).show() }
                    }
                }) { Text(if (backupBusy) "Mengenkripsi…" else "Buat backup") }
            }
        )
    }
    // Export Dialog
    if (showExportDialog) {
        AlertDialog(
            onDismissRequest = { showExportDialog = false },
            containerColor = NavyDarkCard,
            title = {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Icon(Icons.Default.FileDownload, contentDescription = null, tint = BurntOrange, modifier = Modifier.size(20.dp))
                    Spacer(modifier = Modifier.width(8.dp))
                    Text("Export Connection Profiles", color = TextPrimary, fontWeight = FontWeight.Bold, fontSize = 16.sp)
                }
            },
            text = {
                Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                    Text(
                        text = "Backup dilindungi AES-GCM dengan password kamu. Simpan password terpisah dari backup.",
                        color = TextSecondary,
                        fontSize = 12.sp,
                        lineHeight = 16.sp
                    )
                    OutlinedTextField(
                        value = exportPayloadText,
                        onValueChange = {},
                        readOnly = true,
                        modifier = Modifier
                            .fillMaxWidth()
                            .height(110.dp),
                        textStyle = androidx.compose.ui.text.TextStyle(
                            fontFamily = FontFamily.Monospace,
                            fontSize = 11.sp,
                            color = TextPrimary
                        ),
                        colors = OutlinedTextFieldDefaults.colors(
                            focusedBorderColor = BurntOrange,
                            unfocusedBorderColor = NavyDarkBorder,
                            focusedContainerColor = NavyDarkSurface,
                            unfocusedContainerColor = NavyDarkSurface
                        )
                    )
                }
            },
            confirmButton = {
                Button(
                    onClick = {
                        val clipboard = context.getSystemService(Context.CLIPBOARD_SERVICE) as ClipboardManager
                        val clip = ClipData.newPlainText("Hermes Backup", exportPayloadText)
                        clipboard.setPrimaryClip(clip)
                        Toast.makeText(context, "Backup Copied!", Toast.LENGTH_SHORT).show()
                        showExportDialog = false
                    },
                    colors = ButtonDefaults.buttonColors(containerColor = BurntOrange, contentColor = Color.White)
                ) {
                    Icon(Icons.Default.ContentCopy, contentDescription = null, modifier = Modifier.size(16.dp))
                    Spacer(modifier = Modifier.width(6.dp))
                    Text("Copy to Clipboard", fontWeight = FontWeight.Bold, fontSize = 12.sp)
                }
            },
            dismissButton = {
                Row {
                    OutlinedButton(
                        onClick = {
                            val shareIntent = Intent(Intent.ACTION_SEND).apply {
                                type = "text/plain"
                                putExtra(Intent.EXTRA_SUBJECT, "Hermes Connection Profiles Backup")
                                putExtra(Intent.EXTRA_TEXT, exportPayloadText)
                            }
                            context.startActivity(Intent.createChooser(shareIntent, "Share Backup"))
                        },
                        colors = ButtonDefaults.outlinedButtonColors(contentColor = TextSecondary),
                        border = BorderStroke(1.dp, NavyDarkBorder)
                    ) {
                        Icon(Icons.Default.Share, contentDescription = null, modifier = Modifier.size(16.dp))
                        Spacer(modifier = Modifier.width(4.dp))
                        Text("Share", fontSize = 12.sp)
                    }
                    Spacer(modifier = Modifier.width(6.dp))
                    TextButton(onClick = { showExportDialog = false }) {
                        Text("Tutup", color = TextSecondary, fontSize = 12.sp)
                    }
                }
            }
        )
    }

    // Import Dialog
    if (showImportDialog) {
        AlertDialog(
            onDismissRequest = { showImportDialog = false },
            containerColor = NavyDarkCard,
            title = {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Icon(Icons.Default.FileUpload, contentDescription = null, tint = BurntOrange, modifier = Modifier.size(20.dp))
                    Spacer(modifier = Modifier.width(8.dp))
                    Text("Import Connection Profiles", color = TextPrimary, fontWeight = FontWeight.Bold, fontSize = 16.sp)
                }
            },
            text = {
                Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                    OutlinedTextField(value = backupPassword, onValueChange = { backupPassword = it },
                        label = { Text("Password backup V2") }, visualTransformation = PasswordVisualTransformation())
                    Text(
                        text = "Tempel backup V2 dan masukkan passwordnya. Backup V1 lama juga masih dapat diimpor tanpa password.",
                        color = TextSecondary,
                        fontSize = 12.sp,
                        lineHeight = 16.sp
                    )

                    OutlinedTextField(
                        value = importInputText,
                        onValueChange = { importInputText = it },
                        placeholder = { Text("HERMES_BACKUP_V2:...", color = TextSecondary.copy(alpha = 0.5f), fontSize = 12.sp) },
                        modifier = Modifier
                            .fillMaxWidth()
                            .height(110.dp),
                        textStyle = androidx.compose.ui.text.TextStyle(
                            fontFamily = FontFamily.Monospace,
                            fontSize = 11.sp,
                            color = TextPrimary
                        ),
                        colors = OutlinedTextFieldDefaults.colors(
                            focusedBorderColor = BurntOrange,
                            unfocusedBorderColor = NavyDarkBorder,
                            focusedContainerColor = NavyDarkSurface,
                            unfocusedContainerColor = NavyDarkSurface
                        )
                    )

                    // Quick Paste button
                    OutlinedButton(
                        onClick = {
                            val clipboard = context.getSystemService(Context.CLIPBOARD_SERVICE) as ClipboardManager
                            val clipItem = clipboard.primaryClip?.getItemAt(0)
                            val text = clipItem?.text?.toString() ?: ""
                            if (text.isNotBlank()) {
                                importInputText = text.trim()
                                Toast.makeText(context, "Teks ditempel dari clipboard", Toast.LENGTH_SHORT).show()
                            } else {
                                Toast.makeText(context, "Clipboard kosong", Toast.LENGTH_SHORT).show()
                            }
                        },
                        modifier = Modifier.fillMaxWidth().height(36.dp),
                        shape = RoundedCornerShape(10.dp),
                        colors = ButtonDefaults.outlinedButtonColors(contentColor = BurntOrange),
                        border = BorderStroke(1.dp, BurntOrange.copy(alpha = 0.5f)),
                        contentPadding = PaddingValues(0.dp)
                    ) {
                        Icon(Icons.Default.ContentPaste, contentDescription = null, modifier = Modifier.size(14.dp), tint = BurntOrange)
                        Spacer(modifier = Modifier.width(6.dp))
                        Text("Paste from Clipboard", fontSize = 12.sp, color = BurntOrange)
                    }
                }
            },
            confirmButton = {
                Button(
                    onClick = {
                        if (importInputText.isBlank()) {
                            Toast.makeText(context, "Masukkan string backup terlebih dahulu", Toast.LENGTH_SHORT).show()
                        } else {
                            showImportConfirmDialog = true
                        }
                    },
                    colors = ButtonDefaults.buttonColors(containerColor = BurntOrange, contentColor = Color.White)
                ) {
                    Text("Lanjutkan Import", fontWeight = FontWeight.Bold, fontSize = 12.sp)
                }
            },
            dismissButton = {
                TextButton(onClick = { showImportDialog = false }) {
                    Text("Batal", color = TextSecondary, fontSize = 12.sp)
                }
            }
        )
    }

    // Import Confirmation Dialog
    if (showImportConfirmDialog) {
        AlertDialog(
            onDismissRequest = { showImportConfirmDialog = false },
            containerColor = NavyDarkCard,
            title = {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Icon(Icons.Default.Warning, contentDescription = null, tint = BurntOrange, modifier = Modifier.size(20.dp))
                    Spacer(modifier = Modifier.width(8.dp))
                    Text("Konfirmasi Restore Profil", color = TextPrimary, fontWeight = FontWeight.Bold, fontSize = 16.sp)
                }
            },
            text = {
                Text(
                    text = "Importing will overwrite current profiles. Continue?",
                    color = TextSecondary,
                    fontSize = 13.sp,
                    lineHeight = 18.sp
                )
            },
            confirmButton = {
                Button(
                    onClick = {
                        showImportConfirmDialog = false
                        backupBusy = true
                        scope.launch {
                        val res = withContext(Dispatchers.IO) { viewModel.importProfiles(importInputText, {}, backupPassword) }
                        backupBusy = false
                        res.onSuccess { count ->
                            onConfigUpdated(encryptedStorage.loadConfig())
                            Toast.makeText(context, "Profiles Restored! ($count profil)", Toast.LENGTH_SHORT).show()
                            showImportDialog = false
                            importInputText = ""
                            backupPassword = ""
                        }.onFailure { err ->
                            Toast.makeText(context, "Gagal import: ${err.localizedMessage}", Toast.LENGTH_LONG).show()
                        }
                        }
                    },
                    colors = ButtonDefaults.buttonColors(containerColor = BurntOrange, contentColor = Color.White)
                ) {
                    Text("Ya, Timpa & Restore", fontWeight = FontWeight.Bold, fontSize = 12.sp)
                }
            },
            dismissButton = {
                TextButton(onClick = { showImportConfirmDialog = false }) {
                    Text("Batal", color = TextSecondary, fontSize = 12.sp)
                }
            }
        )
    }

    Column(
        modifier = modifier
            .fillMaxSize()
            .background(NavyDarkBackground)
    ) {
        CustomScreenHeader(
            icon = Icons.Default.Settings,
            title = "Settings",
            subtitle = "Koneksi, profil & keamanan"
        )

        LazyColumn(
            state = scrollState,
            modifier = Modifier
                .weight(1f)
                .fillMaxWidth()
                .padding(horizontal = 20.dp, vertical = 8.dp),
            verticalArrangement = Arrangement.spacedBy(20.dp)
        ) {
            item(key = "profiles", contentType = "form") {
            // Profile Selector Card (Rumah LAN vs Di Luar Tailscale)
            TailHarnessCard(
                backgroundColor = NavyDarkCard,
                borderColor = NavyCardBorder
            ) {
                Column(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(20.dp),
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
                            text = if (uiState.activeProfile.id == ConnectionProfile.PROFILE_HOME_ID) "LAN"
                            else if (uiState.activeProfile.id == ConnectionProfile.PROFILE_TAILSCALE_ID) "Tailscale"
                            else "Kustom",
                            isSuccess = true
                        )
                    }

                    Text(
                        text = "Pilih koneksi untuk di rumah atau saat bepergian.",
                        color = TextSecondary,
                        fontSize = 12.sp,
                        lineHeight = 16.sp
                    )

                    // Profiles Horizontal Chips Bar (Clean Material Icons, No Emojis in Text)
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .horizontalScroll(rememberScrollState()),
                        horizontalArrangement = Arrangement.spacedBy(8.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        uiState.profiles.forEach { profile ->
                            val isSelected = profile.id == uiState.activeProfileId
                            val isHome = profile.id == ConnectionProfile.PROFILE_HOME_ID || profile.name.contains("Rumah", ignoreCase = true) || profile.name.contains("LAN", ignoreCase = true)
                            val isTailscale = profile.id == ConnectionProfile.PROFILE_TAILSCALE_ID || profile.name.contains("Tailscale", ignoreCase = true) || profile.name.contains("Luar", ignoreCase = true)
                            
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
                                    imageVector = when {
                                        isHome -> Icons.Default.Home
                                        isTailscale -> Icons.Default.Public
                                        else -> Icons.Default.Dns
                                    },
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
                            onClick = { showAddProfileDialog = true },
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

                    // Backup & Restore Action Bar
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.spacedBy(8.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        OutlinedButton(
                            onClick = {
                                backupPassword = ""
                                showBackupPasswordDialog = true
                            },
                            modifier = Modifier.weight(1f).height(38.dp),
                            shape = RoundedCornerShape(10.dp),
                            colors = ButtonDefaults.outlinedButtonColors(contentColor = BurntOrange),
                            border = BorderStroke(1.dp, BurntOrange.copy(alpha = 0.6f)),
                            contentPadding = PaddingValues(horizontal = 8.dp, vertical = 0.dp)
                        ) {
                            Icon(Icons.Default.FileDownload, contentDescription = null, modifier = Modifier.size(15.dp), tint = BurntOrange)
                            Spacer(modifier = Modifier.width(4.dp))
                            Text("Export Profiles", fontSize = 12.sp, fontWeight = FontWeight.SemiBold, color = BurntOrange)
                        }

                        OutlinedButton(
                            onClick = {
                                importInputText = ""
                                showImportDialog = true
                            },
                            modifier = Modifier.weight(1f).height(38.dp),
                            shape = RoundedCornerShape(10.dp),
                            colors = ButtonDefaults.outlinedButtonColors(contentColor = TextSecondary),
                            border = BorderStroke(1.dp, NavyDarkBorder),
                            contentPadding = PaddingValues(horizontal = 8.dp, vertical = 0.dp)
                        ) {
                            Icon(Icons.Default.FileUpload, contentDescription = null, modifier = Modifier.size(15.dp), tint = TextSecondary)
                            Spacer(modifier = Modifier.width(4.dp))
                            Text("Import Profiles", fontSize = 12.sp, fontWeight = FontWeight.SemiBold, color = TextSecondary)
                        }
                    }
                }
            }

            }
            item(key = "security", contentType = "summary") {
            // Security Info Card (Android Keystore + Hardware-backed encryption)
            TailHarnessCard(
                backgroundColor = NavyDarkCard,
                borderColor = NavyCardBorder
            ) {
                Column(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(14.dp),
                    verticalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.SpaceBetween
                    ) {
                        Row(
                            verticalAlignment = Alignment.CenterVertically,
                            modifier = Modifier.weight(1f, fill = false)
                        ) {
                            Box(
                                modifier = Modifier
                                    .size(36.dp)
                                    .clip(RoundedCornerShape(10.dp))
                                    .background(BurntOrangeBadgeBg)
                                    .border(1.dp, BurntOrangeBorder, RoundedCornerShape(10.dp)),
                                contentAlignment = Alignment.Center
                            ) {
                                Icon(
                                    Icons.Default.Lock,
                                    contentDescription = null,
                                    tint = BurntOrange,
                                    modifier = Modifier.size(18.dp)
                                )
                            }
                            Spacer(modifier = Modifier.width(10.dp))
                            Text(
                                text = "Kredensial terlindungi",
                                color = TextPrimary,
                                fontWeight = FontWeight.Bold,
                                fontSize = 14.sp
                            )
                        }
                        Spacer(modifier = Modifier.width(8.dp))
                        StatusBadge(
                            text = "Terenkripsi",
                            isSuccess = true
                        )
                    }
                    Text(
                        text = "Kredensial disimpan terenkripsi menggunakan Android Keystore.",
                        color = TextSecondary,
                        fontSize = 12.sp,
                        lineHeight = 16.sp
                    )
                }
            }

            }
            item(key = "ssh", contentType = "form") {
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
                                text = "Koneksi SSH",
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

            }
            item(key = "hermes", contentType = "form") {
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
                                text = "Hermes Agent",
                                color = TextPrimary,
                                fontWeight = FontWeight.Bold,
                                fontSize = 15.sp
                            )
                            Text(
                                text = "Hubungkan ke API agent pribadi kamu",
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

            }
            item(key = "save", contentType = "actions") {
            Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
            // Action Buttons
            Button(
                onClick = { viewModel.saveSettings(onConfigUpdated) },
                colors = ButtonDefaults.buttonColors(
                    containerColor = BurntOrange,
                    contentColor = Color.White
                ),
                shape = RoundedCornerShape(14.dp),
                modifier = Modifier
                    .fillMaxWidth()
                    .height(50.dp)
                    .testTag("save_settings_button")
            ) {
                Icon(Icons.Default.Save, contentDescription = null)
                Spacer(modifier = Modifier.width(8.dp))
                Text("Simpan pengaturan", fontWeight = FontWeight.Bold, fontSize = 15.sp)
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
    }
}
