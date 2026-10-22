package com.xlabsx27.naveeoffline.service

/**
 * Decides when to let go of a parked scooter: once it has reported locked for [update]'s `afterMs`
 * while connected, the app disconnects and stays away until the user connects by hand, so a parked
 * scooter does not keep the phone's Bluetooth and GPS busy.
 *
 * An unlock or a dropped link restarts the wait. It fires once per locked stretch.
 *
 * No Android types: the service feeds it the state and acts on [update]'s answer.
 */
class LockSleep {
    /** When the current locked stretch began, or null while not locked and connected. */
    var lockedSince: Long? = null
        private set
    private var fired = false

    /** Returns true exactly when the app should disconnect now. */
    fun update(enabled: Boolean, connected: Boolean, locked: Boolean?, afterMs: Long, now: Long): Boolean {
        if (!connected || locked == false) {
            lockedSince = null
            fired = false
            return false
        }
        if (locked == null) return false
        val since = lockedSince ?: now.also { lockedSince = it }
        if (!enabled || fired || now - since < afterMs) return false
        fired = true
        return true
    }
}
