package com.example.ui.settings

import androidx.compose.foundation.layout.*
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.ArrowDropDown
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.unit.dp
import com.example.data.model.LlmProvider
import com.example.ui.i18n.tr

@Composable
fun LlmProviderPicker(selected: String, onSelect: (LlmProvider) -> Unit) {
    var expanded by remember { mutableStateOf(false) }
    Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
        Text(tr("Provider AI", "AI provider"), style = MaterialTheme.typography.labelLarge)
        Box {
            OutlinedButton(onClick = { expanded = true }, colors = ButtonDefaults.outlinedButtonColors(contentColor = MaterialTheme.colorScheme.primary), modifier = Modifier.fillMaxWidth().heightIn(min = 48.dp).testTag("llm_provider_picker")) {
                Text(if (selected == "custom") tr("9router / server custom", "9router / custom server") else LlmProvider.fromId(selected).label, modifier = Modifier.weight(1f))
                Icon(Icons.Default.ArrowDropDown, null)
            }
            DropdownMenu(expanded = expanded, onDismissRequest = { expanded = false }) {
                LlmProvider.entries.forEach { provider ->
                    DropdownMenuItem(text = { Text(if (provider.id == "custom") tr("9router / server custom", "9router / custom server") else provider.label) }, onClick = { expanded = false; onSelect(provider) },
                        modifier = Modifier.testTag("llm_provider_${provider.id}"))
                }
            }
        }
        Text(tr("URL, API key, dan model diingat per provider setelah disimpan.", "Each provider remembers its URL, API key, and model after saving."), style = MaterialTheme.typography.bodySmall)
    }
}
