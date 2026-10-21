package com.xlabsx27.naveeoffline.pairing

import org.junit.Assert.assertArrayEquals
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class PairingFileTest {
    private val id = byteArrayOf(0x88.toByte(), 0x00, 0x12, 0x34, 0x56, 0x78)

    @Test
    fun roundTrip() {
        val p = Pairing(id, "AA:BB:CC:DD:EE:FF", "NAVEE \"E45\" Pro")
        val text = PairingFile.encode(p)
        assertEquals(p, PairingFile.decode(text))
    }

    @Test
    fun encodesExpectedShape() {
        val text = PairingFile.encode(Pairing(id, "AA:BB:CC:DD:EE:FF", "NAVEE"))
        assertEquals(
            "{\"format\":\"navee-offline-pairing\",\"version\":1,\"clientId\":\"880012345678\"," +
                "\"scooter\":{\"address\":\"AA:BB:CC:DD:EE:FF\",\"name\":\"NAVEE\"}}\n",
            text,
        )
    }

    @Test
    fun acceptsWhitespaceLowercaseMacAndNoScooter() {
        val p = PairingFile.decode(
            """
            { "version": 1, "format" : "navee-offline-pairing",
              "clientId": "880012345678", "scooter": { "address": "aa:bb:cc:dd:ee:ff" } }
            """.trimIndent(),
        )
        assertArrayEquals(id, p.clientId)
        assertEquals("AA:BB:CC:DD:EE:FF", p.address)
        assertNull(p.name)

        val bare = PairingFile.decode("""{"format":"navee-offline-pairing","version":1,"clientId":"880012345678"}""")
        assertNull(bare.address)
    }

    @Test
    fun rejectsBadFiles() {
        val good = """"format":"navee-offline-pairing","version":1"""
        val bad = listOf(
            "",
            "not json",
            "[]",
            """{$good}""",
            """{$good,"clientId":"8800123456"}""",
            """{$good,"clientId":"880012345678ff"}""",
            """{$good,"clientId":"8800123456zz"}""",
            """{$good,"clientId":"000000000000"}""",
            """{$good,"clientId":12}""",
            """{"format":"something-else","version":1,"clientId":"880012345678"}""",
            """{"format":"navee-offline-pairing","version":2,"clientId":"880012345678"}""",
            """{$good,"clientId":"880012345678","scooter":{"address":"AA:BB:CC:DD:EE"}}""",
            """{$good,"clientId":"880012345678","scooter":{"address":"AA-BB-CC-DD-EE-FF"}}""",
            """{$good,"clientId":"880012345678","scooter":"AA:BB:CC:DD:EE:FF"}""",
            """{$good,"clientId":"880012345678","scooter":{"name":"a\u0000b"}}""",
            """{$good,"clientId":"880012345678"} trailing""",
            """{$good,"clientId":"880012345678","clientId":"8800123456a5"}""",
            "{" + "\"x\":".repeat(20) + "1" + "}".repeat(20),
            """{$good,"clientId":"880012345678","pad":"${"x".repeat(5000)}"}""",
        )
        for (text in bad) {
            try {
                PairingFile.decode(text)
                throw AssertionError("accepted: $text")
            } catch (_: PairingFormatException) {
            }
        }
    }
}
