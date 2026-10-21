package com.xlabsx27.naveeoffline.service

import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import android.content.pm.ServiceInfo
import android.util.Log
import androidx.core.app.NotificationCompat
import androidx.core.app.ServiceCompat
import androidx.lifecycle.LifecycleService
import androidx.lifecycle.lifecycleScope
import com.xlabsx27.naveeoffline.R
import com.xlabsx27.naveeoffline.ble.LinkState
import com.xlabsx27.naveeoffline.ble.LinkStatus
import com.xlabsx27.naveeoffline.container
import com.xlabsx27.naveeoffline.data.Trip
import com.xlabsx27.naveeoffline.protocol.Setting
import com.xlabsx27.naveeoffline.protocol.Telemetry
import com.xlabsx27.naveeoffline.ui.MainActivity
import com.xlabsx27.naveeoffline.ui.Format
import com.xlabsx27.naveeoffline.ui.Permissions
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.conflate
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.drop
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.launch

/**
 * Keeps the scooter link, GPS and trip recording alive with the screen off. Runs while a scooter is
 * wanted or a trip is being recorded, and stops itself when neither is true.
 */
class ScooterService : LifecycleService() {

    private val c by lazy { container }
    private var lastNotification: Notification? = null
    private val autoLock = AutoLock()

    override fun onCreate() {
        super.onCreate()
        createChannel()

        val conn = c.connection
        val parking = ParkingTracker(c)
        // GpsSource emits nothing while location features are off, so no GPS is registered then.
        lifecycleScope.launch {
            c.gps.fixes.collect { fix ->
                parking.onFix(fix)
                val t = conn.telemetry.value
                if (conn.status.value.state == LinkState.CONNECTED && c.settings.value.autoRecord) {
                    c.recorder.maybeAutoStart(t, if (fix.hasSpeed()) fix.speed else null, conn.status.value.name)
                }
                c.recorder.onFix(fix, t)
            }
        }
        lifecycleScope.launch {
            conn.telemetry.collect { t ->
                c.recorder.onTelemetry(t)
                if (conn.status.value.state == LinkState.CONNECTED && c.settings.value.autoRecord) {
                    c.recorder.maybeAutoStart(t, null, conn.status.value.name)
                }
                parking.onTelemetry(t)
            }
        }
        lifecycleScope.launch {
            conn.status.collect { s ->
                parking.onLink(s.isConnected)
                if (s.state != LinkState.CONNECTED) autoLock.onDisconnected()
            }
        }
        lifecycleScope.launch {
            conn.rssiReadings.collect { rssi ->
                autoLock.onRssi(rssi)
                val s = c.settings.value
                val t = conn.telemetry.value
                val lock = autoLock.update(
                    enabled = s.autoLock,
                    connected = conn.status.value.state == LinkState.CONNECTED,
                    locked = t.locked,
                    speedKmh = t.speedKmh,
                    threshold = s.autoLockRange,
                    now = System.currentTimeMillis(),
                )
                if (lock) {
                    Log.i(TAG, "auto-lock: signal weak at standstill")
                    c.write(Setting.LOCK, 1)
                    notifyAutoLocked()
                }
            }
        }
        lifecycleScope.launch {
            while (true) {
                delay(10_000)
                c.recorder.tick(conn.status.value.isConnected, c.settings.value.idleStopMinutes, conn.telemetry.value)
            }
        }
        lifecycleScope.launch {
            combine(conn.status, c.recorder.active) { s, trip -> s.isWanted || trip != null }
                .distinctUntilChanged()
                .collect { needed ->
                    if (!needed) {
                        delay(1_500) // let the parked-position write and the last notification settle
                        if (!conn.status.value.isWanted && c.recorder.active.value == null) stopSelf()
                    }
                }
        }
        // Turning location features on or off changes the foreground service type.
        lifecycleScope.launch {
            c.settings.map { it.locationEnabled }.distinctUntilChanged().drop(1).collect {
                lastNotification?.let(::goForeground)
            }
        }
        lifecycleScope.launch {
            combine(conn.status, conn.telemetry.map { it.soc to it.locked }.distinctUntilChanged(), c.recorder.active) { s, _, trip ->
                buildNotification(s, conn.telemetry.value, trip)
            }.conflate().collect { n ->
                lastNotification = n
                getSystemService(NotificationManager::class.java).notify(NOTIFICATION_ID, n)
                delay(2_000)
            }
        }
    }

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        super.onStartCommand(intent, flags, startId)
        // Every startForegroundService() must be answered with startForeground(), even when running.
        goForeground(lastNotification ?: buildNotification(c.connection.status.value, c.connection.telemetry.value, c.recorder.active.value))
        when (intent?.action) {
            ACTION_LOCK -> c.write(Setting.LOCK, 1)
            ACTION_UNLOCK -> {
                c.write(Setting.LOCK, 0)
                getSystemService(NotificationManager::class.java).cancel(AUTO_LOCK_NOTIFICATION_ID)
            }
            ACTION_DISCONNECT -> c.disconnect()
            ACTION_STOP_RECORDING -> c.stopRecording()
        }
        return START_NOT_STICKY
    }

    private fun goForeground(n: Notification) {
        var types = 0
        if (Permissions.bluetoothGranted(this)) types = types or ServiceInfo.FOREGROUND_SERVICE_TYPE_CONNECTED_DEVICE
        // Location type only while location features are on: otherwise the app holds no location claim at all.
        if (c.settings.value.locationEnabled && Permissions.locationGranted(this)) {
            types = types or ServiceInfo.FOREGROUND_SERVICE_TYPE_LOCATION
        }
        if (types == 0) {
            stopSelf()
            return
        }
        ServiceCompat.startForeground(this, NOTIFICATION_ID, n, types)
    }

    private fun createChannel() {
        val nm = getSystemService(NotificationManager::class.java)
        val ch = NotificationChannel(CHANNEL, getString(R.string.channel_link), NotificationManager.IMPORTANCE_LOW)
        ch.setShowBadge(false)
        nm.createNotificationChannel(ch)
        nm.createNotificationChannel(
            NotificationChannel(ALERT_CHANNEL, getString(R.string.channel_alerts), NotificationManager.IMPORTANCE_DEFAULT),
        )
    }

    private fun notifyAutoLocked() {
        val n = NotificationCompat.Builder(this, ALERT_CHANNEL)
            .setSmallIcon(R.drawable.ic_stat_scooter)
            .setContentTitle(getString(R.string.notif_auto_locked))
            .setContentText(getString(R.string.notif_auto_locked_text))
            .setContentIntent(openApp())
            .setAutoCancel(true)
            .addAction(0, getString(R.string.unlock), action(ACTION_UNLOCK))
            .build()
        getSystemService(NotificationManager::class.java).notify(AUTO_LOCK_NOTIFICATION_ID, n)
    }

    private fun openApp() = PendingIntent.getActivity(
        this, 0, Intent(this, MainActivity::class.java), PendingIntent.FLAG_IMMUTABLE,
    )

    private fun buildNotification(s: LinkStatus, t: Telemetry, trip: Trip?): Notification {
        val units = c.settings.value.units
        val title = when (s.state) {
            LinkState.CONNECTED -> getString(R.string.notif_connected, s.name ?: getString(R.string.scooter))
            LinkState.CONNECTING, LinkState.AUTHENTICATING -> getString(R.string.connecting)
            LinkState.RECONNECTING -> getString(R.string.notif_out_of_range)
            else -> if (trip != null) getString(R.string.notif_recording) else getString(R.string.app_name)
        }
        val text = buildList {
            t.soc?.let { add("$it %") }
            t.locked?.let { add(getString(if (it) R.string.locked else R.string.unlocked)) }
            trip?.let { add(getString(R.string.notif_trip, Format.distance(it.distanceM, units))) }
        }.joinToString(" · ")

        val open = openApp()
        val b = NotificationCompat.Builder(this, CHANNEL)
            .setSmallIcon(R.drawable.ic_stat_scooter)
            .setContentTitle(title)
            .setContentText(text)
            .setContentIntent(open)
            .setOngoing(true)
            .setOnlyAlertOnce(true)
            .setForegroundServiceBehavior(NotificationCompat.FOREGROUND_SERVICE_IMMEDIATE)
        if (s.state == LinkState.CONNECTED) {
            if (t.locked == true) b.addAction(0, getString(R.string.unlock), action(ACTION_UNLOCK))
            else b.addAction(0, getString(R.string.lock), action(ACTION_LOCK))
        }
        if (trip != null) b.addAction(0, getString(R.string.stop_trip), action(ACTION_STOP_RECORDING))
        if (s.isWanted) b.addAction(0, getString(R.string.disconnect), action(ACTION_DISCONNECT))
        return b.build()
    }

    private fun action(a: String) = PendingIntent.getService(
        this, a.hashCode(), Intent(this, ScooterService::class.java).setAction(a), PendingIntent.FLAG_IMMUTABLE,
    )

    companion object {
        private const val TAG = "ScooterService"
        private const val CHANNEL = "link"
        private const val ALERT_CHANNEL = "alerts"
        private const val NOTIFICATION_ID = 1
        private const val AUTO_LOCK_NOTIFICATION_ID = 2
        private const val ACTION_LOCK = "lock"
        private const val ACTION_UNLOCK = "unlock"
        private const val ACTION_DISCONNECT = "disconnect"
        private const val ACTION_STOP_RECORDING = "stop_recording"

        /** No-op without Bluetooth permission: the service could not go foreground then. */
        fun start(context: Context) {
            if (!Permissions.bluetoothGranted(context)) return
            context.startForegroundService(Intent(context, ScooterService::class.java))
        }
    }
}
