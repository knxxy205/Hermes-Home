package com.example

import com.example.data.sftp.FilePreview
import org.junit.Assert.*
import org.junit.Test
import java.io.ByteArrayInputStream
import java.io.ByteArrayOutputStream

class FilePreviewTest {
    @Test fun routesImageExtensionsCaseInsensitively() {
        listOf("photo.PNG", "camera.JpEg", "a.webp", "a.heic", "a.gif").forEach { assertTrue(FilePreview.isImage(it)) }
        assertFalse(FilePreview.isImage("photo.png.txt"))
        assertFalse(FilePreview.isImage("Dockerfile"))
    }
    @Test fun binaryAndInvalidUtf8CannotReachTextPreview() {
        assertThrows(IllegalArgumentException::class.java) { FilePreview.decodeText(byteArrayOf(0x89.toByte(), 0x50, 0x4e, 0x47, 0)) }
        assertThrows(IllegalArgumentException::class.java) { FilePreview.decodeText(byteArrayOf(0xc3.toByte(), 0x28)) }
        assertEquals("héllo\n\t世界", FilePreview.decodeText("héllo\n\t世界".toByteArray()))
    }
    @Test fun downloadLimitIsEnforcedOnActualBytes() {
        val out = ByteArrayOutputStream()
        assertThrows(IllegalArgumentException::class.java) {
            FilePreview.copyBounded(ByteArrayInputStream(ByteArray(100_000)), out, 40_000)
        }
        assertTrue(out.size() <= 40_000)
        val exact = ByteArrayOutputStream()
        FilePreview.copyBounded(ByteArrayInputStream(ByteArray(20)), exact, 20)
        assertEquals(20, exact.size())
    }
}
