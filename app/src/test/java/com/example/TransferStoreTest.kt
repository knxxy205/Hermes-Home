package com.example

import com.example.data.transfer.*
import org.junit.Assert.*
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [28])
class TransferStoreTest {
    @Test fun cancellationKeepsCompletedFilesAndMarksPartialProgressRetryable() {
        val batch = UploadBatch("batch", "work", "profile", "Fixture server", entries = listOf(
            UploadEntry("content://fixture/private-source", "private-file.txt", "/private-destination", 32, UploadState.SUCCEEDED, 32),
            UploadEntry("content://fixture/failed", "failed.txt", "/private-destination", state = UploadState.FAILED, error = "Permission denied"),
            UploadEntry("content://fixture/running", "running.txt", "/private-destination", state = UploadState.UPLOADING, bytes = 12)
        ))
        val saved = cancelUnfinished(batch)
        assertFalse(saved.active)
        assertEquals(1, saved.succeeded)
        assertEquals(32L, saved.completedBytes)
        assertEquals(listOf("failed.txt", "running.txt"), saved.retryable.map { it.name })
        assertEquals(12L, saved.entries.last().bytes)
    }

    @Test fun codecPreservesUnicodePathsAndPerFileResults() {
        val batch = UploadBatch("a", "b", "c", "Rumah", entries = listOf(
            UploadEntry("content://fixture/1", "catatan 界.txt", "/folder dengan spasi", 18, UploadState.FAILED, 7, "Server unavailable")
        ))
        assertEquals(listOf(batch), TransferCodec.decode(TransferCodec.encode(listOf(batch))))
    }

    @Test fun interruptedWorkMarksOnlyUnfinishedFilesFailedAndRetryable() {
        val batch = UploadBatch("a", "b", "c", "Server", entries = listOf(
            UploadEntry("content://one", "done.jpg", "/gallery", 40, UploadState.SUCCEEDED, 40),
            UploadEntry("content://two", "in-flight.jpg", "/gallery", 80, UploadState.UPLOADING, 24),
            UploadEntry("content://three", "next.jpg", "/gallery")
        ))
        val recovered = finishInterrupted(batch, false, "Upload berhenti")
        assertEquals(UploadState.SUCCEEDED, recovered.entries[0].state)
        assertEquals(UploadState.FAILED, recovered.entries[1].state)
        assertEquals(24L, recovered.entries[1].bytes)
        assertEquals(listOf("in-flight.jpg", "next.jpg"), recovered.retryable.map { it.name })
        assertFalse(recovered.active)
    }

    @Test fun currentFolderShowsActualUploadingBatchBeforeNewerWaitingBatch() {
        val uploading = UploadBatch("active", "a", "home", "Home", createdAt = 1, entries = listOf(UploadEntry("1", "photo.jpg", "/gallery", state = UploadState.UPLOADING)))
        val queued = uploading.copy(id = "waiting", createdAt = 2, entries = listOf(UploadEntry("2", "next.jpg", "/gallery")))
        val otherFolder = uploading.copy(id = "other", createdAt = 3, entries = listOf(UploadEntry("3", "file.txt", "/other", state = UploadState.UPLOADING)))
        val batches = listOf(queued, otherFolder, uploading)
        assertEquals("active", currentUploadBatch(batches, "home", "/gallery")?.id)
        assertEquals("other", currentUploadBatch(batches, "home", "/other")?.id)
        assertNull(currentUploadBatch(batches, "different-server", "/gallery"))
        assertNull(currentUploadBatch(listOf(uploading.copy(entries = uploading.entries.map { it.copy(state = UploadState.SUCCEEDED) })), "home", "/gallery"))
    }
}
