package com.example.data.worker

import android.content.Context
import android.net.Uri
import android.os.Build
import androidx.work.CoroutineWorker
import androidx.work.Data
import androidx.work.ForegroundInfo
import androidx.work.WorkerParameters
import com.example.R
import com.example.data.security.EncryptedStorage
import com.example.data.sftp.SftpManager
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.withContext
import org.json.JSONArray
import java.io.File
import java.util.Locale
import com.example.data.transfer.*
import com.example.data.ssh.SshConnectionPolicy
import kotlinx.coroutines.*
import kotlinx.coroutines.sync.withLock

class SftpUploadWorker(context: Context, params: WorkerParameters) : CoroutineWorker(context, params) {
    private val batchId get() = tags.firstOrNull { it.startsWith("$TAG:") }?.substringAfter(':')
        ?: inputData.getString(KEY_BATCH_ID) ?: id.toString()
    private val journal by lazy { TransferStore(applicationContext) }
    override suspend fun getForegroundInfo(): ForegroundInfo = foreground("Menyiapkan unggahan", 0, 0)

    override suspend fun doWork(): Result = withContext(Dispatchers.IO) {
        com.example.data.security.SshTrust.initialize(applicationContext)
        UploadCoordinator.observe(applicationContext)
        UploadCoordinator.lock.withLock { upload() }
    }

    private suspend fun upload(): Result = withContext(Dispatchers.IO) {
        val queuePath = inputData.getString(KEY_QUEUE)
        val profileId = journal.find(batchId)?.profileId ?: inputData.getString(KEY_PROFILE)
        val remotePath = inputData.getString(KEY_REMOTE_PATH) ?: "/"
        val queueFile = File(queuePath ?: "")
        try {
            require(!profileId.isNullOrBlank()) { applicationContext.getString(R.string.profile_missing) }
            val savedBatch = journal.find(batchId)
            val uploads = (if (savedBatch != null) JSONArray().apply {
                savedBatch.entries.forEach { entry -> put(org.json.JSONObject().apply {
                    put("uri", entry.uri); put("destinationPath", entry.destination)
                }) }
            } else if (queueFile.isFile) JSONArray(queueFile.readText()) else JSONArray()).let { json ->
                (0 until json.length()).map { index ->
                    val entry = json.get(index)
                    if (entry is org.json.JSONObject) {
                        UploadItem(
                            uri = Uri.parse(entry.getString("uri")),
                            destinationPath = normalizeRemotePath(entry.optString("destinationPath", remotePath))
                        )
                    } else {
                        UploadItem(Uri.parse(entry.toString()), normalizeRemotePath(remotePath))
                    }
                }
            }
            if (uploads.isEmpty()) return@withContext Result.failure()

            val storage = EncryptedStorage(applicationContext)
            val profile = storage.loadProfiles().firstOrNull { it.id == profileId }
                ?: error(applicationContext.getString(R.string.profile_missing))
            val config = profile.config
            journal.find(batchId)?.let { batch ->
                require(batch.host.isBlank() || (config.sshHost == batch.host && config.sshPort == batch.port && config.sshUser == batch.user)) {
                    applicationContext.getString(R.string.upload_target_changed)
                }
            }
            if (journal.find(batchId) == null) journal.add(UploadBatch(batchId, id.toString(), profileId, profile.name,
                entries = uploads.map { UploadEntry(it.uri.toString(), displayName(it.uri), it.destinationPath) }))
            val manager = SftpManager()
            val workerJob = currentCoroutineContext().job
            val cancellationWatcher = CoroutineScope(currentCoroutineContext()).launch(Dispatchers.IO) {
                try { awaitCancellation() } finally { manager.close() }
            }
            var complete = 0L
            var failed = 0
            var queueTotalBytes = 0L
            var lastFileName = ""
            var lastFileBytes = 0L
            var lastFileSize = -1L
            try {
                TransferNotifications.starting(applicationContext, id)
                setForeground(foreground("Mengunggah ${uploads.size} file", 0, 0))
                setProgress(Data.Builder()
                    .putString(KEY_REMOTE_PATH, uploads.first().destinationPath)
                    .putString(KEY_BATCH_ID, batchId)
                    .putInt(KEY_COUNT, uploads.size)
                    .putInt(KEY_INDEX, 0)
                    .putInt(KEY_PERCENT, 0)
                    .build())
                val sizes = uploads.map { upload ->
                    runCatching { applicationContext.contentResolver.openAssetFileDescriptor(upload.uri, "r")?.use { it.length } ?: -1L }.getOrDefault(-1L)
                }
                queueTotalBytes = sizes.filter { it > 0 }.sum()
                val totalKnown = sizes.all { it >= 0 }
                for ((index, upload) in uploads.withIndex()) {
                    workerJob.ensureActive()
                    val previous = journal.find(batchId)?.entries?.getOrNull(index)
                    if (previous?.state == UploadState.SUCCEEDED) { complete += previous.bytes; continue }
                    val uri = upload.uri
                    val itemRemotePath = upload.destinationPath
                    val name = previous?.name ?: displayName(uri)
                    lastFileName = name
                    lastFileBytes = 0L
                    val size = sizes[index]
                    journal.item(batchId, index) { it.copy(state = UploadState.UPLOADING, size = size, error = "") }
                    lastFileSize = size
                    var fileUploadedBytes = 0L
                    setProgress(Data.Builder().putString(KEY_NAME, name).putString(KEY_REMOTE_PATH, itemRemotePath)
                        .putString(KEY_BATCH_ID, batchId).putInt(KEY_INDEX, index + 1).putInt(KEY_COUNT, uploads.size)
                        .putLong(KEY_FILE_SIZE, size).putLong(KEY_FILE_BYTES, 0L).build())
                    setForeground(foreground("${index + 1}/${uploads.size} · $name", 0, 0))
                    var result: kotlin.Result<Unit> = kotlin.Result.failure(IllegalStateException("Unggahan belum dicoba"))
                    for (attempt in 1..3) {
                        val attemptStream = runCatching { applicationContext.contentResolver.openInputStream(uri) }.getOrElse {
                            result = kotlin.Result.failure(it); null
                        }
                        if (attemptStream == null) {
                            if (result.exceptionOrNull()?.message == "Unggahan belum dicoba") result = kotlin.Result.failure(IllegalStateException("File sumber tidak dapat dibuka. Pilih file kembali."))
                            break
                        }
                        var lastReported = 0L
                        var lastReportAt = android.os.SystemClock.elapsedRealtime()
                        result = attemptStream.use {
                            manager.uploadFile(itemRemotePath, name, it, config, totalBytes = size) { fileBytes ->
                                workerJob.ensureActive()
                                fileUploadedBytes = fileBytes
                                lastFileBytes = fileBytes
                                val now = android.os.SystemClock.elapsedRealtime()
                                val shouldReport = now - lastReportAt >= 500 || (size > 0 && fileBytes >= size && fileBytes != lastReported)
                                if (!shouldReport) return@uploadFile
                                lastReported = fileBytes
                                lastReportAt = now
                                lastFileBytes = fileBytes
                                fileUploadedBytes = fileBytes
                                val progress = Data.Builder()
                                    .putString(KEY_NAME, name)
                                    .putLong(KEY_FILE_BYTES, fileBytes)
                                    .putLong(KEY_FILE_SIZE, size)
                                    .putString(KEY_REMOTE_PATH, itemRemotePath)
                                    .putString(KEY_BATCH_ID, batchId)
                                    .putInt(KEY_INDEX, index + 1)
                                    .putInt(KEY_COUNT, uploads.size)
                                    .putInt(KEY_PERCENT, if (queueTotalBytes > 0) (((complete + fileBytes) * 100) / queueTotalBytes).toInt().coerceIn(0, 100) else 0)
                                    .putLong(KEY_DONE_BYTES, complete + fileBytes)
                                    .putLong(KEY_TOTAL_BYTES, if (totalKnown) queueTotalBytes else -1L)
                                    .build()
                                runBlocking(workerJob) {
                                    setProgress(progress)
                                    journal.item(batchId, index) { entry -> entry.copy(bytes = fileBytes) }
                                    setForeground(foreground("${index + 1}/${uploads.size} · $name", progress.getInt(KEY_PERCENT, 0), if (totalKnown) 100 else 0))
                                }
                            }
                        }
                        workerJob.ensureActive()
                        if (result.isSuccess) break
                        if (result.exceptionOrNull() is CancellationException) throw result.exceptionOrNull()!!
                        manager.close()
                        if (result.exceptionOrNull()?.let { !SshConnectionPolicy.canRetry(it) } == true) break
                        if (attempt < 3) kotlinx.coroutines.delay(1_000L * attempt)
                    }
                    if (result.isSuccess) {
                        journal.item(batchId, index) { it.copy(state = UploadState.SUCCEEDED, bytes = size.coerceAtLeast(fileUploadedBytes), error = "") }
                        complete += size.coerceAtLeast(fileUploadedBytes)
                        val percent = if (queueTotalBytes > 0) ((complete * 100) / queueTotalBytes).toInt().coerceIn(0, 100) else 0
                        setProgress(Data.Builder().putString(KEY_NAME, name).putLong(KEY_FILE_BYTES, fileUploadedBytes)
                            .putString(KEY_REMOTE_PATH, itemRemotePath)
                            .putString(KEY_BATCH_ID, batchId)
                            .putLong(KEY_FILE_SIZE, size).putInt(KEY_INDEX, index + 1).putInt(KEY_COUNT, uploads.size)
                            .putInt(KEY_PERCENT, percent).putLong(KEY_DONE_BYTES, complete).putLong(KEY_TOTAL_BYTES, if (totalKnown) queueTotalBytes else -1L).build())
                    } else {
                        failed++
                        journal.item(batchId, index) { it.copy(state = UploadState.FAILED, bytes = fileUploadedBytes,
                            error = result.exceptionOrNull()?.localizedMessage ?: "Unggahan gagal") }
                    }
                }
            } finally {
                cancellationWatcher.cancel()
                manager.close()
            }
            val summary = Data.Builder()
                .putInt(KEY_COUNT, uploads.size)
                .putInt(KEY_FAILED, failed)
                .putString(KEY_BATCH_ID, batchId)
                .putLong(KEY_DONE_BYTES, complete)
                .putLong(KEY_TOTAL_BYTES, queueTotalBytes)
                .putString(KEY_NAME, lastFileName)
                .putLong(KEY_FILE_BYTES, lastFileBytes)
                .putLong(KEY_FILE_SIZE, lastFileSize)
                .putString(KEY_REMOTE_PATH, uploads.last().destinationPath)
                .build()
            Result.success(summary)
        } catch (e: CancellationException) {
            journal.cancelRemaining(batchId)
            throw e
        } catch (e: Exception) {
            journal.update(batchId) { batch -> batch.copy(entries = batch.entries.map {
                if (it.state in listOf(UploadState.QUEUED, UploadState.UPLOADING)) it.copy(state = UploadState.FAILED, error = e.localizedMessage.orEmpty()) else it
            }) }
            Result.failure(Data.Builder()
                .putString("error", e.localizedMessage ?: "Unggahan gagal")
                .putString(KEY_BATCH_ID, batchId)
                .putString(KEY_REMOTE_PATH, remotePath)
                .putInt(KEY_FAILED, 1)
                .build())
        } finally {
            queueFile.delete()
            journal.close()
        }
    }

    private data class UploadItem(val uri: Uri, val destinationPath: String)

    private fun normalizeRemotePath(path: String): String {
        val trimmed = path.trim()
        if (trimmed.isBlank() || trimmed == "/") return "/"
        return "/${trimmed.trim('/')}"
    }

    private fun displayName(uri: Uri): String {
        applicationContext.contentResolver.query(uri, arrayOf(android.provider.OpenableColumns.DISPLAY_NAME), null, null, null)?.use { c ->
            if (c.moveToFirst()) c.getString(0)?.takeIf(String::isNotBlank)?.let { return it }
        }
        return uri.lastPathSegment?.substringAfterLast('/')?.takeIf(String::isNotBlank) ?: "upload-${System.currentTimeMillis()}"
    }

    private fun foreground(text: String, progress: Int, max: Int): ForegroundInfo {
        val notification = TransferNotifications.progress(applicationContext, batchId, id, text, progress, max > 0)
        return if (Build.VERSION.SDK_INT >= 29) {
            ForegroundInfo(id.hashCode() and Int.MAX_VALUE, notification, android.content.pm.ServiceInfo.FOREGROUND_SERVICE_TYPE_DATA_SYNC)
        } else ForegroundInfo(id.hashCode() and Int.MAX_VALUE, notification)
    }

    companion object {
        const val TAG = "sftp_upload"
        const val KEY_QUEUE = "queue_file"
        const val KEY_PROFILE = "profile_id"
        const val KEY_REMOTE_PATH = "remote_path"
        const val KEY_BATCH_ID = "batch_id"
        const val KEY_NAME = "file_name"
        const val KEY_FILE_BYTES = "file_bytes"
        const val KEY_FILE_SIZE = "file_size"
        const val KEY_INDEX = "file_index"
        const val KEY_COUNT = "file_count"
        const val KEY_PERCENT = "percent"
        const val KEY_DONE_BYTES = "done_bytes"
        const val KEY_TOTAL_BYTES = "total_bytes"
        const val KEY_FAILED = "failed_count"
        fun formatBytes(bytes: Long): String = when {
            bytes < 1024 * 1024 -> String.format(Locale.getDefault(), "%.0f KB", bytes / 1024.0)
            else -> String.format(Locale.getDefault(), "%.1f MB", bytes / (1024.0 * 1024.0))
        }
    }
}
