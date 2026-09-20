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

data class FilesUiState(
    val currentPath: String = "/",
    val files: List<RemoteFileItem> = emptyList(),
    val isLoading: Boolean = false,
    val isUploading: Boolean = false,
    val uploadingFileName: String? = null,
    val errorMessage: String? = null,
    val statusMessage: String? = null,
    val searchQuery: String = "",
    val viewingFileContent: Pair<String, String>? = null
)

class FilesViewModel(
    private val sftpManager: SftpManager
) : ViewModel() {

    private val _uiState = MutableStateFlow(FilesUiState())
    val uiState: StateFlow<FilesUiState> = _uiState.asStateFlow()

    fun updateSearchQuery(query: String) {
        _uiState.update { it.copy(searchQuery = query) }
    }

    fun clearStatusMessage() {
        _uiState.update { it.copy(statusMessage = null) }
    }

    fun setViewingFile(content: Pair<String, String>?) {
        _uiState.update { it.copy(viewingFileContent = content) }
    }

    fun navigateTo(path: String, config: ServerConfig) {
        _uiState.update { it.copy(currentPath = path) }
        loadFiles(path, config)
    }

    fun navigateUp(config: ServerConfig) {
        val current = _uiState.value.currentPath
        if (current != "/" && current.contains("/")) {
            val parent = current.substringBeforeLast("/")
            val newPath = if (parent.isBlank()) "/" else parent
            navigateTo(newPath, config)
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
        config: ServerConfig
    ) {
        if (uris.isEmpty()) return
        val currentPath = _uiState.value.currentPath
        val totalCount = uris.size

        _uiState.update {
            it.copy(
                isUploading = true,
                uploadingFileName = "1/$totalCount",
                statusMessage = if (totalCount == 1) {
                    val singleName = resolveFileName(context, uris[0])
                    "Mengunggah: $singleName..."
                } else {
                    "Mempersiapkan unggahan $totalCount berkas..."
                }
            )
        }

        viewModelScope.launch(Dispatchers.IO) {
            var successCount = 0
            var failCount = 0

            for ((index, uri) in uris.withIndex()) {
                val currentNum = index + 1
                val fileName = resolveFileName(context, uri)

                withContext(Dispatchers.Main) {
                    _uiState.update {
                        it.copy(
                            uploadingFileName = if (totalCount > 1) "$fileName ($currentNum/$totalCount)" else fileName,
                            statusMessage = if (totalCount > 1) {
                                "Mengunggah ($currentNum/$totalCount): $fileName..."
                            } else {
                                "Mengunggah: $fileName..."
                            }
                        )
                    }
                }

                var inputStream: InputStream? = null
                try {
                    inputStream = try {
                        context.contentResolver.openInputStream(uri)
                    } catch (e: Exception) {
                        null
                    }

                    if (inputStream == null) {
                        failCount++
                        continue
                    }

                    val result = sftpManager.uploadFile(
                        parentPath = currentPath,
                        fileName = fileName,
                        inputStream = inputStream,
                        config = config,
                        useSandbox = !config.isSshConfigured
                    )

                    if (result.isSuccess) {
                        successCount++
                    } else {
                        failCount++
                    }
                } catch (e: Exception) {
                    failCount++
                } finally {
                    try {
                        inputStream?.close()
                    } catch (_: Exception) {}
                }
            }

            withContext(Dispatchers.Main) {
                _uiState.update {
                    it.copy(
                        isUploading = false,
                        uploadingFileName = null,
                        statusMessage = when {
                            totalCount == 1 && successCount == 1 -> "File berhasil diunggah."
                            totalCount == 1 && failCount == 1 -> "Gagal mengunggah file."
                            failCount == 0 -> "Berhasil mengunggah $successCount berkas."
                            successCount == 0 -> "Gagal mengunggah seluruh berkas ($failCount berkas)."
                            else -> "Unggahan selesai: $successCount berhasil, $failCount gagal."
                        }
                    )
                }
                loadFiles(currentPath, config)
            }
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

    fun readFile(item: RemoteFileItem, config: ServerConfig) {
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
        viewModelScope.launch(Dispatchers.IO) {
            val result = sftpManager.downloadToLocal(
                remotePath = item.path,
                targetFile = destFile,
                config = config,
                useSandbox = !config.isSshConfigured
            )
            withContext(Dispatchers.Main) {
                if (result.isSuccess) {
                    _uiState.update { it.copy(statusMessage = "Disimpan ke: ${destFile.name} (${item.formattedSize})") }
                } else {
                    val err = result.exceptionOrNull()
                    _uiState.update { it.copy(statusMessage = "Download gagal: ${err?.localizedMessage ?: err?.message}") }
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
