package com.example.ui.files

import android.widget.Toast
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
import androidx.compose.material.icons.filled.CreateNewFolder
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.Description
import androidx.compose.material.icons.filled.DriveFileRenameOutline
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
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.OutlinedTextFieldDefaults
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
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
import kotlinx.coroutines.launch
import java.io.File

@Composable
fun FilesScreen(
    sftpManager: SftpManager,
    serverConfig: ServerConfig,
    onAskHermesAboutFile: ((fileName: String, content: String) -> Unit)? = null,
    modifier: Modifier = Modifier
) {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()

    var currentPath by remember { mutableStateOf("/") }
    var fileList by remember { mutableStateOf<List<RemoteFileItem>>(emptyList()) }
    var isLoading by remember { mutableStateOf(false) }
    var errorMessage by remember { mutableStateOf<String?>(null) }
    var searchQuery by remember { mutableStateOf("") }

    // Dialog states
    var showNewFolderDialog by remember { mutableStateOf(false) }
    var newFolderName by remember { mutableStateOf("") }

    var showNewFileDialog by remember { mutableStateOf(false) }
    var newFileName by remember { mutableStateOf("") }
    var newFileContent by remember { mutableStateOf("") }

    var itemToRename by remember { mutableStateOf<RemoteFileItem?>(null) }
    var renameNewName by remember { mutableStateOf("") }

    var itemToDelete by remember { mutableStateOf<RemoteFileItem?>(null) }

    var fileContentToView by remember { mutableStateOf<Pair<String, String>?>(null) }

    fun refreshFiles() {
        isLoading = true
        errorMessage = null
        scope.launch {
            val result = sftpManager.listFiles(
                currentPath = currentPath,
                config = serverConfig,
                useSandbox = !serverConfig.isSshConfigured
            )
            isLoading = false
            result.onSuccess {
                fileList = it
            }.onFailure { error ->
                errorMessage = error.localizedMessage ?: "Gagal memuat daftar file SFTP"
            }
        }
    }

    LaunchedEffect(currentPath, serverConfig) {
        refreshFiles()
    }

    val filteredList = remember(fileList, searchQuery) {
        if (searchQuery.isBlank()) fileList
        else fileList.filter { it.name.contains(searchQuery, ignoreCase = true) }
    }

    Column(
        modifier = modifier
            .fillMaxSize()
            .background(NavyDarkBackground)
    ) {
        // Custom Screen Header
        CustomScreenHeader(
            icon = Icons.Default.FolderOpen,
            title = "Files",
            subtitle = if (serverConfig.isSshConfigured) "SFTP Browser (${serverConfig.sshHost})" else "SFTP Sandbox Browser",
            trailingContent = {
                Row {
                    IconButton(
                        onClick = { showNewFolderDialog = true },
                        modifier = Modifier.testTag("new_folder_button")
                    ) {
                        Icon(
                            Icons.Default.CreateNewFolder,
                            contentDescription = "New Folder",
                            tint = BurntOrange
                        )
                    }
                    IconButton(
                        onClick = { showNewFileDialog = true },
                        modifier = Modifier.testTag("new_file_button")
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
                onClick = {
                    if (currentPath != "/" && currentPath.contains("/")) {
                        val parent = currentPath.substringBeforeLast("/")
                        currentPath = if (parent.isBlank()) "/" else parent
                    }
                },
                enabled = currentPath != "/"
            ) {
                Icon(
                    Icons.AutoMirrored.Filled.ArrowBack,
                    contentDescription = "Go Up",
                    tint = if (currentPath != "/") BurntOrange else TextSecondary.copy(alpha = 0.4f),
                    modifier = Modifier.size(20.dp)
                )
            }

            Text(
                text = currentPath,
                color = TextPrimary,
                fontFamily = FontFamily.Monospace,
                fontSize = 13.sp,
                fontWeight = FontWeight.SemiBold,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
                modifier = Modifier.weight(1f).padding(horizontal = 4.dp)
            )

            IconButton(onClick = { refreshFiles() }) {
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
            value = searchQuery,
            onValueChange = { searchQuery = it },
            placeholder = { Text("Cari file atau folder...", color = TextSecondary, fontSize = 13.sp) },
            leadingIcon = {
                Icon(Icons.Default.Search, contentDescription = null, tint = TextSecondary, modifier = Modifier.size(18.dp))
            },
            singleLine = true,
            shape = RoundedCornerShape(14.dp),
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
                .height(50.dp)
        )

        Spacer(modifier = Modifier.height(4.dp))

        // Content Area
        Box(
            modifier = Modifier
                .weight(1f)
                .fillMaxWidth()
        ) {
            when {
                isLoading -> {
                    Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                        CircularProgressIndicator(color = BurntOrange)
                    }
                }
                errorMessage != null -> {
                    Column(
                        modifier = Modifier
                            .fillMaxSize()
                            .padding(24.dp),
                        horizontalAlignment = Alignment.CenterHorizontally,
                        verticalArrangement = Arrangement.Center
                    ) {
                        Text(
                            text = errorMessage ?: "Error",
                            color = TerminalRed,
                            fontSize = 14.sp,
                            fontFamily = FontFamily.Monospace
                        )
                        Spacer(modifier = Modifier.height(12.dp))
                        Button(
                            onClick = { refreshFiles() },
                            colors = ButtonDefaults.buttonColors(containerColor = BurntOrange)
                        ) {
                            Text("Coba Lagi", color = NavyDarkBackground)
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
                            tint = TextSecondary.copy(alpha = 0.4f),
                            modifier = Modifier.size(48.dp)
                        )
                        Spacer(modifier = Modifier.height(12.dp))
                        Text("Folder kosong", color = TextSecondary, fontSize = 14.sp)
                    }
                }
                else -> {
                    LazyColumn(
                        modifier = Modifier.fillMaxSize(),
                        contentPadding = PaddingValues(horizontal = 16.dp, vertical = 8.dp),
                        verticalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        items(filteredList, key = { it.path }) { item ->
                            FileItemCard(
                                item = item,
                                onOpen = {
                                    if (item.isDirectory) {
                                        currentPath = item.path
                                    } else {
                                        scope.launch {
                                            isLoading = true
                                            val contentResult = sftpManager.readFileContent(
                                                item.path,
                                                serverConfig,
                                                useSandbox = !serverConfig.isSshConfigured
                                            )
                                            isLoading = false
                                            contentResult.onSuccess { content ->
                                                fileContentToView = Pair(item.name, content)
                                            }.onFailure {
                                                Toast.makeText(context, "Gagal membuka file: ${it.message}", Toast.LENGTH_SHORT).show()
                                            }
                                        }
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
                                    scope.launch {
                                        val dest = File(context.cacheDir, item.name)
                                        val res = sftpManager.downloadToLocal(
                                            item.path,
                                            dest,
                                            serverConfig,
                                            useSandbox = !serverConfig.isSshConfigured
                                        )
                                        res.onSuccess {
                                            Toast.makeText(context, "Disimpan ke: ${it.name} (${item.formattedSize})", Toast.LENGTH_LONG).show()
                                        }.onFailure {
                                            Toast.makeText(context, "Download gagal: ${it.message}", Toast.LENGTH_SHORT).show()
                                        }
                                    }
                                }
                            )
                        }
                    }
                }
            }
        }
    }

    // New Folder Dialog
    if (showNewFolderDialog) {
        AlertDialog(
            onDismissRequest = { showNewFolderDialog = false },
            containerColor = NavyDarkCard,
            titleContentColor = TextPrimary,
            title = { Text("Buat Folder Baru", fontWeight = FontWeight.Bold) },
            text = {
                OutlinedTextField(
                    value = newFolderName,
                    onValueChange = { newFolderName = it },
                    placeholder = { Text("Nama folder (contoh: scripts)") },
                    singleLine = true,
                    colors = OutlinedTextFieldDefaults.colors(
                        focusedBorderColor = BurntOrange,
                        unfocusedBorderColor = NavyDarkBorder,
                        focusedTextColor = TextPrimary,
                        unfocusedTextColor = TextPrimary
                    )
                )
            },
            confirmButton = {
                Button(
                    onClick = {
                        if (newFolderName.isNotBlank()) {
                            scope.launch {
                                sftpManager.createDirectory(
                                    currentPath,
                                    newFolderName.trim(),
                                    serverConfig,
                                    useSandbox = !serverConfig.isSshConfigured
                                )
                                showNewFolderDialog = false
                                newFolderName = ""
                                refreshFiles()
                            }
                        }
                    },
                    colors = ButtonDefaults.buttonColors(containerColor = BurntOrange)
                ) {
                    Text("Buat", color = NavyDarkBackground, fontWeight = FontWeight.Bold)
                }
            },
            dismissButton = {
                TextButton(onClick = { showNewFolderDialog = false }) {
                    Text("Batal", color = TextSecondary)
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
            title = { Text("Buat File Baru", fontWeight = FontWeight.Bold) },
            text = {
                Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    OutlinedTextField(
                        value = newFileName,
                        onValueChange = { newFileName = it },
                        placeholder = { Text("Nama file (contoh: config.json)") },
                        singleLine = true,
                        colors = OutlinedTextFieldDefaults.colors(
                            focusedBorderColor = BurntOrange,
                            unfocusedBorderColor = NavyDarkBorder,
                            focusedTextColor = TextPrimary,
                            unfocusedTextColor = TextPrimary
                        )
                    )
                    OutlinedTextField(
                        value = newFileContent,
                        onValueChange = { newFileContent = it },
                        placeholder = { Text("Isi file teks...") },
                        maxLines = 6,
                        modifier = Modifier.height(120.dp),
                        colors = OutlinedTextFieldDefaults.colors(
                            focusedBorderColor = BurntOrange,
                            unfocusedBorderColor = NavyDarkBorder,
                            focusedTextColor = TextPrimary,
                            unfocusedTextColor = TextPrimary
                        )
                    )
                }
            },
            confirmButton = {
                Button(
                    onClick = {
                        if (newFileName.isNotBlank()) {
                            scope.launch {
                                sftpManager.createTextFile(
                                    currentPath,
                                    newFileName.trim(),
                                    newFileContent,
                                    serverConfig,
                                    useSandbox = !serverConfig.isSshConfigured
                                )
                                showNewFileDialog = false
                                newFileName = ""
                                newFileContent = ""
                                refreshFiles()
                            }
                        }
                    },
                    colors = ButtonDefaults.buttonColors(containerColor = BurntOrange)
                ) {
                    Text("Simpan", color = NavyDarkBackground, fontWeight = FontWeight.Bold)
                }
            },
            dismissButton = {
                TextButton(onClick = { showNewFileDialog = false }) {
                    Text("Batal", color = TextSecondary)
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
            title = { Text("Ganti Nama", fontWeight = FontWeight.Bold) },
            text = {
                OutlinedTextField(
                    value = renameNewName,
                    onValueChange = { renameNewName = it },
                    singleLine = true,
                    colors = OutlinedTextFieldDefaults.colors(
                        focusedBorderColor = BurntOrange,
                        unfocusedBorderColor = NavyDarkBorder,
                        focusedTextColor = TextPrimary,
                        unfocusedTextColor = TextPrimary
                    )
                )
            },
            confirmButton = {
                Button(
                    onClick = {
                        if (renameNewName.isNotBlank() && renameNewName != renameItem.name) {
                            scope.launch {
                                sftpManager.rename(
                                    renameItem.path,
                                    renameNewName.trim(),
                                    serverConfig,
                                    useSandbox = !serverConfig.isSshConfigured
                                )
                                itemToRename = null
                                refreshFiles()
                            }
                        }
                    },
                    colors = ButtonDefaults.buttonColors(containerColor = BurntOrange)
                ) {
                    Text("Simpan", color = NavyDarkBackground, fontWeight = FontWeight.Bold)
                }
            },
            dismissButton = {
                TextButton(onClick = { itemToRename = null }) {
                    Text("Batal", color = TextSecondary)
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
            title = { Text("Hapus ${if (deleteItem.isDirectory) "Folder" else "File"}?", fontWeight = FontWeight.Bold) },
            text = {
                Text("Apakah Anda yakin ingin menghapus '${deleteItem.name}' dari homeserver?", color = TextSecondary)
            },
            confirmButton = {
                Button(
                    onClick = {
                        scope.launch {
                            sftpManager.delete(
                                deleteItem.path,
                                deleteItem.isDirectory,
                                serverConfig,
                                useSandbox = !serverConfig.isSshConfigured
                            )
                            itemToDelete = null
                            refreshFiles()
                        }
                    },
                    colors = ButtonDefaults.buttonColors(containerColor = TerminalRed)
                ) {
                    Text("Hapus", color = NavyDarkBackground, fontWeight = FontWeight.Bold)
                }
            },
            dismissButton = {
                TextButton(onClick = { itemToDelete = null }) {
                    Text("Batal", color = TextSecondary)
                }
            }
        )
    }

    // File Preview Dialog
    val previewData = fileContentToView
    if (previewData != null) {
        val (previewName, previewContent) = previewData
        AlertDialog(
            onDismissRequest = { fileContentToView = null },
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
                                fileContentToView = null
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
                        onClick = { fileContentToView = null },
                        colors = ButtonDefaults.buttonColors(containerColor = BurntOrange)
                    ) {
                        Text("Tutup", color = NavyDarkBackground, fontWeight = FontWeight.Bold, fontSize = 12.sp)
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
                    .background(if (item.isDirectory) BurntOrangeBadgeBg else NavyDarkSurface)
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
                        color = BurntOrange,
                        fontSize = 11.sp,
                        fontWeight = FontWeight.Medium
                    )
                    Text(
                        text = " • ",
                        color = TextSecondary,
                        fontSize = 11.sp
                    )
                    Text(
                        text = item.permissions,
                        color = TextSecondary,
                        fontSize = 10.sp,
                        fontFamily = FontFamily.Monospace
                    )
                    if (item.formattedDate != "-") {
                        Text(
                            text = " • ",
                            color = TextSecondary,
                            fontSize = 11.sp
                        )
                        Text(
                            text = item.formattedDate,
                            color = TextSecondary,
                            fontSize = 10.sp
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
