package com.example.data.transfer

import android.content.Intent
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow

data class TransferDestination(val batchId: String, val openFolder: Boolean = false)

object TransferNavigation {
    const val EXTRA_BATCH = "upload_batch"
    const val EXTRA_FOLDER = "upload_folder"
    private val mutable = MutableStateFlow<TransferDestination?>(null)
    val destination = mutable.asStateFlow()
    fun accept(intent: Intent?, context: android.content.Context? = null) {
        val id = intent?.getStringExtra(EXTRA_BATCH)?.takeIf { it.isNotBlank() } ?: return
        context?.let { TransferNotifications.opened(it, id) }
        mutable.value = TransferDestination(id, intent.getBooleanExtra(EXTRA_FOLDER, false))
        intent.removeExtra(EXTRA_BATCH)
        intent.removeExtra(EXTRA_FOLDER)
    }
    fun consumed() { mutable.value = null }
}
