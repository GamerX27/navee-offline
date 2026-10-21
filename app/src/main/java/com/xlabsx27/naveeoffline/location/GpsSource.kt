package com.xlabsx27.naveeoffline.location

import android.Manifest
import android.annotation.SuppressLint
import android.content.Context
import android.content.pm.PackageManager
import android.location.Location
import android.location.LocationListener
import android.location.LocationManager
import android.os.Looper
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.channels.awaitClose
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharedFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.callbackFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.emptyFlow
import kotlinx.coroutines.flow.flatMapLatest
import kotlinx.coroutines.flow.onEach
import kotlinx.coroutines.flow.shareIn

/**
 * Whether location is allowed at all, how often to ask for a position, and whether Wi-Fi/cell
 * positioning may fill in without a GPS fix.
 */
data class GpsConfig(val enabled: Boolean, val intervalSec: Int, val useNetwork: Boolean)

/**
 * Positions from the platform LocationManager (no Google Play Services). Satellite GPS is preferred;
 * if [GpsConfig.useNetwork] is on, the phone's Wi-Fi/cell position fills in while GPS has no fix
 * (indoors). Updates only run while someone collects [fixes] and [GpsConfig.enabled] is on; with it
 * off this class never touches the LocationManager.
 */
class GpsSource(private val context: Context, scope: CoroutineScope, config: Flow<GpsConfig>) {
    private val manager = context.getSystemService(LocationManager::class.java)

    private val _lastFix = MutableStateFlow<Location?>(null)
    val lastFix: StateFlow<Location?> = _lastFix.asStateFlow()

    private val permissionTick = MutableStateFlow(0)

    @OptIn(ExperimentalCoroutinesApi::class)
    val fixes: SharedFlow<Location> = combine(config.distinctUntilChanged(), permissionTick) { c, _ -> c }
        .flatMapLatest {
            if (it.enabled) {
                updates(it.intervalSec * 1000L, it.useNetwork)
            } else {
                _lastFix.value = null
                emptyFlow()
            }
        }
        .onEach { _lastFix.value = it }
        .shareIn(scope, SharingStarted.WhileSubscribed(5_000), replay = 1)

    /** Call after a location permission prompt so updates start without waiting for a setting change. */
    fun onPermissionChanged() = permissionTick.update { it + 1 }

    fun hasPermission(): Boolean =
        context.checkSelfPermission(Manifest.permission.ACCESS_FINE_LOCATION) == PackageManager.PERMISSION_GRANTED

    val isGpsEnabled: Boolean get() = manager?.isProviderEnabled(LocationManager.GPS_PROVIDER) == true

    @SuppressLint("MissingPermission")
    private fun updates(intervalMs: Long, useNetwork: Boolean): Flow<Location> = callbackFlow {
        val lm = manager
        if (lm == null || !hasPermission()) {
            close()
            return@callbackFlow
        }
        val providers = buildList {
            add(LocationManager.GPS_PROVIDER)
            if (useNetwork && LocationManager.NETWORK_PROVIDER in lm.allProviders) add(LocationManager.NETWORK_PROVIDER)
        }
        // Start from the newest cached position, however old, so the map is not empty. Consumers
        // that need a fresh fix check Location.time themselves.
        providers.mapNotNull { lm.getLastKnownLocation(it) }.maxByOrNull { it.time }?.let { trySend(it) }

        var lastGpsAt = 0L
        val listener = LocationListener { loc ->
            if (loc.isGps) {
                lastGpsAt = loc.time
                trySend(loc)
            } else if (loc.time - lastGpsAt > GPS_PREFERRED_MS) {
                trySend(loc) // only while GPS has nothing recent
            }
        }
        providers.forEach { lm.requestLocationUpdates(it, intervalMs, 0f, listener, Looper.getMainLooper()) }
        awaitClose { lm.removeUpdates(listener) }
    }

    private companion object {
        const val GPS_PREFERRED_MS = 15_000L
    }
}

/** True for a satellite fix (as opposed to Wi-Fi/cell positioning). */
val Location.isGps: Boolean get() = provider == LocationManager.GPS_PROVIDER
