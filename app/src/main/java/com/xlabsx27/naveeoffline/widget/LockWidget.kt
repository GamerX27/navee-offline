package com.xlabsx27.naveeoffline.widget

import android.app.PendingIntent
import android.appwidget.AppWidgetManager
import android.appwidget.AppWidgetProvider
import android.content.ComponentName
import android.content.Context
import android.content.Intent
import android.widget.RemoteViews
import com.xlabsx27.naveeoffline.AppContainer
import com.xlabsx27.naveeoffline.R
import com.xlabsx27.naveeoffline.ble.LinkState
import com.xlabsx27.naveeoffline.container
import com.xlabsx27.naveeoffline.service.ScooterService
import com.xlabsx27.naveeoffline.ui.Format
import com.xlabsx27.naveeoffline.ui.MainActivity
import com.xlabsx27.naveeoffline.ui.Permissions

/**
 * Home-screen widget with one button: lock or unlock the scooter. A tap goes to [ScooterService],
 * which connects to the remembered scooter first if needed. Redrawn by [AppContainer] on every
 * link or lock change, so it needs no periodic updates.
 */
class LockWidget : AppWidgetProvider() {

    override fun onUpdate(context: Context, manager: AppWidgetManager, ids: IntArray) = render(context, context.container)

    companion object {
        fun render(context: Context, c: AppContainer) {
            val manager = AppWidgetManager.getInstance(context)
            val component = ComponentName(context, LockWidget::class.java)
            if (manager.getAppWidgetIds(component).isEmpty()) return

            val s = c.connection.status.value
            // Disconnected by lock sleep: the scooter was locked when the app let go of it.
            val sleepingSince = c.settings.value.sleepingSince.takeIf { !s.isWanted }
            val locked = c.connection.telemetry.value.locked.takeIf { s.state == LinkState.CONNECTED }
                ?: true.takeIf { sleepingSince != null }
            val views = RemoteViews(context.packageName, if (locked == true) R.layout.widget_lock_locked else R.layout.widget_lock)
            views.setImageViewResource(
                R.id.widget_icon,
                when (locked) {
                    true -> R.drawable.ic_widget_lock
                    false -> R.drawable.ic_widget_lock_open
                    null -> R.drawable.ic_stat_scooter
                },
            )
            views.setTextViewText(
                R.id.widget_label,
                when {
                    sleepingSince != null -> context.getString(R.string.locked_since, Format.time(sleepingSince))
                    locked == true -> context.getString(R.string.locked)
                    locked == false -> context.getString(R.string.unlocked)
                    s.isWanted -> context.getString(R.string.connecting)
                    else -> context.getString(R.string.widget_tap_connect)
                },
            )

            // The service can only go foreground with Bluetooth permission and needs a scooter to reach.
            val canToggle = Permissions.bluetoothGranted(context) &&
                (s.state == LinkState.CONNECTED || (s.address ?: c.settings.value.lastAddress) != null)
            val tap = if (canToggle) {
                PendingIntent.getForegroundService(
                    context, 0,
                    Intent(context, ScooterService::class.java).setAction(ScooterService.ACTION_TOGGLE_LOCK),
                    PendingIntent.FLAG_IMMUTABLE,
                )
            } else {
                PendingIntent.getActivity(context, 0, Intent(context, MainActivity::class.java), PendingIntent.FLAG_IMMUTABLE)
            }
            views.setOnClickPendingIntent(R.id.widget_root, tap)
            manager.updateAppWidget(component, views)
        }
    }
}
