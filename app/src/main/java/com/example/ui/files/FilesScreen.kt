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
import androidx.compose.foundation.combinedClickable
import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.foundation.text.selection.SelectionContainer
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
import androidx.compose.material.icons.filled.Info
import androidx.compose.material.icons.filled.NoteAdd
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material.icons.filled.SaveAlt
import androidx.compose.material.icons.filled.Search
import androidx.compose.material.icons.filled.SmartToy
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Checkbox
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
import androidx.compose.runtime.DisposableEffect
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
import androidx.compose.foundation.layout.widthIn
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
import androidx.work.WorkInfo
import androidx.work.WorkManager
import com.example.data.worker.SftpUploadWorker
import com.example.R
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.platform.LocalResources
import com.example.data.transfer.*
import com.example.data.preferences.AppPreferencesStore
import com.example.data.security.EncryptedStorage
import androidx.compose.material.icons.filled.History
import com.example.ui.i18n.tr

@Composable
fun FilesScreen(
    sftpManager: SftpManager,
    serverConfig: ServerConfig,
    viewModel: FilesViewModel = viewModel(factory = FilesViewModel.provideFactory(sftpManager)),
    onAskHermesAboutFile: ((fileName: String, content: String) -> Unit)? = null,
    onOpenSettings: (() -> Unit)? = null,
    isTabActive: Boolean = true,
    modifier: Modifier = Modifier,
    preferencesStore: AppPreferencesStore? = null,
    transferDestination: TransferDestination? = null,
    onTransferDestinationHandled: () -> Unit = {},
    onSelectTransferProfile: (String) -> ServerConfig? = { null },
    bottomOverlayInset: androidx.compose.ui.unit.Dp = 0.dp
) {
    val context = LocalContext.current
    val resources = LocalResources.current
    val appearanceStore = remember { preferencesStore ?: AppPreferencesStore(context) }
    val preferences by appearanceStore.state.collectAsState()
    val transferStore = remember { TransferStore(context) }
    val batches by transferStore.state.collectAsState()
    var showTransfers by rememberSaveable { mutableStateOf(false) }
    var selectedTransfer by rememberSaveable { mutableStateOf<String?>(null) }
    var fileActions by remember { mutableStateOf(false) }
    var sortMenu by remember { mutableStateOf(false) }
    var reselectSource by remember { mutableStateOf<UploadBatch?>(null) }
    DisposableEffect(transferStore, appearanceStore) { onDispose { transferStore.close(); if (preferencesStore == null) appearanceStore.close() } }
    val uiState by viewModel.uiState.collectAsState()
    val uploadWorkInfos by remember(context) {
        WorkManager.getInstance(context).getWorkInfosByTagFlow(SftpUploadWorker.TAG)
    }.collectAsState(initial = emptyList())
    val activeProfileId = EncryptedStorage(context).getActiveProfileId()
    val currentBatch = currentUploadBatch(batches, activeProfileId, uiState.currentPath)
    val currentUpload = uploadWorkInfos.firstOrNull { it.id.toString() == currentBatch?.workId }
    val uploadRunning = currentUpload?.state in setOf(WorkInfo.State.RUNNING, WorkInfo.State.ENQUEUED, WorkInfo.State.BLOCKED)
    val observedWork = remember { mutableMapOf<String, WorkInfo.State>() }
    val observingSince = remember { System.currentTimeMillis() }
    LaunchedEffect(uploadWorkInfos) {
        uploadWorkInfos.forEach { work ->
            val id = work.id.toString()
            val previous = observedWork.put(id, work.state)
            if (work.state.isFinished && previous?.isFinished != true) {
                val batch = transferStore.read().firstOrNull { it.workId == id }
                if (batch?.profileId == activeProfileId && (previous != null || batch.createdAt >= observingSince) && batch.entries.any { it.destination == uiState.currentPath }) {
                    viewModel.reportBackgroundUploadResult("${batch.succeeded}/${batch.entries.size} " + resources.getString(R.string.transfer_done), batch.failed > 0 || batch.cancelled > 0)
                    viewModel.loadFiles(config = serverConfig)
                }
            }
        }
    }
    fun openTransferFolder(batch: UploadBatch, path: String) {
        val config = onSelectTransferProfile(batch.profileId)
        if (config == null) Toast.makeText(context, resources.getString(R.string.profile_missing), Toast.LENGTH_LONG).show()
        else { viewModel.navigateTo(path, config); showTransfers = false }
    }
    LaunchedEffect(transferDestination, isTabActive) {
        if (transferDestination != null && isTabActive) {
            selectedTransfer = transferDestination.batchId
            showTransfers = true
            if (transferDestination.openFolder) transferStore.find(transferDestination.batchId)?.let { batch ->
                batch.entries.firstOrNull()?.let { openTransferFolder(batch, it.destination) }
            }
            onTransferDestinationHandled()
        }
    }
    if (showTransfers) TransferHistorySheet(transferStore, selectedTransfer, { selectedTransfer = it }, ::openTransferFolder, { showTransfers = false }, { reselectSource = it })

    // Dialog states for user input
    var showNewFolderDialog by remember { mutableStateOf(false) }
    var newFolderName by remember { mutableStateOf("") }

    var showNewFileDialog by remember { mutableStateOf(false) }
    var newFileName by remember { mutableStateOf("") }
    var newFileContent by remember { mutableStateOf("") }

    var itemToRename by remember { mutableStateOf<RemoteFileItem?>(null) }
    var renameNewName by remember { mutableStateOf("") }

    var itemToDelete by remember { mutableStateOf<RemoteFileItem?>(null) }
    var itemToInspect by remember { mutableStateOf<RemoteFileItem?>(null) }
    var itemsToDelete by remember { mutableStateOf<List<RemoteFileItem>?>(null) }
    var selectedPaths by remember { mutableStateOf<Set<String>>(emptySet()) }

    LaunchedEffect(uiState.currentPath, uiState.searchQuery, serverConfig) {
        selectedPaths = emptySet()
        itemToDelete = null
        itemsToDelete = null
        itemToRename = null
        itemToInspect = null
    }
    LaunchedEffect(uiState.files) {
        selectedPaths = selectedPaths.intersect(uiState.files.map { it.path }.toSet())
    }
    val selectedItems = remember(uiState.files, selectedPaths) {
        uiState.files.filter { it.path in selectedPaths }
    }
    fun toggleSelection(item: RemoteFileItem) {
        selectedPaths = if (item.path in selectedPaths) selectedPaths - item.path else selectedPaths + item.path
    }
    fun openOrSelect(item: RemoteFileItem, open: () -> Unit) {
        if (selectedPaths.isNotEmpty()) toggleSelection(item) else open()
    }

    // Native Back-Button Navigation:
    // 1. If any dialog/file preview is open, dismiss it.
    // 2. If in a sub-directory, navigate up to parent folder.
    // 3. When at root directory ("/") and no dialog is open, BackHandler is disabled, allowing default system back behavior (closing tab/app).
    val canNavigateUp = viewModel.canNavigateUp()
    val isAnyOverlayOpen = itemToInspect != null || uiState.searchQuery.isNotBlank() || selectedPaths.isNotEmpty() || uiState.imagePreview != null || uiState.viewingFileContent != null || showNewFolderDialog || showNewFileDialog || itemToRename != null || itemToDelete != null || itemsToDelete != null

    BackHandler(enabled = isTabActive && (canNavigateUp || isAnyOverlayOpen)) {
        when {
            showNewFolderDialog -> showNewFolderDialog = false
            showNewFileDialog -> showNewFileDialog = false
            itemsToDelete != null -> itemsToDelete = null
            itemToInspect != null -> itemToInspect = null
            selectedPaths.isNotEmpty() -> selectedPaths = emptySet()
            itemToRename != null -> itemToRename = null
            itemToDelete != null -> itemToDelete = null
            uiState.imagePreview != null -> viewModel.closeImagePreview()
            uiState.viewingFileContent != null -> viewModel.setViewingFile(null)
            uiState.searchQuery.isNotBlank() -> viewModel.updateSearchQuery("")
            canNavigateUp -> viewModel.navigateUp(serverConfig)
        }
    }

    // 1. Storage Access Framework (SAF) Multiple File Picker Contract initialized in root Composable
    var uploadDestinationPath by rememberSaveable { mutableStateOf(uiState.currentPath) }
    var uploadProfileId by rememberSaveable { mutableStateOf(activeProfileId) }
    var uploadHost by rememberSaveable { mutableStateOf(serverConfig.sshHost) }
    var uploadPort by rememberSaveable { mutableStateOf(serverConfig.sshPort) }
    var uploadUser by rememberSaveable { mutableStateOf(serverConfig.sshUser) }
    var pendingUploads by rememberSaveable { mutableStateOf<List<Uri>>(emptyList()) }
    val notificationPermissionLauncher = rememberLauncherForActivityResult(ActivityResultContracts.RequestPermission()) { }
    val filePickerLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.OpenMultipleDocuments()
    ) { uris: List<Uri> ->
        pendingUploads = uris
        if (uris.isNotEmpty() && android.os.Build.VERSION.SDK_INT >= 33 &&
            androidx.core.content.ContextCompat.checkSelfPermission(context, android.Manifest.permission.POST_NOTIFICATIONS) != android.content.pm.PackageManager.PERMISSION_GRANTED) {
            notificationPermissionLauncher.launch(android.Manifest.permission.POST_NOTIFICATIONS)
        }
    }
    if (pendingUploads.isNotEmpty()) AlertDialog(onDismissRequest = { pendingUploads = emptyList() },
        title = { Text(tr("Unggah ${pendingUploads.size} file", "Upload ${pendingUploads.size} files")) },
        text = { Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
            Text("${EncryptedStorage(context).loadProfiles().firstOrNull { it.id == uploadProfileId }?.name.orEmpty()} · $uploadDestinationPath", maxLines = 1, overflow = TextOverflow.Ellipsis)
            val fileNames = pendingUploads.map { uploadDisplayName(context, it) }
            Text(fileNames.take(3).joinToString("\n") + if (fileNames.size > 3) "\n+${fileNames.size - 3}" else "", maxLines = 4, overflow = TextOverflow.Ellipsis)
            val replacements = uiState.files.filter { !it.isDirectory && it.name in fileNames }
            if (replacements.isNotEmpty()) Text(
                tr("File yang sudah ada dan akan diganti:\n", "Existing files that will be replaced:\n") + replacements.joinToString("\n") { it.name },
                color = MaterialTheme.colorScheme.error
            )
        } }, confirmButton = { TextButton(onClick = {
            val config = EncryptedStorage(context).loadProfiles().firstOrNull { it.id == uploadProfileId }?.config
            if (config == null || config.sshHost != uploadHost || config.sshPort != uploadPort || config.sshUser != uploadUser) {
                Toast.makeText(context, resources.getString(R.string.upload_target_changed), Toast.LENGTH_LONG).show()
            } else viewModel.uploadMultipleFiles(context.applicationContext, pendingUploads, config, uploadDestinationPath, uploadProfileId)
            pendingUploads = emptyList()
        }) { Text(tr("Unggah", "Upload")) } }, dismissButton = { TextButton(onClick = { pendingUploads = emptyList() }) { Text(tr("Batal", "Cancel")) } })

    val downloadFolderLauncher = rememberLauncherForActivityResult(ActivityResultContracts.OpenDocumentTree()) { uri ->
        viewModel.completeDownload(context.applicationContext, uri)
        if (uri != null) selectedPaths = emptySet()
    }
    val chooseDownloadFolder: (List<RemoteFileItem>) -> Unit = { items ->
        if (viewModel.prepareDownload(items, serverConfig)) {
            try {
                downloadFolderLauncher.launch(null)
            } catch (e: Exception) {
                viewModel.cancelDownloadPicker()
                Toast.makeText(context, "Tidak dapat membuka pemilih folder: ${e.localizedMessage}", Toast.LENGTH_LONG).show()
            }
        }
    }

    // Safe launch helper for file picker using SAF OpenMultipleDocuments
    val launchFilePicker = {
        try {
            uploadDestinationPath = viewModel.currentRemotePath()
            uploadProfileId = activeProfileId
            uploadHost = serverConfig.sshHost; uploadPort = serverConfig.sshPort; uploadUser = serverConfig.sshUser
            filePickerLauncher.launch(arrayOf("*/*"))
        } catch (e: Exception) {
            Toast.makeText(
                context,
                "Tidak dapat membuka pemilih berkas: ${e.localizedMessage ?: e.message}",
                Toast.LENGTH_LONG
            ).show()
        }
    }
    LaunchedEffect(reselectSource) {
        reselectSource?.let { batch ->
            val config = onSelectTransferProfile(batch.profileId)
            if (config != null) {
                uploadDestinationPath = batch.entries.first().destination
                uploadProfileId = batch.profileId
                uploadHost = config.sshHost; uploadPort = config.sshPort; uploadUser = config.sshUser
                viewModel.navigateTo(uploadDestinationPath, config)
                showTransfers = false
                filePickerLauncher.launch(arrayOf("*/*"))
            } else Toast.makeText(context, resources.getString(R.string.profile_missing), Toast.LENGTH_LONG).show()
            reselectSource = null
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

    val filteredList = remember(uiState.files, preferences.filesSort, preferences.showHiddenFiles) {
        val order = when (preferences.filesSort) {
            "modified" -> compareByDescending<RemoteFileItem> { it.modifiedTime }.thenBy { it.name.lowercase() }
            "size" -> compareByDescending<RemoteFileItem> { it.sizeBytes }.thenBy { it.name.lowercase() }
            else -> compareBy<RemoteFileItem> { it.name.lowercase() }
        }
        uiState.files.filter { preferences.showHiddenFiles || !it.name.startsWith('.') }
            .sortedWith(compareBy<RemoteFileItem> { !it.isDirectory }.then(order))
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
                title = tr("Berkas", "Files"),
                subtitle = if (serverConfig.isSshConfigured) serverConfig.sshHost else tr("Jelajahi file server", "Browse server files"),
                trailingContent = {
                    Row {
                        IconButton(
                            onClick = { selectedTransfer = null; showTransfers = true },
                            modifier = Modifier.size(44.dp).testTag("transfer_history_button")
                        ) {
                            Icon(
                                Icons.Default.History,
                                contentDescription = stringResource(R.string.transfer_history),
                                tint = BurntOrange
                            )
                        }
                        Box {
                            IconButton(onClick = { fileActions = true }, modifier = Modifier.size(44.dp)) { Icon(Icons.Default.MoreVert, stringResource(R.string.files_actions), tint = TextSecondary) }
                            DropdownMenu(fileActions, { fileActions = false }) {
                                DropdownMenuItem(enabled = serverConfig.isSshConfigured, text = { Text(stringResource(R.string.new_folder)) }, onClick = { fileActions = false; showNewFolderDialog = true }, modifier = Modifier.testTag("new_folder_button"))
                                DropdownMenuItem(enabled = serverConfig.isSshConfigured, text = { Text(stringResource(R.string.new_file)) }, onClick = { fileActions = false; showNewFileDialog = true }, modifier = Modifier.testTag("new_file_button"))
                                DropdownMenuItem(text = { Text(tr("Muat ulang", "Refresh")) }, onClick = { fileActions = false; viewModel.loadFiles(config = serverConfig) })
                            }
                        }
                    }
                }
            )

            if (selectedPaths.isNotEmpty()) {
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(horizontal = 16.dp, vertical = 4.dp)
                        .clip(RoundedCornerShape(12.dp))
                        .background(MaterialTheme.colorScheme.primaryContainer)
                        .border(1.dp, BurntOrangeBorder, RoundedCornerShape(12.dp))
                        .padding(horizontal = 8.dp, vertical = 4.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    IconButton(onClick = { selectedPaths = emptySet() }) {
                        Icon(Icons.Default.Close, "Batal memilih", tint = MaterialTheme.colorScheme.onPrimaryContainer)
                    }
                    Text(tr("${selectedItems.size} dipilih", "${selectedItems.size} selected"), color = MaterialTheme.colorScheme.onPrimaryContainer, fontWeight = FontWeight.Bold, modifier = Modifier.weight(1f))
                    IconButton(
                        onClick = { selectedPaths = filteredList.mapTo(mutableSetOf()) { it.path } },
                        enabled = selectedItems.size < filteredList.size,
                        modifier = Modifier.testTag("files_select_all")
                    ) {
                        Icon(Icons.Default.CheckCircle, tr("Pilih semua", "Select all"), tint = if (selectedItems.size < filteredList.size) MaterialTheme.colorScheme.onPrimaryContainer else TextSecondary)
                    }
                    IconButton(
                        onClick = {
                            chooseDownloadFolder(selectedItems)
                        },
                        enabled = selectedItems.isNotEmpty() && selectedItems.none { it.isDirectory } &&
                            !uiState.isDownloading && !uiState.isChoosingDownloadFolder
                    ) {
                        Icon(Icons.Default.SaveAlt, tr("Download pilihan", "Download selected files"), tint = MaterialTheme.colorScheme.onPrimaryContainer)
                    }
                    IconButton(onClick = { itemsToDelete = selectedItems }, enabled = selectedItems.isNotEmpty() && !uiState.isDeleting) {
                        Icon(Icons.Default.Delete, tr("Hapus pilihan", "Delete selected items"), tint = TerminalRed)
                    }
                }
                if (selectedItems.any { it.isDirectory }) {
                    Text(tr("Download hanya untuk file. Batalkan pilihan folder untuk mengunduh.", "Downloads support files. Deselect folders to continue."),
                        color = TextSecondary, fontSize = 12.sp, modifier = Modifier.padding(horizontal = 20.dp))
                }
            }

            if (serverConfig.isSshConfigured) FolderBreadcrumbs(uiState.currentPath) { viewModel.navigateTo(it, serverConfig) }
            if (serverConfig.isSshConfigured) {
            OutlinedTextField(
                value = uiState.searchQuery,
                onValueChange = { viewModel.updateSearchQuery(it) },
                placeholder = { Text(tr("Cari di folder ini dan subfolder...", "Search this folder and subfolders…"), color = TextSecondary, fontSize = 13.sp) },
                trailingIcon = {
                    if (uiState.searchQuery.isNotEmpty()) IconButton(onClick = { viewModel.updateSearchQuery("") }) {
                        Icon(Icons.Default.Close, tr("Hapus pencarian", "Clear search"))
                    }
                },
                leadingIcon = {
                    Icon(Icons.Default.Search, contentDescription = null, tint = TextSecondary, modifier = Modifier.size(18.dp))
                },
                singleLine = true,
                shape = RoundedCornerShape(50),
                colors = OutlinedTextFieldDefaults.colors(
                    focusedContainerColor = NavyDarkCard,
                    unfocusedContainerColor = NavyDarkCard,
                    focusedBorderColor = BurntOrange,
                    unfocusedBorderColor = MaterialTheme.colorScheme.outline,
                    focusedTextColor = TextPrimary,
                    unfocusedTextColor = TextPrimary
                ),
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 16.dp, vertical = 4.dp)
                    .heightIn(min = 56.dp)
            )

            if (serverConfig.isSshConfigured) Row(Modifier.fillMaxWidth().padding(horizontal = 20.dp), verticalAlignment = Alignment.CenterVertically) {
                Text(tr("${filteredList.size} item", "${filteredList.size} items"), color = TextSecondary, fontSize = 12.sp, modifier = Modifier.weight(1f))
                Box {
                    TextButton(onClick = { sortMenu = true }, modifier = Modifier.testTag("files_sort")) { Text(stringResource(R.string.files_sort), color = BurntOrange) }
                    DropdownMenu(sortMenu, { sortMenu = false }) {
                        listOf("name" to R.string.sort_name, "modified" to R.string.sort_modified, "size" to R.string.sort_size).forEach { (sort, label) ->
                            DropdownMenuItem(text = { Text(stringResource(label)) }, onClick = { appearanceStore.update(preferences.copy(filesSort = sort)); sortMenu = false }, trailingIcon = { if (preferences.filesSort == sort) Icon(Icons.Default.CheckCircle, null) })
                        }
                        DropdownMenuItem(text = { Text(stringResource(R.string.hidden_files)) }, onClick = { appearanceStore.update(preferences.copy(showHiddenFiles = !preferences.showHiddenFiles)); sortMenu = false }, trailingIcon = { Checkbox(preferences.showHiddenFiles, null) })
                    }
                }
            }

            }
            if (uiState.searchQuery.isNotBlank()) {
                Row(Modifier.fillMaxWidth().padding(horizontal = 20.dp), verticalAlignment = Alignment.CenterVertically) {
                    Text(
                        if (uiState.isSearching) "Mencari… ${uiState.files.size} hasil di ${uiState.currentPath} dan subfolder" else
                            "${uiState.files.size} hasil di ${uiState.currentPath} dan subfolder",
                        color = TextSecondary, fontSize = 12.sp, modifier = Modifier.weight(1f)
                    )
                    if (uiState.isSearching) TextButton(onClick = viewModel::stopSearch) { Text(tr("Berhenti", "Stop")) }
                }
                uiState.searchNotice?.let {
                    Text(it, color = TerminalRed, fontSize = 12.sp, modifier = Modifier.padding(horizontal = 20.dp))
                }
            }
            if (uiState.isDeleting) {
                Text(tr("Menghapus item terpilih…", "Deleting selected items…"), color = TextSecondary, modifier = Modifier.padding(horizontal = 20.dp))
            }

            // Transfer state: persistent progress while active, concise completion result afterwards.
            if (uploadRunning || uiState.isDownloading || uiState.transferStatus != null) {
                TailHarnessCard(
                    backgroundColor = NavyDarkCard,
                    borderColor = NavyDarkBorder,
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(horizontal = 16.dp, vertical = 4.dp)
                ) {
                    Column(Modifier.fillMaxWidth().padding(horizontal = 14.dp, vertical = 12.dp)) {
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            if (uploadRunning || uiState.isDownloading) {
                                CircularProgressIndicator(color = BurntOrange, strokeWidth = 2.dp, modifier = Modifier.size(18.dp))
                            } else if (uiState.transferFailed) {
                                Icon(Icons.Default.ErrorOutline, contentDescription = null, tint = TerminalRed, modifier = Modifier.size(20.dp))
                            } else {
                                Icon(Icons.Default.CheckCircle, contentDescription = null, tint = TerminalGreen, modifier = Modifier.size(20.dp))
                            }
                            Spacer(Modifier.width(12.dp))
                            Column(Modifier.weight(1f)) {
                                Text(
                                    if (uploadRunning) {
                                        val fileName = currentUpload?.progress?.getString(SftpUploadWorker.KEY_NAME) ?: currentBatch?.entries?.firstOrNull()?.name.orEmpty()
                                        val index = currentUpload?.progress?.getInt(SftpUploadWorker.KEY_INDEX, 0) ?: 0
                                        val count = currentUpload?.progress?.getInt(SftpUploadWorker.KEY_COUNT, 0) ?: 0
                                        if (currentBatch?.entries?.none { it.state == UploadState.UPLOADING } != false) tr("Menunggu · $fileName", "Waiting · $fileName")
                                        else "$index/$count · $fileName"
                                    } else uiState.transferStatus ?: tr("Mengunduh file", "Downloading files"),
                                    color = TextPrimary, fontWeight = FontWeight.SemiBold, fontSize = 13.sp,
                                    maxLines = 2, overflow = TextOverflow.Ellipsis
                                )
                                (if (uploadRunning) "${currentUpload?.progress?.getInt(SftpUploadWorker.KEY_PERCENT, 0) ?: 0}%" else uiState.downloadingFileName)?.let {
                                    Text(it, color = TextSecondary, fontSize = 12.sp, fontFamily = FontFamily.Monospace,
                                        maxLines = 1, overflow = TextOverflow.Ellipsis)
                                }
                            }
                            if (uploadRunning) TextButton(onClick = { selectedTransfer = batches.firstOrNull { it.workId == currentUpload?.id.toString() }?.id; showTransfers = true }) { Text(stringResource(R.string.view_results)) }
                            if (!uploadRunning && uiState.transferStatus != null) {
                                IconButton(onClick = viewModel::clearTransferStatus, modifier = Modifier.size(44.dp)) {
                                    Icon(Icons.Default.Close, "Tutup status transfer", tint = TextSecondary)
                                }
                            }
                        }
                        if (uploadRunning || uiState.isDownloading) {
                            Spacer(Modifier.height(10.dp))
                            val progress = currentUpload?.progress?.getInt(SftpUploadWorker.KEY_PERCENT, 0) ?: 0
                            if (uploadRunning && (currentUpload?.progress?.getLong(SftpUploadWorker.KEY_TOTAL_BYTES, -1L) ?: -1L) > 0) LinearProgressIndicator(
                                progress = { progress / 100f },
                                modifier = Modifier.fillMaxWidth().height(4.dp).clip(RoundedCornerShape(4.dp)),
                                color = BurntOrange, trackColor = NavyDarkBorder
                            ) else LinearProgressIndicator(
                                modifier = Modifier.fillMaxWidth().height(4.dp).clip(RoundedCornerShape(4.dp)),
                                color = BurntOrange, trackColor = NavyDarkBorder
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
                            Box(Modifier.size(72.dp).clip(RoundedCornerShape(24.dp)).background(BurntOrangeBadgeBg), contentAlignment = Alignment.Center) {
                                Icon(Icons.Default.FolderOpen, null, tint = BurntOrange, modifier = Modifier.size(34.dp))
                            }
                            Spacer(Modifier.height(18.dp))
                             Text(tr("Hubungkan ke server", "Connect to a server"), color = TextPrimary, fontSize = 18.sp, fontWeight = FontWeight.Bold)
                            Spacer(Modifier.height(6.dp))
                            Text(tr("Simpan konfigurasi SSH di Settings untuk menjelajahi dan mengelola file.", "Save an SSH connection in Settings to browse and manage server files."),
                                color = TextSecondary, fontSize = 14.sp, lineHeight = 20.sp)
                            Spacer(Modifier.height(18.dp))
                            Button(onClick = { onOpenSettings?.invoke() }, colors = ButtonDefaults.buttonColors(containerColor = BurntOrange)) {
                                Text(tr("Buka Settings", "Open Settings"), color = MaterialTheme.colorScheme.onPrimary)
                            }
                        }
                    }
                    uiState.isLoading -> {
                        Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                            Column(horizontalAlignment = Alignment.CenterHorizontally) {
                                CircularProgressIndicator(color = BurntOrange)
                                Spacer(Modifier.height(12.dp))
                                Text(tr("Memuat file server…", "Loading server files…"), color = TextSecondary, fontSize = 13.sp)
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
                                color = TerminalRed,
                                fontSize = 14.sp,
                                fontFamily = FontFamily.Monospace
                            )
                            Spacer(modifier = Modifier.height(8.dp))
                            Text(
                                if (uiState.searchQuery.isNotBlank()) "Pencarian belum selesai. Coba lagi atau hapus pencarian untuk kembali ke folder." else
                                    "Tidak dapat memuat folder. Periksa koneksi lalu coba lagi.",
                                color = TextSecondary, fontSize = 13.sp, lineHeight = 18.sp
                            )
                            Spacer(modifier = Modifier.height(12.dp))
                            Button(
                                onClick = { viewModel.loadFiles(config = serverConfig) },
                                colors = ButtonDefaults.buttonColors(containerColor = BurntOrange)
                            ) {
                                Text(tr("Coba Lagi", "Try again"), color = MaterialTheme.colorScheme.onPrimary)
                            }
                        }
                    }
                    filteredList.isEmpty() && uiState.isSearching -> {
                        Column(Modifier.fillMaxSize().padding(24.dp), horizontalAlignment = Alignment.CenterHorizontally,
                            verticalArrangement = Arrangement.Center) {
                            CircularProgressIndicator(color = BurntOrange)
                            Spacer(Modifier.height(12.dp))
                            Text(tr("Mencari file dan folder…", "Searching files and folders…"), color = TextPrimary)
                            Text(tr("Hasil akan muncul saat ditemukan.", "Results appear as they are found."), color = TextSecondary, fontSize = 13.sp)
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
                                tint = TextSecondary.copy(alpha = 0.6f),
                                modifier = Modifier.size(48.dp)
                            )
                            Spacer(modifier = Modifier.height(12.dp))
                            Text(if (uiState.searchQuery.isNotBlank()) tr("Tidak ada hasil pencarian", "No matching files") else tr("Folder ini masih kosong", "This folder is empty"), color = TextPrimary, fontSize = 16.sp, fontWeight = FontWeight.SemiBold)
                            Spacer(Modifier.height(4.dp))
                            Text(if (uiState.searchQuery.isNotBlank()) tr("Coba kata lain atau cari dari folder induk.", "Try another name or search from a parent folder.") else tr("Unggah file atau buat folder untuk mulai mengisi.", "Upload a file or create a folder to begin."), color = TextSecondary, fontSize = 13.sp)
                        }
                    }
                    else -> {
                        FileItemsList(filteredList, selectedPaths, uiState.searchQuery.isNotBlank(), bottomOverlayInset,
                            onOpen = { item -> openOrSelect(item) {
                                if (item.isDirectory) viewModel.navigateTo(item.path, serverConfig)
                                else viewModel.readFile(item, serverConfig, context.cacheDir)
                            } },
                            onLongClick = ::toggleSelection,
                            onRename = { itemToRename = it; renameNewName = it.name },
                            onDelete = { itemToDelete = it },
                            onDownload = { chooseDownloadFolder(listOf(it)) },
                            onInfo = { itemToInspect = it })
                    }
                }
            }
        }

        // Floating Action Button for quick upload
        if (serverConfig.isSshConfigured) FloatingActionButton(
            onClick = launchFilePicker,
            modifier = Modifier
                .align(Alignment.BottomEnd)
                .padding(start = 20.dp, end = 20.dp, top = 20.dp, bottom = 20.dp + bottomOverlayInset)
                .testTag("upload_file_fab"),
            containerColor = BurntOrange,
            contentColor = MaterialTheme.colorScheme.onPrimary,
            shape = RoundedCornerShape(16.dp)
        ) {
            Row(
                modifier = Modifier.padding(horizontal = 14.dp, vertical = 8.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                Icon(
                    Icons.Default.FileUpload,
                    contentDescription = tr("Unggah file", "Upload files"),
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
    itemToInspect?.let { item ->
        AlertDialog(
            onDismissRequest = { itemToInspect = null },
            title = { Text(if (item.isDirectory) "Informasi folder" else "Informasi file", fontWeight = FontWeight.Bold) },
            text = {
                SelectionContainer {
                    Column(Modifier.verticalScroll(rememberScrollState()), verticalArrangement = Arrangement.spacedBy(12.dp)) {
                        FileInfoDetail(tr("Nama lengkap", "Full name"), item.name)
                        FileInfoDetail(tr("Lokasi", "Location"), item.path)
                        FileInfoDetail(tr("Jenis", "Type"), when {
                            item.isDirectory -> "Folder"
                            item.permissions.startsWith("l") -> "Symbolic link"
                            item.extension.isBlank() -> tr("File tanpa ekstensi", "File without extension")
                            else -> tr("File ${item.extension.uppercase(java.util.Locale.ROOT)} (berdasarkan ekstensi)", "${item.extension.uppercase(java.util.Locale.ROOT)} file (by extension)")
                        })
                        FileInfoDetail(tr("Ukuran", "Size"), if (item.isDirectory) tr("Ukuran total isi folder belum dihitung", "Folder contents have not been measured") else
                            "${item.formattedSize} · ${java.text.NumberFormat.getIntegerInstance().format(item.sizeBytes)} byte")
                        FileInfoDetail(tr("Terakhir diubah", "Last modified"), item.formattedDate.takeUnless { it == "-" } ?: tr("Tidak tersedia", "Unavailable"))
                        FileInfoDetail("Permission", item.permissions)
                    }
                }
            },
            confirmButton = { TextButton(onClick = { itemToInspect = null }) { Text(tr("Tutup", "Close")) } }
        )
    }

    if (showNewFolderDialog) {
        AlertDialog(
            onDismissRequest = { showNewFolderDialog = false },
            containerColor = NavyDarkCard,
            titleContentColor = TextPrimary,
            shape = RoundedCornerShape(28.dp),
            title = { Text(tr("Buat folder baru", "Create folder"), fontWeight = FontWeight.Bold) },
            text = {
                OutlinedTextField(
                    value = newFolderName,
                    onValueChange = { newFolderName = it },
                    label = { Text(tr("Nama folder", "Folder name")) },
                    placeholder = { Text(tr("contoh: scripts", "for example: scripts")) },
                    singleLine = true,
                    colors = OutlinedTextFieldDefaults.colors(
                        focusedBorderColor = BurntOrange,
                        unfocusedBorderColor = MaterialTheme.colorScheme.outline,
                        focusedTextColor = TextPrimary,
                        unfocusedTextColor = TextPrimary,
                        focusedLabelColor = BurntOrange
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
                    colors = ButtonDefaults.buttonColors(containerColor = BurntOrange)
                ) {
                    Text(tr("Buat", "Create"), color = MaterialTheme.colorScheme.onPrimary, fontWeight = FontWeight.Bold)
                }
            },
            dismissButton = {
                    TextButton(onClick = { showNewFolderDialog = false }) {
                    Text(tr("Batal", "Cancel"), color = TextSecondary)
                }
            }
        )
    }

    // New File Dialog
    if (showNewFileDialog) {
        AlertDialog(
            onDismissRequest = { showNewFileDialog = false },
            containerColor = NavyDarkCard,
            titleContentColor = TextPrimary,
            shape = RoundedCornerShape(28.dp),
            title = { Text(tr("Buat file baru", "Create file"), fontWeight = FontWeight.Bold) },
            text = {
                Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    OutlinedTextField(
                        value = newFileName,
                        onValueChange = { newFileName = it },
                        label = { Text(tr("Nama file", "File name")) },
                        placeholder = { Text(tr("contoh: config.json", "for example: config.json")) },
                        singleLine = true,
                        colors = OutlinedTextFieldDefaults.colors(
                            focusedBorderColor = BurntOrange,
                            unfocusedBorderColor = MaterialTheme.colorScheme.outline,
                            focusedTextColor = TextPrimary,
                            unfocusedTextColor = TextPrimary,
                            focusedLabelColor = BurntOrange
                        )
                    )
                    OutlinedTextField(
                        value = newFileContent,
                        onValueChange = { newFileContent = it },
                        label = { Text(tr("Isi file", "File content")) },
                        placeholder = { Text(tr("Tulis isi file teks…", "Enter text file content…")) },
                        maxLines = 6,
                        modifier = Modifier.height(120.dp),
                        colors = OutlinedTextFieldDefaults.colors(
                            focusedBorderColor = BurntOrange,
                            unfocusedBorderColor = MaterialTheme.colorScheme.outline,
                            focusedTextColor = TextPrimary,
                            unfocusedTextColor = TextPrimary,
                            focusedLabelColor = BurntOrange
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
                    colors = ButtonDefaults.buttonColors(containerColor = BurntOrange)
                ) {
                    Text(tr("Simpan", "Save"), color = MaterialTheme.colorScheme.onPrimary, fontWeight = FontWeight.Bold)
                }
            },
            dismissButton = {
                    TextButton(onClick = { showNewFileDialog = false }) {
                    Text(tr("Batal", "Cancel"), color = TextSecondary)
                }
            }
        )
    }

    // Rename Dialog
    val renameItem = itemToRename
    if (renameItem != null) {
        AlertDialog(
            onDismissRequest = { itemToRename = null },
            containerColor = NavyDarkCard,
            titleContentColor = TextPrimary,
            shape = RoundedCornerShape(28.dp),
            title = { Text(tr("Ganti nama", "Rename"), fontWeight = FontWeight.Bold) },
            text = {
                OutlinedTextField(
                    value = renameNewName,
                    onValueChange = { renameNewName = it },
                    label = { Text(tr("Nama baru", "New name")) },
                    singleLine = true,
                    colors = OutlinedTextFieldDefaults.colors(
                        focusedBorderColor = BurntOrange,
                        unfocusedBorderColor = MaterialTheme.colorScheme.outline,
                        focusedTextColor = TextPrimary,
                        unfocusedTextColor = TextPrimary,
                        focusedLabelColor = BurntOrange
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
                    colors = ButtonDefaults.buttonColors(containerColor = BurntOrange)
                ) {
                    Text(tr("Simpan", "Save"), color = MaterialTheme.colorScheme.onPrimary, fontWeight = FontWeight.Bold)
                }
            },
            dismissButton = {
                    TextButton(onClick = { itemToRename = null }) {
                    Text(tr("Batal", "Cancel"), color = TextSecondary)
                }
            }
        )
    }

    // Delete Confirmation Dialog
    val deleteItem = itemToDelete
    if (deleteItem != null) {
        AlertDialog(
            onDismissRequest = { itemToDelete = null },
            containerColor = NavyDarkCard,
            titleContentColor = TextPrimary,
            shape = RoundedCornerShape(28.dp),
            title = { Text(if (deleteItem.isDirectory) tr("Hapus folder?", "Delete folder?") else tr("Hapus file?", "Delete file?"), fontWeight = FontWeight.Bold) },
            text = {
                Text(deleteItem.path + tr("\n\nTindakan ini tidak dapat dibatalkan.", "\n\nThis cannot be undone.") +
                    if (deleteItem.isDirectory) tr(" Folder harus kosong agar bisa dihapus.", " The folder must be empty before deletion.") else "", color = TextSecondary)
            },
            confirmButton = {
                Button(
                    onClick = {
                        viewModel.deleteItem(deleteItem, serverConfig)
                        itemToDelete = null
                    },
                    colors = ButtonDefaults.buttonColors(containerColor = TerminalRed, contentColor = MaterialTheme.colorScheme.onError)
                ) {
                    Text(tr("Hapus", "Delete"), color = MaterialTheme.colorScheme.onError, fontWeight = FontWeight.Bold)
                }
            },
            dismissButton = {
                    TextButton(onClick = { itemToDelete = null }) {
                    Text(tr("Batal", "Cancel"), color = TextSecondary)
                }
            }
        )
    }

    val selectedDeleteItems = itemsToDelete
    if (selectedDeleteItems != null) {
        AlertDialog(
            onDismissRequest = { itemsToDelete = null },
            containerColor = NavyDarkCard,
            titleContentColor = TextPrimary,
            shape = RoundedCornerShape(28.dp),
            title = { Text(tr("Hapus ${selectedDeleteItems.size} item?", "Delete ${selectedDeleteItems.size} items?"), fontWeight = FontWeight.Bold) },
            text = {
                Text(
                    selectedDeleteItems.take(5).joinToString("\n") { it.path } +
                        (if (selectedDeleteItems.size > 5) "\n…" else "") +
                        tr("\n\nTindakan ini tidak dapat dibatalkan. Folder harus kosong agar bisa dihapus.", "\n\nThis cannot be undone. Folders must be empty before deletion."),
                    color = TextSecondary
                )
            },
            confirmButton = {
                Button(
                    onClick = {
                        viewModel.deleteItems(selectedDeleteItems, serverConfig)
                        itemsToDelete = null
                        selectedPaths = emptySet()
                    },
                    colors = ButtonDefaults.buttonColors(containerColor = TerminalRed, contentColor = MaterialTheme.colorScheme.onError)
                ) { Text("Hapus", color = MaterialTheme.colorScheme.onError, fontWeight = FontWeight.Bold) }
            },
            dismissButton = {
                TextButton(onClick = { itemsToDelete = null }) { Text("Batal", color = TextSecondary) }
            }
        )
    }

    uiState.imagePreview?.let { preview ->
        androidx.compose.ui.window.Dialog(onDismissRequest = { viewModel.closeImagePreview() },
            properties = androidx.compose.ui.window.DialogProperties(usePlatformDefaultWidth = false)) {
            Column(Modifier.fillMaxSize().background(Color(0xff141729)).padding(16.dp)) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Text(preview.name, color = Color.White, maxLines = 1, overflow = TextOverflow.Ellipsis, modifier = Modifier.weight(1f))
                    TextButton(onClick = { viewModel.closeImagePreview() }) { Text(tr("Tutup", "Close"), color = Color.White) }
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
                            Text(tr("Mengunduh gambar…", "Downloading image…"), color = Color.White)
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
                            Text(tr("Tanya Hermes", "Ask Hermes"), fontWeight = FontWeight.Bold, fontSize = 12.sp)
                        }
                    }
                    Button(
                        onClick = { viewModel.setViewingFile(null) },
                        colors = ButtonDefaults.buttonColors(containerColor = BurntOrange)
                    ) {
                        Text(tr("Tutup", "Close"), color = MaterialTheme.colorScheme.onPrimary, fontWeight = FontWeight.Bold, fontSize = 12.sp)
                    }
                }
            }
        )
    }
}

private fun uploadDisplayName(context: Context, uri: Uri): String = runCatching {
    context.contentResolver.query(uri, arrayOf(android.provider.OpenableColumns.DISPLAY_NAME), null, null, null)?.use { cursor ->
        if (cursor.moveToFirst()) cursor.getString(0)?.takeIf(String::isNotBlank) else null
    }
}.getOrNull() ?: uri.lastPathSegment?.substringAfterLast('/') ?: trFileFallback(context)

private fun trFileFallback(context: Context): String = context.getString(com.example.R.string.new_file)

@Composable
internal fun FileItemsList(
    items: List<RemoteFileItem>, selectedPaths: Set<String>, showPath: Boolean,
    bottomOverlayInset: androidx.compose.ui.unit.Dp,
    onOpen: (RemoteFileItem) -> Unit, onLongClick: (RemoteFileItem) -> Unit,
    onRename: (RemoteFileItem) -> Unit, onDelete: (RemoteFileItem) -> Unit,
    onDownload: (RemoteFileItem) -> Unit, onInfo: (RemoteFileItem) -> Unit
) {
    val (folders, files) = remember(items) { items.partition { it.isDirectory } }
    LazyColumn(
        modifier = Modifier.fillMaxSize().testTag("files_list"),
        contentPadding = PaddingValues(start = 16.dp, end = 16.dp, top = 8.dp, bottom = 96.dp + bottomOverlayInset),
        verticalArrangement = Arrangement.spacedBy(8.dp)
    ) {
        if (folders.isNotEmpty()) {
            item(key = "folders_header", contentType = "header") {
                Text(tr("Folder", "Folders"), style = MaterialTheme.typography.titleMedium, color = TextPrimary)
            }
        }
        items(folders, key = { it.path }, contentType = { "file_row" }) { item ->
            FileItemCard(item, { onOpen(item) }, { onLongClick(item) }, { onRename(item) },
                { onDelete(item) }, { onDownload(item) }, { onInfo(item) },
                item.path in selectedPaths, selectedPaths.isNotEmpty(), showPath)
        }
        if (files.isNotEmpty()) {
            item(key = "files_header", contentType = "header") {
                Text(tr("File", "Files"), style = MaterialTheme.typography.titleMedium, color = TextPrimary,
                    modifier = Modifier.padding(top = 8.dp))
            }
        }
        items(files, key = { it.path }, contentType = { "file_row" }) { item ->
            FileItemCard(item, { onOpen(item) }, { onLongClick(item) }, { onRename(item) },
                { onDelete(item) }, { onDownload(item) }, { onInfo(item) },
                item.path in selectedPaths, selectedPaths.isNotEmpty(), showPath)
        }
    }
}

@Composable
private fun FileInfoDetail(label: String, value: String) {
    Column(verticalArrangement = Arrangement.spacedBy(3.dp)) {
        Text(label, color = TextSecondary, fontSize = 12.sp)
        Text(value, color = TextPrimary, fontSize = 14.sp)
    }
}

@Composable
@OptIn(ExperimentalFoundationApi::class)
fun FileItemCard(
    item: RemoteFileItem,
    onOpen: () -> Unit,
    onLongClick: () -> Unit,
    onRename: () -> Unit,
    onDelete: () -> Unit,
    onDownload: () -> Unit,
    onInfo: () -> Unit,
    isSelected: Boolean = false,
    selectionMode: Boolean = false,
    showPath: Boolean = false
) {
    var menuExpanded by remember { mutableStateOf(false) }
    val locale = androidx.compose.ui.platform.LocalConfiguration.current.locales[0]
    val timeZone = java.util.TimeZone.getDefault().id
    val formattedDate = remember(item.modifiedTime, locale, timeZone) { item.formattedDate }
    val formattedSize = remember(item.sizeBytes, item.isDirectory) { item.formattedSize }

    val icon: ImageVector = when {
        item.isDirectory -> Icons.Default.Folder
        item.extension in listOf("sh", "py", "js", "ts", "json", "yml", "yaml", "c", "cpp", "go") -> Icons.Default.Code
        item.extension in listOf("txt", "md", "log", "conf", "env") -> Icons.Default.Description
        else -> Icons.AutoMirrored.Filled.InsertDriveFile
    }

    val iconTint = if (item.isDirectory) BurntOrange else TerminalCyan

    TailHarnessCard(
        modifier = Modifier.testTag("file_item_${item.name}"),
        onClick = onOpen,
        onLongClick = onLongClick,
        backgroundColor = NavyDarkCard,
        borderColor = if (isSelected) BurntOrange else NavyCardBorder,
        shadowElevation = 0.dp
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
                Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                    Text(
                        text = item.name,
                        color = TextPrimary,
                        fontWeight = FontWeight.SemiBold,
                        fontSize = 14.sp,
                        maxLines = 2,
                        overflow = TextOverflow.Ellipsis,
                        modifier = Modifier.weight(1f)
                    )
                    if (!item.isDirectory) Text(
                        item.extension.ifBlank { "FILE" }.uppercase(java.util.Locale.ROOT),
                        color = MaterialTheme.colorScheme.onPrimaryContainer, fontWeight = FontWeight.Bold, fontSize = 10.sp,
                        maxLines = 1, overflow = TextOverflow.Ellipsis,
                        modifier = Modifier.widthIn(max = 72.dp).clip(RoundedCornerShape(5.dp))
                            .background(MaterialTheme.colorScheme.primaryContainer).padding(horizontal = 5.dp, vertical = 3.dp)
                    )
                }
                Spacer(modifier = Modifier.height(2.dp))
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Text(
                        text = if (item.isDirectory) tr("Folder", "Folder") else formattedSize,
                        color = TextSecondary,
                        fontSize = 12.sp,
                        fontWeight = FontWeight.Medium
                    )
                    if (formattedDate != "-") {
                        Text(" · ", color = TextSecondary, fontSize = 12.sp)
                        Text(
                            text = formattedDate,
                            color = TextSecondary,
                            fontSize = 12.sp,
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis
                        )
                    }
                }
                if (showPath) {
                    Text(
                        text = item.path,
                        color = TextSecondary,
                        fontSize = 11.sp,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                        modifier = Modifier.padding(top = 2.dp)
                    )
                }
            }

            // 3-dots Action Menu
            if (selectionMode) {
                Checkbox(checked = isSelected, onCheckedChange = { onOpen() })
            } else Box {
                IconButton(onClick = { menuExpanded = true }) {
                    Icon(
                        Icons.Default.MoreVert,
                        contentDescription = tr("Tindakan untuk ${item.name}", "Actions for ${item.name}"),
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
                        text = { Text(if (item.isDirectory) tr("Buka folder", "Open folder") else tr("Lihat isi file", "Preview file"), color = TextPrimary) },
                        leadingIcon = {
                            Icon(if (item.isDirectory) Icons.Default.FolderOpen else Icons.Default.Description, contentDescription = null, tint = BurntOrange)
                        },
                        onClick = {
                            menuExpanded = false
                            onOpen()
                        }
                    )
                    DropdownMenuItem(
                        text = { Text(tr("Informasi file", "File information"), color = TextPrimary) },
                        leadingIcon = { Icon(Icons.Default.Info, null, tint = TextSecondary) },
                        onClick = { menuExpanded = false; onInfo() }
                    )
                    if (!item.isDirectory) {
                        DropdownMenuItem(
                            text = { Text(tr("Download ke HP", "Download to device"), color = TextPrimary) },
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
                        text = { Text(tr("Ganti nama", "Rename"), color = TextPrimary) },
                        leadingIcon = {
                            Icon(Icons.Default.DriveFileRenameOutline, contentDescription = null, tint = TextSecondary)
                        },
                        onClick = {
                            menuExpanded = false
                            onRename()
                        }
                    )
                    DropdownMenuItem(
                        text = { Text(tr("Hapus", "Delete"), color = TerminalRed) },
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
