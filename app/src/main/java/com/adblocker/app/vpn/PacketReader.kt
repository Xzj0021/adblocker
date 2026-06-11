package com.adblocker.app.vpn

import com.adblocker.app.dns.DnsCodec
import com.adblocker.app.ip.IpHeader
import com.adblocker.app.ip.TcpHeader
import com.adblocker.app.ip.UdpHeader
import java.io.FileInputStream
import java.io.FileOutputStream
import java.util.concurrent.atomic.AtomicBoolean

object PacketReader {

    private val isRunning = AtomicBoolean(false)
    private var thread: Thread? = null

    fun start(inputStream: FileInputStream, outputStream: FileOutputStream) {
        if (isRunning.getAndSet(true)) return
        thread = Thread({
            readLoop(inputStream, outputStream)
        }, "packet-reader").apply {
            priority = Thread.MAX_PRIORITY
            isDaemon = true
            start()
        }
    }

    fun stop() {
        isRunning.set(false)
        thread?.interrupt()
        thread = null
    }

    private fun readLoop(input: FileInputStream, output: FileOutputStream) {
        val buffer = ByteArray(65535)

        while (isRunning.get()) {
            val length: Int
            try {
                length = input.read(buffer)
            } catch (e: Exception) {
                if (isRunning.get()) Thread.sleep(100)
                continue
            }

            if (length <= 0) continue

            // Only handle IPv4
            if ((buffer[0].toInt() shr 4) != 4) continue

            try {
                val ipHeader = IpHeader.parse(buffer, 0)

                when (ipHeader.protocol) {
                    IpHeader.TCP -> {
                        if (length < ipHeader.headerLength + 20) continue
                        val tcpHeader = TcpHeader.parse(buffer, ipHeader.headerLength)
                        handleTcp(buffer, length, ipHeader, tcpHeader, output)
                    }
                    IpHeader.UDP -> {
                        if (length < ipHeader.headerLength + 8) continue
                        val udpHeader = UdpHeader.parse(buffer, ipHeader.headerLength)

                        if (udpHeader.destinationPort == 53) {
                            handleDns(buffer, length, ipHeader, udpHeader, output)
                        } else {
                            UdpRelay.handle(buffer, length, ipHeader, udpHeader, output)
                        }
                    }
                }
            } catch (e: Exception) {
                // malformed packet, skip
            }
        }
    }

    private fun handleDns(
        packet: ByteArray,
        length: Int,
        ipHeader: IpHeader,
        udpHeader: UdpHeader,
        output: FileOutputStream
    ) {
        val dnsOffset = ipHeader.headerLength + 8
        val dnsLength = length - dnsOffset

        val response = DnsCodec.processQuery(packet, dnsOffset, dnsLength)

        // Build UDP response: swap IP src/dst, swap UDP ports
        val udpLen = 8 + response.size
        val totalLen = 20 + udpLen
        val outPacket = ByteArray(totalLen)

        // IP header
        outPacket[0] = 0x45.toByte()
        IpHeader.writeShort(totalLen, outPacket, 2)
        IpHeader.writeShort(0, outPacket, 4)
        outPacket[6] = 0x40.toByte(); outPacket[7] = 0
        outPacket[8] = 64
        outPacket[9] = IpHeader.UDP.toByte()
        IpHeader.writeInt(ipHeader.destIp, outPacket, 12)
        IpHeader.writeInt(ipHeader.sourceIp, outPacket, 16)

        // UDP header
        val udpOff = 20
        IpHeader.writeShort(udpHeader.destinationPort, outPacket, udpOff)
        IpHeader.writeShort(udpHeader.sourcePort, outPacket, udpOff + 2)
        IpHeader.writeShort(udpLen, outPacket, udpOff + 4)
        IpHeader.writeShort(0, outPacket, udpOff + 6)

        // DNS payload
        System.arraycopy(response, 0, outPacket, 28, response.size)

        try {
            output.write(outPacket)
        } catch (e: Exception) {
            // output error
        }
    }

    private fun handleTcp(
        packet: ByteArray,
        length: Int,
        ipHeader: IpHeader,
        tcpHeader: TcpHeader,
        output: FileOutputStream
    ) {
        when {
            tcpHeader.isSyn && !tcpHeader.isAck -> {
                TcpRelay.handleSyn(packet, ipHeader, tcpHeader)
            }
            tcpHeader.isRst -> {
                TcpRelay.handleRst(packet, ipHeader, tcpHeader)
            }
            tcpHeader.isFin -> {
                TcpRelay.handleFin(packet, ipHeader, tcpHeader, output)
            }
            else -> {
                TcpRelay.handleData(packet, length, ipHeader, tcpHeader, output)
            }
        }
    }
}
