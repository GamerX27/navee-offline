package com.xlabsx27.naveeoffline.protocol

import javax.crypto.Cipher
import javax.crypto.spec.SecretKeySpec
import kotlin.random.Random

/**
 * Session handshake that makes the scooter start pushing its live reports (0x90/0x92).
 *
 * 1. App sends 0x30 `[keyIndex, 0, id×6, 0]` with this install's id (see [newClientId]).
 * 2. Scooter answers 0x30 with a 16-byte challenge, optionally prefixed by a mode byte
 *    (0 = XOR with the key, anything else = AES-128-ECB with the key).
 * 3. App sends 0x31 with the transformed block.
 * 4. Scooter acks 0x31. **We stop here** and never send a second 0x30 ourselves.
 *
 * A scooter that is not bound to an account may bind itself to the id it sees (an E45 Pro sent a
 * late 0x30 "complete" a few minutes after connecting). From then on it rejects any other id with
 * err 2, so the id must stay the same across connections: it is generated once and stored.
 */
object NaveeAuth {
    const val CMD_INIT = 0x30
    const val CMD_RESPONSE = 0x31
    const val KEY_INDEX = 1

    private val KEY = byteArrayOf(
        0x44, 0x6D, 0x10, 0x72, 0x6D, 0xBE.toByte(), 0x05, 0xF6.toByte(),
        0x62, 0xDF.toByte(), 0xAA.toByte(), 0xF0.toByte(), 0x13, 0x27, 0x30, 0x3F,
    )

    const val CLIENT_ID_SIZE = 6

    /** A fresh client id: non-zero leading byte, as the scooter expects. Generate once, then store. */
    fun newClientId(random: Random = Random.Default): ByteArray {
        val id = ByteArray(CLIENT_ID_SIZE)
        id[0] = 0x88.toByte()
        for (i in 2 until CLIENT_ID_SIZE) id[i] = random.nextInt(256).toByte()
        return id
    }

    fun initPayload(clientId: ByteArray): ByteArray {
        require(clientId.size == CLIENT_ID_SIZE) { "client id must be $CLIENT_ID_SIZE bytes" }
        return byteArrayOf(KEY_INDEX.toByte(), 0, *clientId, 0)
    }

    /** The 0x31 payload for a 0x30 challenge, or null if [challenge] carries no 16-byte block. */
    fun respond(challenge: RxFrame): ByteArray? {
        val d = challenge.data
        return when {
            d.size > 16 -> {
                val block = d.copyOfRange(1, 17)
                if (d[0].toInt() == 0) xor(block) else aes(block)
            }
            d.size == 16 -> aes(d)
            else -> null
        }
    }

    fun xor(block: ByteArray): ByteArray = ByteArray(16) { (block[it].toInt() xor KEY[it].toInt()).toByte() }

    // The scooter mandates single-block AES-ECB; there is no IV or chaining to choose.
    @Suppress("GetInstance")
    fun aes(block: ByteArray): ByteArray {
        val c = Cipher.getInstance("AES/ECB/NoPadding")
        c.init(Cipher.ENCRYPT_MODE, SecretKeySpec(KEY, "AES"))
        return c.doFinal(block)
    }
}

/** One handshake attempt. Feed every incoming frame to [onFrame] until it reports [Step.Finished]. */
class AuthSession(private val clientId: ByteArray) {

    sealed interface Step {
        class Send(val frame: ByteArray) : Step
        /** [rejected] means the scooter refused this client id: it is bound to someone else. */
        data class Finished(val armed: Boolean, val reason: String, val rejected: Boolean = false) : Step
    }

    var finished = false
        private set
    private var responded = false

    fun start(): ByteArray = NaveeFrame.write(NaveeAuth.CMD_INIT, NaveeAuth.initPayload(clientId))

    fun onFrame(f: RxFrame): Step? {
        if (finished) return null
        return when (f.cmd) {
            NaveeAuth.CMD_INIT -> when {
                !f.ok -> finish(false, "init rejected (err ${f.err})", rejected = true)
                f.data.isEmpty() -> finish(true, "complete")
                responded -> null
                else -> {
                    val r = NaveeAuth.respond(f) ?: return finish(false, "malformed challenge")
                    responded = true
                    Step.Send(NaveeFrame.write(NaveeAuth.CMD_RESPONSE, r))
                }
            }
            NaveeAuth.CMD_RESPONSE -> finish(f.ok, if (f.ok) "armed" else "response rejected (err ${f.err})")
            else -> null
        }
    }

    /** Give up waiting (old firmware may not answer at all). */
    fun timeout(): Step.Finished = finish(false, "timeout")

    private fun finish(armed: Boolean, reason: String, rejected: Boolean = false): Step.Finished {
        finished = true
        return Step.Finished(armed, reason, rejected)
    }
}
