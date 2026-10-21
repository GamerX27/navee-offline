package com.xlabsx27.naveeoffline.ble

import android.annotation.SuppressLint
import android.bluetooth.BluetoothManager
import android.bluetooth.le.ScanCallback
import android.bluetooth.le.ScanResult
import android.bluetooth.le.ScanSettings
import android.content.Context
import kotlinx.coroutines.channels.awaitClose
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.callbackFlow

data class ScanHit(val address: String, val name: String?, val rssi: Int, val seenAt: Long) {
    /** NAVEE scooters do not advertise their service UUID, so the name is the only filter. */
    val isNavee: Boolean get() = name?.startsWith("NAVEE", ignoreCase = true) == true
}

class BleScanner(context: Context) {
    private val manager = context.getSystemService(BluetoothManager::class.java)

    val isEnabled: Boolean get() = manager?.adapter?.isEnabled == true

    /** Emits every advertisement while collected. Callers need BLUETOOTH_SCAN. */
    @SuppressLint("MissingPermission")
    fun scan(): Flow<ScanHit> = callbackFlow {
        val scanner = manager?.adapter?.bluetoothLeScanner
        if (scanner == null) {
            close(IllegalStateException("Bluetooth is off"))
            return@callbackFlow
        }
        val callback = object : ScanCallback() {
            override fun onScanResult(callbackType: Int, result: ScanResult) {
                trySend(
                    ScanHit(
                        address = result.device.address,
                        name = result.scanRecord?.deviceName ?: runCatching { result.device.name }.getOrNull(),
                        rssi = result.rssi,
                        seenAt = System.currentTimeMillis(),
                    ),
                )
            }

            override fun onScanFailed(errorCode: Int) {
                close(IllegalStateException("Scan failed ($errorCode)"))
            }
        }
        val settings = ScanSettings.Builder().setScanMode(ScanSettings.SCAN_MODE_LOW_LATENCY).build()
        scanner.startScan(null, settings, callback)
        awaitClose { runCatching { scanner.stopScan(callback) } }
    }
}
