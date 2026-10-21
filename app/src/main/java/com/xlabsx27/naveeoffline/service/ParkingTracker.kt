package com.xlabsx27.naveeoffline.service

import android.location.Location
import com.xlabsx27.naveeoffline.AppContainer
import com.xlabsx27.naveeoffline.data.ParkReason
import com.xlabsx27.naveeoffline.protocol.Telemetry

/**
 * Decides when the phone's position becomes the scooter's parking spot.
 *
 * - Locking the scooter parks it right there. That spot stands until the scooter is unlocked, so
 *   walking away afterwards does not drag the pin along with the phone.
 * - For a short while after locking, a more accurate fix refines the spot (indoor fixes are coarse).
 * - If the link drops while the scooter is unlocked, the last position is kept as a fallback.
 */
class ParkingTracker(private val c: AppContainer) {
    private var wasLocked: Boolean? = null
    /** Last lock state the scooter reported; survives the telemetry reset when the link drops. */
    private var lastKnownLocked: Boolean? = null
    private var lockedAt: Long? = null
    private var wasConnected = false

    fun onTelemetry(t: Telemetry) {
        val locked = t.locked
        if (locked != null) lastKnownLocked = locked
        if (wasLocked == false && locked == true && c.settings.value.parkOnLock) {
            val now = System.currentTimeMillis()
            lockedAt = now
            c.markParked(ParkReason.LOCKED, time = now)
        }
        if (locked == false) lockedAt = null
        wasLocked = locked
    }

    fun onLink(connected: Boolean) {
        if (wasConnected && !connected) {
            val lockSpotStands = lastKnownLocked == true && c.parked.value?.reason == ParkReason.LOCKED
            if (!lockSpotStands) c.markParked(ParkReason.OUT_OF_RANGE)
        }
        wasConnected = connected
    }

    fun onFix(fix: Location) {
        val since = lockedAt ?: return
        val now = System.currentTimeMillis()
        if (now - since > REFINE_WINDOW_MS) {
            lockedAt = null
            return
        }
        val saved = c.parked.value ?: return
        if (saved.reason != ParkReason.LOCKED || !fix.hasAccuracy()) return
        if (fix.accuracy < (saved.accuracyM ?: Float.MAX_VALUE)) {
            c.markParked(ParkReason.LOCKED, fix = fix, time = saved.time)
        }
    }

    private companion object {
        const val REFINE_WINDOW_MS = 2 * 60_000L
    }
}
