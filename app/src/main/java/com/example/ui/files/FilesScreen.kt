package com.example.ui.files
import androidx.compose.ui.graphics.asImageBitmap

import android.content.Context
import android.net.Uri
import android.widget.Toast
import androidx.activity.compose.BackHandler
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
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
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.automirrored.filled.InsertDriveFile
import androidx.compose.material.icons.filled.Code
import androidx.compose.material.icons.filled.CheckCircle
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.CreateNewFolder
import androidx.compose.material.icons.filled.CloudUpload
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.Description
import androidx.compose.material.icons.filled.ErrorOutline
import androidx.compose.material.icons.filled.DriveFileRenameOutline
import androidx.compose.material.icons.filled.FileUpload
import androidx.compose.material.icons.filled.Folder
import androidx.compose.material.icons.filled.FolderOpen
import androidx.compose.material.icons.filled.MoreVert
import androidx.compose.material.icons.filled.NoteAdd
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material.icons.filled.SaveAlt
import androidx.compose.material.icons.filled.Search
import androidx.compose.material.icons.filled.SmartToy
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.FloatingActionButton
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.OutlinedTextFieldDefaults
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.graphics.Color
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.foundation.layout.heightIn
import com.example.ui.theme.FolderBlue
import com.example.ui.theme.FolderPeach
import com.example.ui.theme.FolderRose
import com.example.ui.theme.FolderMint
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.viewmodel.compose.viewModel
import com.example.data.model.ServerConfig
import com.example.data.sftp.RemoteFileItem
import com.example.data.sftp.SftpManager
import com.example.ui.components.CustomScreenHeader
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
import java.io.File
import androidx.work.WorkInfo
import androidx.work.WorkManager
import com.example.data.worker.SftpUploadWorker

@Composable
fun FilesScreen(
    sftpManager: SftpManager,
    serverConfig: ServerConfig,
    viewModel: FilesViewModel = viewModel(factory = FilesViewModel.provideFactory(sftpManager)),
    onAskHermesAboutFile: ((fileName: String, content: String) -> Unit)? = null,
    onOpenSettings: (() -> Unit)? = null,
    isTabActive: Boolean = true,
    modifier: Modifier = Modifier
) {
    val context = LocalContext.current
    val uiState by viewModel.uiState.collectAsState()
    val uploadWorkInfos by remember(context) {
        WorkManager.getInstance(context).getWorkInfosByTagFlow(SftpUploadWorker.TAG)
    }.collectAsState(initial = emptyList())
    val currentUpload = uploadWorkInfos
        .filter { it.state == WorkInfo.State.RUNNING }
        .firstOrNull()
        ?: uploadWorkInfos
            .filter { it.state == WorkInfo.State.ENQUEUED }
            .firstOrNull()
        ?: uploadWorkInfos.firstOrNull()
    val uploadRunning = currentUpload?.state == WorkInfo.State.RUNNING || currentUpload?.state == WorkInfo.State.ENQUEUED
    LaunchedEffect(currentUpload?.id, currentUpload?.state) {
        when (currentUpload?.state) {
            WorkInfo.State.SUCCEEDED -> {
                val count = currentUpload.outputData.getInt(SftpUploadWorker.KEY_COUNT, 0)
                val bytes = currentUpload.outputData.getLong(SftpUploadWorker.KEY_DONE_BYTES, 0L)
                val total = currentUpload.outputData.getLong(SftpUploadWorker.KEY_TOTAL_BYTES, -1L)
                val path = currentUpload.outputData.getString(SftpUploadWorker.KEY_REMOTE_PATH).orEmpty()
                val failed = currentUpload.outputData.getInt(SftpUploadWorker.KEY_FAILED, 0)
                val batchId = currentUpload.outputData.getString(SftpUploadWorker.KEY_BATCH_ID)
                if (batchId != null) {
                    viewModel.reportBackgroundUploadResult("Upload selesai · ${count - failed}/$count berhasil · ${SftpUploadWorker.formatBytes(bytes)}${if (total > 0) " / ${SftpUploadWorker.formatBytes(total)}" else ""} ke $path", failed > 0)
                }
                if (path == uiState.currentPath) viewModel.loadFiles(config = serverConfig)
            }
            WorkInfo.State.FAILED -> {
                val count = currentUpload.outputData.getInt(SftpUploadWorker.KEY_FAILED, 0)
                val path = currentUpload.outputData.getString(SftpUploadWorker.KEY_REMOTE_PATH).orEmpty()
                val batchId = currentUpload.outputData.getString(SftpUploadWorker.KEY_BATCH_ID)
                if (batchId != null) {
                    viewModel.reportBackgroundUploadResult("Upload selesai dengan $count file gagal ke $path", true)
                }
                if (path == uiState.currentPath) viewModel.loadFiles(config = serverConfig)
            }
            else -> Unit
        }
    }

    // Dialog states for user input
    var showNewFolderDialog by remember { mutableStateOf(false) }
    var newFolderName by remember { mutableStateOf("") }

    var showNewFileDialog by remember { mutableStateOf(false) }
    var newFileName by remember { mutableStateOf("") }
    var newFileContent by remember { mutableStateOf("") }

    var itemToRename by remember { mutableStateOf<RemoteFileItem?>(null) }
    var renameNewName by remember { mutableStateOf("") }

    var itemToDelete by remember { mutableStateOf<RemoteFileItem?>(null) }

    // Native Back-Button Navigation:
    // 1. If any dialog/file preview is open, dismiss it.
    // 2. If in a sub-directory, navigate up to parent folder.
    // 3. When at root directory ("/") and no dialog is open, BackHandler is disabled, allowing default system back behavior (closing tab/app).
    val canNavigateUp = viewModel.canNavigateUp()
    val isAnyOverlayOpen = uiState.imagePreview != null || uiState.viewingFileContent != null || showNewFolderDialog || showNewFileDialog || itemToRename != null || itemToDelete != null

    BackHandler(enabled = isTabActive && (canNavigateUp || isAnyOverlayOpen)) {
        when {
            showNewFolderDialog -> showNewFolderDialog = false
            showNewFileDialog -> showNewFileDialog = false
            itemToRename != null -> itemToRename = null
            itemToDelete != null -> itemToDelete = null
            uiState.imagePreview != null -> viewModel.closeImagePreview()
            uiState.viewingFileContent != null -> viewModel.setViewingFile(null)
            canNavigateUp -> viewModel.navigateUp(serverConfig)
        }
    }

    // 1. Storage Access Framework (SAF) Multiple File Picker Contract initialized in root Composable
    var uploadDestinationPath by rememberSaveable { mutableStateOf(uiState.currentPath) }
    val filePickerLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.OpenMultipleDocuments()
    ) { uris: List<Uri> ->
        if (uris.isNotEmpty()) {
            viewModel.uploadMultipleFiles(
                context = context.applicationContext,
                uris = uris,
                config = serverConfig,
                destinationPath = uploadDestinationPath
            )
        }
    }

    val notificationPermissionLauncher = rememberLauncherForActivityResult(
        ActivityResultContracts.RequestPermission()
    ) { }
    val ensureUploadNotificationPermission = {
        if (android.os.Build.VERSION.SDK_INT >= 33 &&
            androidx.core.content.ContextCompat.checkSelfPermission(context, android.Manifest.permission.POST_NOTIFICATIONS) != android.content.pm.PackageManager.PERMISSION_GRANTED
        ) notificationPermissionLauncher.launch(android.Manifest.permission.POST_NOTIFICATIONS)
    }

    // Safe launch helper for file picker using SAF OpenMultipleDocuments
    val launchFilePicker = {
        try {
            ensureUploadNotificationPermission()
            uploadDestinationPath = viewModel.currentRemotePath()
            filePickerLauncher.launch(arrayOf("*/*"))
        } catch (e: Exception) {
            Toast.makeText(
                context,
                "Tidak dapat membuka pemilih berkas: ${e.localizedMessage ?: e.message}",
                Toast.LENGTH_LONG
            ).show()
        }
    }

    // Observe status messages for feedback
    LaunchedEffect(uiState.statusMessage) {
        uiState.statusMessage?.let { msg ->
            Toast.makeText(context, msg, Toast.LENGTH_SHORT).show()
            viewModel.clearStatusMessage()
        }
    }

    LaunchedEffect(uiState.transferStatus) {
        if (uiState.transferStatus != null) {
            kotlinx.coroutines.delay(5000)
            viewModel.clearTransferStatus()
        }
    }

    // Initial load and reload when config changes
    LaunchedEffect(serverConfig) {
        viewModel.loadFiles(config = serverConfig)
    }

    val filteredList = remember(uiState.files, uiState.searchQuery) {
        if (uiState.searchQuery.isBlank()) uiState.files
        else uiState.files.filter { it.name.contains(uiState.searchQuery, ignoreCase = true) }
    }

    Box(
        modifier = modifier
            .fillMaxSize()
            .background(NavyDarkBackground)
    ) {
        Column(
            modifier = Modifier.fillMaxSize()
        ) {
            // Custom Screen Header
            CustomScreenHeader(
                icon = Icons.Default.FolderOpen,
                title = "Files",
                subtitle = if (serverConfig.isSshConfigured) serverConfig.sshHost else "Jelajahi file · Sandbox",
                trailingContent = {
                    if (serverConfig.isSshConfigured) Row {
                        IconButton(
                            onClick = launchFilePicker,
                            modifier = Modifier.size(48.dp).testTag("upload_file_button")
                        ) {
                            Icon(
                                Icons.Default.FileUpload,
                                contentDescription = "Upload File",
                                tint = BurntOrange
                            )
                        }
                        IconButton(
                            onClick = { showNewFolderDialog = true },
                            modifier = Modifier.size(48.dp).testTag("new_folder_button")
                        ) {
                            Icon(
                                Icons.Default.CreateNewFolder,
                                contentDescription = "New Folder",
                                tint = BurntOrange
                            )
                        }
                        IconButton(
                            onClick = { showNewFileDialog = true },
                            modifier = Modifier.size(48.dp).testTag("new_file_button")
                        ) {
                            Icon(
                                Icons.Default.NoteAdd,
                                contentDescription = "New File",
                                tint = BurntOrange
                            )
                        }
                    }
                }
            )

            // Path Navigation & Action Bar
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 16.dp, vertical = 6.dp)
                    .clip(RoundedCornerShape(12.dp))
                    .background(NavyDarkSurface)
                    .border(1.dp, NavyDarkBorder, RoundedCornerShape(12.dp))
                    .padding(horizontal = 8.dp, vertical = 6.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                IconButton(
                    onClick = { viewModel.navigateUp(serverConfig) },
                    enabled = uiState.currentPath != "/"
                ) {
                    Icon(
                        Icons.AutoMirrored.Filled.ArrowBack,
                        contentDescription = "Go Up",
                        tint = if (uiState.currentPath != "/") BurntOrange else TextSecondary.copy(alpha = 0.4f),
                        modifier = Modifier.size(20.dp)
                    )
                }

                Text(
                    text = uiState.currentPath,
                    color = TextPrimary,
                    fontFamily = FontFamily.Monospace,
                    fontSize = 13.sp,
                    fontWeight = FontWeight.SemiBold,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                    modifier = Modifier.weight(1f).padding(horizontal = 4.dp)
                )

                IconButton(onClick = { viewModel.loadFiles(config = serverConfig) }) {
                    Icon(
                        Icons.Default.Refresh,
                        contentDescription = "Refresh",
                        tint = BurntOrange,
                        modifier = Modifier.size(20.dp)
                    )
                }
            }

            // Search Bar
            OutlinedTextField(
                value = uiState.searchQuery,
                onValueChange = { viewModel.updateSearchQuery(it) },
                placeholder = { Text("Cari file atau folder...", color = TextSecondary, fontSize = 13.sp) },
                leadingIcon = {
                    Icon(Icons.Default.Search, contentDescription = null, tint = TextSecondary, modifier = Modifier.size(18.dp))
                },
                singleLine = true,
                shape = RoundedCornerShape(50),
                colors = OutlinedTextFieldDefaults.colors(
                    focusedContainerColor = NavyDarkCard,
                    unfocusedContainerColor = NavyDarkCard,
                    focusedBorderColor = BurntOrange,
                    unfocusedBorderColor = NavyDarkBorder,
                    focusedTextColor = TextPrimary,
                    unfocusedTextColor = TextPrimary
                ),
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 16.dp, vertical = 4.dp)
                    .heightIn(min = 56.dp)
            )

            // Transfer state: persistent progress while active, concise completion result afterwards.
            if (uploadRunning || uiState.isDownloading || uiState.transferStatus != null) {
                TailHarnessCard(
                    backgroundColor = Color.White,
                    borderColor = Color(0xffe2e8f0),
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(horizontal = 16.dp, vertical = 4.dp)
                ) {
                    Column(Modifier.fillMaxWidth().padding(horizontal = 14.dp, vertical = 12.dp)) {
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            if (uploadRunning || uiState.isDownloading) {
                                CircularProgressIndicator(color = Color(0xff3b82f6), strokeWidth = 2.dp, modifier = Modifier.size(18.dp))
                            } else if (uiState.transferFailed) {
                                Icon(Icons.Default.ErrorOutline, contentDescription = null, tint = Color(0xffbe123c), modifier = Modifier.size(20.dp))
                            } else {
                                Icon(Icons.Default.CheckCircle, contentDescription = null, tint = Color(0xff0f5f51), modifier = Modifier.size(20.dp))
                            }
                            Spacer(Modifier.width(12.dp))
                            Column(Modifier.weight(1f)) {
                                Text(
                                    uiState.transferStatus ?: if (uploadRunning) {
                                        val fileName = currentUpload?.progress?.getString(SftpUploadWorker.KEY_NAME).orEmpty()
                                        val index = currentUpload?.progress?.getInt(SftpUploadWorker.KEY_INDEX, 0) ?: 0
                                        val count = currentUpload?.progress?.getInt(SftpUploadWorker.KEY_COUNT, 0) ?: 0
                                        val bytes = currentUpload?.progress?.getLong(SftpUploadWorker.KEY_FILE_BYTES, 0L) ?: 0L
                                        val total = currentUpload?.progress?.getLong(SftpUploadWorker.KEY_FILE_SIZE, -1L) ?: -1L
                                        val targetPath = currentUpload?.progress?.getString(SftpUploadWorker.KEY_REMOTE_PATH).orEmpty()
                                        "Mengunggah ke $targetPath · $index/$count · $fileName · ${SftpUploadWorker.formatBytes(bytes)}${if (total > 0) " / ${SftpUploadWorker.formatBytes(total)}" else ""}"
                                    } else "Mengunduh file",
                                    color = Color(0xff1e293b), fontWeight = FontWeight.SemiBold, fontSize = 13.sp,
                                    maxLines = 2, overflow = TextOverflow.Ellipsis
                                )
                                (if (uploadRunning) "${currentUpload?.progress?.getInt(SftpUploadWorker.KEY_PERCENT, 0) ?: 0}%" else uiState.downloadingFileName)?.let {
                                    Text(it, color = Color(0xff64748b), fontSize = 12.sp, fontFamily = FontFamily.Monospace,
                                        maxLines = 1, overflow = TextOverflow.Ellipsis)
                                }
                            }
                            if (uiState.transferStatus != null) {
                                IconButton(onClick = viewModel::clearTransferStatus, modifier = Modifier.size(40.dp)) {
                                    Icon(Icons.Default.Close, "Tutup status transfer", tint = Color(0xff64748b))
                                }
                            }
                        }
                        if (uploadRunning || uiState.isDownloading) {
                            Spacer(Modifier.height(10.dp))
                            val progress = currentUpload?.progress?.getInt(SftpUploadWorker.KEY_PERCENT, 0) ?: 0
                            if (uploadRunning) LinearProgressIndicator(
                                progress = { progress / 100f },
                                modifier = Modifier.fillMaxWidth().height(4.dp).clip(RoundedCornerShape(4.dp)),
                                color = Color(0xff3b82f6), trackColor = Color(0xffe2e8f0)
                            ) else LinearProgressIndicator(
                                modifier = Modifier.fillMaxWidth().height(4.dp).clip(RoundedCornerShape(4.dp)),
                                color = Color(0xff3b82f6), trackColor = Color(0xffe2e8f0)
                            )
                        }
                    }
                }
            }

            Spacer(modifier = Modifier.height(4.dp))

            // Content Area
            Box(
                modifier = Modifier
                    .weight(1f)
                    .fillMaxWidth()
            ) {
                when {
                    !serverConfig.isSshConfigured -> {
                        Column(
                            Modifier.fillMaxSize().padding(28.dp),
                            horizontalAlignment = Alignment.CenterHorizontally,
                            verticalArrangement = Arrangement.Center
                        ) {
                            Box(Modifier.size(72.dp).clip(RoundedCornerShape(24.dp)).background(Color(0xffeaf2fe)), contentAlignment = Alignment.Center) {
                                Icon(Icons.Default.FolderOpen, null, tint = Color(0xff3b82f6), modifier = Modifier.size(34.dp))
                            }
                            Spacer(Modifier.height(18.dp))
                            Text("Hubungkan ke server", color = Color(0xff1e293b), fontSize = 18.sp, fontWeight = FontWeight.Bold)
                            Spacer(Modifier.height(6.dp))
                            Text("Simpan konfigurasi SSH di Settings untuk menjelajahi dan mengelola file.",
                                color = Color(0xff64748b), fontSize = 14.sp, lineHeight = 20.sp)
                            Spacer(Modifier.height(18.dp))
                            Button(onClick = { onOpenSettings?.invoke() }, colors = ButtonDefaults.buttonColors(containerColor = Color(0xff3b82f6))) {
                                Text("Buka Settings", color = Color.White)
                            }
                        }
                    }
                    uiState.isLoading -> {
                        Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                            Column(horizontalAlignment = Alignment.CenterHorizontally) {
                                CircularProgressIndicator(color = Color(0xff3b82f6))
                                Spacer(Modifier.height(12.dp))
                                Text("Memuat file server…", color = Color(0xff64748b), fontSize = 13.sp)
                            }
                        }
                    }
                    uiState.errorMessage != null -> {
                        Column(
                            modifier = Modifier
                                .fillMaxSize()
                                .padding(24.dp),
                            horizontalAlignment = Alignment.CenterHorizontally,
                            verticalArrangement = Arrangement.Center
                        ) {
                            Text(
                                text = uiState.errorMessage ?: "Error",
                                color = Color(0xffbe123c),
                                fontSize = 14.sp,
                                fontFamily = FontFamily.Monospace
                            )
                            Spacer(modifier = Modifier.height(8.dp))
                            Text(
                                "Periksa host, port, username, dan koneksi jaringan server.",
                                color = Color(0xff64748b), fontSize = 13.sp, lineHeight = 18.sp
                            )
                            Spacer(modifier = Modifier.height(12.dp))
                            Button(
                                onClick = { viewModel.loadFiles(config = serverConfig) },
                                colors = ButtonDefaults.buttonColors(containerColor = Color(0xff3b82f6))
                            ) {
                                Text("Coba Lagi", color = Color.White)
                            }
                        }
                    }
                    filteredList.isEmpty() -> {
                        Column(
                            modifier = Modifier
                                .fillMaxSize()
                                .padding(24.dp),
                            horizontalAlignment = Alignment.CenterHorizontally,
                            verticalArrangement = Arrangement.Center
                        ) {
                            Icon(
                                Icons.Default.FolderOpen,
                                contentDescription = null,
                                tint = Color(0xff64748b).copy(alpha = 0.45f),
                                modifier = Modifier.size(48.dp)
                            )
                            Spacer(modifier = Modifier.height(12.dp))
                            Text("Folder ini masih kosong", color = Color(0xff1e293b), fontSize = 16.sp, fontWeight = FontWeight.SemiBold)
                            Spacer(Modifier.height(4.dp))
                            Text("Unggah file atau buat folder untuk mulai mengisi.", color = Color(0xff64748b), fontSize = 13.sp)
                        }
                    }
                    else -> {
                        LazyColumn(
                            modifier = Modifier.fillMaxSize(),
                            contentPadding = PaddingValues(start = 20.dp, end = 20.dp, top = 16.dp, bottom = 96.dp),
                            verticalArrangement = Arrangement.spacedBy(12.dp)
                        ) {
                            val folders = filteredList.filter { it.isDirectory }
                            val files = filteredList.filterNot { it.isDirectory }
                            if (folders.isNotEmpty()) {
                                item {
                                    Text("Folders", style = MaterialTheme.typography.titleMedium, color = TextPrimary)
                                }
                                items(folders.chunked(2), key = { "folders_${it.first().path}" }) { pair ->
                                    Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                                        pair.forEach { folder ->
                                            Box(Modifier.weight(1f)) {
                                                FileItemCard(
                                                    item = folder,
                                                    onOpen = { viewModel.navigateTo(folder.path, serverConfig) },
                                                    onRename = { itemToRename = folder; renameNewName = folder.name },
                                                    onDelete = { itemToDelete = folder },
                                                    onDownload = {}
                                                )
                                            }
                                        }
                                        if (pair.size == 1) Spacer(Modifier.weight(1f))
                                    }
                                }
                            }
                            if (files.isNotEmpty()) {
                                item {
                                    Text("Files", style = MaterialTheme.typography.titleMedium, color = TextPrimary,
                                        modifier = Modifier.padding(top = 8.dp))
                                }
                            }
                            items(files, key = { it.path }) { item ->
                                FileItemCard(
                                    item = item,
                                    onOpen = {
                                        if (item.isDirectory) {
                                            viewModel.navigateTo(item.path, serverConfig)
                                        } else {
                                            viewModel.readFile(item, serverConfig, context.cacheDir)
                                        }
                                    },
                                    onRename = {
                                        itemToRename = item
                                        renameNewName = item.name
                                    },
                                    onDelete = {
                                        itemToDelete = item
                                    },
                                    onDownload = {
                                        val dest = File(context.cacheDir, item.name)
                                        viewModel.downloadFile(item, dest, serverConfig)
                                    }
                                )
                            }
                        }
                    }
                }
            }
        }

        // Floating Action Button for quick upload
        if (serverConfig.isSshConfigured) FloatingActionButton(
            onClick = launchFilePicker,
            modifier = Modifier
                .align(Alignment.BottomEnd)
                .padding(20.dp)
                .testTag("upload_file_fab"),
            containerColor = Color(0xff3b82f6),
            contentColor = Color.White,
            shape = RoundedCornerShape(16.dp)
        ) {
            Row(
                modifier = Modifier.padding(horizontal = 14.dp, vertical = 8.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                Icon(
                    Icons.Default.FileUpload,
                    contentDescription = "Upload File",
                    modifier = Modifier.size(20.dp)
                )
                Spacer(modifier = Modifier.width(6.dp))
                Text(
                    "Upload File",
                    fontWeight = FontWeight.Bold,
                    fontSize = 13.sp
                )
            }
        }
    }

    // New Folder Dialog
    if (showNewFolderDialog) {
        AlertDialog(
            onDismissRequest = { showNewFolderDialog = false },
            containerColor = Color.White,
            titleContentColor = Color(0xff1e293b),
            shape = RoundedCornerShape(28.dp),
            title = { Text("Buat Folder Baru", fontWeight = FontWeight.Bold) },
            text = {
                OutlinedTextField(
                    value = newFolderName,
                    onValueChange = { newFolderName = it },
                    label = { Text("Nama folder") },
                    placeholder = { Text("contoh: scripts") },
                    singleLine = true,
                    colors = OutlinedTextFieldDefaults.colors(
                        focusedBorderColor = Color(0xff3b82f6),
                        unfocusedBorderColor = Color(0xffe2e8f0),
                        focusedTextColor = Color(0xff1e293b),
                        unfocusedTextColor = Color(0xff1e293b),
                        focusedLabelColor = Color(0xff3b82f6)
                    )
                )
            },
            confirmButton = {
                Button(
                    onClick = {
                        if (newFolderName.isNotBlank()) {
                            viewModel.createFolder(newFolderName, serverConfig)
                            showNewFolderDialog = false
                            newFolderName = ""
                        }
                    },
                    colors = ButtonDefaults.buttonColors(containerColor = Color(0xff3b82f6))
                ) {
                    Text("Buat", color = Color.White, fontWeight = FontWeight.Bold)
                }
            },
            dismissButton = {
                    TextButton(onClick = { showNewFolderDialog = false }) {
                    Text("Batal", color = Color(0xff64748b))
                }
            }
        )
    }

    // New File Dialog
    if (showNewFileDialog) {
        AlertDialog(
            onDismissRequest = { showNewFileDialog = false },
            containerColor = Color.White,
            titleContentColor = Color(0xff1e293b),
            shape = RoundedCornerShape(28.dp),
            title = { Text("Buat File Baru", fontWeight = FontWeight.Bold) },
            text = {
                Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    OutlinedTextField(
                        value = newFileName,
                        onValueChange = { newFileName = it },
                        label = { Text("Nama file") },
                        placeholder = { Text("contoh: config.json") },
                        singleLine = true,
                        colors = OutlinedTextFieldDefaults.colors(
                            focusedBorderColor = Color(0xff3b82f6),
                            unfocusedBorderColor = Color(0xffe2e8f0),
                            focusedTextColor = Color(0xff1e293b),
                            unfocusedTextColor = Color(0xff1e293b),
                            focusedLabelColor = Color(0xff3b82f6)
                        )
                    )
                    OutlinedTextField(
                        value = newFileContent,
                        onValueChange = { newFileContent = it },
                        label = { Text("Isi file") },
                        placeholder = { Text("Tulis isi file teks…") },
                        maxLines = 6,
                        modifier = Modifier.height(120.dp),
                        colors = OutlinedTextFieldDefaults.colors(
                            focusedBorderColor = Color(0xff3b82f6),
                            unfocusedBorderColor = Color(0xffe2e8f0),
                            focusedTextColor = Color(0xff1e293b),
                            unfocusedTextColor = Color(0xff1e293b),
                            focusedLabelColor = Color(0xff3b82f6)
                        )
                    )
                }
            },
            confirmButton = {
                Button(
                    onClick = {
                        if (newFileName.isNotBlank()) {
                            viewModel.createFile(newFileName, newFileContent, serverConfig)
                            showNewFileDialog = false
                            newFileName = ""
                            newFileContent = ""
                        }
                    },
                    colors = ButtonDefaults.buttonColors(containerColor = Color(0xff3b82f6))
                ) {
                    Text("Simpan", color = Color.White, fontWeight = FontWeight.Bold)
                }
            },
            dismissButton = {
                    TextButton(onClick = { showNewFileDialog = false }) {
                    Text("Batal", color = Color(0xff64748b))
                }
            }
        )
    }

    // Rename Dialog
    val renameItem = itemToRename
    if (renameItem != null) {
        AlertDialog(
            onDismissRequest = { itemToRename = null },
            containerColor = Color.White,
            titleContentColor = Color(0xff1e293b),
            shape = RoundedCornerShape(28.dp),
            title = { Text("Ganti Nama", fontWeight = FontWeight.Bold) },
            text = {
                OutlinedTextField(
                    value = renameNewName,
                    onValueChange = { renameNewName = it },
                    label = { Text("Nama baru") },
                    singleLine = true,
                    colors = OutlinedTextFieldDefaults.colors(
                        focusedBorderColor = Color(0xff3b82f6),
                        unfocusedBorderColor = Color(0xffe2e8f0),
                        focusedTextColor = Color(0xff1e293b),
                        unfocusedTextColor = Color(0xff1e293b),
                        focusedLabelColor = Color(0xff3b82f6)
                    )
                )
            },
            confirmButton = {
                Button(
                    onClick = {
                        if (renameNewName.isNotBlank() && renameNewName != renameItem.name) {
                            viewModel.rename(renameItem, renameNewName, serverConfig)
                            itemToRename = null
                        }
                    },
                    colors = ButtonDefaults.buttonColors(containerColor = Color(0xff3b82f6))
                ) {
                    Text("Simpan", color = Color.White, fontWeight = FontWeight.Bold)
                }
            },
            dismissButton = {
                    TextButton(onClick = { itemToRename = null }) {
                    Text("Batal", color = Color(0xff64748b))
                }
            }
        )
    }

    // Delete Confirmation Dialog
    val deleteItem = itemToDelete
    if (deleteItem != null) {
        AlertDialog(
            onDismissRequest = { itemToDelete = null },
            containerColor = Color.White,
            titleContentColor = Color(0xff1e293b),
            shape = RoundedCornerShape(28.dp),
            title = { Text("Hapus ${if (deleteItem.isDirectory) "folder" else "file"}?", fontWeight = FontWeight.Bold) },
            text = {
                Text("${deleteItem.name}\n\nItem ini akan dihapus dari ${uiState.currentPath}. Tindakan ini tidak dapat dibatalkan.", color = Color(0xff64748b))
            },
            confirmButton = {
                Button(
                    onClick = {
                        viewModel.deleteItem(deleteItem, serverConfig)
                        itemToDelete = null
                    },
                    colors = ButtonDefaults.buttonColors(containerColor = Color(0xffbe123c))
                ) {
                    Text("Hapus", color = Color.White, fontWeight = FontWeight.Bold)
                }
            },
            dismissButton = {
                    TextButton(onClick = { itemToDelete = null }) {
                    Text("Batal", color = Color(0xff64748b))
                }
            }
        )
    }

    uiState.imagePreview?.let { preview ->
        androidx.compose.ui.window.Dialog(onDismissRequest = { viewModel.closeImagePreview() },
            properties = androidx.compose.ui.window.DialogProperties(usePlatformDefaultWidth = false)) {
            Column(Modifier.fillMaxSize().background(Color(0xff141729)).padding(16.dp)) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Text(preview.name, color = Color.White, maxLines = 1, overflow = TextOverflow.Ellipsis, modifier = Modifier.weight(1f))
                    TextButton(onClick = { viewModel.closeImagePreview() }) { Text("Tutup", color = Color.White) }
                }
                Box(Modifier.weight(1f).fillMaxWidth(), contentAlignment = Alignment.Center) {
                    when {
                        preview.bitmap != null -> androidx.compose.foundation.Image(
                            bitmap = preview.bitmap.asImageBitmap(), contentDescription = preview.name,
                            modifier = Modifier.fillMaxSize(), contentScale = androidx.compose.ui.layout.ContentScale.Fit)
                        preview.error != null -> Text(preview.error, color = Color.White)
                        else -> Column(horizontalAlignment = Alignment.CenterHorizontally) {
                            CircularProgressIndicator(color = Color(0xff62ae91))
                            Spacer(Modifier.height(12.dp))
                            Text("Mengunduh gambar…", color = Color.White)
                        }
                    }
                }
            }
        }
    }

    // File Preview Dialog
    val previewData = uiState.viewingFileContent
    if (previewData != null) {
        val (previewName, previewContent) = previewData
        AlertDialog(
            onDismissRequest = { viewModel.setViewingFile(null) },
            containerColor = NavyDarkCard,
            titleContentColor = TextPrimary,
            title = { Text(previewName, fontWeight = FontWeight.Bold, maxLines = 1, overflow = TextOverflow.Ellipsis) },
            text = {
                Box(
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(260.dp)
                        .clip(RoundedCornerShape(8.dp))
                        .background(NavyDarkBackground)
                        .padding(12.dp)
                ) {
                    LazyColumn {
                        item {
                            Text(
                                text = previewContent,
                                color = TerminalGreen,
                                fontFamily = FontFamily.Monospace,
                                fontSize = 12.sp,
                                lineHeight = 16.sp
                            )
                        }
                    }
                }
            },
            confirmButton = {
                Row(
                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    if (onAskHermesAboutFile != null) {
                        Button(
                            onClick = {
                                onAskHermesAboutFile(previewName, previewContent)
                                viewModel.setViewingFile(null)
                            },
                            colors = ButtonDefaults.buttonColors(
                                containerColor = BurntOrangeBadgeBg,
                                contentColor = BurntOrange
                            ),
                            border = androidx.compose.foundation.BorderStroke(1.dp, BurntOrangeBorder)
                        ) {
                            Icon(Icons.Default.SmartToy, contentDescription = null, modifier = Modifier.size(16.dp))
                            Spacer(modifier = Modifier.width(4.dp))
                            Text("Tanya Hermes", fontWeight = FontWeight.Bold, fontSize = 12.sp)
                        }
                    }
                    Button(
                        onClick = { viewModel.setViewingFile(null) },
                        colors = ButtonDefaults.buttonColors(containerColor = BurntOrange)
                    ) {
                        Text("Tutup", color = Color.White, fontWeight = FontWeight.Bold, fontSize = 12.sp)
                    }
                }
            }
        )
    }
}

@Composable
fun FileItemCard(
    item: RemoteFileItem,
    onOpen: () -> Unit,
    onRename: () -> Unit,
    onDelete: () -> Unit,
    onDownload: () -> Unit
) {
    var menuExpanded by remember { mutableStateOf(false) }

    val icon: ImageVector = when {
        item.isDirectory -> Icons.Default.Folder
        item.extension in listOf("sh", "py", "js", "ts", "json", "yml", "yaml", "c", "cpp", "go") -> Icons.Default.Code
        item.extension in listOf("txt", "md", "log", "conf", "env") -> Icons.Default.Description
        else -> Icons.AutoMirrored.Filled.InsertDriveFile
    }

    val iconTint = if (item.isDirectory) BurntOrange else TerminalCyan

    if (item.isDirectory) {
        val palette = listOf(
            FolderBlue to Color(0xFF2879C9), FolderPeach to Color(0xFFAD711B),
            FolderRose to Color(0xFFBE4568), FolderMint to Color(0xFF278D80)
        )
        val (tint, accent) = palette[Math.floorMod(item.name.hashCode(), palette.size)]
        Surface(
            onClick = onOpen,
            shape = RoundedCornerShape(22.dp),
            color = tint,
            modifier = Modifier.fillMaxWidth().testTag("file_item_${item.name}")
        ) {
            Column(Modifier.padding(16.dp)) {
                Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.SpaceBetween) {
                    Icon(Icons.Default.Folder, null, tint = accent, modifier = Modifier.size(32.dp))
                    Box {
                        IconButton(onClick = { menuExpanded = true }, modifier = Modifier.size(48.dp)) {
                            Icon(Icons.Default.MoreVert, "Tindakan folder", tint = accent)
                        }
                        DropdownMenu(expanded = menuExpanded, onDismissRequest = { menuExpanded = false }) {
                            DropdownMenuItem(text = { Text("Ganti nama") }, onClick = { menuExpanded = false; onRename() })
                            DropdownMenuItem(text = { Text("Hapus", color = TerminalRed) }, onClick = { menuExpanded = false; onDelete() })
                        }
                    }
                }
                Spacer(Modifier.height(12.dp))
                Text(item.name, color = TextPrimary, fontSize = 15.sp, fontWeight = FontWeight.Bold,
                    maxLines = 1, overflow = TextOverflow.Ellipsis)
                Text("Folder", color = accent, fontSize = 12.sp, modifier = Modifier.padding(top = 4.dp))
            }
        }
        return
    }

    TailHarnessCard(
        modifier = Modifier.testTag("file_item_${item.name}"),
        onClick = onOpen,
        backgroundColor = NavyDarkCard,
        borderColor = NavyCardBorder
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 14.dp, vertical = 12.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            // Icon Badge with rounded corners
            Box(
                modifier = Modifier
                    .size(42.dp)
                    .clip(RoundedCornerShape(12.dp))
                    .background(BurntOrangeBadgeBg)
                    .border(
                        1.dp,
                        if (item.isDirectory) BurntOrangeBorder else NavyDarkBorder,
                        RoundedCornerShape(12.dp)
                    ),
                contentAlignment = Alignment.Center
            ) {
                Icon(
                    imageVector = icon,
                    contentDescription = null,
                    tint = iconTint,
                    modifier = Modifier.size(22.dp)
                )
            }

            Spacer(modifier = Modifier.width(14.dp))

            Column(modifier = Modifier.weight(1f)) {
                Text(
                    text = item.name,
                    color = TextPrimary,
                    fontWeight = FontWeight.SemiBold,
                    fontSize = 14.sp,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis
                )
                Spacer(modifier = Modifier.height(2.dp))
                        Row(verticalAlignment = Alignment.CenterVertically) {
                    Text(
                        text = item.formattedSize,
                        color = TextSecondary,
                        fontSize = 12.sp,
                        fontWeight = FontWeight.Medium
                    )
                    if (item.formattedDate != "-") {
                        Text(" · ", color = TextSecondary, fontSize = 12.sp)
                        Text(
                            text = item.formattedDate,
                            color = TextSecondary,
                            fontSize = 12.sp,
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis
                        )
                    }
                }
            }

            // 3-dots Action Menu
            Box {
                IconButton(onClick = { menuExpanded = true }) {
                    Icon(
                        Icons.Default.MoreVert,
                        contentDescription = "Actions",
                        tint = TextSecondary,
                        modifier = Modifier.size(20.dp)
                    )
                }

                DropdownMenu(
                    expanded = menuExpanded,
                    onDismissRequest = { menuExpanded = false },
                    modifier = Modifier.background(NavyDarkCard)
                ) {
                    DropdownMenuItem(
                        text = { Text(if (item.isDirectory) "Buka Folder" else "Lihat Isi File", color = TextPrimary) },
                        leadingIcon = {
                            Icon(if (item.isDirectory) Icons.Default.FolderOpen else Icons.Default.Description, contentDescription = null, tint = BurntOrange)
                        },
                        onClick = {
                            menuExpanded = false
                            onOpen()
                        }
                    )
                    if (!item.isDirectory) {
                        DropdownMenuItem(
                            text = { Text("Download ke HP", color = TextPrimary) },
                            leadingIcon = {
                                Icon(Icons.Default.SaveAlt, contentDescription = null, tint = TerminalCyan)
                            },
                            onClick = {
                                menuExpanded = false
                                onDownload()
                            }
                        )
                    }
                    DropdownMenuItem(
                        text = { Text("Ganti Nama (Rename)", color = TextPrimary) },
                        leadingIcon = {
                            Icon(Icons.Default.DriveFileRenameOutline, contentDescription = null, tint = TextSecondary)
                        },
                        onClick = {
                            menuExpanded = false
                            onRename()
                        }
                    )
                    DropdownMenuItem(
                        text = { Text("Hapus", color = TerminalRed) },
                        leadingIcon = {
                            Icon(Icons.Default.Delete, contentDescription = null, tint = TerminalRed)
                        },
                        onClick = {
                            menuExpanded = false
                            onDelete()
                        }
                    )
                }
            }
        }
    }
}
