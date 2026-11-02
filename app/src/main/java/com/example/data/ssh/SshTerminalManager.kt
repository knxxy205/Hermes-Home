package com.example.data.ssh

import androidx.compose.ui.text.AnnotatedString
import com.example.data.model.ServerConfig
import com.jcraft.jsch.ChannelExec
import com.jcraft.jsch.JSch
import com.jcraft.jsch.Session
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.flatMapLatest
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.withContext

@OptIn(ExperimentalCoroutinesApi::class)
class SshTerminalManager(
    externalScope: CoroutineScope? = null
) {
    private val coroutineScope = externalScope ?: CoroutineScope(kotlinx.coroutines.SupervisorJob() + Dispatchers.IO)

    private val _sessions = MutableStateFlow<List<SshTerminalSession>>(emptyList())
    val sessions: StateFlow<List<SshTerminalSession>> = _sessions.asStateFlow()

    private val _activeSessionIndex = MutableStateFlow(0)
    val activeSessionIndex: StateFlow<Int> = _activeSessionIndex.asStateFlow()

    private val _activeSession = MutableStateFlow<SshTerminalSession>(
        SshTerminalSession(initialTitle = "bash 1", coroutineScope = coroutineScope)
    )
    val activeSession: StateFlow<SshTerminalSession> = _activeSession.asStateFlow()

    init {
        val firstSession = _activeSession.value
        _sessions.value = listOf(firstSession)
        _activeSession.value = firstSession
    }

    // Delegated state flows to active session for transparent backward-compatibility
    val connectionState: StateFlow<TerminalConnectionState> = _activeSession
        .flatMapLatest { it.connectionState }
        .stateIn(coroutineScope, SharingStarted.Eagerly, TerminalConnectionState.Disconnected)

    val terminalOutput: StateFlow<String> = _activeSession
        .flatMapLatest { it.terminalOutput }
        .stateIn(coroutineScope, SharingStarted.Eagerly, "")

    val terminalAnnotatedOutput: StateFlow<AnnotatedString> = _activeSession
        .flatMapLatest { it.terminalAnnotatedOutput }
        .stateIn(coroutineScope, SharingStarted.Eagerly, AnnotatedString(""))

    val isInteractiveMode: StateFlow<Boolean> = _activeSession
        .flatMapLatest { it.isInteractiveMode }
        .stateIn(coroutineScope, SharingStarted.Eagerly, false)

    fun createSession(config: ServerConfig? = null, title: String? = null): SshTerminalSession {
        val currentList = _sessions.value
        if (currentList.size >= 5) {
            return _activeSession.value
        }
        val sessionNum = currentList.size + 1
        val newTitle = title ?: "bash $sessionNum"
        val newSession = SshTerminalSession(initialTitle = newTitle, coroutineScope = coroutineScope)
        
        val updated = currentList + newSession
        _sessions.value = updated
        val newIndex = updated.lastIndex
        _activeSessionIndex.value = newIndex
        _activeSession.value = newSession

        if (config != null && config.isSshConfigured) {
            newSession.connect(config)
        }
        return newSession
    }

    fun selectSession(index: Int) {
        val list = _sessions.value
        if (index in list.indices) {
            _activeSessionIndex.value = index
            _activeSession.value = list[index]
        }
    }

    fun closeSession(index: Int) {
        val list = _sessions.value
        if (list.size <= 1) return // Keep at least one session

        if (index in list.indices) {
            val closingSession = list[index]
            closingSession.destroy()

            val updated = list.toMutableList()
            updated.removeAt(index)
            _sessions.value = updated

            val newIndex = if (_activeSessionIndex.value >= updated.size) {
                updated.lastIndex
            } else if (_activeSessionIndex.value == index) {
                (index - 1).coerceAtLeast(0)
            } else {
                _activeSessionIndex.value.coerceAtMost(updated.lastIndex)
            }
            _activeSessionIndex.value = newIndex
            _activeSession.value = updated[newIndex]
        }
    }

    // Forwarding functions to active session
    fun connect(config: ServerConfig) {
        _activeSession.value.connect(config)
    }

    fun disconnect() {
        _activeSession.value.disconnect()
    }

    fun startSandboxMode() {
        _activeSession.value.startSandboxMode()
    }

    fun sendCommand(command: String) {
        _activeSession.value.sendCommand(command)
    }

    fun sendRawBytes(bytes: ByteArray) {
        _activeSession.value.sendRawBytes(bytes)
    }

    fun sendKeyUp() = _activeSession.value.sendKeyUp()
    fun sendKeyDown() = _activeSession.value.sendKeyDown()
    fun sendKeyRight() = _activeSession.value.sendKeyRight()
    fun sendKeyLeft() = _activeSession.value.sendKeyLeft()
    fun sendKeyEsc() = _activeSession.value.sendKeyEsc()
    fun sendKeyTab() = _activeSession.value.sendKeyTab()
    fun sendKeyEnter() = _activeSession.value.sendKeyEnter()
    fun sendKeyCtrlC() = _activeSession.value.sendKeyCtrlC()
    fun sendKeyCtrlO() = _activeSession.value.sendKeyCtrlO()
    fun sendKeyCtrlX() = _activeSession.value.sendKeyCtrlX()
    fun sendKeyAlt() = _activeSession.value.sendKeyAlt()
    fun sendKeyCtrl(char: Char) = _activeSession.value.sendKeyCtrl(char)

    fun clearBuffer() {
        _activeSession.value.clearBuffer()
    }

    /**
     * Executes a single command on the remote server via SSH ChannelExec
     * and captures full stdout/stderr text output.
     * Also writes into active session buffer for transparency.
     */
    suspend fun executeCommand(config: ServerConfig, command: String): Result<String> = withContext(Dispatchers.IO) {
        val trimmedCmd = command.trim()
        if (trimmedCmd.isBlank()) {
            return@withContext Result.failure(IllegalArgumentException("Perintah bash kosong"))
        }

        if (!config.isSshConfigured) {
            // Provide simulated output if SSH credentials are not yet saved
            val mock = simulateCommandOutput(trimmedCmd)
            return@withContext Result.success(mock)
        }

        var execSession: Session? = null
        var execChannel: ChannelExec? = null

        try {
            val jsch = JSch()
            val trust = com.example.data.security.SshTrust.configure(jsch)
            execSession = jsch.getSession(config.sshUser, config.sshHost, config.sshPort).apply {
                setPassword(config.sshPassword)
                userInfo = trust
                setConfig("StrictHostKeyChecking", "ask")
                setConfig("PreferredAuthentications", "password,keyboard-interactive")
                connect(10000)
            }

            execChannel = (execSession.openChannel("exec") as ChannelExec).apply {
                setCommand(trimmedCmd)
                inputStream = null
                setErrStream(null)
            }

            val stdoutStream = execChannel.inputStream
            val stderrStream = execChannel.errStream

            execChannel.connect(10000)

            val output = StringBuilder()
            val buffer = ByteArray(2048)
            val startTime = System.currentTimeMillis()

            while (!execChannel.isClosed && System.currentTimeMillis() - startTime < 30000) {
                while (stdoutStream.available() > 0) {
                    val len = stdoutStream.read(buffer)
                    if (len > 0) output.append(String(buffer, 0, len, Charsets.UTF_8))
                }
                while (stderrStream.available() > 0) {
                    val len = stderrStream.read(buffer)
                    if (len > 0) output.append(String(buffer, 0, len, Charsets.UTF_8))
                }
                if (execChannel.isClosed) break
                Thread.sleep(80)
            }

            // Flush remaining data
            while (stdoutStream.available() > 0) {
                val len = stdoutStream.read(buffer)
                if (len > 0) output.append(String(buffer, 0, len, Charsets.UTF_8))
            }
            while (stderrStream.available() > 0) {
                val len = stderrStream.read(buffer)
                if (len > 0) output.append(String(buffer, 0, len, Charsets.UTF_8))
            }

            val resultStr = output.toString().trim()
            val exitCode = execChannel.exitStatus
            val finalOutput = if (resultStr.isNotBlank()) {
                resultStr
            } else if (exitCode == 0) {
                "OK (Perintah selesai dengan exit code 0 tanpa output teks)"
            } else {
                "Exit code $exitCode"
            }

            Result.success(finalOutput)
        } catch (e: Exception) {
            // If live connection fails (e.g., test environment), return informative error or sandbox fallback
            Result.failure(Exception("Gagal eksekusi SSH di ${config.sshHost}: ${e.localizedMessage}"))
        } finally {
            try { execChannel?.disconnect() } catch (_: Exception) {}
            try { execSession?.disconnect() } catch (_: Exception) {}
        }
    }

    private fun simulateCommandOutput(cmd: String): String {
        val trimmed = cmd.trim()
        return when {
            trimmed.contains("docker ps") -> """
CONTAINER ID   IMAGE                 COMMAND                  STATUS         PORTS                    NAMES
a4f91b0c12e8   tailscale/tailscale   "/usr/local/bin/con…"   Up 4 days      41641/udp                tailscale-daemon
8e9102ab3c4d   beszel/agent          "/beszel-agent"          Up 12 days     0.0.0.0:45876->45876/tcp beszel-agent
f1c2d3e4a5b6   ollama/ollama         "/bin/ollama serve"      Up 6 hours     0.0.0.0:11434->11434/tcp ollama-hermes
33a18b9c0d1e   casaos/gateway        "/casaos-gateway"        Up 14 days     0.0.0.0:80->80/tcp       casaos-app
            """.trimIndent()

            trimmed.contains("uptime") -> " 14:32:01 up 14 days, 3:28, 2 users, load average: 0.18, 0.24, 0.29"

            trimmed.contains("free") -> """
               total        used        free      shared  buff/cache   available
Mem:        31984216     6214820    18420112      312400     7349284    25456996
Swap:        8388604           0     8388604
            """.trimIndent()

            trimmed.contains("df") -> """
Filesystem      Size  Used Avail Use% Mounted on
/dev/nvme0n1p2  938G  142G  749G  16% /
/dev/sda1       3.7T  1.8T  1.8T  50% /mnt/storage
            """.trimIndent()

            trimmed.contains("tailscale status") -> """
100.115.92.40   homeserver           admin@       linux   -
100.98.12.14    hermes-mobile        fadjri@      android active; direct 192.168.1.100:41641, tx 42MB rx 180MB
            """.trimIndent()

            trimmed.contains("ls") -> """
total 32
drwxr-xr-x 5 admin admin 4096 Sep 17 08:00 .
drwxr-xr-x 3 root  root  4096 Sep  1 12:00 ..
drwxr-xr-x 4 admin admin 4096 Sep 10 14:22 docker-compose
-rw-r--r-- 1 admin admin  420 Sep 15 19:10 hermes.env
drwxr-xr-x 2 admin admin 4096 Sep 16 22:00 logs
            """.trimIndent()

            else -> "Eksekusi berhasil pada $(hostname): $trimmed"
        }
    }
}
