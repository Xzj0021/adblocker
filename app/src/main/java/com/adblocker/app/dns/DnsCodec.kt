package com.adblocker.app.dns

import com.adblocker.app.blocklist.BlocklistStore
import com.adblocker.app.vpn.SocketNanny
import java.net.DatagramPacket
import java.net.DatagramSocket
import java.net.InetAddress
import java.net.SocketTimeoutException
import java.util.concurrent.atomic.AtomicLong

object DnsCodec {

    val blockedCount = AtomicLong(0)
    val allowedCount = AtomicLong(0)
    private val upstreamDns = "1.1.1.1"

    fun processQuery(
        packet: ByteArray,
        dnsOffset: Int,
        dnsLength: Int,
        statsCallback: ((Boolean) -> Unit)? = null
    ): ByteArray {
        if (dnsLength < 12) return buildServfail(packet, dnsOffset)

        val domain = extractDomain(packet, dnsOffset) ?: return buildServfail(packet, dnsOffset)

        return if (BlocklistStore.isBlocked(domain)) {
            blockedCount.incrementAndGet()
            statsCallback?.invoke(true)
            buildBlockedResponse(packet, dnsOffset)
        } else {
            allowedCount.incrementAndGet()
            statsCallback?.invoke(false)
            forwardToUpstream(packet, dnsOffset, dnsLength)
        }
    }

    fun extractDomain(packet: ByteArray, offset: Int): String? {
        try {
            var pos = offset
            val labels = mutableListOf<String>()

            while (pos < packet.size) {
                val len = packet[pos].toInt() and 0xFF
                if (len == 0) break
                if ((len and 0xC0) == 0xC0) {
                    if (pos + 1 >= packet.size) return null
                    val ptr = ((len and 0x3F) shl 8) or (packet[pos + 1].toInt() and 0xFF)
                    val pointed = extractDomain(packet, ptr)
                    return if (pointed != null && labels.isNotEmpty()) {
                        labels.joinToString(".") + "." + pointed
                    } else {
                        pointed ?: labels.joinToString(".")
                    }
                }
                pos++
                if (pos + len > packet.size) return null
                labels.add(String(packet, pos, len, Charsets.US_ASCII).lowercase())
                pos += len
            }
            return labels.joinToString(".").ifEmpty { null }
        } catch (e: Exception) {
            return null
        }
    }

    private fun buildBlockedResponse(packet: ByteArray, dnsOffset: Int): ByteArray {
        val response = packet.copyOf()
        // Set QR bit to 1 (response), keep transaction ID
        response[dnsOffset + 2] = ((response[dnsOffset + 2].toInt() and 0xFF) or 0x80).toByte()
        // ANCOUNT = 1
        response[dnsOffset + 6] = 0.toByte()
        response[dnsOffset + 7] = 1.toByte()

        // Find end of question section to place answer
        var qEnd = dnsOffset + 12
        while (qEnd < response.size && response[qEnd] != 0.toByte()) {
            if ((response[qEnd].toInt() and 0xC0) == 0xC0) {
                qEnd += 2
                break
            }
            qEnd++
        }
        qEnd += 5 // skip null byte + QTYPE(2) + QCLASS(2)

        // Build answer: NAME pointer (0xC0 0x0C), TYPE A, CLASS IN, TTL 60, RDLENGTH 4, RDATA 0.0.0.0
        val answer = byteArrayOf(
            0xC0.toByte(), 0x0C.toByte(),  // pointer to question name
            0x00, 0x01,                     // TYPE A
            0x00, 0x01,                     // CLASS IN
            0x00, 0x00, 0x00, 0x3C,        // TTL = 60
            0x00, 0x04,                     // RDLENGTH = 4
            0x00, 0x00, 0x00, 0x00         // 0.0.0.0
        )

        // Copy answer into response at the end of the question section
        if (qEnd + answer.size <= response.size) {
            System.arraycopy(answer, 0, response, qEnd, answer.size)
            return response.copyOf(qEnd + answer.size)
        }
        return response
    }

    private fun buildServfail(packet: ByteArray, dnsOffset: Int): ByteArray {
        val response = packet.copyOf()
        response[dnsOffset + 2] = ((response[dnsOffset + 2].toInt() and 0xFF) or 0x80).toByte()
        response[dnsOffset + 3] = (((response[dnsOffset + 3].toInt() and 0xFF) and 0xF0) or 0x02).toByte()
        return response
    }

    private fun forwardToUpstream(packet: ByteArray, dnsOffset: Int, dnsLength: Int): ByteArray {
        var socket: DatagramSocket? = null
        return try {
            socket = DatagramSocket()
            SocketNanny.protect(socket)
            socket.soTimeout = 5000

            val dnsPayload = packet.copyOfRange(dnsOffset, dnsOffset + dnsLength)
            val request = DatagramPacket(dnsPayload, dnsPayload.size, InetAddress.getByName(upstreamDns), 53)
            socket.send(request)

            val responseBuf = ByteArray(4096)
            val reply = DatagramPacket(responseBuf, responseBuf.size)
            socket.receive(reply)

            responseBuf.copyOf(reply.length)
        } catch (e: SocketTimeoutException) {
            buildServfail(packet, dnsOffset)
        } catch (e: Exception) {
            buildServfail(packet, dnsOffset)
        } finally {
            try { socket?.close() } catch (_: Exception) {}
        }
    }
}
