package com.xlabsx27.naveeoffline.service

import android.location.Location
import com.xlabsx27.naveeoffline.data.AppDatabase
import com.xlabsx27.naveeoffline.data.TrackPoint
import com.xlabsx27.naveeoffline.data.Trip
import com.xlabsx27.naveeoffline.location.isGps
import com.xlabsx27.naveeoffline.protocol.Telemetry
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import kotlin.math.abs
import kotlin.math.max

data class LatLon(val lat: Double, val lon: Double)

/**
 * Turns GPS fixes and scooter telemetry into trips. Every mutation runs on one serial dispatcher, so
 * the bookkeeping below needs no locks.
 */
class TripRecorder(private val db: AppDatabase, private val scope: CoroutineScope) {
    private val serial = Dispatchers.IO.limitedParallelism(1)
    private val dao get() = db.trips()

    private val _active = MutableStateFlow<Trip?>(null)
    /** The trip being recorded, updated as it grows. */
    val active: StateFlow<Trip?> = _active.asStateFlow()

    private val _livePath = MutableStateFlow<List<LatLon>>(emptyList())
    val livePath: StateFlow<List<LatLon>> = _livePath.asStateFlow()

    private var manual = false
    private var lastPoint: Location? = null
    private var lastMovementAt = 0L
    private var lastTelemetryAt = 0L
    private var disconnectedSince: Long? = null
    /** The scooter reported unlocked during this trip, so a later lock means the ride is over. */
    private var seenUnlocked = false

    init {
        // A process death mid-ride leaves a trip open; close it at its last point.
        scope.launch(serial) {
            for (t in dao.unfinished()) {
                val end = dao.lastPoint(t.id)?.time
                if (end == null || t.distanceM < MIN_TRIP_M) dao.delete(t.id) else dao.update(t.copy(endedAt = end))
            }
        }
    }

    fun start(telemetry: Telemetry, scooterName: String?, isManual: Boolean) = scope.launch(serial) {
        if (_active.value != null) return@launch
        val now = System.currentTimeMillis()
        val trip = Trip(
            startedAt = now,
            socStart = telemetry.soc,
            odometerStartKm = telemetry.odometerKm,
            scooterName = scooterName,
        )
        manual = isManual
        lastPoint = null
        lastMovementAt = now
        lastTelemetryAt = 0L
        disconnectedSince = null
        seenUnlocked = telemetry.locked == false
        _livePath.value = emptyList()
        _active.value = trip.copy(id = dao.insert(trip))
    }

    fun stop(telemetry: Telemetry? = null) = scope.launch(serial) { finish(telemetry) }

    /** Starts an automatic trip once the scooter (or the phone) is clearly moving. */
    fun maybeAutoStart(telemetry: Telemetry, gpsSpeedMs: Float?, scooterName: String?) {
        if (_active.value != null) return
        val moving = (telemetry.speedKmh ?: 0.0) >= AUTO_START_KMH || (gpsSpeedMs ?: 0f) * 3.6f >= AUTO_START_KMH
        if (moving) start(telemetry, scooterName, isManual = false)
    }

    fun onFix(fix: Location, telemetry: Telemetry) = scope.launch(serial) {
        val trip = _active.value ?: return@launch
        // Tracks are satellite-only and live: no Wi-Fi/cell positions, no cached fixes.
        if (!fix.isGps || System.currentTimeMillis() - fix.time > MAX_FIX_AGE_MS) return@launch
        if (fix.hasAccuracy() && fix.accuracy > MAX_ACCURACY_M) return@launch

        val scooterKmh = telemetry.speedKmh
        val gpsKmh = if (fix.hasSpeed()) fix.speed * 3.6 else null
        val speedKmh = scooterKmh ?: gpsKmh ?: 0.0
        if (speedKmh >= MOVING_KMH) lastMovementAt = fix.time

        val prev = lastPoint
        var distance = trip.distanceM
        var moving = trip.movingMs
        if (prev != null) {
            val d = prev.distanceTo(fix).toDouble()
            // Ignore jitter while standing still.
            if (d < max(MIN_STEP_M, (fix.accuracy + prev.accuracy) / 4.0)) return@launch
            val dt = fix.time - prev.time
            if (dt in 1..MAX_GAP_MS) {
                distance += d
                if (d / (dt / 1000.0) * 3.6 >= MOVING_KMH) moving += dt
            }
        }
        lastPoint = fix

        val updated = trip.copy(
            distanceM = distance,
            movingMs = moving,
            maxSpeedKmh = max(trip.maxSpeedKmh, scooterKmh ?: gpsKmh ?: 0.0),
            socEnd = telemetry.soc ?: trip.socEnd,
            odometerEndKm = telemetry.odometerKm ?: trip.odometerEndKm,
        )
        dao.insertPoint(
            TrackPoint(
                tripId = trip.id,
                time = fix.time,
                lat = fix.latitude,
                lon = fix.longitude,
                altitude = if (fix.hasAltitude()) fix.altitude else null,
                accuracyM = if (fix.hasAccuracy()) fix.accuracy else null,
                gpsSpeedKmh = gpsKmh?.toFloat(),
                scooterSpeedKmh = scooterKmh?.toFloat(),
                soc = telemetry.soc,
                voltage = telemetry.voltage?.toFloat(),
                currentA = telemetry.current?.toFloat(),
            ),
        )
        dao.update(updated)
        _active.value = updated
        _livePath.value = _livePath.value + LatLon(fix.latitude, fix.longitude)
    }

    /** Integrates pack power over time for the trip's energy use. */
    fun onTelemetry(t: Telemetry) = scope.launch(serial) {
        val trip = _active.value ?: return@launch
        val power = t.powerW ?: return@launch
        val now = t.updatedAt
        val dt = now - lastTelemetryAt
        lastTelemetryAt = now
        if ((t.speedKmh ?: 0.0) >= MOVING_KMH) lastMovementAt = now
        if (dt in 1..MAX_ENERGY_GAP_MS) {
            _active.value = trip.copy(energyWh = trip.energyWh + abs(power) * dt / 3_600_000.0)
        }
    }

    /**
     * Periodic check for the automatic stop conditions. Locking the scooter or losing it for a few
     * minutes ends any trip; standing still only ends automatic ones (a manual trip may wait at a
     * red light for as long as it likes).
     */
    fun tick(linkConnected: Boolean, idleStopMinutes: Int, telemetry: Telemetry) = scope.launch(serial) {
        if (_active.value == null) return@launch
        val now = System.currentTimeMillis()
        disconnectedSince = if (linkConnected) null else disconnectedSince ?: now
        val lostLink = disconnectedSince?.let { now - it > LINK_LOSS_STOP_MS } == true
        if (linkConnected && telemetry.locked == false) seenUnlocked = true
        val locked = seenUnlocked && linkConnected && telemetry.locked == true
        val idle = !manual && now - lastMovementAt > idleStopMinutes * 60_000L
        if (lostLink || locked || idle) finish(telemetry)
    }

    private suspend fun finish(telemetry: Telemetry?) {
        val trip = _active.value ?: return
        val end = lastPoint?.time ?: System.currentTimeMillis()
        val odoEnd = telemetry?.odometerKm ?: trip.odometerEndKm
        // Without a GPS fix the scooter's odometer is the only distance we have.
        val odoM = if (trip.odometerStartKm != null && odoEnd != null) (odoEnd - trip.odometerStartKm) * 1000 else 0.0
        val distance = if (trip.distanceM < MIN_TRIP_M && odoM > trip.distanceM) odoM else trip.distanceM
        if (distance < MIN_TRIP_M) {
            dao.delete(trip.id)
        } else {
            dao.update(
                trip.copy(
                    endedAt = end,
                    distanceM = distance,
                    socEnd = telemetry?.soc ?: trip.socEnd,
                    odometerEndKm = odoEnd,
                ),
            )
        }
        _active.value = null
        _livePath.value = emptyList()
        lastPoint = null
    }

    private companion object {
        const val AUTO_START_KMH = 5.0
        const val MOVING_KMH = 3.0
        const val MAX_ACCURACY_M = 30f
        const val MAX_FIX_AGE_MS = 30_000L
        const val MIN_STEP_M = 3.0
        const val MAX_GAP_MS = 60_000L
        const val MAX_ENERGY_GAP_MS = 10_000L
        const val LINK_LOSS_STOP_MS = 3 * 60_000L
        const val MIN_TRIP_M = 50.0
    }
}
