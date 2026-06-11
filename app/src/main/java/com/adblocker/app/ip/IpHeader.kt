package com.adblocker.app.ip

data class IpHeader(
    val version: Int,
    val headerLength: Int,
    val totalLength: Int,
    val protocol: Int,
    val sourceIp: Int,
    val destIp: Int
) {
    companion object {
        const val TCP = 6
        const val UDP = 17

        fun parse(packet: ByteArray, offset: Int): IpHeader {
            val b0 = packet[offset].toInt() and 0xFF
            val headerLength = (b0 and 0x0F) * 4
            val totalLength = ((packet[offset + 2].toInt() and 0xFF) shl 8) or
                    (packet[offset + 3].toInt() and 0xFF)
            val protocol = packet[offset + 9].toInt() and 0xFF
            val srcIp = readInt(packet, offset + 12)
            val dstIp = readInt(packet, offset + 16)
            return IpHeader(b0 shr 4, headerLength, totalLength, protocol, srcIp, dstIp)
        }

        fun readInt(bytes: ByteArray, offset: Int): Int =
            ((bytes[offset].toInt() and 0xFF) shl 24) or
                    ((bytes[offset + 1].toInt() and 0xFF) shl 16) or
                    ((bytes[offset + 2].toInt() and 0xFF) shl 8) or
                    (bytes[offset + 3].toInt() and 0xFF)

        fun writeInt(value: Int, bytes: ByteArray, offset: Int) {
            bytes[offset] = ((value shr 24) and 0xFF).toByte()
            bytes[offset + 1] = ((value shr 16) and 0xFF).toByte()
            bytes[offset + 2] = ((value shr 8) and 0xFF).toByte()
            bytes[offset + 3] = (value and 0xFF).toByte()
        }

        fun writeShort(value: Int, bytes: ByteArray, offset: Int) {
            bytes[offset] = ((value shr 8) and 0xFF).toByte()
            bytes[offset + 1] = (value and 0xFF).toByte()
        }
    }
}
