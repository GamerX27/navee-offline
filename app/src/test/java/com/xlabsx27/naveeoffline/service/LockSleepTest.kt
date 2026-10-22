package com.xlabsx27.naveeoffline.service

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class LockSleepTest {
    private val after = 10 * 60_000L

    private fun LockSleep.at(now: Long, locked: Boolean? = true, connected: Boolean = true, enabled: Boolean = true) =
        update(enabled, connected, locked, after, now)

    @Test
    fun firesOnceAfterTheDelay() {
        val s = LockSleep()
        assertFalse(s.at(0))
        assertFalse(s.at(after - 1))
        assertTrue(s.at(after))
        assertEquals(0L, s.lockedSince)
        assertFalse(s.at(after + 10_000))
    }

    @Test
    fun unlockRestartsTheWait() {
        val s = LockSleep()
        s.at(0)
        s.at(5 * 60_000L, locked = false)
        assertNull(s.lockedSince)
        assertFalse(s.at(6 * 60_000L))
        assertFalse(s.at(after + 1))
        assertTrue(s.at(6 * 60_000L + after))
    }

    @Test
    fun disconnectRestartsTheWait() {
        val s = LockSleep()
        s.at(0)
        s.at(5 * 60_000L, connected = false)
        assertFalse(s.at(after))
    }

    @Test
    fun unknownLockStateKeepsTheWaitButNeverFires() {
        val s = LockSleep()
        s.at(0)
        assertFalse(s.at(after, locked = null))
        assertTrue(s.at(after + 1))
    }

    @Test
    fun neverFiresWhenDisabled() {
        val s = LockSleep()
        assertFalse(s.at(0, enabled = false))
        assertFalse(s.at(after * 2, enabled = false))
    }

    @Test
    fun firesAgainAfterUnlockAndRelock() {
        val s = LockSleep()
        s.at(0)
        assertTrue(s.at(after))
        s.at(after + 1, locked = false)
        s.at(after + 2)
        assertTrue(s.at(2 * after + 2))
    }
}
