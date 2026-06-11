package com.adblocker.app.service

import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import android.net.VpnService
import android.os.Build
import android.os.ParcelFileDescriptor
import com.adblocker.app.MainActivity
import com.adblocker.app.R
import com.adblocker.app.blocklist.BlocklistStore
import com.adblocker.app.dns.DnsCodec
import com.adblocker.app.vpn.PacketReader
import com.adblocker.app.vpn.SocketNanny
import com.adblocker.app.vpn.TcpRelay
import com.adblocker.app.vpn.UdpRelay
import kotlinx.coroutines.*
import java.io.FileInputStream
import java.io.FileOutputStream

class AdBlockVpnService : VpnService() {

    private var vpnInterface: ParcelFileDescriptor? = null
    private val serviceScope = CoroutineScope(Dispatchers.IO + SupervisorJob())
    @Volatile
    private var running = false

    companion object {
        const val ACTION_START = "START"
        const val ACTION_STOP = "STOP"
        const val NOTIFICATION_ID = 42
        const val CHANNEL_ID = "adblocker_vpn"
    }

    override fun onCreate() {
        super.onCreate()
        SocketNanny.init(this)
        createNotificationChannel()
    }

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        when (intent?.action) {
            ACTION_STOP -> {
                stopVpn()
                return START_NOT_STICKY
            }
        }
        startVpn()
        return START_STICKY
    }

    private fun startVpn() {
        if (running) return

        val notification = buildNotification()
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.UPSIDE_DOWN_CAKE) {
            startForeground(NOTIFICATION_ID, notification, android.content.pm.ServiceInfo.FOREGROUND_SERVICE_TYPE_SPECIAL_USE)
        } else {
            startForeground(NOTIFICATION_ID, notification)
        }

        serviceScope.launch {
            if (!BlocklistStore.loaded) {
                BlocklistStore.init(this@AdBlockVpnService)
            }
        }

        try {
            val builder = Builder()
                .setSession("AdBlocker")
                .setMtu(1500)
                .addAddress("10.0.0.2", 32)
                .addRoute("0.0.0.0", 0)
                .addDnsServer("1.1.1.1")
                .addDnsServer("8.8.8.8")

            vpnInterface = builder.establish()
            if (vpnInterface == null) {
                VpnStateManager.onVpnStopped()
                return
            }

            running = true
            VpnStateManager.onVpnRunning()

            val input = FileInputStream(vpnInterface!!.fileDescriptor)
            val output = FileOutputStream(vpnInterface!!.fileDescriptor)

            TcpRelay.start()
            PacketReader.start(input, output)

            // Periodic cleanup
            serviceScope.launch {
                while (running) {
                    delay(60_000)
                    com.adblocker.app.vpn.ConnectionTracker.cleanup()
                    UdpRelay.cleanup()
                    updateNotification()
                }
            }

        } catch (e: Exception) {
            running = false
            VpnStateManager.onVpnError(e.message ?: "VPN failed to start")
            stopVpn()
        }
    }

    private fun stopVpn() {
        running = false
        PacketReader.stop()
        TcpRelay.stop()
        UdpRelay.shutdown()
        SocketNanny.reset()
        try { vpnInterface?.close() } catch (_: Exception) {}
        vpnInterface = null
        VpnStateManager.onVpnStopped()
        stopForeground(STOP_FOREGROUND_REMOVE)
        stopSelf()
    }

    override fun onDestroy() {
        stopVpn()
        serviceScope.cancel()
        super.onDestroy()
    }

    private fun createNotificationChannel() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            val channel = NotificationChannel(
                CHANNEL_ID,
                "AdBlocker VPN",
                NotificationManager.IMPORTANCE_LOW
            ).apply {
                description = "Shows when AdBlocker VPN is active"
                setShowBadge(false)
            }
            val manager = getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager
            manager.createNotificationChannel(channel)
        }
    }

    private fun buildNotification(): Notification {
        val openIntent = PendingIntent.getActivity(
            this,
            0,
            Intent(this, MainActivity::class.java),
            PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT
        )

        val stopIntent = PendingIntent.getService(
            this,
            0,
            Intent(this, AdBlockVpnService::class.java).apply { action = ACTION_STOP },
            PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT
        )

        return if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            Notification.Builder(this, CHANNEL_ID)
                .setContentTitle("AdBlocker is active")
                .setContentText("Blocked: ${DnsCodec.blockedCount.get()} ads")
                .setSmallIcon(R.drawable.ic_shield)
                .setContentIntent(openIntent)
                .addAction(android.R.drawable.ic_media_pause, "Stop", stopIntent)
                .setOngoing(true)
                .setPriority(Notification.PRIORITY_LOW)
                .build()
        } else {
            @Suppress("deprecation")
            Notification.Builder(this)
                .setContentTitle("AdBlocker is active")
                .setContentText("Blocked: ${DnsCodec.blockedCount.get()} ads")
                .setSmallIcon(R.drawable.ic_shield)
                .setContentIntent(openIntent)
                .setOngoing(true)
                .setPriority(Notification.PRIORITY_MIN)
                .build()
        }
    }

    private fun updateNotification() {
        val notification = buildNotification()
        val manager = getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager
        manager.notify(NOTIFICATION_ID, notification)
    }
}
