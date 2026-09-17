package com.example.data.worker

import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import android.net.ConnectivityManager
import android.net.NetworkCapabilities
import android.os.Build
import androidx.core.app.NotificationCompat
import androidx.core.app.NotificationManagerCompat
import androidx.work.Constraints
import androidx.work.CoroutineWorker
import androidx.work.ExistingPeriodicWorkPolicy
import androidx.work.ExistingWorkPolicy
import androidx.work.NetworkType
import androidx.work.OneTimeWorkRequestBuilder
import androidx.work.PeriodicWorkRequestBuilder
import androidx.work.WorkManager
import androidx.work.WorkerParameters
import com.example.MainActivity
import com.example.data.security.EncryptedStorage
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.net.InetSocketAddress
import java.net.NetworkInterface
import java.net.Socket
import java.util.concurrent.TimeUnit

class ServerHealthWorker(
    private val context: Context,
    workerParams: WorkerParameters
) : CoroutineWorker(context, workerParams) {

    override suspend fun doWork(): Result = withContext(Dispatchers.IO) {
        val encryptedStorage = EncryptedStorage(context)
        val config = encryptedStorage.loadConfig()

        if (!config.isSshConfigured) {
            // Nothing configured to monitor yet
            return@withContext Result.success()
        }

        val isWifi = isWifiActive(context)
        val isVpn = isVpnActive(context)

        // Network-Aware Guard:
        // Homeservers (LAN 192.168.x.x, 10.x.x.x, 172.16.x.x or Tailscale 100.x.x.x / *.ts.net)
        // are only reachable when connected to WiFi or an active VPN (Tailscale/tun0).
        // If the device is on regular mobile/cellular data with VPN off, SKIP the check to avoid false alarms.
        if (isPrivateOrTailscaleHost(config.sshHost) && !isWifi && !isVpn) {
            return@withContext Result.success()
        }

        val prefs = context.getSharedPreferences("server_health_prefs", Context.MODE_PRIVATE)
        val currentFailures = prefs.getInt("consecutive_failures", 0)

        val isReachable = checkServerReachable(config.sshHost, config.sshPort)

        if (isReachable) {
            // Reset consecutive failure counter on healthy ping
            prefs.edit().putInt("consecutive_failures", 0).apply()
            Result.success()
        } else {
            val newFailures = currentFailures + 1
            prefs.edit().putInt("consecutive_failures", newFailures).apply()

            if (newFailures >= 3) {
                val networkContextStr = when {
                    isVpn && isWifi -> "WiFi & VPN Tailscale aktif"
                    isVpn -> "VPN Tailscale aktif"
                    isWifi -> "WiFi aktif"
                    else -> "Koneksi aktif"
                }
                sendServerDownNotification(
                    host = config.sshHost,
                    port = config.sshPort,
                    failCount = newFailures,
                    networkContext = networkContextStr
                )
            }
            Result.success()
        }
    }

    private fun isWifiActive(ctx: Context): Boolean {
        return try {
            val cm = ctx.getSystemService(Context.CONNECTIVITY_SERVICE) as? ConnectivityManager ?: return false
            val activeNet = cm.activeNetwork ?: return false
            val caps = cm.getNetworkCapabilities(activeNet) ?: return false
            caps.hasTransport(NetworkCapabilities.TRANSPORT_WIFI)
        } catch (_: Exception) {
            false
        }
    }

    private fun isVpnActive(ctx: Context): Boolean {
        // 1. Check active network capability for TRANSPORT_VPN
        val isTransportVpn = try {
            val cm = ctx.getSystemService(Context.CONNECTIVITY_SERVICE) as? ConnectivityManager
            val activeNet = cm?.activeNetwork
            if (activeNet != null) {
                val caps = cm.getNetworkCapabilities(activeNet)
                caps?.hasTransport(NetworkCapabilities.TRANSPORT_VPN) == true
            } else false
        } catch (_: Exception) {
            false
        }

        if (isTransportVpn) return true

        // 2. Check network interfaces for Tailscale / tun / tap interfaces
        return try {
            val interfaces = NetworkInterface.getNetworkInterfaces()
            var vpnFound = false
            while (interfaces != null && interfaces.hasMoreElements()) {
                val iface = interfaces.nextElement()
                val name = iface.name.lowercase()
                if (iface.isUp && (
                    name.startsWith("tun") ||
                    name.startsWith("tap") ||
                    name.contains("tailscale") ||
                    name.startsWith("ppp") ||
                    name.startsWith("ipsec")
                )) {
                    vpnFound = true
                    break
                }
            }
            vpnFound
        } catch (_: Exception) {
            false
        }
    }

    private fun isPrivateOrTailscaleHost(host: String): Boolean {
        val trimmed = host.trim().lowercase()
        return trimmed.startsWith("100.") ||            // Tailscale CGNAT (100.64.0.0/10)
               trimmed.startsWith("192.168.") ||        // Private IPv4 Class C
               trimmed.startsWith("10.") ||             // Private IPv4 Class A
               trimmed.matches(Regex("^172\\.(1[6-9]|2[0-9]|3[0-1])\\..*")) || // Private Class B
               trimmed.endsWith(".ts.net") ||           // Tailscale MagicDNS
               trimmed.endsWith(".local") ||            // mDNS / Avahi
               trimmed.endsWith(".lan") ||              // Local router domain
               trimmed.endsWith(".home")                // Home assistant / router domain
    }

    private fun checkServerReachable(host: String, port: Int): Boolean {
        return try {
            Socket().use { socket ->
                socket.connect(InetSocketAddress(host, port), 4000)
                true
            }
        } catch (_: Exception) {
            false
        }
    }

    private fun sendServerDownNotification(
        host: String,
        port: Int,
        failCount: Int,
        networkContext: String
    ) {
        val channelId = CHANNEL_ID
        val notificationManager = context.getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager

        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            val channel = NotificationChannel(
                channelId,
                "Server Health Alerts",
                NotificationManager.IMPORTANCE_HIGH
            ).apply {
                description = "Notifikasi status pemantauan homeserver Tailscale"
                enableVibration(true)
            }
            notificationManager.createNotificationChannel(channel)
        }

        val intent = Intent(context, MainActivity::class.java).apply {
            flags = Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TASK
        }
        val pendingIntent = PendingIntent.getActivity(
            context,
            0,
            intent,
            PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT
        )

        val notification = NotificationCompat.Builder(context, channelId)
            .setSmallIcon(android.R.drawable.stat_notify_error)
            .setContentTitle("⚠️ Server Down / Unreachable")
            .setContentText("Server $host tidak merespon pada port $port ($networkContext).")
            .setStyle(
                NotificationCompat.BigTextStyle()
                    .bigText("Server $host tidak dapat dijangkau pada port $port setelah ${failCount}x pengecekan berturut-turut ($networkContext). Pastikan service SSH dan node homeserver dalam kondisi online.")
            )
            .setPriority(NotificationCompat.PRIORITY_HIGH)
            .setContentIntent(pendingIntent)
            .setAutoCancel(true)
            .build()

        try {
            NotificationManagerCompat.from(context).notify(NOTIFICATION_ID, notification)
        } catch (_: SecurityException) {
            // In case notification runtime permission is pending on Android 13+
        }
    }

    companion object {
        const val CHANNEL_ID = "server_health_channel"
        const val NOTIFICATION_ID = 9001
        private const val UNIQUE_WORK_NAME = "ServerHealthPeriodicWorker"

        fun schedulePeriodicCheck(context: Context) {
            val constraints = Constraints.Builder()
                .setRequiredNetworkType(NetworkType.CONNECTED)
                .build()

            val periodicRequest = PeriodicWorkRequestBuilder<ServerHealthWorker>(1, TimeUnit.HOURS)
                .setConstraints(constraints)
                .build()

            WorkManager.getInstance(context).enqueueUniquePeriodicWork(
                UNIQUE_WORK_NAME,
                ExistingPeriodicWorkPolicy.KEEP,
                periodicRequest
            )
        }

        fun triggerImmediateCheck(context: Context) {
            val constraints = Constraints.Builder()
                .setRequiredNetworkType(NetworkType.CONNECTED)
                .build()

            val oneTimeRequest = OneTimeWorkRequestBuilder<ServerHealthWorker>()
                .setConstraints(constraints)
                .build()

            WorkManager.getInstance(context).enqueueUniqueWork(
                "ImmediateHealthCheck",
                ExistingWorkPolicy.REPLACE,
                oneTimeRequest
            )
        }
    }
}
