package com.example

import android.content.Context
import androidx.test.core.app.ApplicationProvider
import com.example.data.model.ServerConfig
import com.example.data.security.SshTrust
import com.example.data.sftp.SftpManager
import kotlinx.coroutines.*
import org.apache.sshd.common.file.virtualfs.VirtualFileSystemFactory
import org.apache.sshd.server.SshServer
import org.apache.sshd.server.keyprovider.SimpleGeneratorHostKeyProvider
import org.apache.sshd.sftp.server.SftpSubsystemFactory
import org.junit.Assert.*
import org.junit.Rule
import org.junit.Test
import org.junit.rules.TemporaryFolder
import org.junit.runner.RunWith
import org.robolectric.annotation.Config
import java.io.ByteArrayOutputStream
import java.io.File
import java.io.IOException
import java.io.OutputStream

@RunWith(SshAwareRobolectricTestRunner::class)
@Config(sdk = [34])
class SftpFilesIntegrationTest {
    @get:Rule val temp = TemporaryFolder()

    @Test fun recursiveSearchDownloadFailureAndPartialDeleteUseRealPaths() = runBlocking {
        val root = temp.newFolder("server")
        File(root, "Media/foto").mkdirs()
        File(root, "Downloads").mkdirs()
        File(root, "Media/foto/same.txt").writeText("photo folder")
        File(root, "Downloads/same.txt").writeText("download folder")
        File(root, "literal*.txt").writeText("wildcard name")
        File(root, "literal-other.txt").writeText("must survive")
        val context = ApplicationProvider.getApplicationContext<Context>()
        SshTrust.initialize(context)
        val server = SshServer.setUpDefaultServer().apply {
            host = "127.0.0.1"
            port = 0
            keyPairProvider = SimpleGeneratorHostKeyProvider(temp.newFile("host-key").toPath()).apply {
                algorithm = "RSA"; keySize = 2048
            }
            fileSystemFactory = VirtualFileSystemFactory(root.toPath())
            subsystemFactories = listOf(SftpSubsystemFactory.Builder().build())
            setPasswordAuthenticator { user, password, _ -> user == "test" && password == "test-password" }
            start()
        }
        val scope = CoroutineScope(SupervisorJob() + Dispatchers.IO)
        val trust = scope.launch { SshTrust.pending.collect { it?.answer?.complete(true) } }
        val manager = SftpManager()
        val config = ServerConfig("127.0.0.1", server.port, "test", "test-password")
        try {
            val photo = ByteArray(192 * 1024) { (it % 251).toByte() }
            var uploaded = 0L
            manager.uploadFile("/Media/foto", "foto 界*.jpg", photo.inputStream(), config, totalBytes = photo.size.toLong()) { uploaded = it }.getOrThrow()
            assertArrayEquals(photo, File(root, "Media/foto/foto 界*.jpg").readBytes())
            assertEquals(photo.size.toLong(), uploaded)
            manager.uploadFile("/Downloads", "empty.txt", byteArrayOf().inputStream(), config, totalBytes = 0).getOrThrow()
            assertEquals(0L, File(root, "Downloads/empty.txt").length())
            assertTrue(manager.uploadFile("/missing", "photo.jpg", photo.inputStream(), config).isFailure)
            val results = manager.searchFiles("/", "SAME", config).getOrThrow()
            assertEquals(setOf("/Media/foto/same.txt", "/Downloads/same.txt"), results.files.map { it.path }.toSet())
            val scoped = manager.searchFiles("/Media", "same", config).getOrThrow()
            assertEquals(listOf("/Media/foto/same.txt"), scoped.files.map { it.path })
            assertTrue(manager.searchFiles("/missing", "same", config).isFailure)
            val output = ByteArrayOutputStream()
            manager.downloadToStream("/Media/foto/same.txt", output, config).getOrThrow()
            assertEquals("photo folder", output.toString("UTF-8"))
            val literalOutput = ByteArrayOutputStream()
            manager.downloadToStream("/literal*.txt", literalOutput, config).getOrThrow()
            assertEquals("wildcard name", literalOutput.toString("UTF-8"))
            assertTrue(manager.delete("/literal*.txt", false, config).isSuccess)
            assertTrue(File(root, "literal-other.txt").exists())
            assertTrue(manager.downloadToStream("/Downloads/same.txt", object : OutputStream() {
                override fun write(value: Int) { throw IOException("storage unavailable") }
            }, config).isFailure)
            assertTrue(manager.delete("/Media", true, config).isFailure)
            assertTrue(manager.delete("/Downloads/same.txt", false, config).isSuccess)
            assertTrue(File(root, "Media/foto/same.txt").exists())
            assertFalse(File(root, "Downloads/same.txt").exists())
            assertEquals(listOf("/Media/foto/same.txt"), manager.searchFiles("/", "same", config).getOrThrow().files.map { it.path })
        } finally {
            manager.close()
            trust.cancel()
            scope.cancel()
            server.stop(true)
        }
    }
}
