package com.adblocker.app.ip

data class UdpHeader(
    val sourcePort: Int,
    val destinationPort: Int,
    val length: Int,
    val checksum: Int
) {
    companion object {
        fun parse(packet: ByteArray, offset: Int): UdpHeader {
            val srcPort = ((packet[offset].toInt() and 0xFF) shl 8) or
                    (packet[offset + 1].toInt() and 0xFF)
            val dstPort = ((packet[offset + 2].toInt() and 0xFF) shl 8) or
                    (packet[offset + 3].toInt() and 0xFF)
            val length = ((packet[offset + 4].toInt() and 0xFF) shl 8) or
                    (packet[offset + 5].toInt() and 0xFF)
            val csum = ((packet[offset + 6].toInt() and 0xFF) shl 8) or
                    (packet[offset + 7].toInt() and 0xFF)
            return UdpHeader(srcPort, dstPort, length, csum)
        }
    }
}
