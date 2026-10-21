package com.xlabsx27.naveeoffline.ui.screens

import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.ListItem
import androidx.compose.material3.ListItemDefaults
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.xlabsx27.naveeoffline.AppContainer
import com.xlabsx27.naveeoffline.R
import com.xlabsx27.naveeoffline.ble.LinkState
import com.xlabsx27.naveeoffline.protocol.NaveeFaults
import com.xlabsx27.naveeoffline.ui.Format
import com.xlabsx27.naveeoffline.ui.components.BackButton
import com.xlabsx27.naveeoffline.ui.components.InfoRow
import com.xlabsx27.naveeoffline.ui.faultText
import com.xlabsx27.naveeoffline.ui.components.Section

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun InfoScreen(c: AppContainer, onBack: () -> Unit) {
    val status by c.connection.status.collectAsStateWithLifecycle()
    val t by c.connection.telemetry.collectAsStateWithLifecycle()
    val settings by c.settings.collectAsStateWithLifecycle()
    var showCodes by remember { mutableStateOf(false) }

    Scaffold(
        topBar = { TopAppBar(title = { Text(stringResource(R.string.scooter_info)) }, navigationIcon = { BackButton(onBack) }) },
    ) { padding ->
        Column(Modifier.padding(padding).verticalScroll(rememberScrollState())) {
            Section(stringResource(R.string.section_battery)) {
                InfoRow(stringResource(R.string.charge), t.soc?.let { "$it %" })
                InfoRow(stringResource(R.string.voltage), t.voltage?.let { "${Format.num(it, 2)} V" })
                InfoRow(stringResource(R.string.current), t.current?.let { "${Format.num(it, 2)} A" })
                InfoRow(stringResource(R.string.power), t.powerW?.let { "${Format.num(it, 0)} W" })
                InfoRow(stringResource(R.string.health), t.soh?.let { "$it %" })
                InfoRow(stringResource(R.string.temperature), t.batteryTempC?.let { "$it °C" })
                InfoRow(stringResource(R.string.cycles), t.cycles?.toString())
                InfoRow(stringResource(R.string.range_left), Format.km(t.rangeKm, settings.units, 0))
            }
            Section(stringResource(R.string.section_faults)) {
                val f = t.fault
                ListItem(
                    headlineContent = {
                        Text(
                            when {
                                t.faultRaw == null -> stringResource(R.string.fault_not_reported)
                                f == null -> stringResource(R.string.fault_none)
                                f.code != null -> "${f.code} (${f.number})"
                                else -> stringResource(R.string.fault_unknown, f.raw)
                            },
                        )
                    },
                    supportingContent = f?.let {
                        { Text(faultText(it.number)?.let { r -> stringResource(r) } ?: stringResource(R.string.fault_not_in_table)) }
                    },
                    colors = ListItemDefaults.colors(containerColor = Color.Transparent),
                )
                TextButton(onClick = { showCodes = true }, modifier = Modifier.padding(start = 8.dp)) {
                    Text(stringResource(R.string.fault_all_codes))
                }
            }
            Section(stringResource(R.string.section_identity)) {
                InfoRow(stringResource(R.string.bt_name), status.name)
                InfoRow(stringResource(R.string.address), status.address ?: settings.lastAddress)
                InfoRow(stringResource(R.string.model), t.model ?: t.pid?.let { stringResource(R.string.model_unknown, it) })
                InfoRow(stringResource(R.string.serial), t.serial)
                InfoRow(stringResource(R.string.region), t.region)
            }
            Section(stringResource(R.string.section_firmware)) {
                InfoRow(stringResource(R.string.fw_meter), t.fwMeter)
                InfoRow(stringResource(R.string.fw_controller), t.fwController)
                InfoRow(stringResource(R.string.fw_bms), t.fwBms)
                InfoRow(stringResource(R.string.fw_screen), t.fwScreen)
                InfoRow("UWB", t.fwUwb)
            }
            Section(stringResource(R.string.section_connection)) {
                InfoRow(stringResource(R.string.state), linkStateText(status.state))
                InfoRow(stringResource(R.string.signal), status.rssi?.let { "$it dBm" })
                InfoRow(stringResource(R.string.live_reports), stringResource(if (status.armed) R.string.streaming else R.string.polling_only))
                InfoRow(stringResource(R.string.max_speed_param), t.maxSpeed?.let { "$it km/h" })
            }
            Spacer(Modifier.height(24.dp))
        }
    }

    if (showCodes) {
        AlertDialog(
            onDismissRequest = { showCodes = false },
            title = { Text(stringResource(R.string.fault_all_codes)) },
            text = {
                Column(Modifier.verticalScroll(rememberScrollState())) {
                    NaveeFaults.all.forEach {
                        Text("${it.code} · ${faultText(it.number)?.let { r -> stringResource(r) }}", style = MaterialTheme.typography.bodyMedium)
                        Spacer(Modifier.height(6.dp))
                    }
                }
            },
            confirmButton = { TextButton(onClick = { showCodes = false }) { Text(stringResource(R.string.close)) } },
        )
    }
}

@Composable
fun linkStateText(state: LinkState): String = stringResource(
    when (state) {
        LinkState.IDLE -> R.string.not_connected
        LinkState.CONNECTING, LinkState.AUTHENTICATING -> R.string.connecting
        LinkState.CONNECTED -> R.string.connected
        LinkState.RECONNECTING -> R.string.ride_out_of_range
        LinkState.NOT_A_SCOOTER -> R.string.not_a_scooter
    },
)
