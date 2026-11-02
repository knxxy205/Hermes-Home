package com.example.data.worker

import android.app.NotificationChannel
import android.app.NotificationManager
import android.content.Context
import android.net.Uri
import android.os.Build
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
            val uris = JSONArray(queueFile.readText()).let { json ->
                (0 until json.length()).map { Uri.parse(json.getString(it)) }
            }
            if (uris.isEmpty()) return@withContext Result.failure()

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
                setForeground(foreground("Mengunggah ${uris.size} file", 0, uris.size))
                val sizes = uris.map { uri ->
                    runCatching { applicationContext.contentResolver.openAssetFileDescriptor(uri, "r")?.use { it.length } ?: -1L }.getOrDefault(-1L)
                }
                queueTotalBytes = sizes.filter { it > 0 }.sum()
                for ((index, uri) in uris.withIndex()) {
                    if (isStopped) return@withContext Result.failure()
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
                            manager.uploadFile(remotePath, name, it, config, totalBytes = size) { fileBytes ->
                                val shouldReport = fileBytes - lastReported >= 512 * 1024 || (size > 0 && fileBytes >= size)
                                if (!shouldReport) return@uploadFile
                                lastReported = fileBytes
                                lastFileBytes = fileBytes
                                fileUploadedBytes = fileBytes
                                val progress = Data.Builder()
                                    .putString(KEY_NAME, name)
                                    .putLong(KEY_FILE_BYTES, fileBytes)
                                    .putLong(KEY_FILE_SIZE, size)
                                    .putInt(KEY_INDEX, index + 1)
                                    .putInt(KEY_COUNT, uris.size)
                                    .putInt(KEY_PERCENT, if (queueTotalBytes > 0) (((complete + fileBytes) * 100) / queueTotalBytes).toInt().coerceIn(0, 100) else 0)
                                    .putLong(KEY_DONE_BYTES, complete + fileBytes)
                                    .putLong(KEY_TOTAL_BYTES, queueTotalBytes)
                                    .build()
                                runBlocking {
                                    setProgress(progress)
                                    setForeground(foreground("${index + 1}/${uris.size} · $name · ${formatBytes(fileBytes)}${if (size > 0) " / ${formatBytes(size)}" else ""}", progress.getInt(KEY_PERCENT, 0), 100))
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
                            .putLong(KEY_FILE_SIZE, size).putInt(KEY_INDEX, index + 1).putInt(KEY_COUNT, uris.size)
                            .putInt(KEY_PERCENT, percent).putLong(KEY_DONE_BYTES, complete).putLong(KEY_TOTAL_BYTES, queueTotalBytes).build())
                    } else failed++
                }
            } finally {
                manager.close()
            }
            val summary = Data.Builder()
                .putInt(KEY_COUNT, uris.size)
                .putInt(KEY_FAILED, failed)
                .putLong(KEY_DONE_BYTES, complete)
                .putLong(KEY_TOTAL_BYTES, queueTotalBytes)
                .putString(KEY_NAME, lastFileName)
                .putLong(KEY_FILE_BYTES, lastFileBytes)
                .putLong(KEY_FILE_SIZE, lastFileSize)
                .putString(KEY_REMOTE_PATH, remotePath)
                .build()
            Result.success(summary)
        } catch (e: Exception) {
            Result.failure(Data.Builder().putString("error", e.localizedMessage ?: "Unggahan gagal").build())
        } finally {
            queueFile.delete()
        }
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

    companion object {
        const val TAG = "sftp_upload"
        const val KEY_QUEUE = "queue_file"
        const val KEY_PROFILE = "profile_id"
        const val KEY_REMOTE_PATH = "remote_path"
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

        fun formatBytes(bytes: Long): String = when {
            bytes < 1024 * 1024 -> String.format(Locale.getDefault(), "%.0f KB", bytes / 1024.0)
            else -> String.format(Locale.getDefault(), "%.1f MB", bytes / (1024.0 * 1024.0))
        }
    }
}
