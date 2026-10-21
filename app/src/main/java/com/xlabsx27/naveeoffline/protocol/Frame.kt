package com.xlabsx27.naveeoffline.protocol

/**
 * NAVEE wire format.
 *
 * Outgoing: `55 AA 00 <cmd> [<len> <payload…>] <ck> FE FD`, where `ck` is the low byte of the sum of
 * every byte before it. A read has no length byte; a write always has one.
 *
 * Incoming: `55 AA <flag> <cmd> <len> <err> <data: len-1 bytes> <ck> FE FD` (total `8 + len`).
 */
object NaveeFrame {
    const val H1 = 0x55
    const val H2 = 0xAA
    const val T1 = 0xFE
    const val T2 = 0xFD
    const val MAX_PAYLOAD = 0xFF

    fun checksum(bytes: ByteArray, length: Int = bytes.size): Int {
        var sum = 0
        for (i in 0 until length) sum = (sum + (bytes[i].toInt() and 0xFF)) and 0xFF
        return sum
    }

    fun read(cmd: Int): ByteArray = finish(byteArrayOf(H1.toByte(), H2.toByte(), 0, cmd.toByte()))

    fun write(cmd: Int, payload: ByteArray = ByteArray(0)): ByteArray {
        require(payload.size <= MAX_PAYLOAD) { "payload too long: ${payload.size}" }
        val body = ByteArray(5 + payload.size)
        body[0] = H1.toByte()
        body[1] = H2.toByte()
        body[2] = 0
        body[3] = cmd.toByte()
        body[4] = payload.size.toByte()
        payload.copyInto(body, 5)
        return finish(body)
    }

    private fun finish(body: ByteArray): ByteArray {
        val out = body.copyOf(body.size + 3)
        out[body.size] = checksum(body).toByte()
        out[body.size + 1] = T1.toByte()
        out[body.size + 2] = T2.toByte()
        return out
    }
}

/** One decoded incoming frame. [data] excludes the err byte. */
class RxFrame(val flag: Int, val cmd: Int, val err: Int, val data: ByteArray) {
    val ok: Boolean get() = err == 0

    fun u8(offset: Int): Int? = if (offset in data.indices) data[offset].toInt() and 0xFF else null

    /** Little-endian unsigned read of [length] bytes (1..4); null if the frame is too short. */
    fun le(offset: Int, length: Int): Long? {
        if (offset < 0 || offset + length > data.size) return null
        var v = 0L
        for (i in length - 1 downTo 0) v = (v shl 8) or (data[offset + i].toLong() and 0xFF)
        return v
    }

    fun ascii(offset: Int, length: Int): String? {
        if (offset < 0 || offset + length > data.size) return null
        return buildString {
            for (i in offset until offset + length) {
                val c = data[i].toInt() and 0xFF
                append(if (c in 0x20..0x7E) c.toChar() else '.')
            }
        }
    }

    override fun toString(): String = "RxFrame(cmd=0x%02X, err=%d, data=%s)".format(cmd, err, data.toHex())
}

fun ByteArray.toHex(): String = joinToString(" ") { "%02X".format(it.toInt() and 0xFF) }
