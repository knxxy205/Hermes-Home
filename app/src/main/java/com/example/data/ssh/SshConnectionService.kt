package com.example.data.ssh

import android.app.*
import android.content.Intent
import android.content.pm.ServiceInfo
import android.os.*
import androidx.core.app.NotificationCompat
import androidx.core.app.ServiceCompat
import com.example.MainActivity
import com.example.R
import kotlinx.coroutines.*
import kotlinx.coroutines.flow.*

@OptIn(ExperimentalCoroutinesApi::class)
class SshConnectionService : Service() {
    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.Main.immediate)
    private var wakeLock: PowerManager.WakeLock? = null
    private lateinit var manager: SshTerminalManager

    override fun onCreate() {
        super.onCreate()
        manager = SshSessionHost.manager(this)
        val notifications = getSystemService(NotificationManager::class.java)
        if (Build.VERSION.SDK_INT >= 26) notifications.createNotificationChannel(
            NotificationChannel(CHANNEL, getString(R.string.ssh_notification_channel), NotificationManager.IMPORTANCE_LOW))
        ServiceCompat.startForeground(this, ID, notification(0),
            if (Build.VERSION.SDK_INT >= 34) ServiceInfo.FOREGROUND_SERVICE_TYPE_SPECIAL_USE else 0)
        wakeLock = (getSystemService(POWER_SERVICE) as PowerManager)
            .newWakeLock(PowerManager.PARTIAL_WAKE_LOCK, "$packageName:ssh").apply { setReferenceCounted(false) }
        scope.launch {
            manager.sessions.flatMapLatest { sessions ->
                if (sessions.isEmpty()) flowOf(false) else combine(sessions.map { it.connectionState }) { states ->
                    states.any { it is TerminalConnectionState.Connected || it is TerminalConnectionState.Connecting }
                }
            }.collectLatest { active ->
                if (active) {
                    wakeLock?.acquire(120_000L)
                    while (isActive) { delay(60_000L); wakeLock?.acquire(120_000L) }
                } else if (wakeLock?.isHeld == true) wakeLock?.release()
            }
        }
        scope.launch {
            manager.sessions.flatMapLatest { sessions ->
                if (sessions.isEmpty()) flowOf(emptyList()) else combine(sessions.map { it.connectionState }) { it.toList() }
            }.collectLatest { states ->
                val count = states.count { it is TerminalConnectionState.Connected || it is TerminalConnectionState.Connecting }
                notifications.notify(ID, notification(count))
                if (count == 0) { delay(750); stopSelf() }
            }
        }
    }

    private fun notification(count: Int): Notification {
        val localized = com.example.ui.i18n.AppLanguage.context(this)
        val open = PendingIntent.getActivity(this, 7400,
            Intent(this, MainActivity::class.java).addFlags(Intent.FLAG_ACTIVITY_SINGLE_TOP or Intent.FLAG_ACTIVITY_CLEAR_TOP),
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE)
        val disconnect = PendingIntent.getService(this, 7401,
            Intent(this, SshConnectionService::class.java).setAction(ACTION_DISCONNECT),
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE)
        return NotificationCompat.Builder(this, CHANNEL)
            .setSmallIcon(android.R.drawable.stat_sys_upload_done)
            .setContentTitle(localized.getString(R.string.ssh_active))
            .setContentText(localized.getString(R.string.ssh_active_count, count))
            .setContentIntent(open).setOngoing(true).setOnlyAlertOnce(true)
            .setVisibility(NotificationCompat.VISIBILITY_PRIVATE)
            .addAction(0, localized.getString(R.string.disconnect_all), disconnect).build()
    }

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        if (intent?.action == ACTION_DISCONNECT) { manager.disconnectAll(); stopSelf() }
        return START_NOT_STICKY
    }
    override fun onTaskRemoved(rootIntent: Intent?) { SshSessionHost.close(); stopSelf() }
    override fun onConfigurationChanged(newConfig: android.content.res.Configuration) {
        super.onConfigurationChanged(newConfig)
        val count = manager.sessions.value.count { it.connectionState.value is TerminalConnectionState.Connected || it.connectionState.value is TerminalConnectionState.Connecting }
        getSystemService(NotificationManager::class.java).notify(ID, notification(count))
    }
    override fun onDestroy() {
        scope.cancel()
        wakeLock?.let { if (it.isHeld) it.release() }
        manager.disconnectAll()
        super.onDestroy()
    }
    override fun onBind(intent: Intent?) = null

    companion object {
        private const val CHANNEL = "ssh_connections"
        private const val ID = 7400
        private const val ACTION_DISCONNECT = "com.example.DISCONNECT_SSH"
    }
}
