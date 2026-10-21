package com.xlabsx27.naveeoffline.pairing

/**
 * The pairing file moves this app's scooter pairing to another phone. It holds the 6-byte client id
 * the scooter bound itself to, which makes it a key: whoever has it can lock and unlock the scooter.
 *
 * ```
 * {"format":"navee-offline-pairing","version":1,"clientId":"880012345678",
 *  "scooter":{"address":"AA:BB:CC:DD:EE:FF","name":"NAVEE E45 Pro"}}
 * ```
 *
 * Pure Kotlin (no org.json) so it can be unit-tested on the JVM.
 */
data class Pairing(val clientId: ByteArray, val address: String?, val name: String?) {
    init {
        require(clientId.size == ID_SIZE) { "client id must be $ID_SIZE bytes" }
        require(address == null || MAC.matches(address)) { "bad Bluetooth address" }
    }

    val clientIdHex: String get() = clientId.joinToString("") { "%02x".format(it.toInt() and 0xFF) }

    override fun equals(other: Any?) =
        other is Pairing && clientId.contentEquals(other.clientId) && address == other.address && name == other.name

    override fun hashCode() = (clientId.contentHashCode() * 31 + address.hashCode()) * 31 + name.hashCode()

    companion object {
        const val ID_SIZE = 6
        val MAC = Regex("^[0-9A-F]{2}(:[0-9A-F]{2}){5}$")
    }
}

class PairingFormatException(message: String) : Exception(message)

object PairingFile {
    const val FORMAT = "navee-offline-pairing"
    const val VERSION = 1
    const val FILE_NAME = "navee-pairing.json"
    private const val MAX_SIZE = 4096
    private val HEX_ID = Regex("^[0-9a-fA-F]{12}$")

    fun encode(p: Pairing): String = buildString {
        append("{\"format\":").append(quote(FORMAT))
        append(",\"version\":").append(VERSION)
        append(",\"clientId\":").append(quote(p.clientIdHex))
        if (p.address != null || p.name != null) {
            append(",\"scooter\":{")
            val parts = listOfNotNull(
                p.address?.let { "\"address\":" + quote(it) },
                p.name?.let { "\"name\":" + quote(it) },
            )
            append(parts.joinToString(","))
            append("}")
        }
        append("}\n")
    }

    /** Parses and validates a pairing file. Throws [PairingFormatException] for anything unexpected. */
    fun decode(text: String): Pairing {
        if (text.length > MAX_SIZE) fail("file too large")
        val root = runCatching { Json(text).parseDocument() }.getOrElse { fail("not valid JSON") } as? Map<*, *>
            ?: fail("not a JSON object")
        if (root["format"] != FORMAT) fail("not a Navee Offline pairing file")
        val version = root["version"] as? Long ?: fail("missing version")
        if (version != VERSION.toLong()) fail("unsupported version $version")
        val idHex = root["clientId"] as? String ?: fail("missing clientId")
        if (!HEX_ID.matches(idHex)) fail("clientId must be 12 hex digits")
        val id = idHex.chunked(2).map { it.toInt(16).toByte() }.toByteArray()
        if (id.all { it.toInt() == 0 }) fail("clientId is all zeros")

        val scooter = when (val s = root["scooter"]) {
            null -> null
            is Map<*, *> -> s
            else -> fail("scooter must be an object")
        }
        val address = scooter?.get("address")?.let { it as? String ?: fail("address must be a string") }?.uppercase()
        if (address != null && !Pairing.MAC.matches(address)) fail("bad Bluetooth address")
        val name = scooter?.get("name")?.let { it as? String ?: fail("name must be a string") }
        if (name != null && (name.length > 64 || name.any { it.isISOControl() })) fail("bad scooter name")
        return Pairing(id, address, name)
    }

    private fun fail(msg: String): Nothing = throw PairingFormatException(msg)

    private fun quote(s: String) = buildString {
        append('"')
        for (ch in s) when {
            ch == '"' -> append("\\\"")
            ch == '\\' -> append("\\\\")
            ch < ' ' -> append("\\u%04x".format(ch.code))
            else -> append(ch)
        }
        append('"')
    }
}

/** Minimal strict JSON reader: objects, arrays, strings, integers, booleans and null. */
private class Json(private val s: String) {
    private var i = 0

    fun parseDocument(): Any? {
        val v = value(0)
        ws()
        require(i == s.length) { "trailing data" }
        return v
    }

    private fun value(depth: Int): Any? {
        require(depth < 8) { "nested too deep" }
        ws()
        require(i < s.length) { "unexpected end" }
        return when (s[i]) {
            '{' -> obj(depth)
            '[' -> arr(depth)
            '"' -> str()
            't' -> literal("true", true)
            'f' -> literal("false", false)
            'n' -> literal("null", null)
            else -> int()
        }
    }

    private fun obj(depth: Int): Map<String, Any?> {
        i++
        val m = LinkedHashMap<String, Any?>()
        ws()
        if (peek() == '}') { i++; return m }
        while (true) {
            ws()
            require(peek() == '"') { "expected key" }
            val k = str()
            require(k !in m) { "duplicate key" }
            ws()
            require(next() == ':') { "expected :" }
            m[k] = value(depth + 1)
            ws()
            when (next()) {
                ',' -> continue
                '}' -> return m
                else -> throw IllegalArgumentException("expected , or }")
            }
        }
    }

    private fun arr(depth: Int): List<Any?> {
        i++
        val l = ArrayList<Any?>()
        ws()
        if (peek() == ']') { i++; return l }
        while (true) {
            l += value(depth + 1)
            ws()
            when (next()) {
                ',' -> continue
                ']' -> return l
                else -> throw IllegalArgumentException("expected , or ]")
            }
        }
    }

    private fun str(): String {
        i++
        val b = StringBuilder()
        while (true) {
            val ch = next()
            when {
                ch == '"' -> return b.toString()
                ch == '\\' -> when (val e = next()) {
                    '"', '\\', '/' -> b.append(e)
                    'b' -> b.append('\b')
                    'f' -> b.append('\u000C')
                    'n' -> b.append('\n')
                    'r' -> b.append('\r')
                    't' -> b.append('\t')
                    'u' -> {
                        require(i + 4 <= s.length) { "bad escape" }
                        b.append(s.substring(i, i + 4).toInt(16).toChar())
                        i += 4
                    }
                    else -> throw IllegalArgumentException("bad escape")
                }
                ch < ' ' -> throw IllegalArgumentException("control character in string")
                else -> b.append(ch)
            }
        }
    }

    private fun int(): Long {
        val start = i
        if (peek() == '-') i++
        while (i < s.length && s[i].isDigit()) i++
        require(i > start) { "unexpected character" }
        return s.substring(start, i).toLong()
    }

    private fun literal(word: String, v: Any?): Any? {
        require(s.startsWith(word, i)) { "unexpected character" }
        i += word.length
        return v
    }

    private fun ws() {
        while (i < s.length && s[i] in " \t\r\n") i++
    }

    private fun peek(): Char? = s.getOrNull(i)

    private fun next(): Char {
        require(i < s.length) { "unexpected end" }
        return s[i++]
    }
}
