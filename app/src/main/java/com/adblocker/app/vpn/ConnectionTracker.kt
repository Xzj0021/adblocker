package com.adblocker.app.vpn

import com.adblocker.app.ip.IpHeader
import com.adblocker.app.ip.TcpHeader
import com.adblocker.app.ip.UdpHeader
import java.io.ByteArrayOutputStream
import java.net.DatagramSocket
import java.nio.channels.SocketChannel
import java.util.concurrent.ConcurrentHashMap

enum class TcpState { SYN_SENT, ESTABLISHED, CLOSING, CLOSED }

data class NatEntry(
    val key: String,
    val sourceIp: Int,
    val sourcePort: Int,
    val destIp: Int,
    val destPort: Int,
    val protocol: Int,
    var channel: SocketChannel? = null,
    var udpSocket: DatagramSocket? = null,
    var state: TcpState = TcpState.SYN_SENT,
    var lastActivity: Long = System.currentTimeMillis(),
    var seqToClient: Long = 0,
    var ackToClient: Long = 0,
    var seqFromClient: Long = 0,
    var sendBuffer: ByteArrayOutputStream? = null
)

object ConnectionTracker {

    private val entries = ConcurrentHashMap<String, NatEntry>()
    private const val MAX_ENTRIES = 2000
    private const val TIMEOUT_MS = 300_000L

    fun natKey(ipHdr: IpHeader, tcpHdr: TcpHeader): String =
        "${ipHdr.sourceIp}:${tcpHdr.sourcePort}:${ipHdr.destIp}:${tcpHdr.destinationPort}:6"

    fun natKey(ipHdr: IpHeader, udpHdr: UdpHeader): String =
        "${ipHdr.sourceIp}:${udpHdr.sourcePort}:${ipHdr.destIp}:${udpHdr.destinationPort}:17"

    fun get(key: String): NatEntry? = entries[key]

    fun put(key: String, entry: NatEntry) {
        if (entries.size >= MAX_ENTRIES) {
            evictOldest()
        }
        entries[key] = entry
    }

    fun remove(key: String): NatEntry? = entries.remove(key)

    fun touch(key: String) {
        entries[key]?.lastActivity = System.currentTimeMillis()
    }

    fun cleanup() {
        val now = System.currentTimeMillis()
        val iter = entries.entries.iterator()
        while (iter.hasNext()) {
            val (key, entry) = iter.next()
            if (entry.state == TcpState.CLOSED || now - entry.lastActivity > TIMEOUT_MS) {
                closeEntry(entry)
                iter.remove()
            }
        }
    }

    fun shutdown() {
        entries.values.forEach { closeEntry(it) }
        entries.clear()
    }

    private fun closeEntry(entry: NatEntry) {
        try { entry.channel?.close() } catch (_: Exception) {}
        try { entry.udpSocket?.close() } catch (_: Exception) {}
    }

    private fun evictOldest() {
        val oldest = entries.minByOrNull { it.value.lastActivity }
        if (oldest != null) {
            closeEntry(oldest.value)
            entries.remove(oldest.key)
        }
    }
}
