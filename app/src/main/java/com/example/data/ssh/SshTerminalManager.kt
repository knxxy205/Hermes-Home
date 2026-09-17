package com.example.data.ssh

import androidx.compose.ui.text.AnnotatedString
import com.example.data.model.ServerConfig
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.flatMapLatest
import kotlinx.coroutines.flow.stateIn

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
        val firstSession = SshTerminalSession(initialTitle = "bash 1", coroutineScope = coroutineScope)
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
}
