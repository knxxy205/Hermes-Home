package com.example.data.ssh

sealed interface TerminalConnectionState {
    object Disconnected : TerminalConnectionState
    object Connecting : TerminalConnectionState
    data class Connected(val host: String, val user: String) : TerminalConnectionState
    data class Error(val message: String) : TerminalConnectionState
}
