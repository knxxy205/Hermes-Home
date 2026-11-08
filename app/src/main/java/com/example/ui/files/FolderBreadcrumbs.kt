package com.example.ui.files

import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Edit
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.example.R

@Composable
fun FolderBreadcrumbs(path: String, onNavigate: (String) -> Unit) {
    var edit by remember { mutableStateOf(false) }
    var draft by remember(path) { mutableStateOf(path) }
    Row(Modifier.fillMaxWidth().padding(horizontal = 12.dp), verticalAlignment = Alignment.CenterVertically) {
        Row(Modifier.weight(1f).horizontalScroll(rememberScrollState()), verticalAlignment = Alignment.CenterVertically) {
            TextButton(onClick = { onNavigate("/") }, modifier = Modifier.widthIn(min = 44.dp).testTag("files_root")) { Text("/") }
            val parts = path.trim('/').split('/').filter(String::isNotBlank)
            parts.forEachIndexed { index, part ->
                Text("/", style = MaterialTheme.typography.bodySmall)
                TextButton(onClick = { onNavigate("/" + parts.take(index + 1).joinToString("/")) }, modifier = Modifier.widthIn(max = 180.dp)) {
                    Text(part, maxLines = 1, overflow = TextOverflow.Ellipsis)
                }
            }
        }
        IconButton(onClick = { draft = path; edit = true }, modifier = Modifier.testTag("files_edit_path")) { Icon(Icons.Default.Edit, stringResource(R.string.go_to_folder)) }
    }
    if (edit) AlertDialog(onDismissRequest = { edit = false }, title = { Text(stringResource(R.string.go_to_folder)) },
        text = { OutlinedTextField(draft, { draft = it }, label = { Text(stringResource(R.string.folder_path)) }, singleLine = true, modifier = Modifier.testTag("files_path_input")) },
        confirmButton = { TextButton(enabled = draft.startsWith("/") && '\u0000' !in draft, onClick = { onNavigate(draft.trim().trimEnd('/').ifBlank { "/" }); edit = false }) { Text(stringResource(R.string.open)) } },
        dismissButton = { TextButton(onClick = { edit = false }) { Text(stringResource(R.string.cancel)) } })
}
