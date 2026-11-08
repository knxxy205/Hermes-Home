package com.example.data.transfer

import android.Manifest
import android.app.*
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.os.Build
import androidx.core.app.NotificationCompat
import androidx.work.WorkManager
import com.example.MainActivity
import com.example.R
import java.util.UUID

object TransferNotifications {
    private const val PROGRESS = "sftp_upload_progress_v2"
    private const val RESULTS = "sftp_upload_results_v2"
    private const val RESULT_ID = 7403
    private const val MARKER = "hermes_upload"

    @Synchronized fun opened(context: Context, batchId: String) {
        val prefs = context.applicationContext.getSharedPreferences("hermes_notification_state", Context.MODE_PRIVATE)
        val opened = LinkedHashSet(prefs.getStringSet("opened_batches", emptySet()).orEmpty())
        opened.remove(batchId)
        opened.add(batchId)
        prefs.edit().putStringSet("opened_batches", opened.toList().takeLast(256).toSet()).apply()
        val manager = context.getSystemService(NotificationManager::class.java)
        manager.activeNotifications.filter { notification ->
            val id = notification.notification.extras.getString(TransferNavigation.EXTRA_BATCH)
            !notification.isOngoing && (id == batchId || notification.tag == batchId || (id == null && notification.id == RESULT_ID))
        }.forEach { manager.cancel(it.tag, it.id) }
    }

    fun clearStale(context: Context, running: Set<UUID>) {
        val manager = context.getSystemService(NotificationManager::class.java)
        val activeIds = running.map { it.hashCode() and Int.MAX_VALUE }.toSet()
        manager.activeNotifications.filter { notification ->
            val channel = if (Build.VERSION.SDK_INT >= 26) notification.notification.channelId else null
            val marked = notification.notification.extras.getBoolean(MARKER, false)
            val progress = channel == PROGRESS || (marked && notification.isOngoing)
            val result = channel == RESULTS || (marked && !notification.isOngoing)
            (progress && notification.id !in activeIds) || channel == "sftp_uploads" ||
                (result && (notification.tag != null || notification.id != RESULT_ID || running.isNotEmpty()))
        }.forEach { manager.cancel(it.tag, it.id) }
    }

    fun starting(context: Context, workId: UUID) {
        clearStale(context, setOf(workId))
    }

    private fun channels(context: Context) {
        if (Build.VERSION.SDK_INT >= 26) context.getSystemService(NotificationManager::class.java).apply {
            createNotificationChannel(NotificationChannel(PROGRESS, context.getString(R.string.uploads_running), NotificationManager.IMPORTANCE_LOW))
            createNotificationChannel(NotificationChannel(RESULTS, context.getString(R.string.upload_results), NotificationManager.IMPORTANCE_DEFAULT))
        }
    }
    fun destination(context: Context, id: String, folder: Boolean = false): PendingIntent = PendingIntent.getActivity(context,
        (id + folder).hashCode(), Intent(context, MainActivity::class.java).apply {
            flags = Intent.FLAG_ACTIVITY_SINGLE_TOP or Intent.FLAG_ACTIVITY_CLEAR_TOP
            data = android.net.Uri.parse("hermes://transfer/$id?folder=$folder")
            putExtra(TransferNavigation.EXTRA_BATCH, id); putExtra(TransferNavigation.EXTRA_FOLDER, folder)
        }, PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE)

    fun progress(rawContext: Context, id: String, workId: UUID, text: String, percent: Int, determinate: Boolean): Notification {
        val context = com.example.ui.i18n.AppLanguage.context(rawContext)
        channels(context)
        return NotificationCompat.Builder(context, PROGRESS).setSmallIcon(android.R.drawable.stat_sys_upload)
            .addExtras(android.os.Bundle().apply { putBoolean(MARKER, true); putString(TransferNavigation.EXTRA_BATCH, id) })
            .setContentTitle(context.getString(R.string.uploads_running)).setContentText(text)
            .setContentIntent(destination(context, id)).setOngoing(true).setOnlyAlertOnce(true)
            .setProgress(100, percent.coerceIn(0, 100), !determinate)
            .setVisibility(NotificationCompat.VISIBILITY_PRIVATE)
            .addAction(0, context.getString(R.string.cancel_upload), WorkManager.getInstance(context).createCancelPendingIntent(workId))
            .build()
    }

    @Synchronized fun finished(rawContext: Context, batch: UploadBatch) {
        val context = com.example.ui.i18n.AppLanguage.context(rawContext)
        batch.workId.let { runCatching { UUID.fromString(it) }.getOrNull() }?.let { workId ->
            context.getSystemService(NotificationManager::class.java).cancel(workId.hashCode() and Int.MAX_VALUE)
        }
        if (context.getSharedPreferences("hermes_notification_state", Context.MODE_PRIVATE)
                .getStringSet("opened_batches", emptySet()).orEmpty().contains(batch.id)) return
        if (Build.VERSION.SDK_INT >= 33 && context.checkSelfPermission(Manifest.permission.POST_NOTIFICATIONS) != PackageManager.PERMISSION_GRANTED) return
        channels(context)
        val title = context.getString(when {
            batch.cancelled > 0 -> R.string.upload_cancelled
            batch.failed > 0 && batch.succeeded == 0 -> R.string.upload_failed
            batch.failed > 0 -> R.string.upload_partial
            else -> R.string.upload_complete
        })
        val summary = context.getString(R.string.upload_summary_short, batch.succeeded, batch.entries.size)
        val notification = NotificationCompat.Builder(context, RESULTS).setSmallIcon(android.R.drawable.stat_sys_upload_done)
            .addExtras(android.os.Bundle().apply { putBoolean(MARKER, true); putString(TransferNavigation.EXTRA_BATCH, batch.id) })
            .setContentTitle(title).setContentText(summary)
            .setContentIntent(destination(context, batch.id)).setAutoCancel(true)
            .setOnlyAlertOnce(true).setVisibility(NotificationCompat.VISIBILITY_PRIVATE)
            .addAction(0, context.getString(R.string.open_folder), destination(context, batch.id, true))
            .build()
        context.getSystemService(NotificationManager::class.java).let { manager ->
            manager.activeNotifications.filter {
                val channel = if (Build.VERSION.SDK_INT >= 26) it.notification.channelId else null
                channel == RESULTS || channel == "sftp_uploads" || (it.id == RESULT_ID && !it.isOngoing)
            }
                .forEach { manager.cancel(it.tag, it.id) }
            manager.notify(RESULT_ID, notification)
        }
    }
}
