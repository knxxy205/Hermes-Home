package com.example.data.sftp

import com.example.data.model.ServerConfig
import com.jcraft.jsch.ChannelSftp
import com.jcraft.jsch.JSch
import com.jcraft.jsch.Session
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.io.ByteArrayInputStream
import java.io.ByteArrayOutputStream
import java.io.File
import java.io.FileOutputStream
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale
import java.util.Vector

data class RemoteFileItem(
    val name: String,
    val path: String,
    val isDirectory: Boolean,
    val sizeBytes: Long,
    val permissions: String,
    val modifiedTime: Long,
    val extension: String = if (!isDirectory && name.contains(".")) name.substringAfterLast(".").lowercase() else ""
) {
    val formattedSize: String
        get() = when {
            isDirectory -> "Folder"
            sizeBytes < 1024 -> "$sizeBytes B"
            sizeBytes < 1024 * 1024 -> "${sizeBytes / 1024} KB"
            sizeBytes < 1024 * 1024 * 1024 -> String.format(Locale.US, "%.1f MB", sizeBytes.toDouble() / (1024 * 1024))
            else -> String.format(Locale.US, "%.2f GB", sizeBytes.toDouble() / (1024 * 1024 * 1024))
        }

    val formattedDate: String
        get() = if (modifiedTime > 0) {
            val sdf = SimpleDateFormat("yyyy-MM-dd HH:mm", Locale.getDefault())
            sdf.format(Date(modifiedTime * 1000))
        } else "-"
}

class SftpManager {

    private var activeSession: Session? = null
    private var activeChannelSftp: ChannelSftp? = null

    // Sandbox storage for testing without live server
    private val sandboxFiles = mutableMapOf<String, MutableList<RemoteFileItem>>(
        "/" to mutableListOf(
            RemoteFileItem("home", "/home", true, 4096, "drwxr-xr-x", System.currentTimeMillis() / 1000),
            RemoteFileItem("etc", "/etc", true, 4096, "drwxr-xr-x", System.currentTimeMillis() / 1000),
            RemoteFileItem("var", "/var", true, 4096, "drwxr-xr-x", System.currentTimeMillis() / 1000),
            RemoteFileItem("mnt", "/mnt", true, 4096, "drwxr-xr-x", System.currentTimeMillis() / 1000),
            RemoteFileItem("opt", "/opt", true, 4096, "drwxr-xr-x", System.currentTimeMillis() / 1000),
            RemoteFileItem("root", "/root", true, 4096, "drwx------", System.currentTimeMillis() / 1000)
        ),
        "/home" to mutableListOf(
            RemoteFileItem("admin", "/home/admin", true, 4096, "drwxr-xr-x", System.currentTimeMillis() / 1000)
        ),
        "/home/admin" to mutableListOf(
            RemoteFileItem("docker", "/home/admin/docker", true, 4096, "drwxr-xr-x", System.currentTimeMillis() / 1000),
            RemoteFileItem("backups", "/home/admin/backups", true, 4096, "drwxr-xr-x", System.currentTimeMillis() / 1000 - 86400),
            RemoteFileItem("storage", "/home/admin/storage", true, 4096, "drwxr-xr-x", System.currentTimeMillis() / 1000 - 172800),
            RemoteFileItem("docker-compose.yml", "/home/admin/docker-compose.yml", false, 1420, "-rw-r--r--", System.currentTimeMillis() / 1000 - 3600),
            RemoteFileItem(".env", "/home/admin/.env", false, 280, "-rw-------", System.currentTimeMillis() / 1000 - 7200),
            RemoteFileItem("backup_script.sh", "/home/admin/backup_script.sh", false, 3240, "-rwxr-xr-x", System.currentTimeMillis() / 1000 - 14000),
            RemoteFileItem("server_health.log", "/home/admin/server_health.log", false, 54200, "-rw-r--r--", System.currentTimeMillis() / 1000)
        ),
        "/home/admin/docker" to mutableListOf(
            RemoteFileItem("caddy", "/home/admin/docker/caddy", true, 4096, "drwxr-xr-x", System.currentTimeMillis() / 1000),
            RemoteFileItem("hermes", "/home/admin/docker/hermes", true, 4096, "drwxr-xr-x", System.currentTimeMillis() / 1000),
            RemoteFileItem("Caddyfile", "/home/admin/docker/Caddyfile", false, 890, "-rw-r--r--", System.currentTimeMillis() / 1000)
        )
    )

    private val sandboxFileContents = mutableMapOf<String, String>(
        "/home/admin/docker-compose.yml" to """
version: '3.8'
services:
  hermes-agent:
    image: hermes-agent:v3
    ports:
      - "11434:11434"
    environment:
      - MODEL_NAME=hermes-3-llama-3.1-8b
      - HOST=0.0.0.0
    restart: unless-stopped

  caddy:
    image: caddy:alpine
    ports:
      - "80:80"
      - "443:443"
    volumes:
      - ./Caddyfile:/etc/caddy/Caddyfile
    restart: unless-stopped
        """.trimIndent(),
        "/home/admin/.env" to "TAILSCALE_AUTH_KEY=REMOVED_CREDENTIAL\nHERMES_API_KEY=REMOVED_CREDENTIAL\nSSH_PORT=22",
        "/home/admin/backup_script.sh" to "#!/bin/bash\n# Homeserver backup script\necho 'Starting rsync backup...'\nrsync -avz /home/admin/data/ /mnt/backup/\necho 'Backup finished.'",
        "/home/admin/server_health.log" to "2026-09-16 08:00:01 [OK] Memory 21% used, NVMe SSD health 99%\n2026-09-16 09:00:01 [OK] Tailscale tunnel 100.x.y.z active, 0 packet loss"
    )

    private suspend fun getConnectedSftp(config: ServerConfig): ChannelSftp = withContext(Dispatchers.IO) {
        val currentSftp = activeChannelSftp
        if (currentSftp != null && currentSftp.isConnected && activeSession?.isConnected == true) {
            return@withContext currentSftp
        }

        val jsch = JSch()
        val session = jsch.getSession(config.sshUser, config.sshHost, config.sshPort)
        session.setPassword(config.sshPassword)
        session.setConfig("StrictHostKeyChecking", "no")
        session.connect(15000)

        val channel = session.openChannel("sftp") as ChannelSftp
        channel.connect(10000)

        activeSession = session
        activeChannelSftp = channel
        channel
    }

    suspend fun listFiles(
        currentPath: String,
        config: ServerConfig,
        useSandbox: Boolean = false
    ): Result<List<RemoteFileItem>> = withContext(Dispatchers.IO) {
        if (useSandbox || !config.isSshConfigured) {
            val list = sandboxFiles[currentPath] ?: mutableListOf()
            // Sorted: folders first, then files alphabetically
            val sorted = list.sortedWith(compareBy<RemoteFileItem> { !it.isDirectory }.thenBy { it.name.lowercase() })
            return@withContext Result.success(sorted)
        }

        try {
            val sftp = getConnectedSftp(config)
            val path = if (currentPath.isBlank()) sftp.home else currentPath
            @Suppress("UNCHECKED_CAST")
            val vector = sftp.ls(path) as Vector<ChannelSftp.LsEntry>

            val resultList = mutableListOf<RemoteFileItem>()
            for (entry in vector) {
                val filename = entry.filename
                if (filename == "." || filename == "..") continue
                val attrs = entry.attrs
                val isDir = attrs.isDir
                val size = attrs.size
                val mTime = attrs.mTime.toLong()
                val perms = attrs.permissionsString ?: "-"
                val fullPath = if (path.endsWith("/")) "$path$filename" else "$path/$filename"

                resultList.add(
                    RemoteFileItem(
                        name = filename,
                        path = fullPath,
                        isDirectory = isDir,
                        sizeBytes = size,
                        permissions = perms,
                        modifiedTime = mTime
                    )
                )
            }

            val sorted = resultList.sortedWith(
                compareBy<RemoteFileItem> { !it.isDirectory }.thenBy { it.name.lowercase() }
            )
            Result.success(sorted)
        } catch (e: Exception) {
            Result.failure(e)
        }
    }

    suspend fun createDirectory(
        parentPath: String,
        folderName: String,
        config: ServerConfig,
        useSandbox: Boolean = false
    ): Result<Unit> = withContext(Dispatchers.IO) {
        val targetPath = if (parentPath.endsWith("/")) "$parentPath$folderName" else "$parentPath/$folderName"
        if (useSandbox || !config.isSshConfigured) {
            val list = sandboxFiles.getOrPut(parentPath) { mutableListOf() }
            list.add(
                RemoteFileItem(
                    name = folderName,
                    path = targetPath,
                    isDirectory = true,
                    sizeBytes = 4096,
                    permissions = "drwxr-xr-x",
                    modifiedTime = System.currentTimeMillis() / 1000
                )
            )
            sandboxFiles[targetPath] = mutableListOf()
            return@withContext Result.success(Unit)
        }

        try {
            val sftp = getConnectedSftp(config)
            sftp.mkdir(targetPath)
            Result.success(Unit)
        } catch (e: Exception) {
            Result.failure(e)
        }
    }

    suspend fun createTextFile(
        parentPath: String,
        fileName: String,
        content: String,
        config: ServerConfig,
        useSandbox: Boolean = false
    ): Result<Unit> = withContext(Dispatchers.IO) {
        val targetPath = if (parentPath.endsWith("/")) "$parentPath$fileName" else "$parentPath/$fileName"
        if (useSandbox || !config.isSshConfigured) {
            val list = sandboxFiles.getOrPut(parentPath) { mutableListOf() }
            list.add(
                RemoteFileItem(
                    name = fileName,
                    path = targetPath,
                    isDirectory = false,
                    sizeBytes = content.toByteArray().size.toLong(),
                    permissions = "-rw-r--r--",
                    modifiedTime = System.currentTimeMillis() / 1000
                )
            )
            sandboxFileContents[targetPath] = content
            return@withContext Result.success(Unit)
        }

        try {
            val sftp = getConnectedSftp(config)
            val bytes = content.toByteArray(Charsets.UTF_8)
            ByteArrayInputStream(bytes).use { inStream ->
                sftp.put(inStream, targetPath)
            }
            Result.success(Unit)
        } catch (e: Exception) {
            Result.failure(e)
        }
    }

    suspend fun rename(
        oldPath: String,
        newName: String,
        config: ServerConfig,
        useSandbox: Boolean = false
    ): Result<Unit> = withContext(Dispatchers.IO) {
        val parent = oldPath.substringBeforeLast("/", "")
        val newPath = if (parent.isEmpty()) newName else "$parent/$newName"

        if (useSandbox || !config.isSshConfigured) {
            val list = sandboxFiles[parent]
            val itemIndex = list?.indexOfFirst { it.path == oldPath } ?: -1
            if (itemIndex >= 0 && list != null) {
                val old = list[itemIndex]
                list[itemIndex] = old.copy(name = newName, path = newPath)
                if (sandboxFileContents.containsKey(oldPath)) {
                    val content = sandboxFileContents.remove(oldPath) ?: ""
                    sandboxFileContents[newPath] = content
                }
            }
            return@withContext Result.success(Unit)
        }

        try {
            val sftp = getConnectedSftp(config)
            sftp.rename(oldPath, newPath)
            Result.success(Unit)
        } catch (e: Exception) {
            Result.failure(e)
        }
    }

    suspend fun delete(
        path: String,
        isDirectory: Boolean,
        config: ServerConfig,
        useSandbox: Boolean = false
    ): Result<Unit> = withContext(Dispatchers.IO) {
        if (useSandbox || !config.isSshConfigured) {
            val parent = path.substringBeforeLast("/", "")
            val list = sandboxFiles[parent]
            list?.removeAll { it.path == path }
            sandboxFiles.remove(path)
            sandboxFileContents.remove(path)
            return@withContext Result.success(Unit)
        }

        try {
            val sftp = getConnectedSftp(config)
            if (isDirectory) {
                sftp.rmdir(path)
            } else {
                sftp.rm(path)
            }
            Result.success(Unit)
        } catch (e: Exception) {
            Result.failure(e)
        }
    }

    suspend fun readFileContent(
        remotePath: String,
        config: ServerConfig,
        useSandbox: Boolean = false
    ): Result<String> = withContext(Dispatchers.IO) {
        if (useSandbox || !config.isSshConfigured) {
            val content = sandboxFileContents[remotePath] ?: "[Konten file kosong atau binary]"
            return@withContext Result.success(content)
        }

        try {
            val sftp = getConnectedSftp(config)
            val outputStream = ByteArrayOutputStream()
            sftp.get(remotePath, outputStream)
            val content = outputStream.toString("UTF-8")
            Result.success(content)
        } catch (e: Exception) {
            Result.failure(e)
        }
    }

    suspend fun downloadToLocal(
        remotePath: String,
        targetFile: File,
        config: ServerConfig,
        useSandbox: Boolean = false
    ): Result<File> = withContext(Dispatchers.IO) {
        if (useSandbox || !config.isSshConfigured) {
            val content = sandboxFileContents[remotePath] ?: "Sample file content downloaded from homeserver sandbox"
            targetFile.writeText(content)
            return@withContext Result.success(targetFile)
        }

        try {
            val sftp = getConnectedSftp(config)
            FileOutputStream(targetFile).use { fos ->
                sftp.get(remotePath, fos)
            }
            Result.success(targetFile)
        } catch (e: Exception) {
            Result.failure(e)
        }
    }

    fun close() {
        try {
            activeChannelSftp?.disconnect()
        } catch (_: Exception) {}
        activeChannelSftp = null

        try {
            activeSession?.disconnect()
        } catch (_: Exception) {}
        activeSession = null
    }
}
