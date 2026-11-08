package com.example.data.transfer

import android.content.Context
import androidx.work.WorkInfo
import androidx.work.WorkManager
import com.example.data.worker.SftpUploadWorker
import kotlinx.coroutines.*
import kotlinx.coroutines.flow.collect
import kotlinx.coroutines.sync.Mutex

// Separate WorkRequests allow cancellation without cancelling the next batch.
// The lock keeps transfers sequential while each request owns its notification and destination.
internal object UploadCoordinator {
    val lock = Mutex()
    private var observer: Job? = null

    @Synchronized fun observe(context: Context) {
        if (observer?.isActive == true) return
        val app = context.applicationContext
        observer = CoroutineScope(SupervisorJob() + Dispatchers.IO).launch {
            val reported = mutableSetOf<String>()
            var initial = true
            TransferStore(app).use { store ->
                WorkManager.getInstance(app).getWorkInfosByTagFlow(SftpUploadWorker.TAG).collect { work ->
                    val byId = work.associateBy { it.id.toString() }
                    if (initial) {
                        reported.addAll(store.read().filter { !it.active && byId[it.workId]?.state?.isFinished == true }.map { it.id })
                        initial = false
                    }
                    store.read().filter { it.active }.forEach { batch ->
                        val info = byId[batch.workId] ?: return@forEach
                        if (info.state.isFinished) store.finishInterrupted(batch.id, info.state == WorkInfo.State.CANCELLED,
                            if (info.state == WorkInfo.State.CANCELLED) "Upload dibatalkan" else "Upload terhenti. Coba lagi.")
                    }
                    val running = work.filter { it.state == WorkInfo.State.RUNNING }
                    TransferNotifications.clearStale(app, running.map { it.id }.toSet())
                    val finished = store.read().filter { byId[it.workId]?.state?.isFinished == true }
                    val newlyFinished = finished.filter { it.id !in reported }
                    reported.addAll(finished.map { it.id })
                    if (running.isEmpty()) newlyFinished.maxByOrNull { it.createdAt }?.let {
                        TransferNotifications.finished(app, it)
                    }
                }
            }
        }
    }
}
