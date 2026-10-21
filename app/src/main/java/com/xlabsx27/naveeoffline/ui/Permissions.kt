package com.xlabsx27.naveeoffline.ui

import android.Manifest
import android.content.Context
import android.content.pm.PackageManager
import android.os.Build

/**
 * Runtime permissions, in three groups. Only [bluetooth] and [notifications] are asked for on first
 * start; [location] is asked for when the user turns on "Location features".
 */
object Permissions {
    /** Find and talk to the scooter. Android 10/11 only allow BLE scans with location permission. */
    val bluetooth: List<String> = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
        listOf(Manifest.permission.BLUETOOTH_SCAN, Manifest.permission.BLUETOOTH_CONNECT)
    } else {
        listOf(Manifest.permission.ACCESS_FINE_LOCATION)
    }

    /** The status notification and its Lock/Unlock actions. Optional. */
    val notifications: List<String> = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
        listOf(Manifest.permission.POST_NOTIFICATIONS)
    } else {
        emptyList()
    }

    /** Trip tracks, the map position and the parking spot. Only when "Location features" is on. */
    val location: List<String> = listOf(Manifest.permission.ACCESS_FINE_LOCATION, Manifest.permission.ACCESS_COARSE_LOCATION)

    /** What the first start asks for. */
    val startup: Array<String> = (bluetooth + notifications).toTypedArray()

    fun missing(context: Context, group: List<String>): List<String> =
        group.filter { context.checkSelfPermission(it) != PackageManager.PERMISSION_GRANTED }

    fun bluetoothGranted(context: Context): Boolean = missing(context, bluetooth).isEmpty()

    fun locationGranted(context: Context): Boolean =
        context.checkSelfPermission(Manifest.permission.ACCESS_FINE_LOCATION) == PackageManager.PERMISSION_GRANTED
}
