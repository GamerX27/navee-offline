package com.xlabsx27.naveeoffline.ui.screens

import android.bluetooth.BluetoothAdapter
import android.content.Intent
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Bluetooth
import androidx.compose.material.icons.filled.ElectricScooter
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilterChip
import androidx.compose.material3.Icon
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.ListItem
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateMapOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.xlabsx27.naveeoffline.AppContainer
import com.xlabsx27.naveeoffline.R
import com.xlabsx27.naveeoffline.ble.LinkState
import com.xlabsx27.naveeoffline.ble.ScanHit
import com.xlabsx27.naveeoffline.ui.Permissions
import com.xlabsx27.naveeoffline.ui.components.BackButton
import kotlinx.coroutines.flow.catch
import kotlinx.coroutines.launch

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ConnectScreen(c: AppContainer, permissionTick: Int, requestPermissions: () -> Unit, onBack: () -> Unit) {
    val context = LocalContext.current
    val settings by c.settings.collectAsStateWithLifecycle()
    val status by c.connection.status.collectAsStateWithLifecycle()
    val hits = remember { mutableStateMapOf<String, ScanHit>() }
    var error by remember { mutableStateOf<String?>(null) }
    var btTick by remember { mutableIntStateOf(0) }
    val scope = rememberCoroutineScope()
    val enableBt = rememberLauncherForActivityResult(ActivityResultContracts.StartActivityForResult()) { btTick++ }

    val granted = remember(permissionTick) { Permissions.bluetoothGranted(context) }
    val btOn = remember(btTick) { c.scanner.isEnabled }

    LaunchedEffect(granted, btOn) {
        if (!granted || !btOn) return@LaunchedEffect
        error = null
        c.scanner.scan()
            .catch { error = it.message }
            .collect { hits[it.address] = it }
    }

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text(stringResource(R.string.connect_title)) },
                navigationIcon = { BackButton(onBack) },
            )
        },
    ) { padding ->
        Column(Modifier.padding(padding).fillMaxSize()) {
            when {
                !granted -> Prompt(stringResource(R.string.ride_need_bluetooth), stringResource(R.string.grant), requestPermissions)
                !btOn -> Prompt(stringResource(R.string.bt_off), stringResource(R.string.bt_turn_on)) {
                    enableBt.launch(Intent(BluetoothAdapter.ACTION_REQUEST_ENABLE))
                }
                else -> {
                    LinearProgressIndicator(Modifier.fillMaxWidth())
                    Text(
                        stringResource(R.string.connect_hint),
                        style = MaterialTheme.typography.bodyMedium,
                        modifier = Modifier.padding(16.dp),
                    )
                    FilterChip(
                        selected = settings.showAllDevices,
                        onClick = { scope.launch { c.settingsStore.setShowAllDevices(!settings.showAllDevices) } },
                        label = { Text(stringResource(R.string.show_all_devices)) },
                        modifier = Modifier.padding(horizontal = 16.dp),
                    )
                    error?.let { Text(it, color = MaterialTheme.colorScheme.error, modifier = Modifier.padding(16.dp)) }
                    if (status.state == LinkState.NOT_A_SCOOTER) {
                        Text(
                            stringResource(R.string.not_a_scooter_long),
                            color = MaterialTheme.colorScheme.error,
                            modifier = Modifier.padding(16.dp),
                        )
                    }
                    val list = hits.values
                        .filter { settings.showAllDevices || it.isNavee }
                        .sortedWith(compareByDescending<ScanHit> { it.isNavee }.thenByDescending { it.rssi })
                    LazyColumn {
                        items(list, key = { it.address }) { hit ->
                            val busy = status.address == hit.address && status.isWanted
                            ListItem(
                                headlineContent = { Text(hit.name ?: stringResource(R.string.unknown_device)) },
                                supportingContent = {
                                    Text(
                                        hit.address + when {
                                            busy && status.state == LinkState.CONNECTED -> " · " + stringResource(R.string.connected)
                                            busy -> " · " + stringResource(R.string.connecting)
                                            else -> ""
                                        },
                                    )
                                },
                                leadingContent = {
                                    Icon(if (hit.isNavee) Icons.Filled.ElectricScooter else Icons.Filled.Bluetooth, null)
                                },
                                trailingContent = { Text("${hit.rssi} dBm") },
                                modifier = Modifier.clickable {
                                    c.connect(hit.address, hit.name)
                                    onBack()
                                },
                            )
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun Prompt(text: String, action: String, onClick: () -> Unit) {
    Card(Modifier.fillMaxWidth().padding(16.dp)) {
        Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
            Text(text)
            Button(onClick = onClick) { Text(action) }
        }
    }
}

@Composable
fun PromptCard(text: String, action: String, onClick: () -> Unit, modifier: Modifier = Modifier) {
    Card(modifier.fillMaxWidth()) {
        Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
            Text(text)
            OutlinedButton(onClick = onClick) { Text(action) }
        }
    }
}
