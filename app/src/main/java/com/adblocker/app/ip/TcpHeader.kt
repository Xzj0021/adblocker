package com.adblocker.app.ip

data class TcpHeader(
    val sourcePort: Int,
    val destinationPort: Int,
    val sequenceNumber: Long,
    val acknowledgmentNumber: Long,
    val dataOffset: Int,
    val flags: Int,
    val windowSize: Int,
    val checksum: Int
) {
    val isSyn: Boolean get() = (flags and 0x02) != 0
    val isAck: Boolean get() = (flags and 0x10) != 0
    val isFin: Boolean get() = (flags and 0x01) != 0
    val isRst: Boolean get() = (flags and 0x04) != 0
    val isPsh: Boolean get() = (flags and 0x08) != 0

    companion object {
        fun parse(packet: ByteArray, offset: Int): TcpHeader {
            val srcPort = ((packet[offset].toInt() and 0xFF) shl 8) or
                    (packet[offset + 1].toInt() and 0xFF)
            val dstPort = ((packet[offset + 2].toInt() and 0xFF) shl 8) or
                    (packet[offset + 3].toInt() and 0xFF)
            val seqNum = ((packet[offset + 4].toLong() and 0xFF) shl 24) or
                    ((packet[offset + 5].toLong() and 0xFF) shl 16) or
                    ((packet[offset + 6].toLong() and 0xFF) shl 8) or
                    (packet[offset + 7].toLong() and 0xFF)
            val ackNum = ((packet[offset + 8].toLong() and 0xFF) shl 24) or
                    ((packet[offset + 9].toLong() and 0xFF) shl 16) or
                    ((packet[offset + 10].toLong() and 0xFF) shl 8) or
                    (packet[offset + 11].toLong() and 0xFF)
            val dataOffset = ((packet[offset + 12].toInt() and 0xF0) shr 4) * 4
            val flags = packet[offset + 13].toInt() and 0xFF
            val window = ((packet[offset + 14].toInt() and 0xFF) shl 8) or
                    (packet[offset + 15].toInt() and 0xFF)
            val csum = ((packet[offset + 16].toInt() and 0xFF) shl 8) or
                    (packet[offset + 17].toInt() and 0xFF)
            return TcpHeader(srcPort, dstPort, seqNum, ackNum, dataOffset, flags, window, csum)
        }
    }
}
