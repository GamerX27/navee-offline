package com.xlabsx27.naveeoffline.service

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class AutoLockTest {
    private val range = AutoLockRange.MEDIUM

    /** Feeds one reading every 2 s for [seconds] and returns how many times it said "lock". */
    private fun AutoLock.run(
        seconds: Int,
        rssi: Int,
        start: Long = 0L,
        speed: Double? = 0.0,
        locked: Boolean? = false,
        enabled: Boolean = true,
    ): Int {
        var fires = 0
        var t = start
        while (t <= start + seconds * 1000L) {
            onRssi(rssi)
            if (update(enabled, connected = true, locked = locked, speedKmh = speed, threshold = range, now = t)) fires++
            t += 2_000
        }
        return fires
    }

    @Test
    fun locksOnceAfterStandingStillWithWeakSignal() {
        val a = AutoLock()
        assertEquals(1, a.run(60, rssi = -90))
    }

    @Test
    fun waitsThirtySecondsOfStandstill() {
        val a = AutoLock()
        assertEquals(0, a.run(28, rssi = -90))
        assertEquals(1, a.run(4, rssi = -90, start = 30_000))
    }

    @Test
    fun neverFiresWhileMovingOrSpeedUnknown() {
        assertEquals(0, AutoLock().run(120, rssi = -95, speed = 4.0))
        assertEquals(0, AutoLock().run(120, rssi = -95, speed = null))
    }

    @Test
    fun movementRestartsTheStandstillTimer() {
        val a = AutoLock()
        a.run(20, rssi = -90)
        a.run(0, rssi = -90, start = 22_000, speed = 1.0)
        assertEquals(0, a.run(26, rssi = -90, start = 24_000))
        assertEquals(1, a.run(6, rssi = -90, start = 54_000))
    }

    @Test
    fun strongSignalDoesNotFire() {
        assertEquals(0, AutoLock().run(120, rssi = -60))
        // -79 is still inside the Medium range (-80).
        assertEquals(0, AutoLock().run(120, rssi = -79))
    }

    @Test
    fun averagesTheLastThreeReadings() {
        val a = AutoLock()
        a.run(40, rssi = -60)
        a.onRssi(-95)
        a.onRssi(-95)
        // (-60 - 95 - 95) / 3 = -83.3: weak enough once the third reading is weak too.
        assertTrue(a.update(true, true, false, 0.0, range, 42_000))
    }

    @Test
    fun offOrAlreadyLockedDoesNothing() {
        assertEquals(0, AutoLock().run(120, rssi = -95, enabled = false))
        assertEquals(0, AutoLock().run(120, rssi = -95, locked = true))
        assertEquals(0, AutoLock().run(120, rssi = -95, locked = null))
    }

    @Test
    fun rearmsOnlyAfterLockedThenUnlocked() {
        val a = AutoLock()
        assertEquals(1, a.run(40, rssi = -90))
        // The lock is not confirmed yet: still reports unlocked, must not fire again.
        assertEquals(0, a.run(40, rssi = -90, start = 42_000))
        a.run(4, rssi = -90, start = 84_000, locked = true)
        assertEquals(1, a.run(40, rssi = -90, start = 90_000))
    }

    @Test
    fun disconnectClearsReadings() {
        val a = AutoLock()
        a.run(20, rssi = -90)
        assertFalse(a.update(true, false, false, 0.0, range, 22_000))
        a.onRssi(-90)
        // One reading after reconnecting is not enough, and the standstill timer restarted.
        assertFalse(a.update(true, true, false, 0.0, range, 60_000))
    }
}
