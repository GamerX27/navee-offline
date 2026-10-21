package com.xlabsx27.naveeoffline.ble

import android.content.Context
import com.xlabsx27.naveeoffline.protocol.NaveeAuth

/**
 * The id this app presents in the scooter handshake. It must never change once a scooter may have
 * bound to it, so it is created once per install and read synchronously before every connect. The
 * only way to replace it is importing a pairing file ([set]).
 */
class ClientIdStore(context: Context) {
    private val prefs = context.getSharedPreferences("client_id", Context.MODE_PRIVATE)

    val id: ByteArray
        @Synchronized get() {
            prefs.getString(KEY, null)?.let(::decode)?.let { return it }
            val fresh = NaveeAuth.newClientId()
            prefs.edit().putString(KEY, encode(fresh)).commit()
            return fresh
        }

    /** Replaces the id, e.g. with the one from another phone's pairing file. Takes effect on the next connect. */
    @Synchronized
    fun set(id: ByteArray) {
        require(id.size == NaveeAuth.CLIENT_ID_SIZE) { "client id must be ${NaveeAuth.CLIENT_ID_SIZE} bytes" }
        check(prefs.edit().putString(KEY, encode(id)).commit()) { "could not save the client id" }
    }

    private fun encode(b: ByteArray) = b.joinToString("") { "%02x".format(it.toInt() and 0xFF) }

    private fun decode(s: String): ByteArray? = runCatching {
        s.chunked(2).map { it.toInt(16).toByte() }.toByteArray()
    }.getOrNull()?.takeIf { it.size == NaveeAuth.CLIENT_ID_SIZE }

    private companion object {
        const val KEY = "id"
    }
}
