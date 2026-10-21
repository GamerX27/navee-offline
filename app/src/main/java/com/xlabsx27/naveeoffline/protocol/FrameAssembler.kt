package com.xlabsx27.naveeoffline.protocol

/**
 * Reassembles incoming frames from BLE notifications. A frame can span several notifications and a
 * notification can hold several frames. The length byte is trusted first; if the trailer is not where
 * it says, the assembler falls back to scanning for `FE FD` so one corrupt byte cannot stall the link.
 */
class FrameAssembler(capacity: Int = 1024) {
    private val buf = ByteArray(capacity)
    private var size = 0

    fun reset() {
        size = 0
    }

    fun feed(chunk: ByteArray): List<RxFrame> {
        val out = ArrayList<RxFrame>(2)
        for (b in chunk) {
            if (size == buf.size) dropFront(1) // overflow: oldest byte is garbage anyway
            buf[size++] = b
        }
        while (true) {
            val start = findHeader()
            if (start < 0) {
                // Keep a trailing 0x55: it may be the first half of the next header.
                if (size > 0 && at(size - 1) == NaveeFrame.H1) {
                    buf[0] = buf[size - 1]
                    size = 1
                } else {
                    size = 0
                }
                return out
            }
            if (start > 0) dropFront(start)
            if (size < 5) return out

            val cmd = at(3)
            val total = 8 + at(4)
            if (size < total) return out

            val end = if (isTrailer(total - 2, cmd)) total else scanTrailer(cmd)
            if (end < 0) {
                dropFront(1) // false header or corrupt frame: resync on the next 55 AA
                continue
            }
            parse(end)?.let(out::add)
            dropFront(end)
        }
    }

    private fun at(i: Int) = buf[i].toInt() and 0xFF

    private fun findHeader(): Int {
        for (i in 0 until size - 1) if (at(i) == NaveeFrame.H1 && at(i + 1) == NaveeFrame.H2) return i
        return -1
    }

    /** `FE FD`, or the factory trailer `AE AD` which only appears on commands >= 0xA0. */
    private fun isTrailer(i: Int, cmd: Int): Boolean {
        if (i < 0 || i + 1 >= size) return false
        val a = at(i)
        val b = at(i + 1)
        return (a == NaveeFrame.T1 && b == NaveeFrame.T2) || (cmd >= 0xA0 && a == 0xAE && b == 0xAD)
    }

    /** Returns the exclusive end of the first trailer after the header, or -1. */
    private fun scanTrailer(cmd: Int): Int {
        for (i in 5 until size - 1) if (isTrailer(i, cmd)) return i + 2
        return -1
    }

    private fun parse(end: Int): RxFrame? {
        if (end < 8) return null
        val len = at(4)
        // data sits between the err byte and `ck FE FD`; clamp if the length byte lied.
        val dataLen = minOf(maxOf(0, len - 1), end - 3 - 6)
        val data = buf.copyOfRange(6, 6 + maxOf(0, dataLen))
        return RxFrame(flag = at(2), cmd = at(3), err = at(5), data = data)
    }

    private fun dropFront(n: Int) {
        buf.copyInto(buf, 0, n, size)
        size -= n
    }
}
