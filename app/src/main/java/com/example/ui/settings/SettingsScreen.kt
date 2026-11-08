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
import androidx.compose.foundation.verticalScroll
import androidx.compose.foundation.text.selection.SelectionContainer
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.List
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
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.collectAsState
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
import com.example.BuildConfig
import com.example.data.hermes.HermesApiClient
import com.example.data.model.ConnectionProfile
import com.example.data.model.ServerConfig
import com.example.data.model.LlmProvider
import com.example.ui.hermes.ModelPickerSheet
import com.example.ui.i18n.tr
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
import androidx.compose.material3.MaterialTheme

@Composable
fun SettingsScreen(
    encryptedStorage: EncryptedStorage,
    hermesClient: HermesApiClient,
    onConfigUpdated: (ServerConfig) -> Unit,
    modifier: Modifier = Modifier,
    preferencesStore: com.example.data.preferences.AppPreferencesStore? = null,
    section: String = "app",
    onSectionChange: ((String) -> Unit)? = null,
    externalConfig: ServerConfig? = null,
    bottomOverlayInset: androidx.compose.ui.unit.Dp = 0.dp,
    viewModel: SettingsViewModel = viewModel(
        factory = SettingsViewModel.provideFactory(encryptedStorage, hermesClient)
    )
) {
    val context = LocalContext.current
    val appearanceStore = remember(preferencesStore) { preferencesStore ?: com.example.data.preferences.AppPreferencesStore(context) }
    val appearance by appearanceStore.state.collectAsState()
    DisposableEffect(appearanceStore) { onDispose { if (preferencesStore == null) appearanceStore.close() } }
    val scrollState = rememberLazyListState()
    var localSection by remember { mutableStateOf(section) }
    val activeSection = if (onSectionChange == null) localSection else section
    LaunchedEffect(activeSection) { scrollState.scrollToItem(0) }
    val uiState by viewModel.uiState.collectAsStateWithLifecycle()
    LaunchedEffect(externalConfig) { if (externalConfig != null) viewModel.syncExternalSelection() }

    var showExportDialog by remember { mutableStateOf(false) }
    var showBackupPasswordDialog by remember { mutableStateOf(false) }
    var backupPassword by remember { mutableStateOf("") }
    var backupBusy by remember { mutableStateOf(false) }
    val scope = rememberCoroutineScope()
    var legalDocument by remember { mutableStateOf<Pair<String, String>?>(null) }
    var showSettingsModelPicker by remember { mutableStateOf(false) }
    if (showSettingsModelPicker) ModelPickerSheet(hermesClient, uiState.buildServerConfig(),
        onSelect = { viewModel.updateHermesModel(it); showSettingsModelPicker = false },
        onDismiss = { showSettingsModelPicker = false }, onOpenSettings = { showSettingsModelPicker = false })
    var legalText by remember { mutableStateOf<String?>(null) }
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

    LaunchedEffect(legalDocument) {
        legalText = null
        legalDocument?.let { (_, asset) ->
            legalText = withContext(Dispatchers.IO) {
                runCatching { context.assets.open(asset).bufferedReader().use { it.readText() } }
                    .getOrElse { "Dokumen belum tersedia di build ini." }
            }
        }
    }
    legalDocument?.let { (title, _) ->
        AlertDialog(
            onDismissRequest = { legalDocument = null },
            title = { Text(title, fontWeight = FontWeight.Bold) },
            text = {
                SelectionContainer {
                    Text(legalText ?: "Memuat dokumen…",
                        modifier = Modifier.height(380.dp).verticalScroll(rememberScrollState()),
                        fontSize = 13.sp, lineHeight = 20.sp)
                }
            },
            confirmButton = { TextButton(onClick = { legalDocument = null }) { Text(tr("Tutup", "Close")) } }
        )
    }

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
            containerColor = NavyDarkCard,
            titleContentColor = TextPrimary,
            textContentColor = TextSecondary,
            shape = RoundedCornerShape(28.dp),
            title = { Text(tr("Tambah host", "Add host"), fontWeight = FontWeight.Bold) },
            text = {
                Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                Text(tr("Simpan detail server baru sebagai profil koneksi.", "Save a new server as a connection profile."), color = TextSecondary, fontSize = 13.sp)
                    OutlinedTextField(
                        value = newProfileName, onValueChange = { newProfileName = it },
                        label = { Text(tr("Nama profil", "Profile name")) }, singleLine = true,
                        colors = OutlinedTextFieldDefaults.colors(
                            focusedBorderColor = BurntOrange, unfocusedBorderColor = MaterialTheme.colorScheme.outline,
                            focusedTextColor = TextPrimary, unfocusedTextColor = TextPrimary,
                            focusedLabelColor = BurntOrange
                        ), modifier = Modifier.fillMaxWidth()
                    )
                    OutlinedTextField(
                        value = newProfileHost, onValueChange = { newProfileHost = it },
                        label = { Text(tr("Host atau alamat IP", "Host or IP address")) }, placeholder = { Text(tr("contoh: server.tailnet", "for example: server.tailnet")) },
                        singleLine = true, keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Uri),
                        colors = OutlinedTextFieldDefaults.colors(
                            focusedBorderColor = BurntOrange, unfocusedBorderColor = MaterialTheme.colorScheme.outline,
                            focusedTextColor = TextPrimary, unfocusedTextColor = TextPrimary,
                            focusedLabelColor = BurntOrange
                        ), modifier = Modifier.fillMaxWidth()
                    )
                    Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                        OutlinedTextField(
                            value = newProfileUser, onValueChange = { newProfileUser = it },
                            label = { Text("Username") }, singleLine = true,
                            colors = OutlinedTextFieldDefaults.colors(
                                focusedBorderColor = BurntOrange, unfocusedBorderColor = MaterialTheme.colorScheme.outline,
                                focusedTextColor = TextPrimary, unfocusedTextColor = TextPrimary,
                                focusedLabelColor = BurntOrange
                            ), modifier = Modifier.weight(1f)
                        )
                        OutlinedTextField(
                            value = newProfilePort, onValueChange = { newProfilePort = it.filter(Char::isDigit).take(5) },
                            label = { Text("Port") }, singleLine = true,
                            keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
                            colors = OutlinedTextFieldDefaults.colors(
                                focusedBorderColor = BurntOrange, unfocusedBorderColor = MaterialTheme.colorScheme.outline,
                                focusedTextColor = TextPrimary, unfocusedTextColor = TextPrimary,
                                focusedLabelColor = BurntOrange
                            ), modifier = Modifier.width(104.dp)
                        )
                    }
                    OutlinedTextField(
                        value = newProfilePassword, onValueChange = { newProfilePassword = it },
                        label = { Text(tr("Password SSH (opsional untuk disimpan nanti)", "SSH password (optional until saved)")) }, singleLine = true,
                        visualTransformation = if (newProfilePasswordVisible) VisualTransformation.None else PasswordVisualTransformation(),
                        trailingIcon = {
                            IconButton(onClick = { newProfilePasswordVisible = !newProfilePasswordVisible }) {
                                Icon(if (newProfilePasswordVisible) Icons.Default.Visibility else Icons.Default.VisibilityOff,
                                    contentDescription = if (newProfilePasswordVisible) "Sembunyikan password" else "Tampilkan password",
                                    tint = TextSecondary)
                            }
                        },
                        colors = OutlinedTextFieldDefaults.colors(
                            focusedBorderColor = BurntOrange, unfocusedBorderColor = MaterialTheme.colorScheme.outline,
                            focusedTextColor = TextPrimary, unfocusedTextColor = TextPrimary,
                            focusedLabelColor = BurntOrange
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
                        containerColor = BurntOrange, disabledContainerColor = NavyDarkSurface,
                        disabledContentColor = TextSecondary.copy(alpha = .6f)
                    )
                ) { Text(tr("Simpan", "Save")) }
            },
            dismissButton = {
                TextButton(onClick = { showAddProfileDialog = false }) { Text(tr("Batal", "Cancel"), color = TextSecondary) }
            }
        )
    }

    if (showBackupPasswordDialog) {
        AlertDialog(
            onDismissRequest = { if (!backupBusy) { showBackupPasswordDialog = false; backupPassword = "" } },
            title = { Text(tr("Enkripsi backup", "Encrypt backup")) },
            text = {
                Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
                    Text(tr("Gunakan password minimal 12 karakter. Password ini diperlukan untuk memulihkan backup di perangkat lain.", "Use at least 12 characters. You need this password to restore the backup on another device."))
                    OutlinedTextField(value = backupPassword, onValueChange = { backupPassword = it },
                        label = { Text(tr("Password backup", "Backup password")) }, visualTransformation = PasswordVisualTransformation())
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
                    Text(tr("Ekspor profil koneksi", "Export connection profiles"), color = TextPrimary, fontWeight = FontWeight.Bold, fontSize = 16.sp)
                }
            },
            text = {
                Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                    Text(
                        text = tr("Backup dilindungi AES-GCM dengan password kamu. Simpan password terpisah dari backup.", "The backup uses AES-GCM with your password. Store the password separately."),
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
                    colors = ButtonDefaults.buttonColors(containerColor = BurntOrange, contentColor = MaterialTheme.colorScheme.onPrimary)
                ) {
                    Icon(Icons.Default.ContentCopy, contentDescription = null, modifier = Modifier.size(16.dp))
                    Spacer(modifier = Modifier.width(6.dp))
                    Text(tr("Salin ke clipboard", "Copy to clipboard"), fontWeight = FontWeight.Bold, fontSize = 12.sp)
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
                        Text(tr("Bagikan", "Share"), fontSize = 12.sp)
                    }
                    Spacer(modifier = Modifier.width(6.dp))
                    TextButton(onClick = { showExportDialog = false }) {
                        Text(tr("Tutup", "Close"), color = TextSecondary, fontSize = 12.sp)
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
                    Text(tr("Impor profil koneksi", "Import connection profiles"), color = TextPrimary, fontWeight = FontWeight.Bold, fontSize = 16.sp)
                }
            },
            text = {
                Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                    OutlinedTextField(value = backupPassword, onValueChange = { backupPassword = it },
                        label = { Text(tr("Password backup V2", "V2 backup password")) }, visualTransformation = PasswordVisualTransformation())
                    Text(
                        text = tr("Tempel backup V2 dan masukkan passwordnya. Backup V1 lama juga masih dapat diimpor tanpa password.", "Paste your V2 backup and enter its password. Older V1 backups can still be imported without a password."),
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
                        Text(tr("Tempel dari clipboard", "Paste from clipboard"), fontSize = 12.sp, color = BurntOrange)
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
                    colors = ButtonDefaults.buttonColors(containerColor = BurntOrange, contentColor = MaterialTheme.colorScheme.onPrimary)
                ) {
                    Text(tr("Lanjutkan impor", "Continue import"), fontWeight = FontWeight.Bold, fontSize = 12.sp)
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
                    Text(tr("Konfirmasi pemulihan profil", "Confirm profile restore"), color = TextPrimary, fontWeight = FontWeight.Bold, fontSize = 16.sp)
                }
            },
            text = {
                Text(
                    text = tr("Impor akan mengganti profil saat ini. Lanjutkan?", "Importing will replace your current profiles. Continue?"),
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
                    colors = ButtonDefaults.buttonColors(containerColor = BurntOrange, contentColor = MaterialTheme.colorScheme.onPrimary)
                ) {
                    Text(tr("Ganti dan pulihkan", "Replace and restore"), fontWeight = FontWeight.Bold, fontSize = 12.sp)
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
            title = tr("Pengaturan", "Settings"),
            subtitle = tr("Terminal, chat, dan koneksi", "Terminal, chat, and connections")
        )

        Row(Modifier.fillMaxWidth().padding(horizontal = 20.dp), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            androidx.compose.material3.FilterChip(selected = activeSection == "app", onClick = { localSection = "app"; onSectionChange?.invoke("app") }, label = { Text(tr("Aplikasi", "App")) })
            androidx.compose.material3.FilterChip(selected = activeSection == "connection", onClick = { localSection = "connection"; onSectionChange?.invoke("connection") }, label = { Text(tr("Koneksi & model", "Connections & models")) })
        }
        LazyColumn(
            state = scrollState,
            modifier = Modifier
                .weight(1f)
                .fillMaxWidth()
                .padding(horizontal = 20.dp, vertical = 8.dp)
                .testTag("settings_list"),
            contentPadding = PaddingValues(bottom = bottomOverlayInset),
            verticalArrangement = Arrangement.spacedBy(if (activeSection == "app") 16.dp else 20.dp)
        ) {
            if (activeSection == "app") appPreferenceItems(appearance, appearanceStore::update)
            if (activeSection == "connection") {
            item(key = "profiles", contentType = "profiles") {
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
                                text = tr("Profil koneksi", "Connection profiles"),
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
                        text = tr("Pilih koneksi untuk di rumah atau saat bepergian.", "Choose a connection for home or away."),
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
                            Text(tr("Tambah", "Add"), fontSize = 12.sp, color = BurntOrange)
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
                            label = { Text(tr("Label profil aktif", "Active profile label"), color = TextSecondary) },
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
                            Text(tr("Ekspor profil", "Export profiles"), fontSize = 12.sp, fontWeight = FontWeight.SemiBold, color = BurntOrange)
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
                            Text(tr("Impor profil", "Import profiles"), fontSize = 12.sp, fontWeight = FontWeight.SemiBold, color = TextSecondary)
                        }
                    }
                }
            }

            }
            item(key = "security", contentType = "security_summary") {
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
                                text = tr("Kredensial terlindungi", "Protected credentials"),
                                color = TextPrimary,
                                fontWeight = FontWeight.Bold,
                                fontSize = 14.sp
                            )
                        }
                        Spacer(modifier = Modifier.width(8.dp))
                        StatusBadge(
                            text = tr("Terenkripsi", "Encrypted"),
                            isSuccess = true
                        )
                    }
                    Text(
                        text = tr("Kredensial disimpan terenkripsi menggunakan Android Keystore.", "Credentials are encrypted using Android Keystore."),
                        color = TextSecondary,
                        fontSize = 12.sp,
                        lineHeight = 16.sp
                    )
                }
            }

            }
            item(key = "ssh", contentType = "ssh_form") {
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
                                text = tr("Koneksi SSH", "SSH connection"),
                                color = TextPrimary,
                                fontWeight = FontWeight.Bold,
                                fontSize = 15.sp
                            )
                            Text(
                                text = tr("Gunakan IP server, IP Tailscale, atau nama host.", "Use a server IP, Tailscale IP, or host name."),
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
                            label = { Text(tr("Alamat server / Host", "Server address / Host"), color = TextSecondary) },
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
                        label = { Text(tr("Username SSH", "SSH username"), color = TextSecondary) },
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
                        label = { Text(tr("Password SSH", "SSH password"), color = TextSecondary) },
                        singleLine = true,
                        visualTransformation = if (uiState.passwordVisible) VisualTransformation.None else PasswordVisualTransformation(),
                        trailingIcon = {
                            IconButton(onClick = { viewModel.togglePasswordVisibility() }) {
                                Icon(
                                    imageVector = if (uiState.passwordVisible) Icons.Default.Visibility else Icons.Default.VisibilityOff,
                                    contentDescription = tr("Tampilkan atau sembunyikan password", "Show or hide password"),
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
                            Text(tr("Cek koneksi SSH", "Test SSH connection"), fontSize = 12.sp, fontWeight = FontWeight.SemiBold)
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
            item(key = "hermes", contentType = "hermes_form") {
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
                                text = tr("Pilih layanan AI untuk chat Hermes", "Choose an AI service for Hermes chat"),
                                color = TextSecondary,
                                fontSize = 11.sp
                            )
                        }
                    }

                    LlmProviderPicker(uiState.hermesProvider, viewModel::selectProvider)

                    OutlinedTextField(
                        value = uiState.hermesUrl,
                        onValueChange = { viewModel.updateHermesUrl(it) },
                        readOnly = uiState.hermesProvider != "custom",
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
                        label = { Text(tr("Nama model", "Model name"), color = TextSecondary) },
                            placeholder = { Text(tr("Pilih dari daftar atau isi ID model", "Choose from the list or enter a model ID")) },
                            singleLine = true,
                            trailingIcon = {
                                if (uiState.isDetectingModels) {
                                    CircularProgressIndicator(
                                        modifier = Modifier.size(18.dp),
                                        strokeWidth = 2.dp,
                                        color = BurntOrange
                                    )
                                } else {
                                    IconButton(onClick = { showSettingsModelPicker = true }) {
                                        Icon(
                                            imageVector = Icons.Default.List,
                                            contentDescription = tr("Pilih model provider", "Choose provider model"),
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
                            onClick = { showSettingsModelPicker = true },
                            modifier = Modifier
                                .fillMaxWidth()
                                .height(48.dp).testTag("settings_choose_model"),
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
                                Text(tr("Memuat model…", "Loading models…"), fontSize = 12.sp, color = BurntOrange)
                            } else {
                                Icon(Icons.Default.List, contentDescription = null, modifier = Modifier.size(16.dp), tint = BurntOrange)
                                Spacer(modifier = Modifier.width(8.dp))
                                Text(tr("Pilih model", "Choose model"), fontSize = 12.sp, fontWeight = FontWeight.SemiBold, color = BurntOrange)
                            }
                        }

                        Text(
                            text = tr("Daftar diambil dari provider. Pilih model chat yang mendukung teks, atau masukkan ID model secara manual.", "Models come from your provider. Choose a text chat model, or enter its ID manually."),
                            color = TextSecondary,
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
                        label = { Text(if (LlmProvider.fromId(uiState.hermesProvider).requiresKey) "API key ${LlmProvider.fromId(uiState.hermesProvider).label}" else "API key (opsional)", color = TextSecondary) },
                        placeholder = { Text("sk-...") },
                        singleLine = true,
                        visualTransformation = if (uiState.hermesKeyVisible) VisualTransformation.None else PasswordVisualTransformation(),
                        trailingIcon = {
                            IconButton(onClick = { viewModel.toggleHermesKeyVisibility() }) {
                                Icon(
                                    imageVector = if (uiState.hermesKeyVisible) Icons.Default.Visibility else Icons.Default.VisibilityOff,
                                    contentDescription = if (uiState.hermesKeyVisible) "Sembunyikan API key" else "Tampilkan API key",
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
                            enabled = !uiState.isTestingHermes,
                            modifier = Modifier.testTag("settings_test_ai"),
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
                            Text(tr("Cek koneksi AI", "Test AI connection"), fontSize = 12.sp, fontWeight = FontWeight.SemiBold)
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

                    Text(tr("Cek koneksi mengirim pesan singkat ke model dan memakai kuota API provider.", "Testing sends a short message to the model and uses your provider API quota."), color = TextSecondary, fontSize = 11.sp)

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
                    contentColor = MaterialTheme.colorScheme.onPrimary
                ),
                shape = RoundedCornerShape(14.dp),
                modifier = Modifier
                    .fillMaxWidth()
                    .height(50.dp)
                    .testTag("save_settings_button")
            ) {
                Icon(Icons.Default.Save, contentDescription = null)
                Spacer(modifier = Modifier.width(8.dp))
                Text(tr("Simpan pengaturan", "Save settings"), fontWeight = FontWeight.Bold, fontSize = 15.sp)
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
                Text(tr("Isi contoh konfigurasi (tanpa kredensial)", "Fill sample configuration (no credentials)"), fontSize = 13.sp)
            }

            Spacer(modifier = Modifier.height(16.dp))
            }
            }
            }
            item(key = "about", contentType = "about") {
                TailHarnessCard {
                    Column(Modifier.fillMaxWidth().padding(16.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
                        Text("Hermes Home", color = TextPrimary, fontWeight = FontWeight.Bold, fontSize = 17.sp)
                        Text("Versi ${BuildConfig.VERSION_NAME} · ${if (BuildConfig.DEBUG) "Debug beta" else "Release beta"}",
                            color = TextSecondary, fontSize = 12.sp)
                        Text("© 2026 knxxy205 dan kontributor Hermes Home · GPL-3.0-only",
                            color = TextSecondary, fontSize = 12.sp)
                        Text("Tanpa jaminan. Boleh dimodifikasi dan didistribusikan sesuai GPL versi 3.",
                            color = TextSecondary, fontSize = 12.sp)
                        TextButton(onClick = { legalDocument = "GPL-3.0-only" to "LICENSE" }) { Text(tr("Lisensi aplikasi", "App license")) }
                        TextButton(onClick = { legalDocument = "Privacy" to "PRIVACY.md" }) { Text(tr("Kebijakan privasi", "Privacy policy")) }
                        TextButton(onClick = { legalDocument = "Security" to "SECURITY.md" }) { Text(tr("Pelaporan keamanan", "Security reporting")) }
                        TextButton(onClick = { legalDocument = "Notices" to "THIRD_PARTY_NOTICES.md" }) { Text(tr("Lisensi pihak ketiga", "Third-party licenses")) }
                    }
                }
            }
        }
    }
}
