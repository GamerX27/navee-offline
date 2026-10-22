package com.xlabsx27.naveeoffline.ui.screens

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.BluetoothSearching
import androidx.compose.material.icons.filled.BatteryChargingFull
import androidx.compose.material.icons.filled.Bluetooth
import androidx.compose.material.icons.filled.BluetoothDisabled
import androidx.compose.material.icons.filled.Eco
import androidx.compose.material.icons.filled.FiberManualRecord
import androidx.compose.material.icons.filled.FlashlightOn
import androidx.compose.material.icons.filled.Info
import androidx.compose.material.icons.filled.Lock
import androidx.compose.material.icons.filled.LockOpen
import androidx.compose.material.icons.filled.Speed
import androidx.compose.material.icons.filled.Stop
import androidx.compose.material.icons.filled.Tune
import androidx.compose.material.icons.filled.Warning
import androidx.compose.material.icons.filled.Whatshot
import androidx.compose.material3.AssistChip
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilterChip
import androidx.compose.material3.FilterChipDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.xlabsx27.naveeoffline.AppContainer
import com.xlabsx27.naveeoffline.R
import com.xlabsx27.naveeoffline.ble.LinkState
import com.xlabsx27.naveeoffline.ble.LinkStatus
import com.xlabsx27.naveeoffline.data.Trip
import com.xlabsx27.naveeoffline.data.Units
import com.xlabsx27.naveeoffline.location.isGps
import com.xlabsx27.naveeoffline.protocol.DriveMode
import com.xlabsx27.naveeoffline.protocol.Feature
import com.xlabsx27.naveeoffline.protocol.NaveeModels
import com.xlabsx27.naveeoffline.protocol.Setting
import com.xlabsx27.naveeoffline.protocol.Telemetry
import com.xlabsx27.naveeoffline.ui.Format
import com.xlabsx27.naveeoffline.ui.Permissions
import com.xlabsx27.naveeoffline.ui.components.OptimisticSettings
import com.xlabsx27.naveeoffline.ui.components.StatTile
import com.xlabsx27.naveeoffline.ui.components.TileRow
import com.xlabsx27.naveeoffline.ui.components.rememberOptimisticSettings
import com.xlabsx27.naveeoffline.ui.faultText
import com.xlabsx27.naveeoffline.ui.theme.StatusColors
import kotlinx.coroutines.flow.emptyFlow

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun DashboardScreen(
    c: AppContainer,
    permissionTick: Int,
    onRequestPermissions: () -> Unit,
    onConnect: () -> Unit,
    onInfo: () -> Unit,
    onControls: () -> Unit,
) {
    val context = LocalContext.current
    val status by c.connection.status.collectAsStateWithLifecycle()
    val t by c.connection.telemetry.collectAsStateWithLifecycle()
    val settings by c.settings.collectAsStateWithLifecycle()
    val trip by c.recorder.active.collectAsStateWithLifecycle()
    val connected = status.state == LinkState.CONNECTED
    // Some models (e.g. E45 Pro) never push their speed; a fresh GPS fix stands in, but only with
    // location features on (otherwise GpsSource emits nothing) and while connected.
    val fixes = remember(connected) { if (connected) c.gps.fixes else emptyFlow() }
    val fix by fixes.collectAsStateWithLifecycle(initialValue = null)
    val gpsKmh = fix?.takeIf { settings.locationEnabled && it.isGps && it.hasSpeed() && System.currentTimeMillis() - it.time < 5_000 }
        ?.let { it.speed * 3.6 }
    val units = settings.units

    Scaffold(
        topBar = {
            TopAppBar(
                title = {
                    Column {
                        Text(status.name ?: settings.lastName ?: stringResource(R.string.app_name))
                        t.model?.let { Text(it, style = MaterialTheme.typography.bodySmall) }
                    }
                },
                actions = { LinkChip(status, onConnect) },
            )
        },
    ) { padding ->
        Column(
            Modifier.padding(padding).verticalScroll(rememberScrollState()).padding(horizontal = 12.dp),
            verticalArrangement = Arrangement.spacedBy(10.dp),
        ) {
            val granted = remember(permissionTick) { Permissions.bluetoothGranted(context) }
            if (!granted) {
                PromptCard(stringResource(R.string.ride_need_bluetooth), stringResource(R.string.grant), onRequestPermissions)
            }

            if (status.rejected) {
                Banner(stringResource(R.string.ride_rejected))
            }

            t.fault?.let { f ->
                val text = faultText(f.number)?.let { stringResource(it) }
                Banner(
                    if (f.code != null && text != null) "${f.code} · $text" else stringResource(R.string.fault_unknown, f.raw),
                    Icons.Filled.Warning,
                )
            }

            if (connected) {
                SpeedHero(t, gpsKmh, units)
                LockButton(c, t)
                QuickToggles(rememberOptimisticSettings(c), t)
                TileRow {
                    StatTile(stringResource(R.string.range_left), Format.km(t.rangeKm, units, 0), Modifier.weight(1f))
                    StatTile(stringResource(R.string.trip), Format.km(t.tripKm, units), Modifier.weight(1f))
                    StatTile(stringResource(R.string.odometer), Format.km(t.odometerKm, units, 0), Modifier.weight(1f))
                }
            } else if (granted) {
                NotConnectedCard(c, status, settings.lastAddress, settings.lastName, settings.sleepingSince, onConnect)
            }

            if (settings.locationEnabled) {
                trip?.let { RecordingCard(c, it, units) }
            }

            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                OutlinedButton(onClick = onControls) {
                    Icon(Icons.Filled.Tune, null, Modifier.size(18.dp))
                    Spacer(Modifier.size(6.dp))
                    Text(stringResource(R.string.all_controls))
                }
                OutlinedButton(onClick = onInfo) {
                    Icon(Icons.Filled.Info, null, Modifier.size(18.dp))
                    Spacer(Modifier.size(6.dp))
                    Text(stringResource(R.string.scooter_info))
                }
            }
            if (settings.locationEnabled && trip == null) {
                OutlinedButton(onClick = { c.startRecording() }) {
                    Icon(Icons.Filled.FiberManualRecord, null, Modifier.size(18.dp), tint = StatusColors.bad)
                    Spacer(Modifier.size(6.dp))
                    Text(stringResource(R.string.record_trip))
                }
            }
            Spacer(Modifier.height(16.dp))
        }
    }
}

@Composable
private fun Banner(text: String, icon: ImageVector? = null) {
    Card(colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.errorContainer)) {
        Row(Modifier.padding(16.dp), verticalAlignment = Alignment.CenterVertically) {
            if (icon != null) {
                Icon(icon, null, tint = MaterialTheme.colorScheme.onErrorContainer)
                Spacer(Modifier.size(12.dp))
            }
            Text(text, color = MaterialTheme.colorScheme.onErrorContainer)
        }
    }
}

@Composable
private fun NotConnectedCard(
    c: AppContainer,
    status: LinkStatus,
    lastAddress: String?,
    lastName: String?,
    sleepingSince: Long?,
    onConnect: () -> Unit,
) {
    Card(Modifier.fillMaxWidth()) {
        Column(Modifier.padding(20.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
            val busy = status.isWanted
            Text(
                when {
                    status.state == LinkState.RECONNECTING -> stringResource(R.string.ride_out_of_range)
                    busy -> stringResource(R.string.connecting)
                    lastAddress == null -> stringResource(R.string.ride_no_scooter)
                    sleepingSince != null -> stringResource(R.string.locked_since, Format.time(sleepingSince))
                    else -> stringResource(R.string.not_connected)
                },
                style = MaterialTheme.typography.titleLarge,
            )
            when {
                busy -> OutlinedButton(onClick = { c.disconnect() }) { Text(stringResource(R.string.stop)) }
                lastAddress != null -> Button(onClick = {
                    if (c.scanner.isEnabled) c.connect(lastAddress, lastName) else onConnect()
                }) { Text(stringResource(R.string.connect)) }
                else -> Button(onClick = onConnect) { Text(stringResource(R.string.find_scooter)) }
            }
        }
    }
}

@Composable
private fun SpeedHero(t: Telemetry, gpsKmh: Double?, units: Units) {
    val speed = t.speedKmh ?: gpsKmh
    Card(Modifier.fillMaxWidth()) {
        Row(Modifier.padding(20.dp), verticalAlignment = Alignment.CenterVertically) {
            Column(Modifier.weight(1f)) {
                Text(
                    speed?.let { Format.num(Format.speedValue(it, units), 0) } ?: "–",
                    fontSize = 88.sp,
                    fontWeight = FontWeight.Bold,
                    lineHeight = 88.sp,
                )
                Text(
                    Format.speedUnit(units) + if (t.speedKmh == null && gpsKmh != null) " · GPS" else "",
                    style = MaterialTheme.typography.titleMedium,
                )
            }
            Box(contentAlignment = Alignment.Center) {
                val soc = t.soc
                val color = when {
                    soc == null -> MaterialTheme.colorScheme.outline
                    soc <= 15 -> StatusColors.bad
                    soc <= 30 -> StatusColors.warn
                    else -> StatusColors.ok
                }
                CircularProgressIndicator(
                    progress = { (soc ?: 0) / 100f },
                    modifier = Modifier.size(112.dp),
                    color = color,
                    strokeWidth = 10.dp,
                    trackColor = MaterialTheme.colorScheme.surfaceVariant,
                )
                Column(horizontalAlignment = Alignment.CenterHorizontally) {
                    if (t.chargingState == 1) {
                        Icon(Icons.Filled.BatteryChargingFull, stringResource(R.string.charging), Modifier.size(18.dp))
                    }
                    Text(soc?.let { "$it%" } ?: "–", style = MaterialTheme.typography.headlineSmall, fontWeight = FontWeight.SemiBold)
                    Text(stringResource(R.string.battery), style = MaterialTheme.typography.labelSmall)
                }
            }
        }
    }
}

@Composable
private fun LockButton(c: AppContainer, t: Telemetry) {
    val locked = t.locked ?: return
    // Never immobilise a moving scooter.
    val moving = (t.speedKmh ?: 0.0) > 1.0
    Button(
        onClick = { if (!moving || locked) c.write(Setting.LOCK, if (locked) 0 else 1) },
        modifier = Modifier.fillMaxWidth().height(64.dp),
        colors = if (locked) {
            ButtonDefaults.buttonColors(
                containerColor = MaterialTheme.colorScheme.tertiaryContainer,
                contentColor = MaterialTheme.colorScheme.onTertiaryContainer,
            )
        } else {
            ButtonDefaults.buttonColors()
        },
    ) {
        Icon(if (locked) Icons.Filled.LockOpen else Icons.Filled.Lock, null)
        Spacer(Modifier.size(10.dp))
        Text(
            stringResource(if (locked) R.string.unlock else if (moving) R.string.stop_to_lock else R.string.lock),
            style = MaterialTheme.typography.titleMedium,
        )
    }
}

@OptIn(ExperimentalLayoutApi::class)
@Composable
private fun QuickToggles(o: OptimisticSettings, t: Telemetry) {
    FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
        QuickChip(stringResource(R.string.chip_light), Icons.Filled.FlashlightOn, o.on(Setting.AUTO_LIGHT, t.autoLight)) {
            o.send(Setting.AUTO_LIGHT, if (it) 1 else 0)
        }
        if (NaveeModels.supports(t.pid, Feature.CRUISE)) {
            QuickChip(stringResource(R.string.cruise), Icons.Filled.Speed, o.on(Setting.CRUISE, t.cruise)) {
                o.send(Setting.CRUISE, if (it) 1 else 0)
            }
        }
        QuickChip(stringResource(R.string.mode_turbo), Icons.Filled.Whatshot, o.value(Setting.DRIVE_MODE, t.driveMode) == DriveMode.TURBO) {
            o.send(Setting.DRIVE_MODE, if (it) DriveMode.TURBO else DriveMode.NORMAL)
        }
        if (NaveeModels.supports(t.pid, Feature.ECO)) {
            QuickChip(stringResource(R.string.eco), Icons.Filled.Eco, o.on(Setting.ECO, t.eco)) {
                o.send(Setting.ECO, if (it) 1 else 0)
            }
        }
    }
}

@Composable
private fun QuickChip(label: String, icon: ImageVector, selected: Boolean, onChange: (Boolean) -> Unit) {
    FilterChip(
        selected = selected,
        onClick = { onChange(!selected) },
        label = { Text(label) },
        leadingIcon = { Icon(icon, null, Modifier.size(FilterChipDefaults.IconSize)) },
    )
}

@Composable
private fun RecordingCard(c: AppContainer, trip: Trip, units: Units) {
    Card(Modifier.fillMaxWidth()) {
        Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Icon(Icons.Filled.FiberManualRecord, null, tint = StatusColors.bad, modifier = Modifier.size(14.dp))
                Spacer(Modifier.size(8.dp))
                Text(stringResource(R.string.recording_since, Format.time(trip.startedAt)), style = MaterialTheme.typography.titleMedium)
            }
            TileRow {
                StatTile(stringResource(R.string.distance), Format.distance(trip.distanceM, units), Modifier.weight(1f))
                StatTile(stringResource(R.string.avg), Format.speed(trip.avgSpeedKmh, units), Modifier.weight(1f))
                StatTile(stringResource(R.string.energy), "${Format.num(trip.energyWh, 1)} Wh", Modifier.weight(1f))
            }
            Button(onClick = { c.stopRecording() }) {
                Icon(Icons.Filled.Stop, null)
                Spacer(Modifier.size(8.dp))
                Text(stringResource(R.string.stop_trip))
            }
        }
    }
}

@Composable
private fun LinkChip(status: LinkStatus, onClick: () -> Unit) {
    val (icon, label, color) = when (status.state) {
        LinkState.CONNECTED -> Triple(Icons.Filled.Bluetooth, R.string.connected, StatusColors.ok)
        LinkState.CONNECTING, LinkState.AUTHENTICATING, LinkState.RECONNECTING ->
            Triple(Icons.AutoMirrored.Filled.BluetoothSearching, R.string.connecting_short, StatusColors.warn)
        else -> Triple(Icons.Filled.BluetoothDisabled, R.string.offline, StatusColors.bad)
    }
    AssistChip(
        onClick = onClick,
        label = { Text(stringResource(label)) },
        leadingIcon = { Icon(icon, null, tint = color) },
        modifier = Modifier.padding(end = 8.dp),
    )
}
