package com.example

import android.app.NotificationManager
import android.content.Context
import android.net.Uri
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import androidx.work.WorkInfo
import androidx.work.WorkManager
import com.example.data.model.ServerConfig
import com.example.data.security.EncryptedStorage
import com.example.data.security.SshTrust
import com.example.data.sftp.SftpManager
import com.example.data.transfer.TransferStore
import com.example.data.transfer.UploadQueue
import com.example.data.transfer.UploadState
import com.example.data.transfer.UploadBatch
import com.example.data.transfer.UploadEntry
import com.example.data.transfer.TransferNotifications
import com.example.data.transfer.TransferNavigation
import androidx.work.OneTimeWorkRequestBuilder
import androidx.work.workDataOf
import com.example.data.worker.SftpUploadWorker
import kotlinx.coroutines.*
import org.junit.Assert.*
import org.junit.Test
import org.junit.runner.RunWith
import java.io.ByteArrayOutputStream
import java.io.File
import java.util.UUID

@RunWith(AndroidJUnit4::class)
class UploadRegressionTest {
    private val context = InstrumentationRegistry.getInstrumentation().targetContext
    private val config = ServerConfig("10.0.2.2", 8029, "fixture", "daily-test-password")

    @Test fun uploadTwoFoldersAndRetryFailureWithoutMixingBatches() = runBlocking {
        check(context.packageName.endsWith(".preview"))
        SshTrust.initialize(context)
        val trust = launch(Dispatchers.Default) { SshTrust.pending.collect { it?.answer?.complete(true) } }
        val storage = EncryptedStorage(context)
        storage.saveConfig(config)
        val manager = SftpManager()
        val notifications = context.getSystemService(NotificationManager::class.java)
        val first = File(context.filesDir, "photo-${UUID.randomUUID()}.jpg").apply {
            writeBytes(ByteArray(768 * 1024) { (it % 251).toByte() })
        }
        val second = File(context.filesDir, "file-${UUID.randomUUID()}.txt").apply { writeText("second folder\n") }
        try {
            TransferStore(context).use { store ->
                val one = withContext(Dispatchers.IO) { UploadQueue.enqueue(context, listOf(Uri.fromFile(first)), storage.getActiveProfileId(), "/uploads") }
                val two = withContext(Dispatchers.IO) { UploadQueue.enqueue(context, listOf(Uri.fromFile(second)), storage.getActiveProfileId(), "/") }
                awaitFinished(store, one)
                awaitFinished(store, two)
                assertEquals(store.find(one).toString(), 1, store.find(one)!!.succeeded)
                assertEquals(store.find(two).toString(), 1, store.find(two)!!.succeeded)
                val twoInfo = WorkManager.getInstance(context).getWorkInfoById(UUID.fromString(store.find(two)!!.workId)).get()!!
                assertEquals(two, twoInfo.outputData.getString(SftpUploadWorker.KEY_BATCH_ID))
                assertEquals(second.length(), twoInfo.outputData.getLong(SftpUploadWorker.KEY_DONE_BYTES, -1))
                val bytes = ByteArrayOutputStream()
                manager.downloadToStream("/uploads/${first.name}", bytes, config).getOrThrow()
                assertArrayEquals(first.readBytes(), bytes.toByteArray())
                val text = ByteArrayOutputStream()
                manager.downloadToStream("/${second.name}", text, config).getOrThrow()
                assertEquals("second folder\n", text.toString("UTF-8"))
                assertFalse(manager.listFiles("/uploads", config).getOrThrow().any { it.name == second.name })
                withTimeout(10_000) {
                    while (notifications.activeNotifications.any { it.id == UUID.fromString(store.find(one)!!.workId).hashCode().and(Int.MAX_VALUE) || it.id == UUID.fromString(store.find(two)!!.workId).hashCode().and(Int.MAX_VALUE) }) delay(100)
                }
                val failed = withContext(Dispatchers.IO) { UploadQueue.enqueue(context, listOf(Uri.fromFile(second)), storage.getActiveProfileId(), "/missing-folder") }
                awaitFinished(store, failed)
                assertEquals(1, store.find(failed)!!.failed)
                val after = withContext(Dispatchers.IO) { UploadQueue.enqueue(context, listOf(Uri.fromFile(second)), storage.getActiveProfileId(), "/uploads") }
                awaitFinished(store, after)
                assertEquals(store.find(after).toString(), 1, store.find(after)!!.succeeded)
                val retryId = withContext(Dispatchers.IO) { UploadQueue.retry(context, failed) }
                awaitFinished(store, retryId)
                assertEquals(1, store.find(retryId)!!.failed)
                assertEquals("/missing-folder", store.find(retryId)!!.entries.single().destination)
                withTimeout(10_000) {
                    while (notifications.activeNotifications.count { it.notification.channelId.startsWith("sftp_upload") } != 1) delay(100)
                }
                val result = notifications.activeNotifications.single { it.notification.channelId.startsWith("sftp_upload") }
                assertFalse(result.isOngoing)
                assertNotNull(result.notification.contentIntent)
            }
        } finally {
            manager.close()
            first.delete(); second.delete()
            trust.cancelAndJoin()
        }
    }

    @Test fun cancellationFromNotificationDoesNotCancelFollowingUpload() = runBlocking {
        SshTrust.initialize(context)
        File(context.noBackupFilesDir, "known_hosts").writeText("")
        val storage = EncryptedStorage(context).apply { saveConfig(config) }
        val file = File(context.filesDir, "cancel-${UUID.randomUUID()}.txt").apply { writeText("queued source") }
        val wm = WorkManager.getInstance(context)
        val notifications = context.getSystemService(NotificationManager::class.java)
        var trust: Job? = null
        try {
            TransferStore(context).use { store ->
                val first = withContext(Dispatchers.IO) { UploadQueue.enqueue(context, listOf(Uri.fromFile(file)), storage.getActiveProfileId(), "/uploads") }
                withTimeout(30_000) { while (SshTrust.pending.value == null) delay(100) }
                val second = withContext(Dispatchers.IO) { UploadQueue.enqueue(context, listOf(Uri.fromFile(file)), storage.getActiveProfileId(), "/") }
                withTimeout(10_000) { while (notifications.activeNotifications.none { it.isOngoing && it.notification.channelId.startsWith("sftp_upload") }) delay(100) }
                assertEquals(1, notifications.activeNotifications.count { it.notification.channelId.startsWith("sftp_upload") })
                val active = notifications.activeNotifications.single { it.isOngoing && it.notification.channelId.startsWith("sftp_upload") }
                active.notification.actions.single().actionIntent.send()
                withTimeout(10_000) { while (wm.getWorkInfoById(UUID.fromString(store.find(first)!!.workId)).get()!!.state != WorkInfo.State.CANCELLED) delay(100) }
                trust = launch(Dispatchers.Default) { SshTrust.pending.collect { it?.answer?.complete(true) } }
                awaitFinished(store, first)
                awaitFinished(store, second)
                assertEquals(UploadState.CANCELLED, store.find(first)!!.entries.single().state)
                assertEquals(store.find(second).toString(), 1, store.find(second)!!.succeeded)
            }
        } finally {
            trust?.cancelAndJoin()
            SshTrust.pending.value?.answer?.complete(false)
            file.delete()
        }
    }

    @Test fun legacyMergedInputUsesOwnTagAndDestination() = runBlocking {
        SshTrust.initialize(context)
        val trust = launch(Dispatchers.Default) { SshTrust.pending.collect { it?.answer?.complete(true) } }
        val storage = EncryptedStorage(context).apply { saveConfig(config) }
        val source = File(context.filesDir, "legacy-${UUID.randomUUID()}.jpg").apply { writeBytes(ByteArray(257) { it.toByte() }) }
        val batchId = UUID.randomUUID().toString()
        val request = OneTimeWorkRequestBuilder<SftpUploadWorker>().setInputData(workDataOf(
            SftpUploadWorker.KEY_BATCH_ID to "previous-upload-result",
            SftpUploadWorker.KEY_PROFILE to "previous-upload-profile",
            SftpUploadWorker.KEY_REMOTE_PATH to "/previous-folder"
        )).addTag(SftpUploadWorker.TAG).addTag("${SftpUploadWorker.TAG}:$batchId").build()
        val manager = SftpManager()
        try {
            TransferStore(context).use { store ->
                store.add(UploadBatch(batchId, request.id.toString(), storage.getActiveProfileId(), "Fixture",
                    entries = listOf(UploadEntry(Uri.fromFile(source).toString(), source.name, "/uploads", source.length())),
                    host = config.sshHost, port = config.sshPort, user = config.sshUser))
                WorkManager.getInstance(context).enqueue(request).result.get()
                awaitFinished(store, batchId)
                assertEquals(store.find(batchId).toString(), 1, store.find(batchId)!!.succeeded)
                val data = WorkManager.getInstance(context).getWorkInfoById(request.id).get()!!.outputData
                assertEquals(batchId, data.getString(SftpUploadWorker.KEY_BATCH_ID))
                assertEquals("/uploads", data.getString(SftpUploadWorker.KEY_REMOTE_PATH))
                val downloaded = ByteArrayOutputStream()
                manager.downloadToStream("/uploads/${source.name}", downloaded, config).getOrThrow()
                assertArrayEquals(source.readBytes(), downloaded.toByteArray())
            }
        } finally { manager.close(); trust.cancelAndJoin(); source.delete() }
    }

    @Test fun clearingFinishedHistoryKeepsSourceUsedByActiveRetry() {
        val source = File(File(context.filesDir, "upload_sources").apply { mkdirs() }, UUID.randomUUID().toString()).apply { writeText("retry source") }
        val entry = UploadEntry(Uri.fromFile(source).toString(), "photo.jpg", "/uploads", state = UploadState.FAILED)
        TransferStore(context).use { store ->
            val old = UUID.randomUUID().toString()
            val retry = UUID.randomUUID().toString()
            store.add(UploadBatch(old, UUID.randomUUID().toString(), "fixture", "Fixture", entries = listOf(entry)))
            store.add(UploadBatch(retry, UUID.randomUUID().toString(), "fixture", "Fixture", entries = listOf(entry.copy(state = UploadState.QUEUED))))
            store.clearFinished()
            assertTrue(source.exists())
            store.cancelRemaining(retry)
            store.clearFinished()
            assertFalse(source.exists())
        }
    }

    @Test fun openingCompletionDismissesItAndPreventsLateReposting() = runBlocking {
        val notifications = context.getSystemService(NotificationManager::class.java)
        val first = UploadBatch(UUID.randomUUID().toString(), UUID.randomUUID().toString(), "fixture", "Fixture",
            entries = listOf(UploadEntry("content://fixture/photo", "photo.jpg", "/uploads", 12, UploadState.SUCCEEDED, 12)))
        val second = first.copy(id = UUID.randomUUID().toString(), workId = UUID.randomUUID().toString())
        fun posted(id: String) = notifications.activeNotifications.firstOrNull { it.notification.extras.getString(TransferNavigation.EXTRA_BATCH) == id }
        suspend fun waitFor(label: String, condition: () -> Boolean) {
            try { withTimeout(10_000) { while (!condition()) delay(50) } }
            catch (error: TimeoutCancellationException) {
                throw AssertionError("Timed out: $label; active=" + notifications.activeNotifications.map {
                    "${it.id}:${it.notification.extras.getString(TransferNavigation.EXTRA_BATCH)}"
                }, error)
            }
        }
        TransferStore(context).use { store ->
            store.add(first); store.add(second)
            var scenario: androidx.test.core.app.ActivityScenario<MainActivity>? = null
            var activity: MainActivity? = null
            val launchIntent = android.content.Intent(context, MainActivity::class.java).apply {
                flags = android.content.Intent.FLAG_ACTIVITY_NEW_TASK or android.content.Intent.FLAG_ACTIVITY_CLEAR_TASK
            }
            try {
                scenario = androidx.test.core.app.ActivityScenario.launch<MainActivity>(launchIntent)
                waitFor("activity visible") {
                    var visible = false
                    scenario.onActivity { activity = it; visible = it.window.decorView.isShown && it.hasWindowFocus() }
                    visible
                }
                TransferNotifications.finished(context, first)
                waitFor("first posted: ${first.id}") { posted(first.id) != null }
                val tap = posted(first.id)!!.notification.contentIntent
                tap.send()
                waitFor("first opened") { context.getSharedPreferences("hermes_notification_state", Context.MODE_PRIVATE).getStringSet("opened_batches", emptySet()).orEmpty().contains(first.id) }
                waitFor("first dismissed") { posted(first.id) == null }
                TransferNotifications.finished(context, first)
                assertNull(posted(first.id))
                TransferNotifications.finished(context, second)
                waitFor("second posted") { posted(second.id) != null }
                InstrumentationRegistry.getInstrumentation().runOnMainSync { TransferNavigation.consumed() }
                tap.send()
                waitFor("older result accepted") { TransferNavigation.destination.value?.batchId == first.id }
                assertNotNull("Opening an older result must preserve the newer notification", posted(second.id))
                posted(second.id)!!.notification.actions.single().actionIntent.send()
                waitFor("second opened") { context.getSharedPreferences("hermes_notification_state", Context.MODE_PRIVATE).getStringSet("opened_batches", emptySet()).orEmpty().contains(second.id) }
                waitFor("second dismissed") { posted(second.id) == null }
                TransferNotifications.finished(context, second)
                assertNull(posted(second.id))
            } finally {
                store.remove(first.id); store.remove(second.id)
                InstrumentationRegistry.getInstrumentation().runOnMainSync {
                    TransferNavigation.consumed()
                    // ActivityScenario matches lifecycle events against its original intent.
                    activity?.intent = launchIntent
                }
                scenario?.close()
            }
        }
    }

    private suspend fun awaitFinished(store: TransferStore, id: String) {
        withTimeout(60_000) {
            val workId = UUID.fromString(store.find(id)!!.workId)
            while (true) {
                val work = withContext(Dispatchers.IO) { WorkManager.getInstance(context).getWorkInfoById(workId).get() }
                if (work != null && work.state.isFinished) {
                    if (store.find(id)?.active == false) return@withTimeout
                }
                delay(100)
            }
        }
    }
}
