package com.xlabsx27.naveeoffline.service

/** How far the phone may be from the scooter before auto-lock fires, as a signal threshold. */
enum class AutoLockRange(val dbm: Int) { NEAR(-70), MEDIUM(-80), FAR(-88) }

/**
 * Decides when to lock the scooter because the rider walked away. It fires once when all hold:
 * the feature is on, the link is up, the scooter reports unlocked, it has reported speed 0 for
 * [stillMs], and the average of the last [window] signal readings is below the threshold.
 *
 * It never fires while the speed is above 0 or unknown. After firing it waits until the scooter
 * has reported locked and then unlocked again before it can fire a second time.
 *
 * No Android types: the service feeds it readings and acts on [update]'s answer.
 */
class AutoLock(private val window: Int = 3, private val stillMs: Long = 30_000L) {
    private val samples = ArrayDeque<Int>()
    private var stillSince: Long? = null
    private var fired = false
    private var lockSeen = false

    fun onRssi(rssi: Int) {
        samples.addLast(rssi)
        while (samples.size > window) samples.removeFirst()
    }

    /** The link dropped: old readings say nothing about the next connection. */
    fun onDisconnected() {
        samples.clear()
        stillSince = null
        fired = false
        lockSeen = false
    }

    /** Returns true exactly when the scooter should be locked now. */
    fun update(
        enabled: Boolean,
        connected: Boolean,
        locked: Boolean?,
        speedKmh: Double?,
        threshold: AutoLockRange,
        now: Long,
    ): Boolean {
        if (!connected) {
            onDisconnected()
            return false
        }
        when (locked) {
            true -> if (fired) lockSeen = true
            false -> if (fired && lockSeen) { fired = false; lockSeen = false }
            null -> Unit
        }
        // Standstill is only counted while unlocked, so an unlock always starts a fresh 30 s.
        if (speedKmh == null || speedKmh > 0.0 || locked != false) {
            stillSince = null
            return false
        }
        val since = stillSince ?: now.also { stillSince = it }
        if (!enabled || fired) return false
        if (now - since < stillMs || samples.size < window) return false
        if (samples.average() >= threshold.dbm) return false
        fired = true
        return true
    }
}
