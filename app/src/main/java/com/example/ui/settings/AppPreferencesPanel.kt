package com.example.ui.settings

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyListScope
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.*
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.ExpandMore
import androidx.compose.material.icons.filled.Check
import androidx.compose.ui.semantics.selected
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.data.preferences.AppPreferences
import com.example.ui.components.TailHarnessCard
import com.example.ui.theme.TextPrimary
import com.example.ui.theme.TextSecondary
import androidx.compose.ui.res.stringResource
import com.example.R
import com.example.ui.i18n.AppLanguage
import com.example.ui.i18n.tr

@Composable
fun AppPreferencesPanel(value: AppPreferences, onChange: (AppPreferences) -> Unit) {
    Column(verticalArrangement = Arrangement.spacedBy(16.dp)) {
        PreferencesHeading()
        AppearancePreferences(value, onChange)
        TerminalPreferences(value, onChange)
        ChatPreferences(value, onChange)
        ResetPreferences(onChange)
    }
}

internal fun LazyListScope.appPreferenceItems(value: AppPreferences, onChange: (AppPreferences) -> Unit) {
    item(key = "preferences_heading", contentType = "preferences_heading") { PreferencesHeading() }
    item(key = "appearance", contentType = "appearance") { AppearancePreferences(value, onChange) }
    item(key = "terminal_preferences", contentType = "terminal_preferences") { TerminalPreferences(value, onChange) }
    item(key = "chat_preferences", contentType = "chat_preferences") { ChatPreferences(value, onChange) }
    item(key = "reset_preferences", contentType = "reset_preferences") { ResetPreferences(onChange) }
}

@Composable
private fun PreferencesHeading() {
    Column(verticalArrangement = Arrangement.spacedBy(16.dp)) {
        Text(tr("Pengaturan aplikasi", "App preferences"), color = TextPrimary, style = MaterialTheme.typography.titleLarge, fontWeight = FontWeight.Bold)
        Text(tr("Perubahan langsung diterapkan dan disimpan di perangkat ini.", "Changes apply immediately and are saved on this device."), color = TextSecondary, fontSize = 13.sp)
    }
}

@Composable
private fun AppearancePreferences(value: AppPreferences, onChange: (AppPreferences) -> Unit) {
        TailHarnessCard {
            Column(Modifier.padding(horizontal = 16.dp, vertical = 6.dp)) {
                PreferenceMenu(tr("Tema", "Theme"), value.themeMode,
                    listOf("system" to tr("Ikuti perangkat", "Use device setting"), "light" to tr("Terang", "Light"), "dark" to tr("Gelap", "Dark")), "app_theme") {
                    onChange(value.copy(themeMode = it))
                }
                HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant)
                PreferenceMenu(tr("Bahasa", "Language"), value.appLanguage,
                    listOf("system" to tr("Ikuti perangkat", "Use device setting"), "id" to "Indonesia", "en" to "English"), "app_language") {
                    onChange(value.copy(appLanguage = it)); AppLanguage.apply(it)
                }
            }
        }
}

@Composable
private fun TerminalPreferences(value: AppPreferences, onChange: (AppPreferences) -> Unit) {
        TailHarnessCard {
            Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
                Text(tr("Terminal", "Terminal"), color = TextPrimary, fontWeight = FontWeight.Bold, fontSize = 18.sp)
                FontSizeControl(tr("Ukuran huruf terminal", "Terminal font size"), value.terminalFontSize, 10..24, "terminal_font") {
                    onChange(value.copy(terminalFontSize = it))
                }
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    FilterChip(selected = value.terminalFont == "jetbrains", onClick = { onChange(value.copy(terminalFont = "jetbrains")) }, label = { Text("JetBrains Mono") })
                    FilterChip(selected = value.terminalFont == "fira", onClick = { onChange(value.copy(terminalFont = "fira")) }, label = { Text("Fira Code") })
                }
                Text("user@server:~$ ls -la\nDocuments  scripts  backup", color = Color(0xFF62AE91),
                    fontSize = value.terminalFontSize.sp, fontFamily = FontFamily(androidx.compose.ui.text.font.Font(if (value.terminalFont == "fira") com.example.R.font.fira_code else com.example.R.font.jetbrains_mono)),
                    modifier = Modifier.fillMaxWidth().background(Color(0xFF141729), RoundedCornerShape(12.dp)).padding(14.dp))
                PreferenceSwitch(tr("Tombol tambahan", "Extra keys"), tr("Akses Ctrl, Esc, Tab, dan tombol panah.", "Access Ctrl, Esc, Tab, and arrow keys."), value.extraKeys, "Tombol tambahan") { onChange(value.copy(extraKeys = it)) }
                PreferenceSwitch(tr("Layar tetap menyala", "Keep screen on"), tr("Hanya saat tab terminal sedang terbuka.", "Only while the terminal tab is open."), value.keepScreenOn, "Layar tetap menyala") { onChange(value.copy(keepScreenOn = it)) }
                PreferenceSwitch(tr("Tinjau sebelum menempel", "Preview pasted text"), tr("Periksa teks clipboard sebelum dikirim ke terminal.", "Review clipboard text before sending it to the terminal."), value.previewPaste, "Tinjau sebelum menempel") { onChange(value.copy(previewPaste = it)) }
                Text(tr("Cubit layar terminal untuk mengubah ukuran huruf. Tahan teks untuk memilih dan menyalin sebagian output.", "Pinch the terminal to resize text. Hold text to select and copy output."), color = TextSecondary, fontSize = 12.sp)
            }
        }
}

@Composable
private fun ChatPreferences(value: AppPreferences, onChange: (AppPreferences) -> Unit) {
        TailHarnessCard {
            Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
                Text(tr("Chat Hermes", "Hermes chat"), color = TextPrimary, fontWeight = FontWeight.Bold, fontSize = 18.sp)
                FontSizeControl(tr("Ukuran teks chat", "Chat text size"), value.chatFontSize, 12..22, "chat_font") { onChange(value.copy(chatFontSize = it)) }
                Text(tr("Pesan dan jawaban Hermes akan memakai ukuran ini.", "Messages and replies use this text size."), color = TextPrimary, fontSize = value.chatFontSize.sp)
                PreferenceSwitch(tr("Waktu pesan", "Message timestamps"), tr("Jam pengiriman di setiap pesan.", "Show the time on each message."), value.chatTimestamps, "Waktu pesan") { onChange(value.copy(chatTimestamps = it)) }
                PreferenceSwitch(tr("Enter untuk mengirim", "Enter to send"), tr("Saat mati, Enter membuat baris baru. Shift+Enter selalu membuat baris baru.", "When off, Enter inserts a new line. Shift+Enter always inserts a new line."), value.chatEnterSends, "Enter untuk mengirim") { onChange(value.copy(chatEnterSends = it)) }
                Text(tr("Cara Hermes menjawab", "Response style"), color = TextPrimary, fontWeight = FontWeight.SemiBold)
                listOf("concise" to tr("Ringkas", "Concise"), "steps" to tr("Langkah kerja", "Step by step"), "detailed" to tr("Terperinci", "Detailed")).forEach { (style, title) ->
                    Row(Modifier.fillMaxWidth(), verticalAlignment = androidx.compose.ui.Alignment.CenterVertically) {
                        RadioButton(selected = value.answerStyle == style, onClick = { onChange(value.copy(answerStyle = style)) })
                        Text(title, color = TextPrimary)
                    }
                }
                Text(tr("Effort penalaran", "Reasoning effort"), color = TextPrimary, fontWeight = FontWeight.SemiBold)
                Text(tr("Default mengikuti model. Model yang tidak dikenal dukungan effort-nya akan memakai default. Ubah kapan saja dengan /effort.", "Default follows the model. Unknown model support falls back to its default. You can also change it with /effort."), color = TextSecondary, fontSize = 12.sp)
                listOf(
                    listOf("" to tr("Default", "Default"), "low" to "Low"),
                    listOf("medium" to "Medium", "high" to "High")
                ).forEach { row ->
                    Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        row.forEach { (effort, label) ->
                            FilterChip(selected = value.chatEffort == effort, onClick = { onChange(value.copy(chatEffort = effort)) }, label = { Text(label) }, modifier = Modifier.testTag("chat_effort_${effort.ifBlank { "default" }}"))
                        }
                    }
                }
                OutlinedTextField(value.hermesInstructions, { onChange(value.copy(hermesInstructions = it.take(2000))) },
                    label = { Text(tr("Instruksi tambahan untuk Hermes", "Additional instructions for Hermes")) },
                    placeholder = { Text(tr("Contoh: jelaskan perintah sebelum memberi langkah berikutnya", "For example: explain each command before the next step")) },
                    supportingText = { Text(tr("Dipakai pada pesan berikutnya.", "Applied to your next message.") + " ${value.hermesInstructions.length}/2000") },
                    minLines = 2, maxLines = 5, modifier = Modifier.fillMaxWidth().testTag("hermes_instructions"))
            }
        }
}

@Composable
private fun ResetPreferences(onChange: (AppPreferences) -> Unit) {
        TextButton(onClick = { onChange(AppPreferences()); AppLanguage.apply("id") }, modifier = Modifier.testTag("reset_app_preferences")) { Text(tr("Pulihkan pengaturan aplikasi", "Reset app preferences")) }
}

@Composable
private fun PreferenceMenu(label: String, selected: String, options: List<Pair<String, String>>, tag: String, onSelect: (String) -> Unit) {
    var expanded by remember { mutableStateOf(false) }
    Box {
        TextButton(onClick = { expanded = true }, modifier = Modifier.fillMaxWidth().heightIn(min = 64.dp).testTag("${tag}_picker"),
            contentPadding = PaddingValues(vertical = 12.dp), shape = RoundedCornerShape(10.dp)) {
            Column(Modifier.weight(1f), horizontalAlignment = androidx.compose.ui.Alignment.Start) {
                Text(label, color = TextPrimary, fontWeight = FontWeight.SemiBold, fontSize = 14.sp)
                Text(options.firstOrNull { it.first == selected }?.second.orEmpty(), color = TextSecondary, fontSize = 13.sp)
            }
            Icon(Icons.Default.ExpandMore, contentDescription = null, tint = TextSecondary, modifier = Modifier.size(20.dp))
        }
        DropdownMenu(expanded = expanded, onDismissRequest = { expanded = false }) {
            options.forEach { (key, title) ->
                val isSelected = key == selected
                DropdownMenuItem(text = { Text(title, fontWeight = if (key == selected) FontWeight.SemiBold else FontWeight.Normal) },
                    leadingIcon = { if (key == selected) Icon(Icons.Default.Check, null, Modifier.size(18.dp)) else Spacer(Modifier.size(18.dp)) },
                    onClick = { expanded = false; onSelect(key) }, modifier = Modifier.testTag("${tag}_$key").semantics { this.selected = isSelected })
            }
        }
    }
}

@Composable
private fun FontSizeControl(label: String, size: Int, range: IntRange, tag: String, onChange: (Int) -> Unit) {
    Row(Modifier.fillMaxWidth(), verticalAlignment = androidx.compose.ui.Alignment.CenterVertically) {
        Column(Modifier.weight(1f)) {
            Text(label, color = TextPrimary, fontSize = 14.sp)
            Text("$size sp", color = TextSecondary, fontSize = 13.sp, modifier = Modifier.testTag("${tag}_value"))
        }
        OutlinedButton(onClick = { onChange(size - 1) }, enabled = size > range.first,
            modifier = Modifier.testTag("${tag}_decrease"), contentPadding = PaddingValues(horizontal = 14.dp)) { Text("A−") }
        Spacer(Modifier.width(8.dp))
        OutlinedButton(onClick = { onChange(size + 1) }, enabled = size < range.last,
            modifier = Modifier.testTag("${tag}_increase"), contentPadding = PaddingValues(horizontal = 14.dp)) { Text("A+") }
    }
}

@Composable
private fun PreferenceSwitch(title: String, summary: String, checked: Boolean, tag: String, onChange: (Boolean) -> Unit) {
    Row(Modifier.fillMaxWidth(), verticalAlignment = androidx.compose.ui.Alignment.CenterVertically) {
        Column(Modifier.weight(1f).padding(end = 12.dp)) {
            Text(title, color = TextPrimary, fontSize = 14.sp)
            Text(summary, color = TextSecondary, fontSize = 12.sp, lineHeight = 17.sp)
        }
        Switch(checked, onChange, modifier = Modifier.testTag(tag).semantics { contentDescription = title })
    }
}
