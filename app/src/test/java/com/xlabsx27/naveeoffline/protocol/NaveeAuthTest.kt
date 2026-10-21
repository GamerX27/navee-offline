package com.xlabsx27.naveeoffline.protocol

import org.junit.Assert.assertArrayEquals
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import kotlin.random.Random

class NaveeAuthTest {
    private val block = ByteArray(16) { it.toByte() }

    // Independent vector: openssl enc -aes-128-ecb -K 446d10726dbe05f662dfaaf01327303f -nopad
    private val aesOfBlock = hex("c8c0526bdd6b5d9d9214d15c89042f98")

    private fun decode(bytes: ByteArray) = FrameAssembler().feed(bytes).single()

    @Test fun initPayloadShape() {
        val p = NaveeAuth.initPayload(NaveeAuth.newClientId(Random(1)))
        assertEquals(9, p.size)
        assertEquals(1, p[0].toInt())
        assertEquals(0, p[1].toInt())
        assertEquals(0x88, p[2].toInt() and 0xFF)
        assertEquals(0, p[8].toInt())
    }

    @Test fun sameClientIdEveryConnect() {
        val id = NaveeAuth.newClientId(Random(3))
        assertArrayEquals(AuthSession(id).start(), AuthSession(id).start())
    }

    @Test fun bareChallengeIsAes() {
        assertArrayEquals(aesOfBlock, NaveeAuth.respond(decode(rx(0x30, block))))
    }

    @Test fun modeByteSelectsAesOrXor() {
        assertArrayEquals(aesOfBlock, NaveeAuth.respond(decode(rx(0x30, byteArrayOf(1, *block)))))
        val xored = NaveeAuth.respond(decode(rx(0x30, byteArrayOf(0, *block))))!!
        assertEquals(0x44, xored[0].toInt() and 0xFF)         // 0x00 ^ 0x44
        assertEquals(0x3F xor 0x0F, xored[15].toInt() and 0xFF)
    }

    @Test fun armOnlyHandshakeNeverSendsSecondInit() {
        val s = AuthSession(NaveeAuth.newClientId(Random(7)))
        val init = decode(s.start())
        assertEquals(0x30, init.cmd)

        val step = s.onFrame(decode(rx(0x30, block)))
        assertTrue(step is AuthSession.Step.Send)
        val response = decode((step as AuthSession.Step.Send).frame)
        assertEquals(0x31, response.cmd)

        val done = s.onFrame(decode(rx(0x31, ByteArray(0))))
        assertEquals(AuthSession.Step.Finished(true, "armed"), done)

        // Anything after the ack, including another challenge, produces nothing to send.
        assertNull(s.onFrame(decode(rx(0x30, block))))
        assertNull(s.onFrame(decode(rx(0x31, ByteArray(0)))))
    }

    @Test fun rejectedInitFinishesUnarmed() {
        val s = AuthSession(NaveeAuth.newClientId())
        s.start()
        val done = s.onFrame(decode(rx(0x30, ByteArray(0), err = 2))) as AuthSession.Step.Finished
        assertEquals(false, done.armed)
        assertEquals(true, done.rejected)
    }

    @Test fun answersOnlyOneChallenge() {
        val s = AuthSession(NaveeAuth.newClientId())
        s.start()
        assertTrue(s.onFrame(decode(rx(0x30, block))) is AuthSession.Step.Send)
        assertNull(s.onFrame(decode(rx(0x30, block))))
    }
}
