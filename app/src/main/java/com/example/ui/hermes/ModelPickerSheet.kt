package com.example.ui.hermes

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.selection.selectable
import androidx.compose.foundation.selection.selectableGroup
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material.icons.filled.Search
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import com.example.data.hermes.HermesApiClient
import com.example.data.model.LlmProvider
import com.example.data.model.ServerConfig
import com.example.ui.i18n.tr

@Composable
fun ModelPickerSheet(
    client: HermesApiClient,
    config: ServerConfig,
    onSelect: (String) -> Unit,
    onDismiss: () -> Unit,
    onOpenSettings: () -> Unit,
    initialQuery: String = ""
) {
    var query by rememberSaveable(initialQuery) { mutableStateOf(initialQuery) }
    var loading by remember { mutableStateOf(true) }
    var models by remember { mutableStateOf(emptyList<String>()) }
    var error by remember { mutableStateOf<String?>(null) }
    var refresh by remember { mutableIntStateOf(0) }
    LaunchedEffect(config.hermesProvider, config.hermesBaseUrl, config.hermesApiKey, refresh) {
        loading = true
        error = null
        client.fetchAvailableModels(config)
            .onSuccess { models = it.distinct().sorted() }
            .onFailure { error = it.localizedMessage ?: "Daftar model belum dapat dimuat." }
        loading = false
    }

    // A fixed window avoids the sheet anchor/content-height feedback seen at list limits.
    Dialog(
        onDismissRequest = onDismiss,
        properties = DialogProperties(usePlatformDefaultWidth = false, decorFitsSystemWindows = false)
    ) {
        Box(
            Modifier.fillMaxSize().safeDrawingPadding().imePadding().padding(12.dp),
            contentAlignment = Alignment.Center
        ) {
            Surface(
                modifier = Modifier.widthIn(max = 640.dp).fillMaxWidth().fillMaxHeight().testTag("model_picker"),
                shape = RoundedCornerShape(24.dp),
                color = MaterialTheme.colorScheme.surface
            ) {
                ModelPickerContent(config, models, loading, error, query, { query = it },
                    onSelect, onDismiss, onOpenSettings, { refresh++ })
            }
        }
    }
}

@Composable
internal fun ModelPickerContent(
    config: ServerConfig,
    models: List<String>,
    loading: Boolean,
    error: String?,
    query: String,
    onQuery: (String) -> Unit,
    onSelect: (String) -> Unit,
    onDismiss: () -> Unit,
    onOpenSettings: () -> Unit,
    onRefresh: () -> Unit
) {
    val listState = rememberLazyListState()
    val filtered = remember(models, query, config.hermesModel) {
        val terms = query.trim().split(Regex("\\s+")).filter(String::isNotBlank)
        models.filter { model -> terms.all { model.contains(it, ignoreCase = true) } }
            .sortedBy { it != config.hermesModel }
    }
    LaunchedEffect(query) { listState.scrollToItem(0) }
    Column(Modifier.fillMaxSize()) {
        Row(
            Modifier.fillMaxWidth().padding(start = 20.dp, end = 8.dp, top = 8.dp).testTag("model_picker_header"),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Text(tr("Pilih model", "Choose model"), style = MaterialTheme.typography.titleLarge,
                fontWeight = FontWeight.Bold, modifier = Modifier.weight(1f))
            IconButton(enabled = !loading, onClick = onRefresh, modifier = Modifier.testTag("reload_models")) {
                Icon(Icons.Default.Refresh, tr("Muat ulang model", "Reload models"))
            }
            IconButton(onClick = onDismiss, modifier = Modifier.testTag("close_model_picker")) {
                Icon(Icons.Default.Close, tr("Tutup", "Close"))
            }
        }
        Text(LlmProvider.fromId(config.hermesProvider).label,
            style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant,
            modifier = Modifier.padding(horizontal = 20.dp))
        OutlinedTextField(
            value = query, onValueChange = onQuery,
            label = { Text(tr("Cari nama model", "Search models")) }, singleLine = true,
            leadingIcon = { Icon(Icons.Default.Search, null) },
            trailingIcon = {
                if (query.isNotEmpty()) IconButton(onClick = { onQuery("") }, modifier = Modifier.testTag("clear_model_search")) {
                    Icon(Icons.Default.Close, tr("Hapus pencarian", "Clear search"))
                }
            },
            modifier = Modifier.fillMaxWidth().padding(horizontal = 20.dp, vertical = 12.dp).testTag("model_search")
        )
        if (config.hermesModel.isNotBlank()) {
            Column(Modifier.fillMaxWidth().padding(horizontal = 20.dp).background(
                MaterialTheme.colorScheme.surfaceContainer, RoundedCornerShape(12.dp)).padding(12.dp)) {
                Text(tr("Model aktif", "Current model"), style = MaterialTheme.typography.labelMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant)
                Text(config.hermesModel.removePrefix("models/"), style = MaterialTheme.typography.bodyMedium,
                    fontWeight = FontWeight.SemiBold, maxLines = 2, overflow = TextOverflow.Ellipsis)
            }
        }
        Text(
            when {
                loading -> tr("Memuat daftar model…", "Loading models…")
                error != null -> tr("Periksa koneksi provider", "Check provider connection")
                else -> tr("${filtered.size} model${if (query.isNotBlank()) " cocok" else " tersedia"}",
                    "${filtered.size} models ${if (query.isNotBlank()) "matching" else "available"}")
            }, style = MaterialTheme.typography.labelMedium, color = MaterialTheme.colorScheme.onSurfaceVariant,
            modifier = Modifier.padding(horizontal = 20.dp, vertical = 12.dp)
        )
        HorizontalDivider()
        Box(Modifier.weight(1f).fillMaxWidth()) {
            when {
                loading -> Column(Modifier.align(Alignment.Center).padding(24.dp),
                    horizontalAlignment = Alignment.CenterHorizontally, verticalArrangement = Arrangement.spacedBy(12.dp)) {
                    CircularProgressIndicator(Modifier.size(28.dp), strokeWidth = 2.dp)
                    Text(tr("Mengambil model dari server…", "Loading models from provider…"))
                }
                error != null -> Column(Modifier.align(Alignment.Center).padding(24.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
                    Text(tr("Daftar model gagal dimuat", "Unable to load models"), color = MaterialTheme.colorScheme.error,
                        fontWeight = FontWeight.SemiBold)
                    Text(error, style = MaterialTheme.typography.bodyMedium)
                    TextButton(onClick = onRefresh) { Text(tr("Coba lagi", "Try again")) }
                    TextButton(onClick = onOpenSettings) { Text(tr("Atur koneksi", "Connection settings")) }
                }
                models.isEmpty() -> Column(Modifier.align(Alignment.Center).padding(24.dp)) {
                    Text(tr("Server belum menyediakan model.", "The provider has no models available."))
                    TextButton(onClick = onOpenSettings) { Text(tr("Periksa koneksi di Settings", "Check connection settings")) }
                }
                filtered.isEmpty() -> Column(Modifier.align(Alignment.Center).padding(24.dp)) {
                    Text(tr("Tidak ada nama model yang cocok. Coba kata lain.", "No matching models. Try a different name."))
                    TextButton(onClick = { onQuery("") }) { Text(tr("Hapus pencarian", "Clear search")) }
                }
                else -> LazyColumn(
                    modifier = Modifier.fillMaxSize().selectableGroup().testTag("model_list"),
                    state = listState, overscrollEffect = null,
                    contentPadding = PaddingValues(vertical = 8.dp)
                ) {
                    items(filtered, key = { it }, contentType = { "model" }) { model ->
                        val selected = model == config.hermesModel
                        Row(
                            Modifier.fillMaxWidth().background(if (selected) MaterialTheme.colorScheme.primaryContainer
                                else MaterialTheme.colorScheme.surface)
                                .selectable(selected, role = Role.RadioButton, onClick = { onSelect(model) })
                                .heightIn(min = 64.dp).testTag("model_option_$model").padding(horizontal = 20.dp, vertical = 12.dp),
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Text(model.removePrefix("models/"),
                                style = MaterialTheme.typography.bodyLarge,
                                color = if (selected) MaterialTheme.colorScheme.onPrimaryContainer else MaterialTheme.colorScheme.onSurface,
                                fontWeight = if (selected) FontWeight.SemiBold else FontWeight.Normal,
                                modifier = Modifier.weight(1f).padding(end = 12.dp))
                            RadioButton(selected = selected, onClick = null)
                        }
                    }
                }
            }
        }
    }
}
