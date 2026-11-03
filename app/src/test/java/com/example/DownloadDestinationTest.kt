package com.example

import android.content.Context
import android.content.pm.ProviderInfo
import android.database.Cursor
import android.database.MatrixCursor
import android.os.CancellationSignal
import android.os.ParcelFileDescriptor
import android.provider.DocumentsContract
import android.provider.DocumentsProvider
import androidx.test.core.app.ApplicationProvider
import com.example.data.sftp.DownloadDestination
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.runBlocking
import org.junit.Assert.*
import org.junit.Before
import org.junit.Rule
import org.junit.Test
import org.junit.rules.TemporaryFolder
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import org.robolectric.shadows.ShadowContentResolver
import java.io.File
import java.io.IOException

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [34])
class DownloadDestinationTest {
    @get:Rule val temp = TemporaryFolder()
    private lateinit var destination: DownloadDestination
    private lateinit var root: File

    @Before fun setup() {
        val context = ApplicationProvider.getApplicationContext<Context>()
        root = temp.newFolder("downloads")
        val provider = TestDocumentsProvider().apply { directory = root }
        provider.attachInfo(context, ProviderInfo().apply {
            authority = "com.example.test.downloads"
            exported = true
            grantUriPermissions = true
            readPermission = "android.permission.MANAGE_DOCUMENTS"
            writePermission = "android.permission.MANAGE_DOCUMENTS"
        })
        ShadowContentResolver.registerProviderInternal("com.example.test.downloads", provider)
        destination = DownloadDestination(context.contentResolver,
            DocumentsContract.buildTreeDocumentUri("com.example.test.downloads", "root"))
    }

    @Test fun preservesExistingFilesAndBothSameNamedDownloads() = runBlocking {
        File(root, "photo.jpg").writeText("existing")
        destination.save("photo.jpg") { it.write("first".toByteArray()) }
        destination.save("photo.jpg") { it.write("second".toByteArray()) }
        assertEquals("existing", File(root, "photo.jpg").readText())
        assertEquals("first", File(root, "photo (1).jpg").readText())
        assertEquals("second", File(root, "photo (2).jpg").readText())
    }

    @Test fun failureAndCancellationRemoveOnlyPartialDocuments() = runBlocking {
        File(root, "keep.txt").writeText("keep")
        for (failure in listOf(IOException("connection lost"), CancellationException("cancelled"))) {
            try {
                destination.save("partial.txt") {
                    it.write("partial".toByteArray())
                    throw failure
                }
                fail("Transfer should fail")
            } catch (caught: Exception) {
                assertSame(failure, caught)
            }
            assertFalse(File(root, "partial.txt").exists())
            assertEquals("keep", File(root, "keep.txt").readText())
        }
    }

    @Test fun providerPermissionFailureDoesNotCreateOrOverwriteFiles() = runBlocking {
        File(root, "keep.txt").writeText("keep")
        val invalid = DownloadDestination(ApplicationProvider.getApplicationContext<Context>().contentResolver,
            DocumentsContract.buildTreeDocumentUri("missing.provider", "root"))
        try {
            invalid.save("keep.txt") { it.write("replacement".toByteArray()) }
            fail("Unavailable provider must fail")
        } catch (_: Exception) { }
        assertEquals(listOf("keep.txt"), root.list()!!.toList())
        assertEquals("keep", File(root, "keep.txt").readText())
    }

    class TestDocumentsProvider : DocumentsProvider() {
        lateinit var directory: File
        override fun onCreate() = true
        override fun queryRoots(projection: Array<out String>?): Cursor = MatrixCursor(emptyArray<String>())
        override fun queryDocument(documentId: String, projection: Array<out String>?): Cursor =
            MatrixCursor(projection ?: arrayOf(DocumentsContract.Document.COLUMN_DISPLAY_NAME)).apply {
                addRow(arrayOf(documentId))
            }
        override fun queryChildDocuments(parentDocumentId: String, projection: Array<out String>?, sortOrder: String?): Cursor =
            MatrixCursor(projection ?: arrayOf(DocumentsContract.Document.COLUMN_DISPLAY_NAME)).apply {
                directory.listFiles().orEmpty().forEach { addRow(arrayOf(it.name)) }
            }
        override fun createDocument(parentDocumentId: String, mimeType: String, displayName: String): String {
            check(File(directory, displayName).createNewFile())
            return displayName
        }
        override fun deleteDocument(documentId: String) { check(File(directory, documentId).delete()) }
        override fun openDocument(documentId: String, mode: String, signal: CancellationSignal?): ParcelFileDescriptor =
            ParcelFileDescriptor.open(File(directory, documentId), ParcelFileDescriptor.parseMode(mode))
        override fun isChildDocument(parentDocumentId: String, documentId: String) = true
    }
}
