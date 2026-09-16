package com.example.data.ssh

import com.example.data.model.ServerConfig
import com.jcraft.jsch.ChannelShell
import com.jcraft.jsch.JSch
import com.jcraft.jsch.Session
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.io.InputStream
import java.io.OutputStream

sealed interface TerminalConnectionState {
    object Disconnected : TerminalConnectionState
    object Connecting : TerminalConnectionState
    data class Connected(val host: String, val user: String) : TerminalConnectionState
    data class Error(val message: String) : TerminalConnectionState
}

class SshTerminalManager(
    externalScope: CoroutineScope? = null
) {
    // Resilient IO scope that persists across tab switches and app pauses
    private val coroutineScope = externalScope ?: CoroutineScope(kotlinx.coroutines.SupervisorJob() + Dispatchers.IO)
    private var session: Session? = null
    private var channel: ChannelShell? = null
    private var outputStream: OutputStream? = null
    private var readJob: Job? = null

    private val _connectionState = MutableStateFlow<TerminalConnectionState>(TerminalConnectionState.Disconnected)
    val connectionState: StateFlow<TerminalConnectionState> = _connectionState.asStateFlow()

    private val _terminalOutput = MutableStateFlow(
        "TailHarness v1.0.0 [Tailscale SSH & Homeserver Manager]\n" +
        "Ready. Configure credentials in Settings or tap Connect.\n\n"
    )
    val terminalOutput: StateFlow<String> = _terminalOutput.asStateFlow()

    private var isSandboxMode = false

    fun connect(config: ServerConfig) {
        if (_connectionState.value is TerminalConnectionState.Connected ||
            _connectionState.value is TerminalConnectionState.Connecting
        ) {
            return
        }

        if (!config.isSshConfigured) {
            _connectionState.value = TerminalConnectionState.Error(
                "Konfigurasi SSH belum lengkap. Buka tab Settings untuk mengisi Host, User, dan Password."
            )
            appendOutput("\u001B[31m[ERROR] SSH credentials missing in Settings.\u001B[0m\n")
            return
        }

        _connectionState.value = TerminalConnectionState.Connecting
        appendOutput("\u001B[33mConnecting to ${config.sshUser}@${config.sshHost}:${config.sshPort} via Tailscale...\u001B[0m\n")

        coroutineScope.launch(Dispatchers.IO) {
            try {
                disconnectInternal()
                isSandboxMode = false

                val jsch = JSch()
                val newSession = jsch.getSession(config.sshUser, config.sshHost, config.sshPort)
                newSession.setPassword(config.sshPassword)
                newSession.setConfig("StrictHostKeyChecking", "no")
                newSession.setConfig("PreferredAuthentications", "password,keyboard-interactive")
                newSession.setConfig("TCPKeepAlive", "yes")
                newSession.serverAliveInterval = 10000 // Send keep-alive every 10 seconds
                newSession.serverAliveCountMax = 10   // Allow up to 10 missed keep-alives
                newSession.timeout = 0                // No socket read timeout
                newSession.connect(15000)

                val newChannel = newSession.openChannel("shell") as ChannelShell
                newChannel.setPtyType("xterm-256color")
                newChannel.connect(10000)

                session = newSession
                channel = newChannel
                outputStream = newChannel.outputStream

                withContext(Dispatchers.Main) {
                    _connectionState.value = TerminalConnectionState.Connected(config.sshHost, config.sshUser)
                    appendOutput("\u001B[32m[CONNECTED] SSH session established on ${config.sshHost}\u001B[0m\n\n")
                }

                startReading(newChannel.inputStream)
            } catch (e: Exception) {
                withContext(Dispatchers.Main) {
                    val errMsg = e.localizedMessage ?: "Unknown SSH connection error"
                    _connectionState.value = TerminalConnectionState.Error(errMsg)
                    appendOutput("\u001B[31m[CONNECTION FAILED] $errMsg\u001B[0m\n")
                    appendOutput("\u001B[90mTip: Pastikan Tailscale VPN aktif di HP dan host dapat dijangkau.\u001B[0m\n")
                }
                disconnectInternal()
            }
        }
    }

    private fun startReading(inputStream: InputStream) {
        readJob?.cancel()
        readJob = coroutineScope.launch(Dispatchers.IO) {
            val buffer = ByteArray(4096)
            try {
                while (isActive) {
                    val bytesRead = inputStream.read(buffer)
                    if (bytesRead > 0) {
                        val text = String(buffer, 0, bytesRead, Charsets.UTF_8)
                        withContext(Dispatchers.Main) {
                            appendOutput(text)
                        }
                    } else if (bytesRead == -1) {
                        break
                    }
                }
            } catch (_: Exception) {
                // Channel closed or network reset
            } finally {
                withContext(Dispatchers.Main) {
                    if (_connectionState.value is TerminalConnectionState.Connected) {
                        _connectionState.value = TerminalConnectionState.Disconnected
                        appendOutput("\n\u001B[33m[DISCONNECTED] SSH session closed.\u001B[0m\n")
                    }
                }
            }
        }
    }

    fun startSandboxMode() {
        disconnect()
        isSandboxMode = true
        _connectionState.value = TerminalConnectionState.Connected("tailscale-homeserver (sandbox)", "admin")
        appendOutput("\n\u001B[36m=== TailHarness Sandbox Interactive Mode ===\u001B[0m\n")
        appendOutput("\u001B[32madmin@homeserver:~$\u001B[0m ")
    }

    fun sendCommand(command: String) {
        if (isSandboxMode) {
            handleSandboxCommand(command)
            return
        }

        val out = outputStream
        if (out != null && channel?.isConnected == true) {
            coroutineScope.launch(Dispatchers.IO) {
                try {
                    out.write((command + "\n").toByteArray(Charsets.UTF_8))
                    out.flush()
                } catch (e: Exception) {
                    withContext(Dispatchers.Main) {
                        appendOutput("\u001B[31mError writing to terminal: ${e.message}\u001B[0m\n")
                    }
                }
            }
        } else {
            appendOutput("\u001B[33m[!] Not connected. Connect to server first or enable sandbox mode.\u001B[0m\n")
        }
    }

    fun sendRawBytes(bytes: ByteArray) {
        if (isSandboxMode) {
            // Handle basic shortcut in sandbox
            if (bytes.contentEquals(byteArrayOf(0x03))) {
                appendOutput("^C\n\u001B[32madmin@homeserver:~$\u001B[0m ")
            }
            return
        }

        val out = outputStream
        if (out != null && channel?.isConnected == true) {
            coroutineScope.launch(Dispatchers.IO) {
                try {
                    out.write(bytes)
                    out.flush()
                } catch (_: Exception) {
                }
            }
        }
    }

    // Special Shortcut Key sequences
    fun sendKeyUp() = sendRawBytes(byteArrayOf(0x1B, '['.code.toByte(), 'A'.code.toByte()))
    fun sendKeyDown() = sendRawBytes(byteArrayOf(0x1B, '['.code.toByte(), 'B'.code.toByte()))
    fun sendKeyRight() = sendRawBytes(byteArrayOf(0x1B, '['.code.toByte(), 'C'.code.toByte()))
    fun sendKeyLeft() = sendRawBytes(byteArrayOf(0x1B, '['.code.toByte(), 'D'.code.toByte()))
    fun sendKeyEsc() = sendRawBytes(byteArrayOf(0x1B))
    fun sendKeyTab() = sendRawBytes(byteArrayOf(0x09))
    fun sendKeyCtrlC() = sendRawBytes(byteArrayOf(0x03))
    fun sendKeyAlt() = sendRawBytes(byteArrayOf(0x1B, 0x1B))

    fun clearBuffer() {
        _terminalOutput.value = ""
    }

    private fun appendOutput(newText: String) {
        val current = _terminalOutput.value
        // Retain scrollback up to ~50,000 characters to avoid memory exhaustion
        val updated = if (current.length > 50000) {
            current.takeLast(35000) + newText
        } else {
            current + newText
        }
        _terminalOutput.value = updated
    }

    private fun handleSandboxCommand(cmd: String) {
        appendOutput("$cmd\n")
        val trimmed = cmd.trim()
        val response = when {
            trimmed == "uname -a" ->
                "Linux homeserver-node 6.8.0-45-generic #45-Ubuntu SMP PREEMPT_DYNAMIC x86_64 GNU/Linux\n"
            trimmed == "pwd" ->
                "/home/admin\n"
            trimmed.startsWith("ls") ->
                "drwxr-xr-x 5 admin admin 4096 Sep 16 08:30 \u001B[34mdocker\u001B[0m\n" +
                "drwxr-xr-x 3 admin admin 4096 Sep 15 14:20 \u001B[34mdata\u001B[0m\n" +
                "drwxr-xr-x 2 admin admin 4096 Sep 10 11:15 \u001B[34mbackups\u001B[0m\n" +
                "-rw-r--r-- 1 admin admin  842 Sep 16 09:12 docker-compose.yml\n" +
                "-rw-r--r-- 1 admin admin  312 Sep 12 18:04 .env\n" +
                "-rwxr-xr-x 1 admin admin 1240 Sep 14 20:00 \u001B[32mbackup.sh\u001B[0m\n"
            trimmed.startsWith("docker ps") ->
                "CONTAINER ID   IMAGE                 COMMAND                  STATUS         PORTS                    NAMES\n" +
                "a1b2c3d4e5f6   hermes-agent:latest   \"python3 server.py\"     Up 3 days      0.0.0.0:11434->11434/tcp hermes-core\n" +
                "f7e8d9c0b1a2   caddy:alpine          \"caddy run --config…\"   Up 12 days     0.0.0.0:80->80/tcp       caddy-proxy\n" +
                "9876543210ab   portainer/portainer   \"/portainer\"             Up 12 days     0.0.0.0:9000->9000/tcp   portainer\n" +
                "456789abcdef   nextcloud:apache      \"/entrypoint.sh ap…\"    Up 5 days      0.0.0.0:8080->80/tcp     nextcloud-app\n"
            trimmed.startsWith("df") ->
                "Filesystem     1K-blocks      Used Available Use% Mounted on\n" +
                "/dev/nvme0n1p2 960379920 184512960 727012960  21% /\n" +
                "/dev/sda1      3844697088 1245089000 2404098088 35% /mnt/storage\n"
            trimmed.startsWith("free") ->
                "               total        used        free      shared  buff/cache   available\n" +
                "Mem:        32698944     8421044    18451200      312400     5826700    23541200\n" +
                "Swap:        8388604           0     8388604\n"
            trimmed == "uptime" ->
                " 10:45:12 up 14 days,  3:22,  2 users,  load average: 0.18, 0.24, 0.21\n"
            trimmed == "clear" -> {
                clearBuffer()
                appendOutput("\u001B[32madmin@homeserver:~$\u001B[0m ")
                return
            }
            trimmed.isEmpty() -> ""
            else -> "bash: $trimmed: command executed (sandbox response)\n"
        }
        appendOutput(response)
        appendOutput("\u001B[32madmin@homeserver:~$\u001B[0m ")
    }

    fun disconnect() {
        coroutineScope.launch(Dispatchers.IO) {
            disconnectInternal()
            withContext(Dispatchers.Main) {
                isSandboxMode = false
                _connectionState.value = TerminalConnectionState.Disconnected
                appendOutput("\n\u001B[33m[SESSION CLOSED]\u001B[0m\n")
            }
        }
    }

    private fun disconnectInternal() {
        readJob?.cancel()
        readJob = null
        try {
            outputStream?.close()
        } catch (_: Exception) {}
        outputStream = null

        try {
            channel?.disconnect()
        } catch (_: Exception) {}
        channel = null

        try {
            session?.disconnect()
        } catch (_: Exception) {}
        session = null
    }
}
