package com.xlabsx27.naveeoffline.protocol

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Test

class RedactTest {
    private val id = hex("88 00 12 34 56 78")

    @Test
    fun hidesTheClientIdInTheHandshake() {
        val frame = AuthSession(id).start()
        val text = Redact.tx(frame)
        assertFalse(text.contains("12 34 56 78"))
        assertEquals("55 AA 00 30 09 ** ** ** ** ** ** ** ** ** ** FE FD", text)
    }

    @Test
    fun leavesOtherFramesAlone() {
        assertEquals("55 AA 00 51 01 01 52 FE FD", Redact.tx(Setting.LOCK.frame(1)))
    }

    @Test
    fun hidesTheSerial() {
        val f = FrameAssembler().feed(rx(Commands.READ_SERIAL, "N2611ABCDEF".toByteArray())).single()
        assertEquals("cmd=74 err=0 <11 bytes hidden>", Redact.rx(f))
    }
}
