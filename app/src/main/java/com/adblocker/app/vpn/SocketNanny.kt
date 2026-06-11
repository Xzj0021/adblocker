package com.adblocker.app.vpn

import android.net.VpnService
import java.net.DatagramSocket
import java.net.Socket

object SocketNanny {
    private var vpnService: VpnService? = null

    fun init(service: VpnService) {
        vpnService = service
    }

    fun protect(socket: Socket): Boolean {
        return vpnService?.protect(socket) ?: false
    }

    fun protect(socket: DatagramSocket): Boolean {
        return vpnService?.protect(socket) ?: false
    }

    fun protect(fd: Int): Boolean {
        return vpnService?.protect(fd) ?: false
    }

    fun reset() {
        vpnService = null
    }
}
