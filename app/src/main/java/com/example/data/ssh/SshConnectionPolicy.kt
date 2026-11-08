package com.example.data.ssh

import com.jcraft.jsch.Session

internal object SshConnectionPolicy {
    fun configure(session: Session) {
        session.serverAliveInterval = 20_000
        session.serverAliveCountMax = 3
    }

    fun retryDelay(attempt: Int): Long? = if (attempt in 1..6) (2_000L shl (attempt - 1)).coerceAtMost(30_000L) else null

    fun canRetry(error: Throwable): Boolean {
        val message = error.message.orEmpty().lowercase()
        return listOf("auth fail", "auth cancel", "authentication", "permission denied", "hostkey", "host key", "reject host", "algorithm negotiation").none(message::contains)
    }
}
