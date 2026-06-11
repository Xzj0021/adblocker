package com.adblocker.app.vpn

import com.adblocker.app.ip.IpHeader
import com.adblocker.app.ip.TcpHeader
import java.io.FileOutputStream
import java.net.InetSocketAddress
import java.nio.ByteBuffer
import java.nio.channels.SelectionKey
import java.nio.channels.Selector
import java.nio.channels.SocketChannel
import java.util.concurrent.ConcurrentLinkedQueue
import java.util.concurrent.Executors

object TcpRelay {

    private var selector: Selector? = null
    private var running = false
    private val pendingConnects = ConcurrentLinkedQueue<Pair<String, SocketChannel>>()
    private val executor = Executors.newSingleThreadExecutor { r ->
        Thread(r, "tcp-relay").apply { isDaemon = true }
    }
    private val dataQueue = ConcurrentLinkedQueue<ByteArray>()

    fun start() {
        if (running) return
        running = true
        selector = Selector.open()
        executor.submit { selectorLoop() }
    }

    fun stop() {
        running = false
        selector?.wakeup()
        ConnectionTracker.shutdown()
        executor.shutdown()
    }

    fun handleSyn(packet: ByteArray, ipHdr: IpHeader, tcpHdr: TcpHeader) {
        val key = ConnectionTracker.natKey(ipHdr, tcpHdr)
        if (ConnectionTracker.get(key) != null) return

        try {
            val channel = SocketChannel.open()
            channel.configureBlocking(false)
            SocketNanny.protect(channel.socket())
            channel.connect(InetSocketAddress(intToInet(ipHdr.destIp), tcpHdr.destinationPort))

            val entry = NatEntry(
                key = key,
                sourceIp = ipHdr.sourceIp,
                sourcePort = tcpHdr.sourcePort,
                destIp = ipHdr.destIp,
                destPort = tcpHdr.destinationPort,
                protocol = IpHeader.TCP,
                channel = channel,
                state = TcpState.SYN_SENT,
                lastActivity = System.currentTimeMillis(),
                seqToClient = (Math.random() * Int.MAX_VALUE).toLong() and 0xFFFFFFFFL,
                ackToClient = (tcpHdr.sequenceNumber + 1) and 0xFFFFFFFFL,
                seqFromClient = tcpHdr.sequenceNumber
            )
            ConnectionTracker.put(key, entry)
            pendingConnects.add(Pair(key, channel))
            selector?.wakeup()
        } catch (e: Exception) {
            // connection failed silently
        }
    }

    fun handleData(
        packet: ByteArray,
        length: Int,
        ipHdr: IpHeader,
        tcpHdr: TcpHeader,
        output: FileOutputStream
    ) {
        val key = ConnectionTracker.natKey(ipHdr, tcpHdr)
        val entry = ConnectionTracker.get(key) ?: return
        ConnectionTracker.touch(key)

        if (entry.state != TcpState.ESTABLISHED && entry.state != TcpState.CLOSING) return

        val payloadOffset = ipHdr.headerLength + tcpHdr.dataOffset
        val payloadLength = length - payloadOffset

        if (payloadLength > 0 && entry.channel != null) {
            try {
                val buf = ByteBuffer.wrap(packet, payloadOffset, payloadLength)
                entry.channel?.write(buf)
                // update ack
                entry.ackToClient = (tcpHdr.sequenceNumber + payloadLength) and 0xFFFFFFFFL
            } catch (e: Exception) {
                sendRst(packet, ipHdr, tcpHdr, entry, output)
            }
        }

        if (tcpHdr.isPsh && payloadLength > 0) {
            sendAck(ipHdr, tcpHdr, entry, output)
        }
    }

    fun handleFin(
        packet: ByteArray,
        ipHdr: IpHeader,
        tcpHdr: TcpHeader,
        output: FileOutputStream
    ) {
        val key = ConnectionTracker.natKey(ipHdr, tcpHdr)
        val entry = ConnectionTracker.get(key) ?: return
        entry.state = TcpState.CLOSING
        try { entry.channel?.shutdownOutput() } catch (_: Exception) {}
        sendFinAck(ipHdr, tcpHdr, entry, output)
    }

    fun handleRst(packet: ByteArray, ipHdr: IpHeader, tcpHdr: TcpHeader) {
        val key = ConnectionTracker.natKey(ipHdr, tcpHdr)
        ConnectionTracker.remove(key)
    }

    private fun selectorLoop() {
        val sel = selector ?: return
        val readBuffer = ByteBuffer.allocate(65535)

        while (running) {
            try {
                // Register pending connects
                while (true) {
                    val pair = pendingConnects.poll() ?: break
                    val (key, channel) = pair
                    val entry = ConnectionTracker.get(key) ?: continue
                    channel.register(sel, SelectionKey.OP_CONNECT, entry)
                }

                if (sel.keys().isEmpty() && pendingConnects.isEmpty()) {
                    Thread.sleep(50)
                    continue
                }

                val readyCount = sel.select(100)
                if (readyCount == 0) continue

                val keys = sel.selectedKeys().iterator()
                while (keys.hasNext()) {
                    val sk = keys.next()
                    keys.remove()

                    if (!sk.isValid) continue

                    val entry = sk.attachment() as? NatEntry ?: continue

                    if (sk.isConnectable) {
                        val channel = sk.channel() as SocketChannel
                        try {
                            if (channel.finishConnect()) {
                                entry.state = TcpState.ESTABLISHED
                                sk.interestOps(SelectionKey.OP_READ)
                            }
                        } catch (e: Exception) {
                            entry.state = TcpState.CLOSED
                            sk.cancel()
                        }
                    }

                    if (sk.isReadable) {
                        val channel = sk.channel() as SocketChannel
                        try {
                            readBuffer.clear()
                            val bytesRead = channel.read(readBuffer)
                            if (bytesRead > 0) {
                                readBuffer.flip()
                                val data = ByteArray(bytesRead)
                                readBuffer.get(data)
                                entry.seqToClient = (entry.seqToClient + bytesRead) and 0xFFFFFFFFL
                                // Queue data to be sent back to TUN
                                synchronized(dataQueue) { dataQueue.add(data) }
                            } else if (bytesRead < 0) {
                                entry.state = TcpState.CLOSING
                                sk.cancel()
                            }
                        } catch (e: Exception) {
                            entry.state = TcpState.CLOSED
                            sk.cancel()
                        }
                    }
                }
            } catch (e: Exception) {
                // selector error, continue
            }
        }
    }

    private fun sendAck(ipHdr: IpHeader, tcpHdr: TcpHeader, entry: NatEntry, output: FileOutputStream) {
        val packet = buildTcpPacket(
            ipHdr.destIp, tcpHdr.destinationPort,
            ipHdr.sourceIp, tcpHdr.sourcePort,
            entry.seqToClient, entry.ackToClient,
            0x10, // ACK flag
            byteArrayOf()
        )
        try { output.write(packet) } catch (_: Exception) {}
    }

    private fun sendRst(
        packet: ByteArray, ipHdr: IpHeader, tcpHdr: TcpHeader, entry: NatEntry, output: FileOutputStream
    ) {
        val rst = buildTcpPacket(
            ipHdr.destIp, tcpHdr.destinationPort,
            ipHdr.sourceIp, tcpHdr.sourcePort,
            0, entry.ackToClient,
            0x04, // RST flag
            byteArrayOf()
        )
        try { output.write(rst) } catch (_: Exception) {}
        ConnectionTracker.remove(ConnectionTracker.natKey(ipHdr, tcpHdr))
    }

    private fun sendFinAck(ipHdr: IpHeader, tcpHdr: TcpHeader, entry: NatEntry, output: FileOutputStream) {
        val packet = buildTcpPacket(
            ipHdr.destIp, tcpHdr.destinationPort,
            ipHdr.sourceIp, tcpHdr.sourcePort,
            entry.seqToClient, (entry.ackToClient + 1) and 0xFFFFFFFFL,
            0x11, // FIN + ACK
            byteArrayOf()
        )
        try { output.write(packet) } catch (_: Exception) {}
    }

    private fun buildTcpPacket(
        srcIp: Int, srcPort: Int, dstIp: Int, dstPort: Int,
        seqNum: Long, ackNum: Long, flags: Int, payload: ByteArray
    ): ByteArray {
        val totalLen = 40 + payload.size // 20 IP + 20 TCP + payload
        val packet = ByteArray(totalLen)

        // IP header
        packet[0] = 0x45.toByte() // version 4, IHL 5
        IpHeader.writeShort(totalLen, packet, 2)
        IpHeader.writeShort((Math.random() * 65535).toInt(), packet, 4) // ID
        packet[6] = 0x40.toByte(); packet[7] = 0 // flags + fragment
        packet[8] = 64 // TTL
        packet[9] = IpHeader.TCP.toByte() // protocol
        IpHeader.writeInt(srcIp, packet, 12)
        IpHeader.writeInt(dstIp, packet, 16)

        // TCP header
        val tcpOff = 20
        IpHeader.writeShort(srcPort, packet, tcpOff)
        IpHeader.writeShort(dstPort, packet, tcpOff + 2)
        IpHeader.writeInt(seqNum.toInt(), packet, tcpOff + 4)
        IpHeader.writeInt(ackNum.toInt(), packet, tcpOff + 8)
        packet[tcpOff + 12] = 0x50.toByte() // data offset = 5 (20 bytes)
        packet[tcpOff + 13] = flags.toByte()
        IpHeader.writeShort(65535, packet, tcpOff + 14) // window

        // Copy payload
        if (payload.isNotEmpty()) {
            System.arraycopy(payload, 0, packet, tcpOff + 20, payload.size)
        }

        return packet
    }

    private fun intToInet(ip: Int) = java.net.InetAddress.getByAddress(
        byteArrayOf(
            ((ip shr 24) and 0xFF).toByte(),
            ((ip shr 16) and 0xFF).toByte(),
            ((ip shr 8) and 0xFF).toByte(),
            (ip and 0xFF).toByte()
        )
    )
}
