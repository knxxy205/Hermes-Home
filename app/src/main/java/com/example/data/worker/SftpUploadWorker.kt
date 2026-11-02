package com.example.data.worker

import android.app.NotificationChannel
import android.app.NotificationManager
import android.content.Context
import android.net.Uri
import android.os.Build
import android.content.pm.PackageManager
import androidx.core.app.NotificationCompat
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

class SftpUploadWorker(context: Context, params: WorkerParameters) : CoroutineWorker(context, params) {
    override suspend fun getForegroundInfo(): ForegroundInfo = foreground("Menyiapkan unggahan", 0, 0)

    override suspend fun doWork(): Result = withContext(Dispatchers.IO) {
        val queuePath = inputData.getString(KEY_QUEUE) ?: return@withContext Result.failure()
        val profileId = inputData.getString(KEY_PROFILE) ?: return@withContext Result.failure()
        val remotePath = inputData.getString(KEY_REMOTE_PATH) ?: "/"
        val queueFile = File(queuePath)
        try {
            val uploads = JSONArray(queueFile.readText()).let { json ->
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
                ?: return@withContext Result.failure()
            val config = profile.config
            val manager = SftpManager()
            var complete = 0L
            var failed = 0
            var queueTotalBytes = 0L
            var lastFileName = ""
            var lastFileBytes = 0L
            var lastFileSize = -1L
            try {
                setForeground(foreground("Mengunggah ${uploads.size} file", 0, uploads.size))
                setProgress(Data.Builder()
                    .putString(KEY_REMOTE_PATH, uploads.first().destinationPath)
                    .putString(KEY_BATCH_ID, inputData.getString(KEY_BATCH_ID).orEmpty())
                    .putInt(KEY_COUNT, uploads.size)
                    .putInt(KEY_INDEX, 0)
                    .putInt(KEY_PERCENT, 0)
                    .build())
                val sizes = uploads.map { upload ->
                    runCatching { applicationContext.contentResolver.openAssetFileDescriptor(upload.uri, "r")?.use { it.length } ?: -1L }.getOrDefault(-1L)
                }
                queueTotalBytes = sizes.filter { it > 0 }.sum()
                for ((index, upload) in uploads.withIndex()) {
                    if (isStopped) return@withContext Result.failure()
                    val uri = upload.uri
                    val itemRemotePath = upload.destinationPath
                    val name = displayName(uri)
                    lastFileName = name
                    lastFileBytes = 0L
                    val size = sizes[index]
                    lastFileSize = size
                    var fileUploadedBytes = 0L
                    var result: kotlin.Result<Unit> = kotlin.Result.failure(IllegalStateException("Unggahan belum dicoba"))
                    for (attempt in 1..3) {
                        val attemptStream = applicationContext.contentResolver.openInputStream(uri)
                        if (attemptStream == null) break
                        var lastReported = 0L
                        result = attemptStream.use {
                            manager.uploadFile(itemRemotePath, name, it, config, totalBytes = size) { fileBytes ->
                                val shouldReport = fileBytes - lastReported >= 512 * 1024 || (size > 0 && fileBytes >= size)
                                if (!shouldReport) return@uploadFile
                                lastReported = fileBytes
                                lastFileBytes = fileBytes
                                fileUploadedBytes = fileBytes
                                val progress = Data.Builder()
                                    .putString(KEY_NAME, name)
                                    .putLong(KEY_FILE_BYTES, fileBytes)
                                    .putLong(KEY_FILE_SIZE, size)
                                    .putString(KEY_REMOTE_PATH, itemRemotePath)
                                    .putString(KEY_BATCH_ID, inputData.getString(KEY_BATCH_ID).orEmpty())
                                    .putInt(KEY_INDEX, index + 1)
                                    .putInt(KEY_COUNT, uploads.size)
                                    .putInt(KEY_PERCENT, if (queueTotalBytes > 0) (((complete + fileBytes) * 100) / queueTotalBytes).toInt().coerceIn(0, 100) else 0)
                                    .putLong(KEY_DONE_BYTES, complete + fileBytes)
                                    .putLong(KEY_TOTAL_BYTES, queueTotalBytes)
                                    .build()
                                runBlocking {
                                    setProgress(progress)
                                    setForeground(foreground("${index + 1}/${uploads.size} · $name · ${formatBytes(fileBytes)}${if (size > 0) " / ${formatBytes(size)}" else ""}", progress.getInt(KEY_PERCENT, 0), 100))
                                }
                            }
                        }
                        if (result.isSuccess) break
                        manager.close()
                        if (attempt < 3) kotlinx.coroutines.delay(1_000L * attempt)
                    }
                    if (result.isSuccess) {
                        complete += size.coerceAtLeast(fileUploadedBytes)
                        val percent = if (queueTotalBytes > 0) ((complete * 100) / queueTotalBytes).toInt().coerceIn(0, 100) else 0
                        setProgress(Data.Builder().putString(KEY_NAME, name).putLong(KEY_FILE_BYTES, complete)
                            .putString(KEY_REMOTE_PATH, itemRemotePath)
                            .putString(KEY_BATCH_ID, inputData.getString(KEY_BATCH_ID).orEmpty())
                            .putLong(KEY_FILE_SIZE, size).putInt(KEY_INDEX, index + 1).putInt(KEY_COUNT, uploads.size)
                            .putInt(KEY_PERCENT, percent).putLong(KEY_DONE_BYTES, complete).putLong(KEY_TOTAL_BYTES, queueTotalBytes).build())
                    } else failed++
                }
            } finally {
                manager.close()
            }
            val summary = Data.Builder()
                .putInt(KEY_COUNT, uploads.size)
                .putInt(KEY_FAILED, failed)
                .putString(KEY_BATCH_ID, inputData.getString(KEY_BATCH_ID).orEmpty())
                .putLong(KEY_DONE_BYTES, complete)
                .putLong(KEY_TOTAL_BYTES, queueTotalBytes)
                .putString(KEY_NAME, lastFileName)
                .putLong(KEY_FILE_BYTES, lastFileBytes)
                .putLong(KEY_FILE_SIZE, lastFileSize)
                .putString(KEY_REMOTE_PATH, uploads.last().destinationPath)
                .build()
            notifyUploadFinished(uploads.size - failed, failed, complete, uploads.last().destinationPath)
            Result.success(summary)
        } catch (e: Exception) {
            notifyUploadFinished(0, 1, 0L, remotePath)
            Result.failure(Data.Builder()
                .putString("error", e.localizedMessage ?: "Unggahan gagal")
                .putString(KEY_BATCH_ID, inputData.getString(KEY_BATCH_ID).orEmpty())
                .putString(KEY_REMOTE_PATH, remotePath)
                .putInt(KEY_FAILED, 1)
                .build())
        } finally {
            queueFile.delete()
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

    private fun formatBytes(bytes: Long): String = when {
        bytes < 1024 * 1024 -> String.format(Locale.getDefault(), "%.0f KB", bytes / 1024.0)
        else -> String.format(Locale.getDefault(), "%.1f MB", bytes / (1024.0 * 1024.0))
    }

    private fun foreground(text: String, progress: Int, max: Int): ForegroundInfo {
        val manager = applicationContext.getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager
        if (Build.VERSION.SDK_INT >= 26) {
            manager.createNotificationChannel(NotificationChannel(CHANNEL, "Unggahan SFTP", NotificationManager.IMPORTANCE_LOW))
        }
        val notification = NotificationCompat.Builder(applicationContext, CHANNEL)
            .setSmallIcon(android.R.drawable.stat_sys_upload)
            .setContentTitle("TailHarness · Upload")
            .setContentText(text)
            .setOngoing(max > 0)
            .setOnlyAlertOnce(true)
            .setProgress(max, progress.coerceIn(0, max.coerceAtLeast(1)), max == 0)
            .build()
        return if (Build.VERSION.SDK_INT >= 29) {
            ForegroundInfo(NOTIFICATION_ID, notification, android.content.pm.ServiceInfo.FOREGROUND_SERVICE_TYPE_DATA_SYNC)
        } else ForegroundInfo(NOTIFICATION_ID, notification)
    }

    private fun notifyUploadFinished(successCount: Int, failedCount: Int, bytes: Long, remotePath: String) {
        if (Build.VERSION.SDK_INT >= 33 &&
            applicationContext.checkSelfPermission(android.Manifest.permission.POST_NOTIFICATIONS) != PackageManager.PERMISSION_GRANTED
        ) return

        val manager = applicationContext.getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager
        if (Build.VERSION.SDK_INT >= 26) {
            manager.createNotificationChannel(
                NotificationChannel(CHANNEL, "Unggahan SFTP", NotificationManager.IMPORTANCE_DEFAULT)
            )
        }
        val title = if (failedCount == 0) "Upload selesai" else "Upload selesai dengan kendala"
        val content = "$successCount berhasil · $failedCount gagal · ${formatBytes(bytes)} ke $remotePath"
        val notification = NotificationCompat.Builder(applicationContext, CHANNEL)
            .setSmallIcon(android.R.drawable.stat_sys_upload_done)
            .setContentTitle(title)
            .setContentText(content)
            .setStyle(NotificationCompat.BigTextStyle().bigText(content))
            .setAutoCancel(true)
            .setOngoing(false)
            .setOnlyAlertOnce(false)
            .build()
        manager.notify(COMPLETION_NOTIFICATION_ID, notification)
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
        private const val CHANNEL = "sftp_uploads"
        private const val NOTIFICATION_ID = 7402
        private const val COMPLETION_NOTIFICATION_ID = 7403

        fun formatBytes(bytes: Long): String = when {
            bytes < 1024 * 1024 -> String.format(Locale.getDefault(), "%.0f KB", bytes / 1024.0)
            else -> String.format(Locale.getDefault(), "%.1f MB", bytes / (1024.0 * 1024.0))
        }
    }
}
