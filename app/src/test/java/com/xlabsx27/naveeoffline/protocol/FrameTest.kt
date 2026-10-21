package com.xlabsx27.naveeoffline.protocol

import org.junit.Assert.assertArrayEquals
import org.junit.Assert.assertEquals
import org.junit.Test
import java.util.TimeZone

class FrameTest {

    @Test fun readFrame() {
        assertArrayEquals(hex("55 AA 00 70 6F FE FD"), NaveeFrame.read(0x70))
    }

    @Test fun writeFrame() {
        assertArrayEquals(hex("55 AA 00 51 01 01 52 FE FD"), Setting.LOCK.frame(1))
    }

    @Test fun subCommandFrame() {
        // 55+AA+00+6D+02+03+01 = 0x172
        assertArrayEquals(hex("55 AA 00 6D 02 03 01 72 FE FD"), Setting.DRL.frame(1))
    }

    @Test fun clockSyncIsLocalSecondsBigEndian() {
        val utc = TimeZone.getTimeZone("UTC")
        val f = Commands.clockSync(nowMs = 0x01020304L * 1000, tz = utc)
        assertArrayEquals(hex("06 01 02 03 04"), f.copyOfRange(5, 10))
        val plus2 = Commands.clockSync(nowMs = 0x01020304L * 1000, tz = TimeZone.getTimeZone("GMT+02:00"))
        assertEquals((0x04 + 7200) and 0xFF, plus2[9].toInt() and 0xFF)
    }
}
