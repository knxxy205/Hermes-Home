package com.example

import com.example.data.ssh.SshTerminalManager
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import org.junit.Assert.*
import org.junit.Test

class TerminalSessionsTest {
    @Test fun closingAnEarlierSessionKeepsTheSelectedSessionActive() {
        val scope = CoroutineScope(SupervisorJob() + Dispatchers.Unconfined)
        val manager = SshTerminalManager(scope)
        try {
            manager.createSession(title = "Second")
            val third = manager.createSession(title = "Third")
            manager.closeSession(0)
            assertEquals(third.id, manager.activeSession.value.id)
            assertEquals(1, manager.activeSessionIndex.value)
            manager.closeSession(1)
            assertEquals("Second", manager.activeSession.value.title.value)
            manager.closeSession(0)
            assertTrue(manager.sessions.value.isEmpty())
            assertEquals("Terminal", manager.activeSession.value.title.value)
            val reopened = manager.ensureActiveSession()
            assertEquals(1, manager.sessions.value.size)
            assertNotEquals(third.id, reopened.id)
        } finally {
            manager.sessions.value.forEach { it.destroy() }
            if (manager.sessions.value.isEmpty()) manager.activeSession.value.destroy()
            scope.cancel()
        }
    }
}
