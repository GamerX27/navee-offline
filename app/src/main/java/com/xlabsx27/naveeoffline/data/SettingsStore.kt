package com.xlabsx27.naveeoffline.data

import android.content.Context
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.core.booleanPreferencesKey
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.intPreferencesKey
import androidx.datastore.preferences.core.stringPreferencesKey
import androidx.datastore.preferences.preferencesDataStore
import com.xlabsx27.naveeoffline.service.AutoLockRange
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map

enum class Units { METRIC, IMPERIAL }
enum class ThemeMode { SYSTEM, LIGHT, DARK }

data class AppSettings(
    val units: Units = Units.METRIC,
    val theme: ThemeMode = ThemeMode.SYSTEM,
    val dynamicColor: Boolean = true,
    /** Master switch for everything that uses the phone's position. Off until the user turns it on. */
    val locationEnabled: Boolean = false,
    val gpsIntervalSec: Int = 2,
    /** Let the phone's Wi-Fi/cell position stand in when GPS has no fix (map and parking spot only). */
    val networkLocation: Boolean = false,
    val autoRecord: Boolean = true,
    val autoReconnect: Boolean = true,
    val idleStopMinutes: Int = 5,
    val parkOnLock: Boolean = false,
    val darkMap: Boolean = false,
    val showAllDevices: Boolean = false,
    val autoLock: Boolean = false,
    val autoLockRange: AutoLockRange = AutoLockRange.MEDIUM,
    val lastAddress: String? = null,
    val lastName: String? = null,
)

private val Context.dataStore by preferencesDataStore("settings")

class SettingsStore(private val context: Context) {
    private object K {
        val UNITS = stringPreferencesKey("units")
        val THEME = stringPreferencesKey("theme")
        val DYNAMIC = booleanPreferencesKey("dynamic_color")
        val LOCATION = booleanPreferencesKey("location_enabled")
        val GPS = intPreferencesKey("gps_interval")
        val NETWORK_LOCATION = booleanPreferencesKey("network_location")
        val AUTO_RECORD = booleanPreferencesKey("auto_record")
        val AUTO_RECONNECT = booleanPreferencesKey("auto_reconnect")
        val IDLE = intPreferencesKey("idle_stop_min")
        val PARK_ON_LOCK = booleanPreferencesKey("park_on_lock")
        val DARK_MAP = booleanPreferencesKey("dark_map")
        val SHOW_ALL = booleanPreferencesKey("show_all_devices")
        val AUTO_LOCK = booleanPreferencesKey("auto_lock")
        val AUTO_LOCK_RANGE = stringPreferencesKey("auto_lock_range")
        val LAST_ADDRESS = stringPreferencesKey("last_address")
        val LAST_NAME = stringPreferencesKey("last_name")
    }

    val settings: Flow<AppSettings> = context.dataStore.data.map { p ->
        AppSettings(
            units = p[K.UNITS]?.let { runCatching { Units.valueOf(it) }.getOrNull() } ?: Units.METRIC,
            theme = p[K.THEME]?.let { runCatching { ThemeMode.valueOf(it) }.getOrNull() } ?: ThemeMode.SYSTEM,
            dynamicColor = p[K.DYNAMIC] ?: true,
            locationEnabled = p[K.LOCATION] ?: false,
            gpsIntervalSec = p[K.GPS] ?: 2,
            networkLocation = p[K.NETWORK_LOCATION] ?: false,
            autoRecord = p[K.AUTO_RECORD] ?: true,
            autoReconnect = p[K.AUTO_RECONNECT] ?: true,
            idleStopMinutes = p[K.IDLE] ?: 5,
            parkOnLock = p[K.PARK_ON_LOCK] ?: false,
            darkMap = p[K.DARK_MAP] ?: false,
            showAllDevices = p[K.SHOW_ALL] ?: false,
            autoLock = p[K.AUTO_LOCK] ?: false,
            autoLockRange = p[K.AUTO_LOCK_RANGE]?.let { runCatching { AutoLockRange.valueOf(it) }.getOrNull() }
                ?: AutoLockRange.MEDIUM,
            lastAddress = p[K.LAST_ADDRESS],
            lastName = p[K.LAST_NAME],
        )
    }

    private suspend fun <T> set(key: Preferences.Key<T>, value: T) {
        context.dataStore.edit { it[key] = value }
    }

    suspend fun setUnits(v: Units) = set(K.UNITS, v.name)
    suspend fun setTheme(v: ThemeMode) = set(K.THEME, v.name)
    suspend fun setDynamicColor(v: Boolean) = set(K.DYNAMIC, v)
    suspend fun setLocationEnabled(v: Boolean) = set(K.LOCATION, v)
    suspend fun setGpsInterval(v: Int) = set(K.GPS, v)
    suspend fun setNetworkLocation(v: Boolean) = set(K.NETWORK_LOCATION, v)
    suspend fun setAutoRecord(v: Boolean) = set(K.AUTO_RECORD, v)
    suspend fun setAutoReconnect(v: Boolean) = set(K.AUTO_RECONNECT, v)
    suspend fun setIdleStopMinutes(v: Int) = set(K.IDLE, v)
    suspend fun setParkOnLock(v: Boolean) = set(K.PARK_ON_LOCK, v)
    suspend fun setDarkMap(v: Boolean) = set(K.DARK_MAP, v)
    suspend fun setShowAllDevices(v: Boolean) = set(K.SHOW_ALL, v)
    suspend fun setAutoLock(v: Boolean) = set(K.AUTO_LOCK, v)
    suspend fun setAutoLockRange(v: AutoLockRange) = set(K.AUTO_LOCK_RANGE, v.name)

    suspend fun rememberDevice(address: String, name: String?) = context.dataStore.edit {
        it[K.LAST_ADDRESS] = address
        if (name != null) it[K.LAST_NAME] = name
    }

    suspend fun forgetDevice() = context.dataStore.edit {
        it.remove(K.LAST_ADDRESS)
        it.remove(K.LAST_NAME)
    }
}
