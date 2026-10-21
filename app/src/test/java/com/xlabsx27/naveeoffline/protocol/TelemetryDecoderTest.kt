package com.xlabsx27.naveeoffline.protocol

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class TelemetryDecoderTest {

    private fun apply(t: Telemetry, bytes: ByteArray) =
        FrameAssembler().feed(bytes).fold(t) { acc, f -> TelemetryDecoder.apply(acc, f, now = 1) }

    @Test fun params() {
        val d = ByteArray(36)
            .at(2, 1).at(3, 1).at(4, 1).at(7, 0).at(11, 1).at(19, 3).at(20, 0x80 or 25)
            .at(24, 1).at(25, 30).at(1, DriveMode.TURBO).at(32, 1)
        val t = apply(Telemetry(), rx(0x70, d))
        assertEquals(true, t.locked)
        assertEquals(1, t.cruise)
        assertEquals(1, t.tailLight)
        assertEquals(1, t.tcs)
        assertEquals(3, t.startSpeed)
        assertEquals(25, t.limitSpeed)
        assertEquals(true, t.limitOn)
        assertEquals(1, t.drl)
        assertEquals(30, t.maxSpeed)
        assertEquals(DriveMode.TURBO, t.driveMode)
        assertEquals(1, t.eco)
    }

    @Test fun paramsWithErrorAreIgnored() {
        val t = apply(Telemetry(), rx(0x70, ByteArray(36).at(2, 1), err = 1))
        assertNull(t.locked)
    }

    @Test fun battery() {
        val d = ByteArray(15).at(1, 87).le(2, 4, 48_150).le(6, 4, 0x8000_0000L or 2_500)
            .at(10, 98).at(11, 24).le(13, 2, 142)
        val t = apply(Telemetry(), rx(0x72, d))
        assertEquals(87, t.soc)
        assertEquals(48.15, t.voltage!!, 1e-9)
        assertEquals(-2_500L, t.packMa)
        assertEquals(98, t.soh)
        assertEquals(24, t.batteryTempC)
        assertEquals(142, t.cycles)
    }

    @Test fun homeReport() {
        val d = ByteArray(16).at(0, 0x21).at(1, 3).at(2, 64).at(6, 31).at(7, 2).le(8, 4, 50_000).le(12, 4, 1_200)
        val t = apply(Telemetry(), rx(0x90, d))
        assertEquals("E9", t.fault?.code)
        assertEquals(64, t.soc)
        assertEquals(31, t.rangeKm)
        assertEquals(true, t.locked)  // byte 7 = lock + 1
        assertEquals(1_200L, t.packMa)
    }

    @Test fun shortHomeAckKeepsPreviousValues() {
        val before = Telemetry(soc = 50, locked = false)
        val t = apply(before, rx(0x90, ByteArray(0)))
        assertEquals(50, t.soc)
        assertEquals(false, t.locked)
    }

    @Test fun rideReportV1() {
        val d = ByteArray(14).at(0, 70).le(2, 2, 215).at(4, 20).le(5, 2, 34).at(7, 12)
            .le(8, 2, 251).le(10, 2, 180).le(12, 2, 12_345)
        val t = apply(Telemetry(), rx(0x92, d))
        assertEquals(21.5, t.speedKmh!!, 1e-9)
        assertEquals(3.4, t.tripKm!!, 1e-9)
        assertEquals(1234.5, t.odometerKm!!, 1e-9)
        assertEquals(25.1, t.tripMaxKmh!!, 1e-9)
    }

    @Test fun serialGivesRegionAndModel() {
        val sn = "N2443ABCDE12345".toByteArray()
        val t = apply(Telemetry(), rx(0x74, sn))
        assertEquals("2443", t.pid)
        assertEquals("XT5 Ultra", t.model)
        assertEquals("DE", t.region)
    }

    @Test fun unknownFaultIsRaw() {
        val f = NaveeFaults.decode(0x99)!!
        assertEquals(99, f.number)
        assertNull(f.code)
        assertNull(NaveeFaults.decode(0))
    }
}
