package com.example.ui.hermes

import android.content.ClipData
import android.content.ClipboardManager
import android.content.Context
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.*
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.ContentCopy
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.Alignment
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.text.SpanStyle
import androidx.compose.ui.text.buildAnnotatedString
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.withStyle
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.ui.i18n.tr
import com.example.ui.theme.CodeSurface
import com.example.ui.theme.IsDarkAppearance

sealed interface MessageBlock {
    data class Prose(val text: String, val heading: Int = 0) : MessageBlock
    data class Code(val text: String, val language: String) : MessageBlock
    data class Bullet(val text: String, val marker: String) : MessageBlock
    data class Table(val rows: List<List<String>>) : MessageBlock
}

private val tableDividerPattern = Regex(":?-{3,}:?")
private val headingPattern = Regex("^(#{1,6})\\s+(.+)$")
private val bulletPattern = Regex("^([-*+] |\\d+[.)] )(.+)$")
private val horizontalRulePattern = Regex("[-*_]{3,}")
private val inlinePattern = Regex("(\\*\\*(.+?)\\*\\*|__(.+?)__|`([^`]+)`|\\*([^*]+)\\*|(?<!\\w)_([^_]+)_(?!\\w))")

fun parseMessageBlocks(raw: String): List<MessageBlock> {
    val blocks = mutableListOf<MessageBlock>()
    val paragraph = mutableListOf<String>()
    val code = mutableListOf<String>()
    val table = mutableListOf<List<String>>()
    var inCode = false
    var language = ""
    fun flushParagraph() { if (paragraph.isNotEmpty()) { blocks.add(MessageBlock.Prose(paragraph.joinToString("\n"))); paragraph.clear() } }
    fun flushTable() { if (table.isNotEmpty()) { blocks.add(MessageBlock.Table(table.toList())); table.clear() } }
    for (line in raw.lines()) {
        val trimmed = line.trimStart()
        if (trimmed.startsWith("```")) {
            if (inCode) { blocks.add(MessageBlock.Code(code.joinToString("\n"), language)); code.clear() }
            else { flushParagraph(); flushTable(); language = trimmed.removePrefix("```").trim() }
            inCode = !inCode
        } else if (inCode) code.add(line)
        else if (trimmed.startsWith('|') && trimmed.count { it == '|' } >= 2) {
            flushParagraph()
            val cells = trimmed.trim().trim('|').split('|').map(String::trim)
            if (!cells.all { it.matches(tableDividerPattern) }) table.add(cells)
        } else {
            flushTable()
            val heading = headingPattern.find(trimmed)
            val bullet = bulletPattern.find(trimmed)
            when {
                trimmed.isBlank() -> flushParagraph()
                heading != null -> { flushParagraph(); blocks.add(MessageBlock.Prose(heading.groupValues[2], heading.groupValues[1].length)) }
                bullet != null -> { flushParagraph(); blocks.add(MessageBlock.Bullet(bullet.groupValues[2], if (bullet.groupValues[1].first().isDigit()) bullet.groupValues[1].trim() else "•")) }
                trimmed.matches(horizontalRulePattern) -> flushParagraph()
                else -> paragraph.add(if (trimmed.startsWith("> ")) trimmed.removePrefix("> ") else line)
            }
        }
    }
    if (inCode) blocks.add(MessageBlock.Code(code.joinToString("\n"), language))
    flushParagraph(); flushTable()
    return blocks
}

fun formatInline(text: String): AnnotatedString = buildAnnotatedString {
    var offset = 0
    inlinePattern.findAll(text).forEach { match ->
        append(text.substring(offset, match.range.first))
        val groups = match.groupValues
        when {
            groups[2].isNotEmpty() || groups[3].isNotEmpty() -> withStyle(SpanStyle(fontWeight = FontWeight.Bold)) { append(groups[2].ifEmpty { groups[3] }) }
            groups[4].isNotEmpty() -> withStyle(SpanStyle(fontFamily = FontFamily.Monospace, fontWeight = FontWeight.Medium)) { append(groups[4]) }
            else -> withStyle(SpanStyle(fontStyle = FontStyle.Italic)) { append(groups[5].ifEmpty { groups[6] }) }
        }
        offset = match.range.last + 1
    }
    append(text.substring(offset))
}

@OptIn(ExperimentalLayoutApi::class)
@Composable
fun HermesMessageBody(raw: String, fontSize: Int = 15, textColor: Color = MaterialTheme.colorScheme.onSurface,
    onRun: ((String) -> Unit)? = null, onTerminal: ((String) -> Unit)? = null, fillWidth: Boolean = true) {
    val blocks = remember(raw) { parseMessageBlocks(raw) }
    val context = LocalContext.current
    val dark = IsDarkAppearance
    Column(if (fillWidth) Modifier.fillMaxWidth() else Modifier, verticalArrangement = Arrangement.spacedBy(10.dp)) {
        blocks.forEach { block -> when (block) {
            is MessageBlock.Prose -> Text(remember(block.text) { formatInline(block.text) }, color = textColor,
                fontFamily = FontFamily.SansSerif,
                fontSize = (fontSize + if (block.heading > 0) 2 else 0).sp, lineHeight = (fontSize * 1.5f).sp,
                fontWeight = if (block.heading > 0) FontWeight.SemiBold else FontWeight.Normal)
            is MessageBlock.Bullet -> Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                Text(block.marker, color = textColor, fontFamily = FontFamily.SansSerif, fontSize = fontSize.sp, lineHeight = (fontSize * 1.5f).sp)
                Text(remember(block.text) { formatInline(block.text) }, color = textColor, fontFamily = FontFamily.SansSerif, fontSize = fontSize.sp, lineHeight = (fontSize * 1.5f).sp, modifier = Modifier.weight(1f))
            }
            is MessageBlock.Table -> Column(Modifier.fillMaxWidth().horizontalScroll(rememberScrollState())) {
                block.rows.forEachIndexed { index, row ->
                    Row(Modifier.background(if (index == 0) MaterialTheme.colorScheme.primaryContainer else MaterialTheme.colorScheme.surface)) {
                        row.forEach { cell -> Text(remember(cell) { formatInline(cell) }, color = MaterialTheme.colorScheme.onSurface, fontSize = fontSize.sp,
                            fontWeight = if (index == 0) FontWeight.Bold else FontWeight.Normal, modifier = Modifier.width(180.dp).padding(10.dp)) }
                    }
                    HorizontalDivider()
                }
            }
            is MessageBlock.Code -> Column(Modifier.fillMaxWidth()) {
                val shape = RoundedCornerShape(16.dp)
                Column(Modifier.fillMaxWidth().background(CodeSurface, shape).border(1.dp, MaterialTheme.colorScheme.outlineVariant, shape)) {
                    Row(Modifier.fillMaxWidth().padding(start = 16.dp, end = 4.dp), verticalAlignment = Alignment.CenterVertically) {
                        Text(block.language.ifBlank { tr("Teks", "Text") }, color = MaterialTheme.colorScheme.onSurfaceVariant, fontSize = 11.sp, modifier = Modifier.weight(1f))
                        IconButton(onClick = {
                            (context.getSystemService(Context.CLIPBOARD_SERVICE) as ClipboardManager).setPrimaryClip(ClipData.newPlainText("Kode", block.text))
                        }, modifier = Modifier.size(44.dp).testTag("copy_code_button")) {
                            Icon(Icons.Default.ContentCopy, tr("Salin kode", "Copy code"), tint = MaterialTheme.colorScheme.onSurface, modifier = Modifier.size(19.dp))
                        }
                    }
                    Text(remember(block.text, block.language, dark) { highlightCode(block.text, block.language, dark) }, color = MaterialTheme.colorScheme.onSurface,
                        fontFamily = FontFamily.Monospace, fontSize = (fontSize - 2).coerceAtLeast(12).sp,
                        lineHeight = (fontSize * 1.45f).sp, modifier = Modifier.fillMaxWidth()
                            .horizontalScroll(rememberScrollState()).padding(start = 16.dp, end = 16.dp, top = 4.dp, bottom = 16.dp))
                }
                if (isShellCode(block.language) && (onRun != null || onTerminal != null)) FlowRow(
                    Modifier.fillMaxWidth().padding(top = 2.dp), horizontalArrangement = Arrangement.spacedBy(4.dp)
                ) {
                    if (onRun != null) TextButton(onClick = { onRun(block.text) }, contentPadding = PaddingValues(horizontal = 8.dp),
                        modifier = Modifier.heightIn(min = 44.dp).testTag("run_on_server_button")) { Text(tr("Jalankan di server", "Run on server"), color = MaterialTheme.colorScheme.primary, fontSize = 12.sp) }
                    if (onTerminal != null) TextButton(onClick = { onTerminal(block.text) }, contentPadding = PaddingValues(horizontal = 8.dp),
                        modifier = Modifier.heightIn(min = 44.dp).testTag("open_in_terminal_button")) { Text(tr("Buka di terminal", "Open in terminal"), color = MaterialTheme.colorScheme.primary, fontSize = 12.sp) }
                }
            }
        } }
    }
}
