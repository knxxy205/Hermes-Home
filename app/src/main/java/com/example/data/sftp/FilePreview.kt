package com.example.data.sftp

import android.graphics.Bitmap
import android.graphics.BitmapFactory
import java.io.File
import java.io.InputStream
import java.io.OutputStream
import java.nio.ByteBuffer
import java.nio.charset.CodingErrorAction

object FilePreview {
    const val MAX_IMAGE_BYTES = 25L * 1024 * 1024
    const val MAX_TEXT_BYTES = 512L * 1024
    fun isImage(name: String) = name.substringAfterLast('.', "").lowercase(java.util.Locale.ROOT) in
        setOf("png", "jpg", "jpeg", "webp", "gif", "bmp", "heic", "heif", "avif")

    fun copyBounded(input: InputStream, output: OutputStream, limit: Long) {
        val buffer = ByteArray(32 * 1024)
        var total = 0L
        while (true) {
            val count = input.read(buffer)
            if (count < 0) break
            total += count
            require(total <= limit) { "File terlalu besar untuk preview." }
            output.write(buffer, 0, count)
        }
    }

    fun decodeText(bytes: ByteArray): String {
        require(bytes.none { it == 0.toByte() || (it.toInt() in 1..8) || (it.toInt() in 14..31) }) {
            "File biner tidak dapat ditampilkan sebagai teks. Gunakan Download."
        }
        return try {
            Charsets.UTF_8.newDecoder().onMalformedInput(CodingErrorAction.REPORT)
                .onUnmappableCharacter(CodingErrorAction.REPORT).decode(ByteBuffer.wrap(bytes)).toString()
        } catch (_: java.nio.charset.CharacterCodingException) {
            throw IllegalArgumentException("File bukan teks UTF-8. Gunakan Download.")
        }
    }

    fun decodeImage(file: File): Bitmap {
        val bounds = BitmapFactory.Options().apply { inJustDecodeBounds = true }
        BitmapFactory.decodeFile(file.path, bounds)
        require(bounds.outWidth > 0 && bounds.outHeight > 0) { "Format gambar tidak didukung atau file rusak." }
        var sample = 1
        while (bounds.outWidth / sample > 2048 || bounds.outHeight / sample > 2048) sample *= 2
        return BitmapFactory.decodeFile(file.path, BitmapFactory.Options().apply { inSampleSize = sample })
            ?: throw IllegalArgumentException("Gambar tidak dapat dibuka.")
    }
}
