package com.example

import android.content.Context
import android.view.inputmethod.EditorInfo
import androidx.test.core.app.ApplicationProvider
import com.example.data.model.ServerConfig
import com.example.data.security.SshTrust
import com.example.data.ssh.SshTerminalSession
import com.example.data.ssh.TerminalConnectionState
import com.example.ui.terminal.TerminalView
import kotlinx.coroutines.*
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.test.setMain
import kotlinx.coroutines.test.resetMain
import org.apache.sshd.server.SshServer
import org.apache.sshd.server.keyprovider.SimpleGeneratorHostKeyProvider
import org.junit.Assert.*
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.annotation.Config

@OptIn(ExperimentalCoroutinesApi::class)
@RunWith(SshAwareRobolectricTestRunner::class)
@Config(sdk = [34])
class SshTerminalIntegrationTest {
    @Test fun nativeImeTypingResizeAndReconnectKeepSshUsable() = runBlocking {
        Dispatchers.setMain(Dispatchers.Unconfined)
        val context = ApplicationProvider.getApplicationContext<Context>()
        SshTrust.initialize(context)
        val server = SshServer.setUpDefaultServer().apply {
            host = "127.0.0.1"
            port = 0
            keyPairProvider = SimpleGeneratorHostKeyProvider().apply { algorithm = "RSA"; keySize = 2048 }
            setPasswordAuthenticator { user, password, _ -> user == "test" && password == "test-password" }
            setShellFactory { EchoShell() }
            start()
        }
        val scope = CoroutineScope(SupervisorJob() + Dispatchers.IO)
        val terminal = SshTerminalSession(coroutineScope = scope)
        val trust = scope.launch { SshTrust.pending.collect { it?.answer?.complete(true) } }
        val config = ServerConfig("127.0.0.1", server.port, "test", "test-password")
        fun output() = synchronized(terminal.engine.lock) { terminal.engine.emulator.screen.transcriptText }
        suspend fun awaitOutput(text: String) = withTimeout(10_000) {
            while (!output().contains(text)) delay(20)
        }
        try {
            terminal.connect(config)
            waitConnected(terminal)
            awaitOutput("ready>")
            val view = TerminalView(context).apply { session = terminal; layout(0, 0, 720, 1000) }
            val input = view.onCreateInputConnection(EditorInfo())
            input.setComposingText("l", 1)
            input.setComposingText("ls", 1)
            input.commitText("ls", 1)
            input.performEditorAction(EditorInfo.IME_ACTION_NONE)
            awaitOutput("ls\nACK")
            // Repeated keyboard-sized resizes while traffic is flowing.
            repeat(20) { i ->
                terminal.resize(60, if (i % 2 == 0) 10 else 24)
                input.commitText("x", 1)
                delay(25)
            }
            delay(300)
            assertTrue(terminal.connectionState.value is TerminalConnectionState.Connected)
            input.commitText("héllo界", 1)
            input.performEditorAction(EditorInfo.IME_ACTION_NONE)
            awaitOutput("héllo界")
            assertFalse(output().contains("lls"))
            terminal.disconnect()
            terminal.connect(config)
            waitConnected(terminal)
            terminal.sendRawBytes("after-reconnect\r".toByteArray())
            awaitOutput("after-reconnect")
            assertTrue(terminal.connectionState.value is TerminalConnectionState.Connected)
        } finally {
            terminal.destroy()
            trust.cancel()
            scope.cancel()
            server.stop(true)
            Dispatchers.resetMain()
        }
    }

    private suspend fun waitConnected(terminal: SshTerminalSession) = withTimeout(15_000) {
        while (terminal.connectionState.value !is TerminalConnectionState.Connected) delay(20)
    }
}
