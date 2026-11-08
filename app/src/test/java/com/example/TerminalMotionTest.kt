package com.example

import android.content.Context
import android.graphics.Bitmap
import android.graphics.Canvas
import android.os.SystemClock
import android.view.MotionEvent
import androidx.test.core.app.ApplicationProvider
import com.example.data.ssh.SshTerminalSession
import com.example.ui.terminal.TerminalView
import kotlinx.coroutines.*
import org.junit.Assert.*
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.annotation.Config
import org.robolectric.annotation.GraphicsMode

@RunWith(NavigationTestRunner::class)
@Config(sdk = [28])
@GraphicsMode(GraphicsMode.Mode.NATIVE)
class TerminalMotionTest {
    @Test fun terminalMovesForSubRowDragsAndRestoresEachSessionsPixelPosition() {
        val context = ApplicationProvider.getApplicationContext<Context>()
        val scope = CoroutineScope(SupervisorJob() + Dispatchers.Unconfined)
        val first = SshTerminalSession(coroutineScope = scope)
        val second = SshTerminalSession(coroutineScope = scope)
        try {
            first.engine.append((1..150).joinToString("\r\n") { "row $it: terminal scroll fixture" }.toByteArray())
            val view = TerminalView(context).apply { session = first; layout(0, 0, 720, 640) }
            fun render() = Bitmap.createBitmap(720, 640, Bitmap.Config.ARGB_8888).also { view.draw(Canvas(it)) }
            render().recycle()
            val downTime = SystemClock.uptimeMillis()
            fun touch(action: Int, y: Float, elapsed: Long) {
                val event = MotionEvent.obtain(downTime, downTime + elapsed, action, 200f, y, 0)
                view.onTouchEvent(event)
                event.recycle()
            }
            touch(MotionEvent.ACTION_DOWN, 220f, 0)
            touch(MotionEvent.ACTION_MOVE, 280f, 100)
            val before = render()
            touch(MotionEvent.ACTION_MOVE, 283f, 200)
            val after = render()
            assertFalse("A three-pixel drag must move text without waiting for a whole row", before.sameAs(after))
            touch(MotionEvent.ACTION_CANCEL, 283f, 220)
            view.session = second
            view.session = first
            val restored = render()
            assertTrue("Switching sessions retains the fractional scroll position", after.sameAs(restored))
            before.recycle(); after.recycle(); restored.recycle()
        } finally {
            first.destroy(); second.destroy(); scope.cancel()
        }
    }

    @Test fun keyboardResizeBurstKeepsOnlyTheFinalSizeAndPreservesOutput() = runBlocking {
        val scope = CoroutineScope(SupervisorJob() + Dispatchers.Unconfined)
        val session = SshTerminalSession(coroutineScope = scope)
        try {
            session.engine.append("output before keyboard\r\n".toByteArray())
            repeat(20) { session.resize(60, 30 - it) }
            delay(250)
            synchronized(session.engine.lock) {
                assertEquals(60, session.engine.emulator.mColumns)
                assertEquals(11, session.engine.emulator.mRows)
                assertTrue(session.engine.emulator.screen.transcriptText.contains("output before keyboard"))
            }
        } finally {
            session.destroy(); scope.cancel()
        }
    }
}
