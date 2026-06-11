package com.adblocker.app.vpn

import com.adblocker.app.ip.IpHeader
import com.adblocker.app.ip.UdpHeader
import kotlinx.coroutines.*
import java.io.FileOutputStream
import java.net.DatagramPacket
import java.net.DatagramSocket
import java.net.InetAddress
import java.util.concurrent.ConcurrentHashMap
import java.util.concurrent.atomic.AtomicLong

object UdpRelay {

    private val sessions = ConcurrentHashMap<String, UdpSession>()
    private val scope = CoroutineScope(Dispatchers.IO + SupervisorJob())

    data class UdpSession(
        val socket: DatagramSocket,
        val lastActivity: AtomicLong,
        val srcIp: Int,
        val srcPort: Int
    )

    fun handle(
        packet: ByteArray,
        length: Int,
        ipHdr: IpHeader,
        udpHdr: UdpHeader,
        output: FileOutputStream
    ) {
        val key = ConnectionTracker.natKey(ipHdr, udpHdr)
        var session = sessions[key]

        if (session == null || session.socket.isClosed) {
            try {
                val socket = DatagramSocket()
                SocketNanny.protect(socket)
                socket.soTimeout = 5000
                session = UdpSession(
                    socket = socket,
                    lastActivity = AtomicLong(System.currentTimeMillis()),
                    srcIp = ipHdr.sourceIp,
                    srcPort = udpHdr.sourcePort
                )
                sessions[key] = session

                scope.launch {
                    readResponses(session!!, output)
                }
            } catch (e: Exception) {
                return
            }
        }

        try {
            val payloadOffset = ipHdr.headerLength + 8
            val payloadLength = length - payloadOffset
            if (payloadLength <= 0) return

            val addr = intToInet(ipHdr.destIp)
            val dp = DatagramPacket(packet, payloadOffset, payloadLength, addr, udpHdr.destinationPort)
            session.socket.send(dp)
            session.lastActivity.set(System.currentTimeMillis())
        } catch (e: Exception) {
            sessions.remove(key)
        }
    }

    private suspend fun readResponses(session: UdpSession, output: FileOutputStream) {
        val buffer = ByteArray(65535)
        while (coroutineContext.isActive) {
            try {
                val dp = DatagramPacket(buffer, buffer.size)
                session.socket.receive(dp)
                session.lastActivity.set(System.currentTimeMillis())

                val responseData = buffer.copyOf(dp.length)
                val srcIpBytes = dp.address.address
                val srcIpInt = if (srcIpBytes.size == 4) {
                    ((srcIpBytes[0].toInt() and 0xFF) shl 24) or
                            ((srcIpBytes[1].toInt() and 0xFF) shl 16) or
                            ((srcIpBytes[2].toInt() and 0xFF) shl 8) or
                            (srcIpBytes[3].toInt() and 0xFF)
                } else session.srcIp

                val responsePacket = buildUdpResponsePacket(
                    srcIpInt,
                    dp.port,
                    session.srcIp,
                    session.srcPort,
                    responseData
                )
                withContext(Dispatchers.IO) {
                    try { output.write(responsePacket) } catch (_: Exception) {}
                }
            } catch (e: Exception) {
                if (System.currentTimeMillis() - session.lastActivity.get() > 60_000) break
            }
        }
    }

    fun cleanup() {
        val now = System.currentTimeMillis()
        val iter = sessions.entries.iterator()
        while (iter.hasNext()) {
            val (key, session) = iter.next()
            if (now - session.lastActivity.get() > 300_000) {
                try { session.socket.close() } catch (_: Exception) {}
                iter.remove()
            }
        }
    }

    fun shutdown() {
        sessions.values.forEach { try { it.socket.close() } catch (_: Exception) {} }
        sessions.clear()
        scope.cancel()
    }

    private fun buildUdpResponsePacket(
        srcIp: Int, srcPort: Int,
        dstIp: Int, dstPort: Int,
        payload: ByteArray
    ): ByteArray {
        val totalLen = 28 + payload.size
        val packet = ByteArray(totalLen)

        packet[0] = 0x45.toByte()
        IpHeader.writeShort(totalLen, packet, 2)
        IpHeader.writeShort(0, packet, 4)
        packet[6] = 0x40.toByte(); packet[7] = 0
        packet[8] = 64
        packet[9] = IpHeader.UDP.toByte()
        IpHeader.writeInt(srcIp, packet, 12)
        IpHeader.writeInt(dstIp, packet, 16)

        val udpOff = 20
        IpHeader.writeShort(srcPort, packet, udpOff)
        IpHeader.writeShort(dstPort, packet, udpOff + 2)
        IpHeader.writeShort(8 + payload.size, packet, udpOff + 4)
        IpHeader.writeShort(0, packet, udpOff + 6)

        System.arraycopy(payload, 0, packet, 28, payload.size)
        return packet
    }

    private fun intToInet(ip: Int): InetAddress = InetAddress.getByAddress(
        byteArrayOf(
            ((ip shr 24) and 0xFF).toByte(),
            ((ip shr 16) and 0xFF).toByte(),
            ((ip shr 8) and 0xFF).toByte(),
            (ip and 0xFF).toByte()
        )
    )
}
