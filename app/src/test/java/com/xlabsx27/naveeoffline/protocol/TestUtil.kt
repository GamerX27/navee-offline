package com.xlabsx27.naveeoffline.protocol

fun hex(s: String): ByteArray =
    s.replace(" ", "").chunked(2).map { it.toInt(16).toByte() }.toByteArray()

/** Builds an incoming frame `55 AA 00 cmd len err data ck FE FD` as the scooter would send it. */
fun rx(cmd: Int, data: ByteArray, err: Int = 0): ByteArray {
    val body = byteArrayOf(0x55, 0xAA.toByte(), 0, cmd.toByte(), (data.size + 1).toByte(), err.toByte(), *data)
    return byteArrayOf(*body, NaveeFrame.checksum(body).toByte(), 0xFE.toByte(), 0xFD.toByte())
}

fun ByteArray.at(i: Int, v: Int) = apply { this[i] = v.toByte() }

fun ByteArray.le(i: Int, len: Int, v: Long) = apply { for (k in 0 until len) this[i + k] = (v ushr (8 * k)).toByte() }
