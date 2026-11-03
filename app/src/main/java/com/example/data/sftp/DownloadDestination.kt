package com.example.data.sftp

import android.content.ContentResolver
import android.net.Uri
import android.os.Build
import android.os.Bundle
import android.provider.DocumentsContract
import java.io.OutputStream

/** Writes only newly created documents; a failed transfer removes its partial document. */
class DownloadDestination(private val resolver: ContentResolver, private val tree: Uri) {
    suspend fun save(name: String, transfer: suspend (OutputStream) -> Unit): String {
        val parent = DocumentsContract.buildDocumentUriUsingTree(tree, DocumentsContract.getTreeDocumentId(tree))
        val children = DocumentsContract.buildChildDocumentsUriUsingTree(tree, DocumentsContract.getTreeDocumentId(tree))
        val names = mutableSetOf<String>()
        val projection = arrayOf(DocumentsContract.Document.COLUMN_DISPLAY_NAME)
        val cursor = (if (Build.VERSION.SDK_INT >= 26) {
            resolver.query(children, projection, Bundle.EMPTY, null)
        } else {
            resolver.query(children, projection, null, null, null)
        }) ?: error("Tidak dapat membaca folder tujuan")
        cursor.use {
            while (it.moveToNext()) names += it.getString(0).orEmpty()
        }
        val safeName = name.substringAfterLast('/').substringAfterLast('\\').filterNot { it == '\u0000' }
        require(safeName.isNotBlank() && safeName != "." && safeName != "..") { "Nama file tidak valid" }
        val requestedName = availableName(safeName, names)
        val document = DocumentsContract.createDocument(resolver, parent, "application/octet-stream", requestedName)
            ?: error("Tidak dapat membuat file di folder tujuan")
        try {
            val output = resolver.openOutputStream(document, "w") ?: error("Tidak dapat membuka file tujuan")
            output.use { transfer(it) }
            return requestedName
        } catch (failure: Throwable) {
            runCatching { DocumentsContract.deleteDocument(resolver, document) }
            throw failure
        }
    }

    companion object {
        fun availableName(name: String, existing: Set<String>): String {
            if (existing.none { it.equals(name, ignoreCase = true) }) return name
            val extensionIndex = name.lastIndexOf('.').takeIf { it > 0 } ?: name.length
            val stem = name.substring(0, extensionIndex)
            val extension = name.substring(extensionIndex)
            var index = 1
            while (existing.any { it.equals("$stem ($index)$extension", ignoreCase = true) }) index++
            return "$stem ($index)$extension"
        }
    }
}
