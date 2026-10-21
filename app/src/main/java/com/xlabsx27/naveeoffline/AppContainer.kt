package com.xlabsx27.naveeoffline

import android.content.Context
import android.location.Location
import com.xlabsx27.naveeoffline.ble.BleScanner
import com.xlabsx27.naveeoffline.ble.ClientIdStore
import com.xlabsx27.naveeoffline.ble.LinkState
import com.xlabsx27.naveeoffline.ble.ScooterConnection
import com.xlabsx27.naveeoffline.data.AppDatabase
import com.xlabsx27.naveeoffline.data.AppSettings
import com.xlabsx27.naveeoffline.data.ParkedPosition
import com.xlabsx27.naveeoffline.data.SettingsStore
import com.xlabsx27.naveeoffline.data.Units
import com.xlabsx27.naveeoffline.protocol.DisplayUnit
import com.xlabsx27.naveeoffline.location.GpsConfig
import com.xlabsx27.naveeoffline.location.GpsSource
import com.xlabsx27.naveeoffline.map.MapFileStore
import com.xlabsx27.naveeoffline.pairing.Pairing
import com.xlabsx27.naveeoffline.protocol.Setting
import com.xlabsx27.naveeoffline.service.ScooterService
import com.xlabsx27.naveeoffline.service.TripRecorder
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.distinctUntilChangedBy
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch

/** Hand-wired app-wide singletons. */
class AppContainer(private val context: Context) {
    val scope = CoroutineScope(SupervisorJob() + Dispatchers.Default)

    val settingsStore = SettingsStore(context)
    val settings: StateFlow<AppSettings> = settingsStore.settings.stateIn(scope, SharingStarted.Eagerly, AppSettings())

    val db = AppDatabase.open(context)
    val scanner = BleScanner(context)
    val clientId = ClientIdStore(context)
    val connection = ScooterConnection(context, clientId)
    val gps = GpsSource(context, scope, settings.map { GpsConfig(it.locationEnabled, it.gpsIntervalSec, it.networkLocation) })
    val recorder = TripRecorder(db, scope)
    val maps = MapFileStore(context)

    val parked: StateFlow<ParkedPosition?> = db.parked().observe().stateIn(scope, SharingStarted.Eagerly, null)

    init {
        scope.launch { settings.collect { connection.autoReconnect = it.autoReconnect } }
        // Bring the scooter's screen in line with the app's units once per connection.
        scope.launch {
            var syncedFor: String? = null
            combine(connection.status, connection.telemetry) { s, t -> s to t.displayUnit }.collect { (s, unit) ->
                if (s.state != LinkState.CONNECTED) {
                    syncedFor = null
                } else if (unit != null && syncedFor != s.address) {
                    syncedFor = s.address
                    val wanted = settings.value.units.displayUnit
                    if (unit != wanted) connection.write(Setting.DISPLAY_UNIT, wanted)
                }
            }
        }
        // Remember the scooter once it actually turned out to be one.
        scope.launch {
            connection.status.distinctUntilChangedBy { it.state }.collect { s ->
                if (s.state == LinkState.CONNECTED && s.address != null) settingsStore.rememberDevice(s.address, s.name)
            }
        }
    }

    fun connect(address: String, name: String?) {
        connection.connect(address, name)
        ScooterService.start(context)
    }

    fun disconnect() = connection.disconnect()

    /** This phone's pairing, for the pairing file. */
    fun currentPairing(): Pairing {
        val s = settings.value
        return Pairing(clientId.id, connection.status.value.address ?: s.lastAddress, connection.status.value.name ?: s.lastName)
    }

    /**
     * Takes over another phone's pairing: its client id, and its scooter as the remembered one. The
     * link is restarted so the next handshake uses the new id.
     */
    suspend fun importPairing(p: Pairing) {
        connection.disconnect()
        clientId.set(p.clientId)
        if (p.address != null) {
            settingsStore.rememberDevice(p.address, p.name)
            connect(p.address, p.name)
        }
    }

    /** One unit choice for both the app and the scooter's own screen. */
    fun setUnits(units: Units) {
        scope.launch { settingsStore.setUnits(units) }
        connection.write(Setting.DISPLAY_UNIT, units.displayUnit)
    }

    fun write(setting: Setting, value: Int) = connection.write(setting, value)

    fun startRecording() {
        recorder.start(connection.telemetry.value, connection.status.value.name, isManual = true)
        ScooterService.start(context)
    }

    fun stopRecording() = recorder.stop(connection.telemetry.value)

    /**
     * Saves [fix] (by default the phone's latest position) as the scooter's parking spot. Ignored if
     * the fix is older than [maxFixAgeMs].
     */
    fun markParked(
        reason: String,
        fix: Location? = gps.lastFix.value,
        time: Long = System.currentTimeMillis(),
        maxFixAgeMs: Long = 10 * 60_000L,
    ) {
        if (!settings.value.locationEnabled) return
        if (fix == null || System.currentTimeMillis() - fix.time > maxFixAgeMs) return
        val name = connection.status.value.name ?: settings.value.lastName
        val soc = connection.telemetry.value.soc
        scope.launch {
            db.parked().save(
                ParkedPosition(
                    time = time,
                    lat = fix.latitude,
                    lon = fix.longitude,
                    accuracyM = if (fix.hasAccuracy()) fix.accuracy else null,
                    scooterName = name,
                    soc = soc,
                    reason = reason,
                ),
            )
        }
    }
}

private val Units.displayUnit get() = if (this == Units.IMPERIAL) DisplayUnit.MPH else DisplayUnit.KMH

val Context.container: AppContainer get() = (applicationContext as NaveeApp).container
