package com.xlabsx27.naveeoffline.protocol

/**
 * Frame dumps for the debug log with secrets masked: the client id in the 0x30 handshake (anyone
 * holding it can lock and unlock the scooter) and the serial number in 0x74 replies.
 */
object Redact {
    private const val MASK = "**"

    fun tx(frame: ByteArray): String {
        val cmd = frame.getOrNull(3)?.toInt()?.and(0xFF)
        if (cmd != NaveeAuth.CMD_INIT || frame.size < 8) return frame.toHex()
        // 55 AA 00 30 len [keyIndex 0 id×6 0] ck FE FD: keep framing, hide payload and checksum.
        return frame.mapIndexed { i, b ->
            if (i < 5 || i >= frame.size - 2) "%02X".format(b.toInt() and 0xFF) else MASK
        }.joinToString(" ")
    }

    fun rx(f: RxFrame): String {
        val data = if (f.cmd == Commands.READ_SERIAL) "<${f.data.size} bytes hidden>" else f.data.toHex()
        return "cmd=%02X err=%d %s".format(f.cmd, f.err, data)
    }
}
