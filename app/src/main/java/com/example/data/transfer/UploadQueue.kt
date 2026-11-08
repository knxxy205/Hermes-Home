package com.example.data.transfer

import android.content.Context
import android.content.Intent
import android.net.Uri
import android.provider.OpenableColumns
import androidx.work.*
import com.example.data.security.EncryptedStorage
import com.example.data.worker.SftpUploadWorker
import org.json.JSONArray
import org.json.JSONObject
import java.io.File
import java.util.UUID

object UploadQueue {
    fun enqueue(context: Context, uris: List<Uri>, profileId: String, destination: String): String {
        val profile = EncryptedStorage(context).loadProfiles().firstOrNull { it.id == profileId }
            ?: error(context.getString(com.example.R.string.profile_missing))
        require(profile.config.isSshConfigured) { "Konfigurasi SSH belum lengkap" }
        val entries = mutableListOf<UploadEntry>()
        try {
            uris.forEach { uri ->
                var name = uri.lastPathSegment?.substringAfterLast('/') ?: "upload"
                var size = -1L
                runCatching {
                    context.contentResolver.query(uri, arrayOf(OpenableColumns.DISPLAY_NAME, OpenableColumns.SIZE), null, null, null)?.use { cursor ->
                        if (cursor.moveToFirst()) {
                            val nameColumn = cursor.getColumnIndex(OpenableColumns.DISPLAY_NAME)
                            val sizeColumn = cursor.getColumnIndex(OpenableColumns.SIZE)
                            if (nameColumn >= 0) name = cursor.getString(nameColumn)?.takeIf { it.isNotBlank() } ?: name
                            if (sizeColumn >= 0 && !cursor.isNull(sizeColumn)) size = cursor.getLong(sizeColumn)
                        }
                    }
                }
                val readableUri = try {
                    context.contentResolver.takePersistableUriPermission(uri, Intent.FLAG_GRANT_READ_URI_PERMISSION)
                    uri
                } catch (_: Exception) {
                    val folder = File(context.filesDir, "upload_sources").apply { mkdirs() }
                    val staged = File(folder, UUID.randomUUID().toString())
                    try {
                        val input = context.contentResolver.openInputStream(uri) ?: error("$name: sumber file tidak dapat dibuka")
                        input.use { source -> staged.outputStream().use { target -> source.copyTo(target) } }
                        size = staged.length()
                        Uri.fromFile(staged)
                    } catch (error: Exception) {
                        staged.delete()
                        throw error
                    }
                }
                entries += UploadEntry(readableUri.toString(), name, destination, size)
            }
        } catch (error: Exception) {
            deleteStagedSources(context, entries)
            throw error
        }
        return try { enqueueEntries(context, entries, profileId) }
        catch (error: Exception) {
            deleteStagedSources(context, entries)
            throw error
        }
    }

    fun retry(context: Context, id: String): String {
        val batch = TransferStore(context).use { requireNotNull(it.find(id)) { "Transfer tidak ditemukan" } }
        require(!batch.active) { "Transfer masih berjalan" }
        require(batch.retryable.isNotEmpty()) { "Tidak ada file untuk dicoba ulang" }
        val profile = EncryptedStorage(context).loadProfiles().firstOrNull { it.id == batch.profileId }
            ?: error(context.getString(com.example.R.string.profile_missing))
        require(batch.host.isBlank() || (profile.config.sshHost == batch.host && profile.config.sshPort == batch.port && profile.config.sshUser == batch.user)) {
            context.getString(com.example.R.string.upload_target_changed)
        }
        return enqueueEntries(context, batch.retryable.map { entry ->
            runCatching { context.contentResolver.openInputStream(Uri.parse(entry.uri))?.use { } ?: error("missing") }
                .getOrElse { error("${context.getString(com.example.R.string.choose_source_files)}: ${entry.name}") }
            entry.copy(state = UploadState.QUEUED, bytes = 0, error = "")
        }, batch.profileId)
    }

    private fun enqueueEntries(context: Context, entries: List<UploadEntry>, profileId: String): String {
        require(entries.isNotEmpty())
        require(entries.distinctBy { it.destination to it.name }.size == entries.size) { "Ada file dengan nama tujuan yang sama. Pilih file dengan nama berbeda atau unggah secara terpisah." }
        val profile = EncryptedStorage(context).loadProfiles().firstOrNull { it.id == profileId }
            ?: error("Profil server tidak tersedia")
        require(profile.config.isSshConfigured) { "Konfigurasi SSH belum lengkap" }
        val batchId = UUID.randomUUID().toString()
        val queueFile = File(context.filesDir, "sftp-$batchId.json")
        queueFile.writeText(JSONArray().apply { entries.forEach { entry -> put(JSONObject().apply {
            put("uri", entry.uri); put("destinationPath", entry.destination)
        }) } }.toString())
        val request = OneTimeWorkRequestBuilder<SftpUploadWorker>().setInputData(workDataOf(
            SftpUploadWorker.KEY_QUEUE to queueFile.absolutePath, SftpUploadWorker.KEY_PROFILE to profileId,
            SftpUploadWorker.KEY_REMOTE_PATH to entries.first().destination, SftpUploadWorker.KEY_BATCH_ID to batchId))
            .addTag(SftpUploadWorker.TAG).addTag("${SftpUploadWorker.TAG}:$batchId").build()
        TransferStore(context).use { it.add(UploadBatch(batchId, request.id.toString(), profileId, profile.name, entries = entries,
            host = profile.config.sshHost, port = profile.config.sshPort, user = profile.config.sshUser)) }
        try {
            WorkManager.getInstance(context).enqueueUniqueWork("sftp_upload_$batchId", ExistingWorkPolicy.KEEP, request).result.get()
        } catch (error: Exception) {
            TransferStore(context).use { it.remove(batchId) }
            queueFile.delete()
            throw error
        }
        return batchId
    }

    private fun deleteStagedSources(context: Context, entries: List<UploadEntry>) {
        val root = File(context.filesDir, "upload_sources").canonicalFile
        entries.forEach { entry ->
            val uri = Uri.parse(entry.uri)
            if (uri.scheme == "file") runCatching {
                File(uri.path.orEmpty()).canonicalFile.takeIf { it.parentFile == root }?.delete()
            }
        }
    }
}
