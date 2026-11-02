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
import kotlinx.coroutines.Dispatchers
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
import com.example.data.security.EncryptedStorage
import com.example.data.worker.SftpUploadWorker
import org.json.JSONArray

data class ImagePreview(val name: String, val bitmap: Bitmap? = null, val error: String? = null)

data class FilesUiState(
    val currentPath: String = "/",
    val files: List<RemoteFileItem> = emptyList(),
    val isLoading: Boolean = false,
    val isUploading: Boolean = false,
    val uploadingFileName: String? = null,
    val isDownloading: Boolean = false,
    val downloadingFileName: String? = null,
    val transferStatus: String? = null,
    val transferFailed: Boolean = false,
    val errorMessage: String? = null,
    val statusMessage: String? = null,
    val searchQuery: String = "",
    val viewingFileContent: Pair<String, String>? = null,
    val imagePreview: ImagePreview? = null
)

class FilesViewModel(
    private val sftpManager: SftpManager
) : ViewModel() {

    private val _uiState = MutableStateFlow(FilesUiState())
    val uiState: StateFlow<FilesUiState> = _uiState.asStateFlow()
    private var previewJob: Job? = null
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
    }

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
        _uiState.update { it.copy(currentPath = path) }
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

    fun loadFiles(path: String = _uiState.value.currentPath, config: ServerConfig) {
        _uiState.update { it.copy(isLoading = true, errorMessage = null) }
        viewModelScope.launch(Dispatchers.IO) {
            val result = sftpManager.listFiles(
                currentPath = path,
                config = config,
                useSandbox = !config.isSshConfigured
            )
            withContext(Dispatchers.Main) {
                if (result.isSuccess) {
                    val list = result.getOrNull() ?: emptyList()
                    _uiState.update {
                        it.copy(
                            files = list,
                            isLoading = false,
                            errorMessage = null
                        )
                    }
                } else {
                    val error = result.exceptionOrNull()
                    _uiState.update {
                        it.copy(
                            isLoading = false,
                            errorMessage = error?.localizedMessage ?: "Gagal memuat daftar file SFTP"
                        )
                    }
                }
            }
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
        destinationPath: String = _uiState.value.currentPath
    ) {
        if (uris.isEmpty()) return
        val targetPath = destinationPath.ifBlank { "/" }
        val queueFile = File(context.filesDir, "sftp-upload-${System.currentTimeMillis()}.json")
        val queue = JSONArray()
        uris.forEach { uri ->
            runCatching { context.contentResolver.takePersistableUriPermission(uri, android.content.Intent.FLAG_GRANT_READ_URI_PERMISSION) }
            queue.put(uri.toString())
        }
        queueFile.writeText(queue.toString())
        val profileId = EncryptedStorage(context).getActiveProfileId()
        val request = OneTimeWorkRequestBuilder<SftpUploadWorker>()
            .setInputData(workDataOf(
                SftpUploadWorker.KEY_QUEUE to queueFile.absolutePath,
                SftpUploadWorker.KEY_PROFILE to profileId,
                SftpUploadWorker.KEY_REMOTE_PATH to targetPath
            ))
            .addTag(SftpUploadWorker.TAG)
            .build()
        WorkManager.getInstance(context).enqueueUniqueWork(
            "sftp_upload_queue", androidx.work.ExistingWorkPolicy.APPEND_OR_REPLACE, request
        )
        _uiState.update {
            it.copy(transferStatus = null, transferFailed = false, statusMessage = "Antrean upload berjalan di latar belakang (${uris.size} file).")
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
                    loadFiles(currentPath, config)
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
                    loadFiles(currentPath, config)
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
                    loadFiles(currentPath, config)
                } else {
                    val err = result.exceptionOrNull()
                    _uiState.update { it.copy(statusMessage = "Gagal mengubah nama: ${err?.localizedMessage ?: err?.message}") }
                }
            }
        }
    }

    fun deleteItem(item: RemoteFileItem, config: ServerConfig) {
        val currentPath = _uiState.value.currentPath
        viewModelScope.launch(Dispatchers.IO) {
            val result = sftpManager.delete(
                path = item.path,
                isDirectory = item.isDirectory,
                config = config,
                useSandbox = !config.isSshConfigured
            )
            withContext(Dispatchers.Main) {
                if (result.isSuccess) {
                    _uiState.update { it.copy(statusMessage = "'${item.name}' dihapus") }
                    loadFiles(currentPath, config)
                } else {
                    val err = result.exceptionOrNull()
                    _uiState.update { it.copy(statusMessage = "Gagal menghapus: ${err?.localizedMessage ?: err?.message}") }
                }
            }
        }
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

    fun downloadFile(item: RemoteFileItem, destFile: File, config: ServerConfig) {
        _uiState.update {
            it.copy(isDownloading = true, downloadingFileName = item.name, transferStatus = null, transferFailed = false)
        }
        viewModelScope.launch(Dispatchers.IO) {
            val result = sftpManager.downloadToLocal(
                remotePath = item.path,
                targetFile = destFile,
                config = config,
                useSandbox = !config.isSshConfigured
            )
            withContext(Dispatchers.Main) {
                _uiState.update { it.copy(isDownloading = false, downloadingFileName = null) }
                if (result.isSuccess) {
                    _uiState.update {
                        it.copy(
                            transferStatus = "Download selesai · ${destFile.absolutePath}",
                            transferFailed = false,
                            statusMessage = "Disimpan ke: ${destFile.name} (${item.formattedSize})"
                        )
                    }
                } else {
                    val err = result.exceptionOrNull()
                    _uiState.update {
                        it.copy(
                            transferStatus = "Download gagal · ${err?.localizedMessage ?: err?.message}",
                            transferFailed = true,
                            statusMessage = "Download gagal: ${err?.localizedMessage ?: err?.message}"
                        )
                    }
                }
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
