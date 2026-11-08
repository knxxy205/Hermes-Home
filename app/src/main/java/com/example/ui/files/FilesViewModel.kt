package com.example.ui.files

import android.content.Context
import android.net.Uri
import android.provider.OpenableColumns
import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.viewModelScope
import com.example.data.model.ServerConfig
import com.example.data.sftp.RemoteFileItem
import com.example.data.sftp.SftpManager
import com.example.data.sftp.DownloadDestination
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.NonCancellable
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.io.File
import java.io.InputStream
import com.example.data.sftp.FilePreview
import android.graphics.Bitmap
import kotlinx.coroutines.Job
import kotlinx.coroutines.ensureActive
import kotlinx.coroutines.CancellationException
import androidx.work.Data
import androidx.work.OneTimeWorkRequestBuilder
import androidx.work.WorkManager
import androidx.work.workDataOf
import androidx.work.ExistingWorkPolicy
import com.example.data.security.EncryptedStorage
import com.example.data.worker.SftpUploadWorker
import org.json.JSONArray

data class ImagePreview(val name: String, val bitmap: Bitmap? = null, val error: String? = null)

data class FilesUiState(
    val currentPath: String = "/",
    val files: List<RemoteFileItem> = emptyList(),
    val isLoading: Boolean = false,
    val isSearching: Boolean = false,
    val isUploading: Boolean = false,
    val uploadingFileName: String? = null,
    val isDownloading: Boolean = false,
    val isChoosingDownloadFolder: Boolean = false,
    val isDeleting: Boolean = false,
    val downloadingFileName: String? = null,
    val transferStatus: String? = null,
    val transferFailed: Boolean = false,
    val errorMessage: String? = null,
    val statusMessage: String? = null,
    val searchQuery: String = "",
    val searchNotice: String? = null,
    val viewingFileContent: Pair<String, String>? = null,
    val imagePreview: ImagePreview? = null
)

class FilesViewModel(
    private val sftpManager: SftpManager
) : ViewModel() {

    private val _uiState = MutableStateFlow(FilesUiState())
    val uiState: StateFlow<FilesUiState> = _uiState.asStateFlow()
    private var previewJob: Job? = null
    private var listingJob: Job? = null
    @Volatile private var listingGeneration = 0
    private data class DirectorySnapshot(val path: String, val config: ServerConfig, val files: List<RemoteFileItem>)
    private var directorySnapshot: DirectorySnapshot? = null
    private var previewGeneration = 0

    fun closeImagePreview() {
        previewGeneration++
        previewJob?.cancel()
        val oldBitmap = _uiState.value.imagePreview?.bitmap
        _uiState.update { it.copy(imagePreview = null) }
        if (oldBitmap != null && !oldBitmap.isRecycled) oldBitmap.recycle()
    }

    fun updateSearchQuery(query: String) {
        _uiState.update { it.copy(searchQuery = query) }
        val config = lastConfig ?: return
        val snapshot = directorySnapshot
        if (query.isBlank() && snapshot != null && snapshot.path == _uiState.value.currentPath && snapshot.config == config) {
            listingGeneration++
            listingJob?.cancel()
            _uiState.update {
                it.copy(files = snapshot.files, isLoading = false, isSearching = false, errorMessage = null, searchNotice = null)
            }
        } else {
            loadFiles(config = config)
        }
    }

    private var lastConfig: ServerConfig? = null

    fun clearStatusMessage() {
        _uiState.update { it.copy(statusMessage = null) }
    }

    fun clearTransferStatus() {
        _uiState.update { it.copy(transferStatus = null, transferFailed = false) }
    }

    fun reportBackgroundUploadResult(message: String, failed: Boolean) {
        _uiState.update { it.copy(transferStatus = message, transferFailed = failed) }
    }

    fun setViewingFile(content: Pair<String, String>?) {
        _uiState.update { it.copy(viewingFileContent = content) }
    }

    fun navigateTo(path: String, config: ServerConfig) {
        _uiState.update { it.copy(currentPath = path, searchQuery = "") }
        loadFiles(path, config)
    }

    fun canNavigateUp(): Boolean {
        val current = _uiState.value.currentPath.trim()
        return current != "/" && current.isNotBlank()
    }

    fun navigateUp(config: ServerConfig) {
        val current = _uiState.value.currentPath.trimEnd('/')
        if (current.isNotBlank() && current != "/") {
            val parent = current.substringBeforeLast("/")
            val newPath = if (parent.isBlank()) "/" else parent
            navigateTo(newPath, config)
        } else {
            navigateTo("/", config)
        }
    }

    fun currentRemotePath(): String = _uiState.value.currentPath

    fun loadFiles(path: String = _uiState.value.currentPath, config: ServerConfig) {
        if (path != _uiState.value.currentPath) return
        val profileChanged = lastConfig != null && lastConfig != config
        lastConfig = config
        listingJob?.cancel()
        val generation = ++listingGeneration
        val query = if (profileChanged) "" else _uiState.value.searchQuery
        _uiState.update {
            it.copy(isLoading = query.isBlank(), isSearching = query.isNotBlank(), errorMessage = null,
                searchNotice = null, files = emptyList(), searchQuery = query)
        }
        listingJob = viewModelScope.launch {
            // Each request owns its channel, so an old blocking SFTP call cannot corrupt a new search.
            val manager = if (config.isSshConfigured) SftpManager() else sftpManager
            try {
                if (query.isNotBlank()) kotlinx.coroutines.delay(300)
                val (files, notice) = withContext(Dispatchers.IO) {
                    if (query.isBlank()) manager.listFiles(path, config).getOrThrow() to null else {
                        val result = manager.searchFiles(path, query, config, onProgress = { partial ->
                            _uiState.update { if (generation == listingGeneration) it.copy(files = partial.files) else it }
                        }).getOrThrow()
                        val warnings = buildList {
                            if (result.skippedFolders > 0) add("${result.skippedFolders} folder tidak dapat dibaca")
                            if (result.limited) add("Hasil sebagian: batas waktu atau pencarian tercapai. Cari dari folder yang lebih spesifik.")
                        }
                        result.files to warnings.takeIf { it.isNotEmpty() }?.joinToString(" · ")
                    }
                }
                ensureActive()
                if (query.isBlank() && generation == listingGeneration) directorySnapshot = DirectorySnapshot(path, config, files)
                if (generation == listingGeneration) _uiState.update {
                    it.copy(files = files, isLoading = false, isSearching = false, searchNotice = notice)
                }
            } catch (e: CancellationException) {
                throw e
            } catch (e: Exception) {
                if (generation == listingGeneration) _uiState.update {
                    it.copy(isLoading = false, isSearching = false, errorMessage = e.localizedMessage ?: "Gagal memuat file")
                }
            } finally {
                if (manager !== sftpManager) withContext(NonCancellable + Dispatchers.IO) { manager.close() }
            }
        }
    }

    fun stopSearch() {
        listingGeneration++
        listingJob?.cancel()
        _uiState.update {
            it.copy(isSearching = false, searchNotice = "Pencarian dihentikan. Hasil yang ditampilkan belum lengkap.")
        }
    }

    private fun refreshAfterMutation(path: String, config: ServerConfig) {
        if (lastConfig == config) {
            directorySnapshot = null
            if (_uiState.value.currentPath == path) loadFiles(path, config)
        }
    }

    fun uploadFile(
        context: Context,
        uri: Uri,
        config: ServerConfig
    ) {
        uploadMultipleFiles(context, listOf(uri), config)
    }

    fun uploadMultipleFiles(
        context: Context,
        uris: List<Uri>,
        config: ServerConfig,
        destinationPath: String = _uiState.value.currentPath,
        uploadProfileId: String? = null
    ) {
        if (uris.isEmpty()) return
        val targetPath = destinationPath.trim().let { path ->
            when {
                path.isBlank() -> "/"
                path.startsWith("/") -> path.trimEnd('/').ifBlank { "/" }
                else -> "/${path.trimEnd('/')}"
            }
        }
        val profileId = uploadProfileId ?: EncryptedStorage(context).getActiveProfileId()
        viewModelScope.launch(Dispatchers.IO) {
            runCatching { com.example.data.transfer.UploadQueue.enqueue(context.applicationContext, uris, profileId, targetPath) }
                .onSuccess { _uiState.update { it.copy(transferStatus = null, transferFailed = false, statusMessage = "Antrean upload berjalan di latar belakang (${uris.size} file).") } }
                .onFailure { error -> _uiState.update { it.copy(statusMessage = error.localizedMessage, transferFailed = true) } }
        }
    }

    fun createFolder(name: String, config: ServerConfig) {
        if (name.isBlank()) return
        val currentPath = _uiState.value.currentPath
        viewModelScope.launch(Dispatchers.IO) {
            val result = sftpManager.createDirectory(
                parentPath = currentPath,
                folderName = name.trim(),
                config = config,
                useSandbox = !config.isSshConfigured
            )
            withContext(Dispatchers.Main) {
                if (result.isSuccess) {
                    _uiState.update { it.copy(statusMessage = "Folder '$name' dibuat") }
                    refreshAfterMutation(currentPath, config)
                } else {
                    val err = result.exceptionOrNull()
                    _uiState.update { it.copy(statusMessage = "Gagal membuat folder: ${err?.localizedMessage ?: err?.message}") }
                }
            }
        }
    }

    fun createFile(name: String, content: String, config: ServerConfig) {
        if (name.isBlank()) return
        val currentPath = _uiState.value.currentPath
        viewModelScope.launch(Dispatchers.IO) {
            val result = sftpManager.createTextFile(
                parentPath = currentPath,
                fileName = name.trim(),
                content = content,
                config = config,
                useSandbox = !config.isSshConfigured
            )
            withContext(Dispatchers.Main) {
                if (result.isSuccess) {
                    _uiState.update { it.copy(statusMessage = "File '$name' dibuat") }
                    refreshAfterMutation(currentPath, config)
                } else {
                    val err = result.exceptionOrNull()
                    _uiState.update { it.copy(statusMessage = "Gagal membuat file: ${err?.localizedMessage ?: err?.message}") }
                }
            }
        }
    }

    fun rename(item: RemoteFileItem, newName: String, config: ServerConfig) {
        if (newName.isBlank() || newName == item.name) return
        val currentPath = _uiState.value.currentPath
        viewModelScope.launch(Dispatchers.IO) {
            val result = sftpManager.rename(
                oldPath = item.path,
                newName = newName.trim(),
                config = config,
                useSandbox = !config.isSshConfigured
            )
            withContext(Dispatchers.Main) {
                if (result.isSuccess) {
                    _uiState.update { it.copy(statusMessage = "Nama diubah menjadi '$newName'") }
                    refreshAfterMutation(currentPath, config)
                } else {
                    val err = result.exceptionOrNull()
                    _uiState.update { it.copy(statusMessage = "Gagal mengubah nama: ${err?.localizedMessage ?: err?.message}") }
                }
            }
        }
    }

    fun deleteItem(item: RemoteFileItem, config: ServerConfig) {
        deleteItems(listOf(item), config)
    }

    fun readFile(item: RemoteFileItem, config: ServerConfig, cacheDir: File) {
        closeImagePreview()
        if (FilePreview.isImage(item.name)) {
            val generation = previewGeneration
            _uiState.update { it.copy(viewingFileContent = null, imagePreview = ImagePreview(item.name)) }
            previewJob = viewModelScope.launch {
                try {
                    val bitmap = withContext(Dispatchers.IO) {
                        require(item.sizeBytes <= FilePreview.MAX_IMAGE_BYTES) { "Gambar melebihi batas preview 25 MB. Gunakan Download." }
                        val dir = File(cacheDir, "image-previews").apply { mkdirs() }
                        // Temporary downloads are removed after decoding, also on failure/cancellation.
                        val file = File.createTempFile("preview-", ".image", dir)
                        try {
                            sftpManager.downloadToLocal(item.path, file, config, !config.isSshConfigured,
                                FilePreview.MAX_IMAGE_BYTES).getOrThrow()
                            ensureActive()
                            FilePreview.decodeImage(file)
                        } finally { file.delete() }
                    }
                    if (generation == previewGeneration) _uiState.update { it.copy(imagePreview = ImagePreview(item.name, bitmap)) }
                    else if (!bitmap.isRecycled) bitmap.recycle()
            } catch (e: CancellationException) {
                throw e
                } catch (e: Exception) {
                    if (generation == previewGeneration) _uiState.update { it.copy(imagePreview = ImagePreview(item.name, error = e.message ?: "Gagal membuka gambar")) }
                }
            }
            return
        }
        _uiState.update { it.copy(isLoading = true) }
        viewModelScope.launch(Dispatchers.IO) {
            val result = sftpManager.readFileContent(
                remotePath = item.path,
                config = config,
                useSandbox = !config.isSshConfigured
            )
            withContext(Dispatchers.Main) {
                _uiState.update { it.copy(isLoading = false) }
                if (result.isSuccess) {
                    val content = result.getOrNull() ?: ""
                    _uiState.update { it.copy(viewingFileContent = Pair(item.name, content)) }
                } else {
                    val err = result.exceptionOrNull()
                    _uiState.update { it.copy(statusMessage = "Gagal membaca file: ${err?.localizedMessage ?: err?.message}") }
                }
            }
        }
    }

    private data class PendingDownload(val files: List<RemoteFileItem>, val config: ServerConfig)
    private var pendingDownload: PendingDownload? = null

    fun prepareDownload(items: List<RemoteFileItem>, config: ServerConfig): Boolean {
        if (_uiState.value.isDownloading || pendingDownload != null) return false
        if (items.isEmpty() || items.any { it.isDirectory }) return false
        val files = items.distinctBy { it.path }
        pendingDownload = PendingDownload(files.toList(), config)
        _uiState.update { it.copy(isChoosingDownloadFolder = true) }
        return true
    }

    fun cancelDownloadPicker() {
        pendingDownload = null
        _uiState.update { it.copy(isChoosingDownloadFolder = false) }
    }

    fun completeDownload(context: Context, destination: Uri?) {
        val batch = pendingDownload ?: return
        cancelDownloadPicker()
        if (destination == null) return
        _uiState.update {
            it.copy(
                isDownloading = true,
                downloadingFileName = "${batch.files.size} file",
                transferStatus = null,
                transferFailed = false
            )
        }
        val resolver = context.applicationContext.contentResolver
        viewModelScope.launch(Dispatchers.IO) {
            val manager = if (batch.config.isSshConfigured) SftpManager() else sftpManager
            val target = DownloadDestination(resolver, destination)
            var completed = 0
            val failures = mutableListOf<String>()
            try {
                batch.files.forEachIndexed { index, item ->
                    ensureActive()
                    _uiState.update { it.copy(downloadingFileName = "${index + 1}/${batch.files.size} · ${item.name}") }
                    try {
                        target.save(item.name) { output ->
                            manager.downloadToStream(item.path, output, batch.config).getOrThrow()
                        }
                        completed++
                    } catch (e: CancellationException) {
                        throw e
                    } catch (e: Exception) {
                        failures += "${item.path}: ${e.localizedMessage ?: "Download gagal"}"
                        manager.close()
                    }
                }
            } finally {
                if (manager !== sftpManager) manager.close()
                _uiState.update { it.copy(isDownloading = false, downloadingFileName = null) }
            }
            _uiState.update {
                it.copy(
                    transferStatus = "Download selesai · $completed/${batch.files.size} file tersimpan di folder pilihan" +
                        if (failures.isNotEmpty()) " · ${failures.size} gagal" else "",
                    transferFailed = failures.isNotEmpty(),
                    statusMessage = failures.takeIf { it.isNotEmpty() }?.take(3)?.joinToString("\n")
                )
            }
        }
    }

    fun deleteItems(items: List<RemoteFileItem>, config: ServerConfig) {
        if (items.isEmpty() || _uiState.value.isDeleting) return
        val currentPath = _uiState.value.currentPath
        _uiState.update { it.copy(isDeleting = true) }
        viewModelScope.launch(Dispatchers.IO) {
            val manager = if (config.isSshConfigured) SftpManager() else sftpManager
            var completed = 0
            val failures = mutableListOf<String>()
            try {
                // Children first when search results include a folder and a file within it.
                items.distinctBy { it.path }.sortedByDescending { it.path.count { c -> c == '/' } }.forEach { item ->
                    ensureActive()
                    val result = manager.delete(
                        path = item.path,
                        isDirectory = item.isDirectory,
                        config = config,
                        useSandbox = !config.isSshConfigured
                    )
                    if (result.isSuccess) completed++ else failures += item.path
                }
            } finally {
                if (manager !== sftpManager) manager.close()
                _uiState.update { it.copy(isDeleting = false) }
            }
            withContext(Dispatchers.Main) {
                _uiState.update {
                    it.copy(
                        statusMessage = if (failures.isEmpty()) {
                            "$completed item dihapus"
                        } else {
                            "$completed berhasil dihapus, ${failures.size} gagal: ${failures.joinToString(", ")}"
                        }
                    )
                }
                if (completed > 0) refreshAfterMutation(currentPath, config)
            }
        }
    }

    companion object {
        fun provideFactory(sftpManager: SftpManager): ViewModelProvider.Factory =
            object : ViewModelProvider.Factory {
                @Suppress("UNCHECKED_CAST")
                override fun <T : ViewModel> create(modelClass: Class<T>): T {
                    return FilesViewModel(sftpManager) as T
                }
            }

        fun resolveFileName(context: Context, uri: Uri): String {
            var resolvedName: String? = null
            try {
                if (uri.scheme == "content") {
                    context.contentResolver.query(uri, arrayOf(OpenableColumns.DISPLAY_NAME), null, null, null)?.use { cursor ->
                        if (cursor.moveToFirst()) {
                            val displayNameIndex = cursor.getColumnIndex(OpenableColumns.DISPLAY_NAME)
                            if (displayNameIndex >= 0) {
                                resolvedName = cursor.getString(displayNameIndex)
                            }
                        }
                    }
                } else if (uri.scheme == "file") {
                    resolvedName = uri.lastPathSegment
                }
            } catch (_: Exception) {
                // Ignore cursor / security query failures
            }

            if (resolvedName.isNullOrBlank()) {
                resolvedName = uri.lastPathSegment
            }

            val clean = resolvedName?.substringAfterLast('/')?.trim()
            return if (!clean.isNullOrBlank()) {
                clean
            } else {
                "uploaded_file_${System.currentTimeMillis()}"
            }
        }
    }
}
