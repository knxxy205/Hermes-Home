package com.example

import com.example.ui.hermes.MessageBlock
import com.example.ui.hermes.formatInline
import com.example.ui.hermes.parseMessageBlocks
import com.example.ui.hermes.highlightCode
import com.example.ui.hermes.isShellCode
import com.example.data.hermes.ChatMessage
import org.junit.Assert.*
import org.junit.Test

class MessageFormattingTest {
    @Test fun headingsListsAndInlineFormattingAreReadableWithoutMarkdownMarkers() {
        val blocks = parseMessageBlocks("## Langkah berikutnya\n\n- **Periksa** `df -h`\n1. Coba lagi")
        assertEquals(MessageBlock.Prose("Langkah berikutnya", 2), blocks[0])
        assertEquals(MessageBlock.Bullet("**Periksa** `df -h`", "•"), blocks[1])
        assertEquals("Periksa df -h", formatInline((blocks[1] as MessageBlock.Bullet).text).text)
        assertEquals("1.", (blocks[2] as MessageBlock.Bullet).marker)
    }

    @Test fun codeKeepsIndentationAndUnclosedFencesKeepTheirContent() {
        val raw = "Simpan ini:\n```python\nif ready:\n    print('ok')"
        val code = parseMessageBlocks(raw).last() as MessageBlock.Code
        assertEquals("python", code.language)
        assertEquals("if ready:\n    print('ok')", code.text)
    }

    @Test fun tablesHaveRealRowsWithoutMarkdownSeparatorRows() {
        val blocks = parseMessageBlocks("| Host | Status |\n| :--- | ---: |\n| rumah | aktif |\n\nSelesai.")
        val table = blocks[0] as MessageBlock.Table
        assertEquals(listOf(listOf("Host", "Status"), listOf("rumah", "aktif")), table.rows)
        assertEquals(MessageBlock.Prose("Selesai."), blocks[1])
    }

    @Test fun ordinaryUnderscoresAndUnmatchedMarkersArePreserved() {
        assertEquals("file_name dan *belum selesai", formatInline("file_name dan *belum selesai").text)
        assertEquals("ssh_host_name", formatInline("ssh_host_name").text)
    }

    @Test fun serverResultsNeverOfferExecutionIncludingOldSavedReplies() {
        val suggestion = ChatMessage(role = "assistant", content = "```bash\ndf -h\n```")
        assertTrue(suggestion.canOfferCommandActions)
        assertFalse(suggestion.copy(isServerOutput = true).canOfferCommandActions)
        assertFalse(suggestion.copy(content = "⚡ **Hasil Eksekusi di Server:** (`df -h`)\n```bash\nPermission denied\n```").canOfferCommandActions)
        assertFalse(suggestion.copy(role = "user").canOfferCommandActions)
        assertFalse(suggestion.copy(isError = true).canOfferCommandActions)
        assertTrue(suggestion.copy(content = "Penjelasan tentang Hasil Eksekusi di Server").canOfferCommandActions)
    }

    @Test fun highlightingPreservesCopyableCodeAndLogsAreNotShellCommands() {
        val html = "<!DOCTYPE html>\n<html lang=\"id\">\n  <body>Halo, dunia! 🌍</body>\n</html>"
        assertEquals(html, highlightCode(html, "html").text)
        assertTrue(highlightCode(html, "html").spanStyles.isNotEmpty())
        val log = "Permission denied, please try again.\nDisconnected from server port 22"
        assertEquals(log, highlightCode(log, "text").text)
        assertTrue(highlightCode(log, "text").spanStyles.isEmpty())
        assertFalse(isShellCode("text"))
        assertFalse(isShellCode("html"))
        assertTrue(isShellCode("BASH"))
    }
}
