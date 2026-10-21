package com.xlabsx27.naveeoffline.ui.components

import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableLongStateOf
import androidx.compose.runtime.mutableStateMapOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import com.xlabsx27.naveeoffline.AppContainer
import com.xlabsx27.naveeoffline.protocol.Setting
import kotlinx.coroutines.delay

/** How long a just-sent value is shown before the scooter's own report takes over again. */
private const val PENDING_MS = 2_500L

/**
 * Scooter settings with optimistic values, so a tap responds at once; the 0x70 re-read confirms or
 * reverts them.
 */
class OptimisticSettings internal constructor(
    private val c: AppContainer,
    private val pending: MutableMap<Setting, Pair<Int, Long>>,
    private val now: () -> Long,
    private val touch: () -> Unit,
) {
    fun value(s: Setting, reported: Int?): Int? = pending[s]?.takeIf { now() - it.second <= PENDING_MS }?.first ?: reported
    fun on(s: Setting, reported: Int?) = (value(s, reported) ?: 0) != 0
    fun send(s: Setting, v: Int) {
        touch()
        pending[s] = v to now()
        c.write(s, v)
    }
}

@Composable
fun rememberOptimisticSettings(c: AppContainer): OptimisticSettings {
    val pending = remember { mutableStateMapOf<Setting, Pair<Int, Long>>() }
    var now by remember { mutableLongStateOf(System.currentTimeMillis()) }
    LaunchedEffect(pending.size) {
        while (pending.isNotEmpty()) {
            delay(500)
            now = System.currentTimeMillis()
            pending.filterValues { now - it.second > PENDING_MS }.keys.forEach(pending::remove)
        }
    }
    return OptimisticSettings(c, pending, { now }, { now = System.currentTimeMillis() })
}
