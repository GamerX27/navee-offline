package com.xlabsx27.naveeoffline.ui.screens

import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.DirectionsBike
import androidx.compose.material.icons.filled.Brightness5
import androidx.compose.material.icons.filled.Eco
import androidx.compose.material.icons.filled.EmojiObjects
import androidx.compose.material.icons.filled.FlashlightOn
import androidx.compose.material.icons.filled.Highlight
import androidx.compose.material.icons.filled.Lock
import androidx.compose.material.icons.filled.Speed
import androidx.compose.material.icons.filled.WbTwilight
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.ListItem
import androidx.compose.material3.ListItemDefaults
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SegmentedButton
import androidx.compose.material3.SegmentedButtonDefaults
import androidx.compose.material3.SingleChoiceSegmentedButtonRow
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.xlabsx27.naveeoffline.AppContainer
import com.xlabsx27.naveeoffline.R
import com.xlabsx27.naveeoffline.ble.LinkState
import com.xlabsx27.naveeoffline.protocol.DriveMode
import com.xlabsx27.naveeoffline.protocol.Feature
import com.xlabsx27.naveeoffline.protocol.NaveeModels
import com.xlabsx27.naveeoffline.protocol.Setting
import com.xlabsx27.naveeoffline.protocol.Telemetry
import com.xlabsx27.naveeoffline.ui.components.BackButton
import com.xlabsx27.naveeoffline.ui.components.Divider
import com.xlabsx27.naveeoffline.ui.components.HelpButton
import com.xlabsx27.naveeoffline.ui.components.Section
import com.xlabsx27.naveeoffline.ui.components.SwitchRow
import com.xlabsx27.naveeoffline.ui.components.rememberOptimisticSettings

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ControlsScreen(c: AppContainer, onConnect: () -> Unit, onBack: () -> Unit) {
    val status by c.connection.status.collectAsStateWithLifecycle()
    val t by c.connection.telemetry.collectAsStateWithLifecycle()
    val connected = status.state == LinkState.CONNECTED
    val o = rememberOptimisticSettings(c)
    fun toggle(s: Setting) = { on: Boolean -> o.send(s, if (on) 1 else 0) }

    Scaffold(
        topBar = { TopAppBar(title = { Text(stringResource(R.string.all_controls)) }, navigationIcon = { BackButton(onBack) }) },
    ) { padding ->
        Column(Modifier.padding(padding).verticalScroll(rememberScrollState())) {
            if (!connected) {
                PromptCard(stringResource(R.string.controls_connect_first), stringResource(R.string.connect), onConnect, Modifier.padding(12.dp))
            } else {
                Text(
                    stringResource(R.string.controls_hint),
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier.padding(horizontal = 16.dp, vertical = 8.dp),
                )
            }

            Section(stringResource(R.string.section_riding)) {
                SwitchRow(
                    stringResource(R.string.lock),
                    checked = o.value(Setting.LOCK, t.locked?.let { if (it) 1 else 0 }) == 1,
                    onChange = { if (!it || (t.speedKmh ?: 0.0) <= 1.0) o.send(Setting.LOCK, if (it) 1 else 0) },
                    subtitle = stringResource(R.string.lock_sub),
                    enabled = connected,
                    icon = Icons.Filled.Lock,
                )
                Divider()
                DriveModeRow(o.value(Setting.DRIVE_MODE, t.driveMode), connected) { o.send(Setting.DRIVE_MODE, it) }
                if (NaveeModels.supports(t.pid, Feature.CRUISE)) {
                    Divider()
                    SwitchRow(
                        stringResource(R.string.cruise),
                        o.on(Setting.CRUISE, t.cruise),
                        toggle(Setting.CRUISE),
                        help = stringResource(R.string.cruise_help),
                        enabled = connected,
                        icon = Icons.Filled.Speed,
                    )
                }
                if (NaveeModels.supports(t.pid, Feature.TCS)) {
                    Divider()
                    SwitchRow(
                        stringResource(R.string.tcs),
                        o.on(Setting.TCS, t.tcs),
                        toggle(Setting.TCS),
                        help = stringResource(R.string.tcs_help),
                        enabled = connected,
                        icon = Icons.AutoMirrored.Filled.DirectionsBike,
                    )
                }
                if (NaveeModels.supports(t.pid, Feature.ECO)) {
                    Divider()
                    SwitchRow(
                        stringResource(R.string.eco),
                        o.on(Setting.ECO, t.eco),
                        toggle(Setting.ECO),
                        help = stringResource(R.string.eco_help),
                        enabled = connected,
                        icon = Icons.Filled.Eco,
                    )
                }
                if (NaveeModels.supports(t.pid, Feature.ZERO_START)) {
                    Divider()
                    KickStartRow(t, o.value(Setting.ZERO_START, t.startSpeed), connected) { o.send(Setting.ZERO_START, it) }
                }
            }

            Section(stringResource(R.string.section_lights)) {
                SwitchRow(
                    stringResource(R.string.auto_light), o.on(Setting.AUTO_LIGHT, t.autoLight), toggle(Setting.AUTO_LIGHT),
                    subtitle = stringResource(R.string.auto_light_sub), enabled = connected, icon = Icons.Filled.FlashlightOn,
                )
                if (NaveeModels.supports(t.pid, Feature.DRL)) {
                    Divider()
                    SwitchRow(
                        stringResource(R.string.drl), o.on(Setting.DRL, t.drl), toggle(Setting.DRL),
                        enabled = connected, icon = Icons.Filled.Brightness5,
                    )
                }
                Divider()
                SwitchRow(
                    stringResource(R.string.tail_light), o.on(Setting.TAIL_LIGHT, t.tailLight), toggle(Setting.TAIL_LIGHT),
                    enabled = connected, icon = Icons.Filled.Highlight,
                )
                if (NaveeModels.supports(t.pid, Feature.LOGO_LIGHT)) {
                    Divider()
                    SwitchRow(
                        stringResource(R.string.logo_light), o.on(Setting.LOGO_LIGHT, t.logoLight), toggle(Setting.LOGO_LIGHT),
                        enabled = connected, icon = Icons.Filled.EmojiObjects,
                    )
                }
                if (NaveeModels.supports(t.pid, Feature.AMBIENT_LIGHT)) {
                    Divider()
                    SwitchRow(
                        stringResource(R.string.ambient_light), o.on(Setting.AMBIENT_LIGHT, t.ambientLight), toggle(Setting.AMBIENT_LIGHT),
                        subtitle = stringResource(R.string.ambient_light_sub), enabled = connected, icon = Icons.Filled.WbTwilight,
                    )
                }
            }

            Spacer(Modifier.height(24.dp))
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun DriveModeRow(current: Int?, enabled: Boolean, onSelect: (Int) -> Unit) {
    val options = listOf(DriveMode.NORMAL to stringResource(R.string.mode_normal), DriveMode.TURBO to stringResource(R.string.mode_turbo))
    ListItem(
        headlineContent = { Text(stringResource(R.string.drive_mode)) },
        supportingContent = {
            SingleChoiceSegmentedButtonRow(Modifier.fillMaxWidth().padding(top = 8.dp)) {
                options.forEachIndexed { i, (v, label) ->
                    SegmentedButton(
                        selected = current == v,
                        onClick = { onSelect(v) },
                        shape = SegmentedButtonDefaults.itemShape(i, options.size),
                        enabled = enabled,
                    ) { Text(label) }
                }
            }
        },
        colors = ListItemDefaults.colors(containerColor = Color.Transparent),
    )
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun KickStartRow(t: Telemetry, current: Int?, enabled: Boolean, onSelect: (Int) -> Unit) {
    val levels = NaveeModels.zeroStartLevels(t.region).toList()
    ListItem(
        headlineContent = { Text(stringResource(R.string.kick_start)) },
        supportingContent = {
            Column {
                Text(stringResource(R.string.kick_start_sub))
                SingleChoiceSegmentedButtonRow(Modifier.fillMaxWidth().padding(top = 8.dp)) {
                    levels.forEachIndexed { i, v ->
                        SegmentedButton(
                            selected = current == v,
                            onClick = { onSelect(v) },
                            shape = SegmentedButtonDefaults.itemShape(i, levels.size),
                            enabled = enabled,
                        ) { Text(if (v == 0) stringResource(R.string.kick_start_zero) else "$v") }
                    }
                }
            }
        },
        trailingContent = { HelpButton(stringResource(R.string.kick_start), stringResource(R.string.kick_start_help)) },
        colors = ListItemDefaults.colors(containerColor = Color.Transparent),
    )
}
