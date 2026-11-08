package com.example.ui.hermes

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.DeleteSweep
import androidx.compose.material.icons.filled.Forum
import androidx.compose.material.icons.filled.History
import androidx.compose.material.icons.filled.Memory
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.ui.theme.*
import com.example.ui.components.CustomScreenHeader
import com.example.ui.i18n.tr

@Composable
fun HermesChatHeader(title: String, model: String, enabled: Boolean,
    onModel: () -> Unit, onNew: () -> Unit, onHistory: () -> Unit, onClear: () -> Unit) {
    CustomScreenHeader(icon = Icons.Default.Forum, title = "Hermes",
        subtitle = "${if (title.isBlank() || title == "Obrolan Baru") tr("Obrolan baru", "New chat") else title} • ${model.ifBlank { tr("Pilih model", "Choose model") }}",
        modifier = Modifier.background(NavyDarkBackground),
        titleModifier = Modifier.heightIn(min = 48.dp)
            .clickable(enabled = enabled, role = Role.Button, onClick = onModel)
            .semantics { contentDescription = "${if (model.isBlank()) "Model" else model}" }
            .testTag("hermes_model_picker"), trailingContent = { Row {
        IconButton(onClick = onModel, enabled = enabled, modifier = Modifier.size(48.dp).testTag("chat_model_button")) {
            Icon(Icons.Default.Memory, tr("Pilih model", "Choose model"), tint = BurntOrange, modifier = Modifier.size(23.dp))
        }
        IconButton(onClick = onNew, enabled = enabled, modifier = Modifier.size(44.dp).testTag("new_chat_button")) {
            Icon(Icons.Default.Add, tr("Obrolan baru", "New chat"), tint = BurntOrange, modifier = Modifier.size(23.dp))
        }
        IconButton(onClick = onHistory, enabled = enabled, modifier = Modifier.size(44.dp).testTag("chat_history_button")) {
            Icon(Icons.Default.History, tr("Riwayat obrolan", "Chat history"), tint = BurntOrange, modifier = Modifier.size(23.dp))
        }
        IconButton(onClick = onClear, enabled = enabled, modifier = Modifier.size(44.dp).testTag("clear_chat_button")) {
            Icon(Icons.Default.DeleteSweep, tr("Bersihkan obrolan", "Clear chat"), tint = TextSecondary, modifier = Modifier.size(23.dp))
        }
    } })
}
