package com.example.ui.files

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material3.*
import androidx.compose.foundation.background
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.FolderOpen
import androidx.compose.material.icons.filled.Close
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.work.WorkManager
import com.example.R
import com.example.data.transfer.*
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.text.DateFormat
import java.util.Date
import java.util.UUID

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun TransferHistorySheet(store: TransferStore, selectedId: String?, onSelect: (String?) -> Unit,
    onOpenFolder: (UploadBatch, String) -> Unit, onDismiss: () -> Unit, onReselectSource: (UploadBatch) -> Unit = {}) {
    val context = LocalContext.current
    val batches by store.state.collectAsState()
    val selected = batches.firstOrNull { it.id == selectedId }
    val scope = rememberCoroutineScope()
    var retryTarget by remember { mutableStateOf<UploadBatch?>(null) }
    var error by remember { mutableStateOf<String?>(null) }
    var retrying by remember { mutableStateOf(false) }
    var reselect by remember { mutableStateOf<UploadBatch?>(null) }
    MaterialTheme(colorScheme = MaterialTheme.colorScheme) {
    ModalBottomSheet(onDismissRequest = onDismiss, modifier = Modifier.testTag("transfer_history_sheet"), containerColor = MaterialTheme.colorScheme.surface, sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true)) {
        Column(Modifier.fillMaxWidth().navigationBarsPadding().padding(horizontal = 20.dp)) {
            Row(Modifier.fillMaxWidth(), verticalAlignment = androidx.compose.ui.Alignment.CenterVertically) {
                Text(stringResource(if (selectedId == null) R.string.transfer_history else R.string.transfer_details), style = MaterialTheme.typography.titleMedium, modifier = Modifier.weight(1f))
                IconButton(onClick = onDismiss, modifier = Modifier.testTag("close_transfer_history")) { Icon(Icons.Default.Close, stringResource(R.string.close)) }
            }
            error?.let { Text(it, color = MaterialTheme.colorScheme.error, modifier = Modifier.padding(vertical = 8.dp)) }
            reselect?.let { batch -> TextButton(onClick = { onReselectSource(batch) }) { Text(stringResource(R.string.choose_source_files)) } }
            LazyColumn(Modifier.fillMaxWidth().heightIn(max = 380.dp).testTag("transfer_history_list"), contentPadding = PaddingValues(vertical = 8.dp)) {
                when {
                    selectedId != null && selected == null -> item { Text(stringResource(R.string.transfer_missing)) }
                    selected != null -> {
                        item {
                            Text(selected.profileName, style = MaterialTheme.typography.bodySmall)
                            Text(stringResource(R.string.upload_summary_short, selected.succeeded, selected.entries.size), fontWeight = FontWeight.SemiBold)
                            selected.entries.firstOrNull()?.let { entry ->
                                TextButton(onClick = { onOpenFolder(selected, entry.destination) }, contentPadding = PaddingValues(0.dp)) {
                                    Icon(Icons.Default.FolderOpen, null, modifier = Modifier.size(18.dp))
                                    Spacer(Modifier.width(6.dp))
                                    Text(entry.destination, maxLines = 2, overflow = androidx.compose.ui.text.style.TextOverflow.Ellipsis)
                                }
                            }
                            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                                if (selected.active) OutlinedButton(onClick = {
                                    runCatching { WorkManager.getInstance(context).cancelWorkById(UUID.fromString(selected.workId)) }
                                }) { Icon(Icons.Default.Close, null); Spacer(Modifier.width(6.dp)); Text(stringResource(R.string.cancel_upload)) }
                                else if (selected.retryable.isNotEmpty()) OutlinedButton(enabled = !retrying, onClick = { retryTarget = selected }, modifier = Modifier.testTag("retry_failed_uploads")) { Text(stringResource(R.string.retry_failed)) }
                            }
                        }
                        items(selected.entries, key = { it.uri + it.destination }, contentType = { "transfer_entry" }) { entry ->
                            Column(Modifier.fillMaxWidth().padding(vertical = 7.dp)) {
                                Row(verticalAlignment = androidx.compose.ui.Alignment.CenterVertically) {
                                    Text(entry.name, fontWeight = FontWeight.SemiBold, maxLines = 1, modifier = Modifier.weight(1f), overflow = androidx.compose.ui.text.style.TextOverflow.Ellipsis)
                                    Spacer(Modifier.width(8.dp))
                                    Text(stringResource(when (entry.state) {
                                        UploadState.SUCCEEDED -> R.string.transfer_done
                                        UploadState.FAILED -> R.string.transfer_failed
                                        UploadState.CANCELLED -> R.string.transfer_cancelled
                                        UploadState.QUEUED -> R.string.transfer_queued
                                        UploadState.UPLOADING -> R.string.transfer_uploading
                                    }), color = if (entry.state == UploadState.FAILED) MaterialTheme.colorScheme.error else MaterialTheme.colorScheme.onSurfaceVariant, style = MaterialTheme.typography.bodySmall)
                                }
                                Row(verticalAlignment = androidx.compose.ui.Alignment.CenterVertically) {
                                    val fileProgress = if (entry.state == UploadState.UPLOADING && entry.size > 0) {
                                        "${android.text.format.Formatter.formatShortFileSize(context, entry.bytes)} / ${android.text.format.Formatter.formatShortFileSize(context, entry.size)}"
                                    } else if (entry.size >= 0) android.text.format.Formatter.formatShortFileSize(context, entry.size) else ""
                                    Text(fileProgress, style = MaterialTheme.typography.bodySmall, modifier = Modifier.weight(1f))
                                }
                                if (entry.state == UploadState.UPLOADING) LinearProgressIndicator(
                                    progress = { if (entry.size > 0) (entry.bytes.toFloat() / entry.size).coerceIn(0f, 1f) else 0f },
                                    modifier = Modifier.fillMaxWidth().height(3.dp)
                                )
                                if (entry.error.isNotBlank()) Text(entry.error, color = MaterialTheme.colorScheme.error, style = MaterialTheme.typography.bodySmall, maxLines = 1, overflow = androidx.compose.ui.text.style.TextOverflow.Ellipsis)
                                Box(Modifier.fillMaxWidth().height(1.dp).background(MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.45f)))
                            }
                        }
                    }
                    batches.isEmpty() -> item {
                        Text(stringResource(R.string.no_transfers), modifier = Modifier.padding(vertical = 16.dp))
                    }
                    else -> items(batches, key = { it.id }, contentType = { "transfer_batch" }) { batch ->
                        Column(Modifier.fillMaxWidth().clickable { onSelect(batch.id) }.testTag("transfer_batch_${batch.id}").padding(vertical = 12.dp)) {
                            Text(batch.entries.firstOrNull()?.name.orEmpty(), fontWeight = FontWeight.SemiBold, maxLines = 1, overflow = androidx.compose.ui.text.style.TextOverflow.Ellipsis)
                            Text(if (batch.active) stringResource(R.string.uploads_running) else stringResource(R.string.upload_summary_short, batch.succeeded, batch.entries.size), style = MaterialTheme.typography.bodySmall)
                            val locale = androidx.compose.ui.platform.LocalConfiguration.current.locales[0]
                            val timeZone = java.util.TimeZone.getDefault().id
                            val date = remember(batch.createdAt, locale, timeZone) {
                                DateFormat.getDateTimeInstance(DateFormat.SHORT, DateFormat.SHORT).format(Date(batch.createdAt))
                            }
                            Text("${batch.profileName} · $date", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                        }
                    }
                }
            }
            Row(Modifier.fillMaxWidth().padding(bottom = 8.dp)) {
                if (selectedId != null) TextButton(onClick = { onSelect(null) }) { Text(stringResource(R.string.transfer_history)) }
                if (selectedId == null && batches.any { !it.active }) TextButton(onClick = { store.clearFinished() }) { Text(stringResource(R.string.clear_finished)) }
            }
        }
    }
    retryTarget?.let { batch -> AlertDialog(onDismissRequest = { retryTarget = null },
        title = { Text(stringResource(R.string.retry_upload_title)) }, text = { Text(stringResource(R.string.retry_upload_help)) },
        confirmButton = { TextButton(onClick = {
            retryTarget = null; retrying = true
            scope.launch {
                val result = withContext(Dispatchers.IO) { runCatching { UploadQueue.retry(context.applicationContext, batch.id) } }
                retrying = false
                result.onSuccess { onSelect(it); error = null; reselect = null }.onFailure { error = it.localizedMessage; reselect = batch }
            }
        }) { Text(stringResource(R.string.retry)) } }, dismissButton = { TextButton(onClick = { retryTarget = null }) { Text(stringResource(R.string.cancel)) } }) }
    }
}
