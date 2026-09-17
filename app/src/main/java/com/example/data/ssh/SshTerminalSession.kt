package com.example.data.ssh

import androidx.compose.ui.text.AnnotatedString
import com.example.data.model.ServerConfig
import com.jcraft.jsch.ChannelShell
import com.jcraft.jsch.JSch
import com.jcraft.jsch.Session
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.io.InputStream
import java.io.OutputStream
import java.util.UUID

class SshTerminalSession(
    val id: String = UUID.randomUUID().toString(),
    initialTitle: String = "bash",
    private val coroutineScope: CoroutineScope
) {
    private val _title = MutableStateFlow(initialTitle)
    val title: StateFlow<String> = _title.asStateFlow()

    fun setTitle(newTitle: String) {
        _title.value = newTitle
    }

    private var session: Session? = null
    private var channel: ChannelShell? = null
    private var outputStream: OutputStream? = null
    private var readJob: Job? = null

    private val _connectionState = MutableStateFlow<TerminalConnectionState>(TerminalConnectionState.Disconnected)
    val connectionState: StateFlow<TerminalConnectionState> = _connectionState.asStateFlow()

    private val terminalBuffer = TerminalScreenBuffer(maxCols = 80, maxRows = 24)

    private val _isInteractiveMode = MutableStateFlow(false)
    val isInteractiveMode: StateFlow<Boolean> = _isInteractiveMode.asStateFlow()

    private val initialWelcome = "TailHarness v1.0.0 [Session: ${_title.value}]\n" +
            "Ready. Configure credentials in Settings or tap Connect.\n\n"

    private val _terminalOutput = MutableStateFlow(initialWelcome)
    val terminalOutput: StateFlow<String> = _terminalOutput.asStateFlow()

    private val _terminalAnnotatedOutput = MutableStateFlow(AnnotatedString(initialWelcome))
    val terminalAnnotatedOutput: StateFlow<AnnotatedString> = _terminalAnnotatedOutput.asStateFlow()

    private var isSandboxMode = false
    private var isSandboxNano = false

    init {
        terminalBuffer.onInteractiveModeChanged = { interactive ->
            _isInteractiveMode.value = interactive
        }
    }

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
                newChannel.setPtyType("xterm-256color", 80, 24, 640, 480)
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
                    val read = inputStream.read(buffer)
                    if (read == -1) break
                    if (read > 0) {
                        val text = String(buffer, 0, read, Charsets.UTF_8)
                        withContext(Dispatchers.Main) {
                            appendOutput(text)
                        }
                    }
                }
            } catch (e: Exception) {
                // stream closed or disconnected
            } finally {
                withContext(Dispatchers.Main) {
                    if (_connectionState.value is TerminalConnectionState.Connected) {
                        _connectionState.value = TerminalConnectionState.Disconnected
                        appendOutput("\n\u001B[33m[DISCONNECTED] Sesi SSH ditutup.\u001B[0m\n")
                    }
                }
                disconnectInternal()
            }
        }
    }

    fun startSandboxMode() {
        disconnectInternal()
        isSandboxMode = true
        isSandboxNano = false
        _connectionState.value = TerminalConnectionState.Connected("homeserver.tailscale", "admin")
        _terminalOutput.value = ""
        terminalBuffer.clear()
        _isInteractiveMode.value = false
        appendOutput("\u001B[36m┌──────────────────────────────────────────────────────────┐\u001B[0m\n")
        appendOutput("\u001B[36m│       TailHarness Tailscale SSH Sandbox [Active]         │\u001B[0m\n")
        appendOutput("\u001B[36m│       Mode simulasi interaktif tanpa koneksi server.      │\u001B[0m\n")
        appendOutput("\u001B[36m└──────────────────────────────────────────────────────────┘\u001B[0m\n\n")
        appendOutput("\u001B[32madmin@homeserver:~$\u001B[0m ")
    }

    fun sendCommand(command: String) {
        // Auto-update tab title based on primary command
        val firstWord = command.trim().split(" ").firstOrNull() ?: ""
        if (firstWord.isNotBlank() && firstWord.length in 2..12) {
            _title.value = firstWord
        }

        if (isSandboxMode) {
            handleSandboxCommand(command)
            return
        }

        val out = outputStream
        if (out != null && channel?.isConnected == true) {
            coroutineScope.launch(Dispatchers.IO) {
                try {
                    // Send carriage return (\r) - standard Enter in terminal PTY
                    out.write((command + "\r").toByteArray(Charsets.UTF_8))
                    out.flush()
                } catch (e: Exception) {
                    withContext(Dispatchers.Main) {
                        appendOutput("\n\u001B[31m[SEND FAILED] ${e.localizedMessage}\u001B[0m\n")
                    }
                }
            }
        } else {
            appendOutput("\n\u001B[31m[ERROR] Not connected. Connect SSH first.\u001B[0m\n")
        }
    }

    fun sendRawBytes(bytes: ByteArray) {
        if (isSandboxMode) {
            if (isSandboxNano) {
                if (bytes.contentEquals(byteArrayOf(0x0F))) {
                    // Ctrl+O in simulated nano (Write Out)
                    appendOutput("\n[ File /root/.ssh/authorized_keys saved! (1 line written) ]\n")
                } else if (bytes.contentEquals(byteArrayOf(0x18))) {
                    // Ctrl+X in simulated nano (Exit)
                    isSandboxNano = false
                    _isInteractiveMode.value = false
                    appendOutput("\n\u001B[32madmin@homeserver:~$\u001B[0m ")
                } else if (bytes.contentEquals(byteArrayOf(0x0D))) {
                    // Enter in simulated nano
                    appendOutput("\n")
                }
                return
            }

            // Handle basic shortcuts in sandbox
            if (bytes.contentEquals(byteArrayOf(0x03))) {
                appendOutput("^C\n\u001B[32madmin@homeserver:~$\u001B[0m ")
            } else if (bytes.contentEquals(byteArrayOf(0x0D))) {
                appendOutput("\n\u001B[32madmin@homeserver:~$\u001B[0m ")
            }
            return
        }

        val out = outputStream
        if (out != null && channel?.isConnected == true) {
            coroutineScope.launch(Dispatchers.IO) {
                try {
                    out.write(bytes)
                    out.flush()
                } catch (_: Exception) {}
            }
        }
    }

    fun sendKeyUp() = sendRawBytes(byteArrayOf(0x1B, '['.code.toByte(), 'A'.code.toByte()))
    fun sendKeyDown() = sendRawBytes(byteArrayOf(0x1B, '['.code.toByte(), 'B'.code.toByte()))
    fun sendKeyRight() = sendRawBytes(byteArrayOf(0x1B, '['.code.toByte(), 'C'.code.toByte()))
    fun sendKeyLeft() = sendRawBytes(byteArrayOf(0x1B, '['.code.toByte(), 'D'.code.toByte()))
    fun sendKeyEsc() = sendRawBytes(byteArrayOf(0x1B))
    fun sendKeyTab() = sendRawBytes(byteArrayOf(0x09))
    fun sendKeyEnter() = sendRawBytes(byteArrayOf(0x0D)) // Enter (\r, Carriage Return)
    fun sendKeyCtrlC() = sendRawBytes(byteArrayOf(0x03)) // Ctrl+C (Interrupt)
    fun sendKeyCtrlO() = sendRawBytes(byteArrayOf(0x0F)) // Ctrl+O (Nano Write Out / Save)
    fun sendKeyCtrlX() = sendRawBytes(byteArrayOf(0x18)) // Ctrl+X (Nano Exit)
    fun sendKeyAlt() = sendRawBytes(byteArrayOf(0x1B, 0x1B))

    fun sendKeyCtrl(char: Char) {
        val upper = char.uppercaseChar()
        if (upper in 'A'..'Z') {
            val byteVal = (upper.code - 64).toByte()
            sendRawBytes(byteArrayOf(byteVal))
        }
    }

    fun clearBuffer() {
        _terminalOutput.value = ""
        terminalBuffer.clear()
        _isInteractiveMode.value = false
        _terminalAnnotatedOutput.value = AnnotatedString("")
    }

    private fun appendOutput(newText: String) {
        val current = _terminalOutput.value
        val updated = if (current.length > 50000) {
            current.takeLast(30000) + newText
        } else {
            current + newText
        }
        _terminalOutput.value = updated

        // Process in virtual terminal buffer
        terminalBuffer.process(newText)
        _isInteractiveMode.value = terminalBuffer.isInteractive()
        _terminalAnnotatedOutput.value = terminalBuffer.render()
    }

    private fun handleSandboxCommand(cmd: String) {
        val trimmed = cmd.trim()

        if (isSandboxNano) {
            appendOutput("$cmd\n")
            return
        }

        appendOutput("$cmd\n")

        if (trimmed.startsWith("nano")) {
            isSandboxNano = true
            _isInteractiveMode.value = true
            val filename = trimmed.removePrefix("nano").trim().ifBlank { "/root/.ssh/authorized_keys" }
            appendOutput(
                "\u001B[?1049h\u001B[2J\u001B[1;1H\u001B[7m  GNU nano 8.4                 $filename                 \u001B[0m\n" +
                "ssh-ed25519 AAAAC3NzaC1lZDI1NTE5AAAAIExampleTailscaleKey root@homeserver\n\n\n\n\n\n" +
                "\u001B[23;1H\u001B[7m^G\u001B[0m Get Help  \u001B[7m^O\u001B[0m WriteOut  \u001B[7m^W\u001B[0m Where Is  \u001B[7m^K\u001B[0m Cut       \u001B[7m^T\u001B[0m Execute   \u001B[7m^C\u001B[0m Location\n" +
                "\u001B[24;1H\u001B[7m^X\u001B[0m Exit      \u001B[7m^R\u001B[0m Read File \u001B[7m^\\\u001B[0m Replace   \u001B[7m^U\u001B[0m Paste     \u001B[7m^J\u001B[0m Justify   \u001B[7m^/\u001B[0m Go To Line\n"
            )
            return
        }
        val response = when {
            trimmed == "uname -a" ->
                "Linux homeserver-node 6.8.0-45-generic #45-Ubuntu SMP PREEMPT_DYNAMIC x86_64 GNU/Linux\n"
            trimmed == "docker ps" ->
                "CONTAINER ID   IMAGE                 COMMAND                  CREATED        STATUS        PORTS\n" +
                "9f2b8104c81a   tailscale/tailscale   \"/usr/local/bin/cont…\"   3 days ago     Up 3 days     \n" +
                "a18bc09e1201   nginx:alpine          \"/docker-entrypoint.…\"   5 days ago     Up 5 days     0.0.0.0:80->80/tcp\n" +
                "378a9c801df4   ollama/ollama         \"ollama serve\"           10 days ago    Up 10 days    0.0.0.0:11434->11434/tcp\n"
            trimmed == "df -h" ->
                "Filesystem      Size  Used Avail Use% Mounted on\n" +
                "/dev/nvme0n1p2  915G  142G  727G  17% /\n" +
                "tmpfs            16G  1.2M   16G   1% /run\n" +
                "/dev/sda1       3.6T  2.1T  1.4T  61% /mnt/storage\n"
            trimmed == "ls -la" || trimmed == "ls" ->
                "total 36\n" +
                "drwxr-xr-x 5 admin admin 4096 Sep 16 12:00 .\n" +
                "drwxr-xr-x 3 root  root  4096 Aug 01 10:15 ..\n" +
                "-rw------- 1 admin admin  984 Sep 16 11:30 .bash_history\n" +
                "drwx------ 2 admin admin 4096 Aug 01 10:20 .ssh\n" +
                "drwxr-xr-x 4 admin admin 4096 Aug 15 14:00 docker-compose\n" +
                "-rw-r--r-- 1 admin admin  245 Sep 01 08:30 tailscale_setup.sh\n"
            trimmed == "pwd" ->
                "/home/admin\n"
            trimmed == "free -h" ->
                "               total        used        free      shared  buff/cache   available\n" +
                "Mem:            31Gi       8.4Gi        18Gi       210Mi       5.1Gi        22Gi\n" +
                "Swap:          4.0Gi          0B       4.0Gi\n"
            trimmed == "uptime" ->
                " 17:00:15 up 14 days,  6:23,  2 users,  load average: 0.18, 0.22, 0.19\n"
            trimmed == "systemctl status" || trimmed.startsWith("systemctl status") ->
                "● homeserver-node\n" +
                "    State: running\n" +
                "     Jobs: 0 queued\n" +
                "   Failed: 0 units\n" +
                "    Since: Mon 2026-09-02 10:37:15 UTC; 14 days ago\n"
            trimmed == "tailscale status" ->
                "100.112.44.89   homeserver           admin@       linux   active; direct\n" +
                "100.84.19.23    pixel-phone          admin@       android active; direct\n" +
                "100.99.10.5     work-laptop          admin@       macos   idle\n"
            trimmed.startsWith("cat ") ->
                "Sample content of ${trimmed.removePrefix("cat ").trim()}:\n# Tailscale SSH managed host\nAllowUsers admin\n"
            trimmed == "clear" -> {
                clearBuffer()
                appendOutput("\u001B[32madmin@homeserver:~$\u001B[0m ")
                return
            }
            trimmed.isEmpty() -> ""
            else ->
                "bash: $trimmed: command simulated in sandbox mode. Try: docker ps, df -h, free -h, uptime, ls -la, nano\n"
        }
        appendOutput(response)
        appendOutput("\u001B[32madmin@homeserver:~$\u001B[0m ")
    }

    fun disconnect() {
        disconnectInternal()
        _connectionState.value = TerminalConnectionState.Disconnected
        appendOutput("\n\u001B[33m[DISCONNECTED] Sesi SSH dihentikan oleh pengguna.\u001B[0m\n")
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

    fun destroy() {
        disconnectInternal()
    }
}
