package com.example

import com.example.data.ssh.SshConnectionPolicy
import com.jcraft.jsch.JSch
import org.junit.Assert.*
import org.junit.Test

class ConnectionPolicyTest {
    @Test fun keepAliveRetainsTheSocketTimer() {
        val session = JSch().getSession("fixture", "localhost", 22)
        SshConnectionPolicy.configure(session)
        assertEquals(20_000, session.serverAliveInterval)
        assertEquals(20_000, session.timeout)
        assertEquals(3, session.serverAliveCountMax)
    }

    @Test fun retryHasABoundAndExcludesTrustAndCredentials() {
        assertEquals(listOf(2000L, 4000L, 8000L, 16000L, 30000L, 30000L), (1..6).map { SshConnectionPolicy.retryDelay(it) })
        assertNull(SshConnectionPolicy.retryDelay(7))
        listOf("Auth fail", "Authentication failed", "reject HostKey", "Host key has changed", "Permission denied").forEach {
            assertFalse(it, SshConnectionPolicy.canRetry(Exception(it)))
        }
        assertTrue(SshConnectionPolicy.canRetry(java.net.SocketException("Connection reset")))
    }
}
