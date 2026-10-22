package com.xlabsx27.naveeoffline.protocol

import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class ModelsTest {
    @Test
    fun e45ProHidesWhatItLacks() {
        for (pid in listOf("2611", "2753")) {
            for (f in Feature.entries) assertFalse("$pid $f", NaveeModels.supports(pid, f))
        }
    }

    @Test
    fun unknownModelsGetEverything() {
        for (f in Feature.entries) {
            assertTrue(NaveeModels.supports(null, f))
            assertTrue(NaveeModels.supports("9999", f))
        }
    }

    @Test
    fun olderListsStillApply() {
        // V50i Pro: no cruise, no kick-start, everything else.
        assertFalse(NaveeModels.supports("2329", Feature.CRUISE))
        assertFalse(NaveeModels.supports("2329", Feature.ZERO_START))
        assertTrue(NaveeModels.supports("2329", Feature.TCS))
        // S40: kick-start only.
        assertTrue(NaveeModels.supports("2322", Feature.CRUISE))
        assertFalse(NaveeModels.supports("2322", Feature.ZERO_START))
    }
}
