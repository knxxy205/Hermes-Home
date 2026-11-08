package com.example.data.ssh

import android.content.Context
import android.content.Intent
import androidx.core.content.ContextCompat
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel

object SshSessionHost {
    private var scope: CoroutineScope? = null
    private var instance: SshTerminalManager? = null

    @Synchronized fun manager(context: Context): SshTerminalManager {
        instance?.let { return it }
        val app = context.applicationContext
        val owner = CoroutineScope(SupervisorJob() + Dispatchers.IO)
        scope = owner
        return SshTerminalManager(owner) {
            runCatching { ContextCompat.startForegroundService(app, Intent(app, SshConnectionService::class.java)) }
                .onFailure { android.util.Log.w("SshSessionHost", "Cannot start SSH foreground service", it) }
        }.also { instance = it }
    }

    @Synchronized fun close() {
        instance?.destroy()
        scope?.cancel()
        instance = null
        scope = null
    }

    @Synchronized fun startOwnerService(context: Context) {
        val intent = Intent(context.applicationContext, SshConnectionService::class.java)
        if (android.os.Build.VERSION.SDK_INT >= 26) ContextCompat.startForegroundService(context.applicationContext, intent)
        else context.applicationContext.startService(intent)
    }
}
