package com.example.data.transfer

import android.content.Context
import android.content.SharedPreferences
import com.example.data.security.EncryptedStorage
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.launch
import org.json.JSONArray
import org.json.JSONObject

enum class UploadState { QUEUED, UPLOADING, SUCCEEDED, FAILED, CANCELLED }

data class UploadEntry(
    val uri: String, val name: String, val destination: String, val size: Long = -1,
    val state: UploadState = UploadState.QUEUED, val bytes: Long = 0, val error: String = ""
)

data class UploadBatch(
    val id: String, val workId: String, val profileId: String, val profileName: String,
    val createdAt: Long = System.currentTimeMillis(), val entries: List<UploadEntry>,
    val host: String = "", val port: Int = 22, val user: String = ""
) {
    val active get() = entries.any { it.state == UploadState.QUEUED || it.state == UploadState.UPLOADING }
    val succeeded get() = entries.count { it.state == UploadState.SUCCEEDED }
    val failed get() = entries.count { it.state == UploadState.FAILED }
    val cancelled get() = entries.count { it.state == UploadState.CANCELLED }
    val completedBytes get() = entries.filter { it.state == UploadState.SUCCEEDED }.sumOf { it.bytes }
    val retryable get() = entries.filter { it.state == UploadState.FAILED || it.state == UploadState.CANCELLED }
}

internal object TransferCodec {
    fun encode(batches: List<UploadBatch>): String = JSONArray().apply {
        batches.forEach { batch -> put(JSONObject().apply {
            put("id", batch.id); put("workId", batch.workId); put("profileId", batch.profileId)
            put("profileName", batch.profileName); put("createdAt", batch.createdAt)
            put("host", batch.host); put("port", batch.port); put("user", batch.user)
            put("entries", JSONArray().apply { batch.entries.forEach { entry -> put(JSONObject().apply {
                put("uri", entry.uri); put("name", entry.name); put("destination", entry.destination)
                put("size", entry.size); put("state", entry.state.name); put("bytes", entry.bytes); put("error", entry.error)
            }) } })
        }) }
    }.toString()

    fun decode(raw: String): List<UploadBatch> {
        if (raw.isBlank()) return emptyList()
        return runCatching {
            val array = JSONArray(raw)
            (0 until array.length()).map { index ->
                val batch = array.getJSONObject(index)
                val entries = batch.getJSONArray("entries")
                UploadBatch(batch.getString("id"), batch.getString("workId"), batch.getString("profileId"),
                    batch.getString("profileName"), batch.getLong("createdAt"), (0 until entries.length()).map { i ->
                        val entry = entries.getJSONObject(i)
                        UploadEntry(entry.getString("uri"), entry.getString("name"), entry.getString("destination"),
                            entry.optLong("size", -1), UploadState.valueOf(entry.getString("state")), entry.optLong("bytes"), entry.optString("error"))
                    }, batch.optString("host"), batch.optInt("port", 22), batch.optString("user"))
            }
        }.getOrDefault(emptyList())
    }
}

class TransferStore(context: Context) : AutoCloseable {
    private val uploadSources = java.io.File(context.applicationContext.filesDir, "upload_sources").canonicalFile
    private val prefs = context.applicationContext.getSharedPreferences("hermes_transfer_history", Context.MODE_PRIVATE)
    private val vault = EncryptedStorage(context.applicationContext)
    private val mutable = MutableStateFlow(read())
    val state = mutable.asStateFlow()
    private val listenerScope = CoroutineScope(SupervisorJob() + Dispatchers.IO)
    private val listener = SharedPreferences.OnSharedPreferenceChangeListener { _, key ->
        if (key == "batches") listenerScope.launch { synchronized(lock) { mutable.value = read() } }
    }
    init { prefs.registerOnSharedPreferenceChangeListener(listener) }
    fun read(): List<UploadBatch> = TransferCodec.decode(vault.decrypt(prefs.getString("batches", "").orEmpty()))
    fun find(id: String): UploadBatch? = read().firstOrNull { it.id == id }
    fun add(batch: UploadBatch) = edit { list -> (list.filterNot { it.id == batch.id } + batch).sortedByDescending { it.createdAt } }
    fun update(id: String, transform: (UploadBatch) -> UploadBatch) = edit { list -> list.map { if (it.id == id) transform(it) else it } }
    fun item(id: String, index: Int, transform: (UploadEntry) -> UploadEntry) = update(id) { batch ->
        batch.copy(entries = batch.entries.mapIndexed { i, entry -> if (i == index) transform(entry) else entry })
    }
    fun cancelRemaining(id: String) = update(id, ::cancelUnfinished)
    fun clearFinished() {
        synchronized(lock) {
            val finished = read().filterNot(UploadBatch::active)
            edit { it.filter(UploadBatch::active) }
            finished.flatMap { it.entries }.forEach(::deleteStagedSource)
        }
    }
    fun remove(id: String) {
        val batch = find(id) ?: return
        edit { it.filterNot { current -> current.id == id } }
        batch.entries.forEach(::deleteStagedSource)
    }
    fun finishInterrupted(id: String, cancelled: Boolean, message: String) = update(id) { finishInterrupted(it, cancelled, message) }
    private fun deleteStagedSource(entry: UploadEntry) {
        if (read().any { batch -> batch.entries.any { it.uri == entry.uri } }) return
        val uri = android.net.Uri.parse(entry.uri)
        if (uri.scheme != "file") return
        runCatching {
            val file = java.io.File(uri.path.orEmpty()).canonicalFile
            if (file.parentFile == uploadSources) file.delete()
        }
    }
    private fun edit(transform: (List<UploadBatch>) -> List<UploadBatch>) {
        synchronized(lock) {
            val next = transform(read())
            prefs.edit().putString("batches", vault.encrypt(TransferCodec.encode(next))).apply()
            mutable.value = next
        }
    }
    override fun close() { prefs.unregisterOnSharedPreferenceChangeListener(listener); listenerScope.cancel() }
    companion object { private val lock = Any() }
}

internal fun cancelUnfinished(batch: UploadBatch) = batch.copy(entries = batch.entries.map {
    if (it.state == UploadState.QUEUED || it.state == UploadState.UPLOADING) it.copy(state = UploadState.CANCELLED) else it
})

internal fun finishInterrupted(batch: UploadBatch, cancelled: Boolean, message: String) = batch.copy(entries = batch.entries.map { entry ->
    if (entry.state == UploadState.QUEUED || entry.state == UploadState.UPLOADING) entry.copy(
        state = if (cancelled) UploadState.CANCELLED else UploadState.FAILED,
        error = message
    ) else entry
})

internal fun currentUploadBatch(batches: List<UploadBatch>, profileId: String, path: String): UploadBatch? {
    val relevant = batches.filter { batch -> batch.profileId == profileId && batch.entries.any { it.destination == path } }
        .sortedByDescending { it.createdAt }
    return relevant.firstOrNull { batch -> batch.entries.any { it.state == UploadState.UPLOADING } }
        ?: relevant.firstOrNull { it.active }
}
