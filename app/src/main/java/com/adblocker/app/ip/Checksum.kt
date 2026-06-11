package com.adblocker.app.ip

object Checksum {

    fun ipHeader(packet: ByteArray, offset: Int, headerLength: Int) {
        packet[offset + 10] = 0
        packet[offset + 11] = 0
        val sum = sumBytes(packet, offset, headerLength)
        val csum = onesComplement(sum)
        IpHeader.writeShort(csum, packet, offset + 10)
    }

    fun tcp(ipHeader: IpHeader, tcpData: ByteArray, tcpOffset: Int, tcpLength: Int): Int {
        var sum = 0L
        // pseudo header
        sum += ((ipHeader.sourceIp ushr 16) and 0xFFFF).toLong()
        sum += (ipHeader.sourceIp and 0xFFFF).toLong()
        sum += ((ipHeader.destIp ushr 16) and 0xFFFF).toLong()
        sum += (ipHeader.destIp and 0xFFFF).toLong()
        sum += IpHeader.TCP.toLong()
        sum += tcpLength.toLong()
        sum += sumBytes(tcpData, tcpOffset, tcpLength)
        return onesComplement(sum)
    }

    fun udp(ipHeader: IpHeader, udpData: ByteArray, offset: Int, length: Int): Int {
        var sum = 0L
        sum += ((ipHeader.sourceIp ushr 16) and 0xFFFF).toLong()
        sum += (ipHeader.sourceIp and 0xFFFF).toLong()
        sum += ((ipHeader.destIp ushr 16) and 0xFFFF).toLong()
        sum += (ipHeader.destIp and 0xFFFF).toLong()
        sum += IpHeader.UDP.toLong()
        sum += length.toLong()
        sum += sumBytes(udpData, offset, length)
        return onesComplement(sum)
    }

    private fun sumBytes(data: ByteArray, offset: Int, length: Int): Long {
        var sum = 0L
        var i = offset
        val end = offset + length
        while (i < end - 1) {
            sum += ((data[i].toInt() and 0xFF) shl 8) or (data[i + 1].toInt() and 0xFF)
            i += 2
        }
        if (i < end) {
            sum += ((data[i].toInt() and 0xFF) shl 8).toLong()
        }
        return sum
    }

    private fun onesComplement(sum: Long): Int {
        var s = sum
        while (s shr 16 > 0) {
            s = (s and 0xFFFF) + (s shr 16)
        }
        return (s.toInt() xor 0xFFFF) and 0xFFFF
    }
}
