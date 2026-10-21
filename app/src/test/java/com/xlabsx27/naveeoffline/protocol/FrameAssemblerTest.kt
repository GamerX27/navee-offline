package com.xlabsx27.naveeoffline.protocol

import org.junit.Assert.assertArrayEquals
import org.junit.Assert.assertEquals
import org.junit.Test

class FrameAssemblerTest {

    @Test fun singleFrame() {
        val frames = FrameAssembler().feed(rx(0x90, hex("00 03 50")))
        assertEquals(1, frames.size)
        assertEquals(0x90, frames[0].cmd)
        assertArrayEquals(hex("00 03 50"), frames[0].data)
    }

    @Test fun frameSplitAcrossChunks() {
        val a = FrameAssembler()
        val bytes = rx(0x72, ByteArray(15) { it.toByte() })
        assertEquals(0, a.feed(bytes.copyOfRange(0, 3)).size)
        assertEquals(0, a.feed(bytes.copyOfRange(3, 10)).size)
        val out = a.feed(bytes.copyOfRange(10, bytes.size))
        assertEquals(1, out.size)
        assertEquals(15, out[0].data.size)
    }

    @Test fun twoFramesInOneChunkWithNoise() {
        val chunk = byteArrayOf(0x01, 0x02, *rx(0x30, ByteArray(0)), 0x55, *rx(0x31, ByteArray(0)))
        val out = FrameAssembler().feed(chunk)
        assertEquals(listOf(0x30, 0x31), out.map { it.cmd })
    }

    @Test fun recoversFromWrongLengthByte() {
        val bad = rx(0x90, hex("00 03 50")).also { it[4] = 2 } // says 1 data byte, really 3
        val out = FrameAssembler().feed(byteArrayOf(*bad, *rx(0x31, ByteArray(0))))
        assertEquals(listOf(0x90, 0x31), out.map { it.cmd })
    }

    @Test fun headerSplitBetweenChunks() {
        val a = FrameAssembler()
        val bytes = rx(0x30, ByteArray(0))
        assertEquals(0, a.feed(byteArrayOf(bytes[0])).size)
        assertEquals(1, a.feed(bytes.copyOfRange(1, bytes.size)).size)
    }
}
