package com.xlabsx27.naveeoffline.ui.screens

import android.content.Intent
import android.net.Uri
import android.os.Build
import android.widget.Toast
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.appcompat.app.AppCompatDelegate
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.BluetoothSearching
import androidx.compose.material.icons.automirrored.filled.KeyboardArrowRight
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.ElectricScooter
import androidx.compose.material.icons.filled.Info
import androidx.compose.material.icons.filled.Key
import androidx.compose.material.icons.filled.LocationOn
import androidx.compose.material.icons.filled.Lock
import androidx.compose.material.icons.filled.Map
import androidx.compose.material.icons.filled.Tune
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.ListItem
import androidx.compose.material3.ListItemDefaults
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SegmentedButton
import androidx.compose.material3.SegmentedButtonDefaults
import androidx.compose.material3.SingleChoiceSegmentedButtonRow
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalResources
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import androidx.core.os.LocaleListCompat
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.xlabsx27.naveeoffline.AppContainer
import com.xlabsx27.naveeoffline.R
import com.xlabsx27.naveeoffline.data.ThemeMode
import com.xlabsx27.naveeoffline.data.Units
import com.xlabsx27.naveeoffline.export.Backup
import com.xlabsx27.naveeoffline.pairing.Pairing
import com.xlabsx27.naveeoffline.pairing.PairingFile
import com.xlabsx27.naveeoffline.service.AutoLockRange
import com.xlabsx27.naveeoffline.ui.Permissions
import com.xlabsx27.naveeoffline.ui.components.Divider
import com.xlabsx27.naveeoffline.ui.components.HelpButton
import com.xlabsx27.naveeoffline.ui.components.Section
import com.xlabsx27.naveeoffline.ui.components.SwitchRow
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.io.File
import kotlin.system.exitProcess

private enum class Language(val tag: String) { SYSTEM(""), NB("nb"), EN("en") }

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun SettingsScreen(
    c: AppContainer,
    onConnect: () -> Unit,
    onInfo: () -> Unit,
    onControls: () -> Unit,
    onAbout: () -> Unit,
) {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    val s by c.settings.collectAsStateWithLifecycle()
    val status by c.connection.status.collectAsStateWithLifecycle()
    val maps by c.maps.files.collectAsStateWithLifecycle()
    var importing by remember { mutableStateOf(false) }
    var deleteMap by remember { mutableStateOf<File?>(null) }
    var confirmRestore by remember { mutableStateOf<Uri?>(null) }
    var confirmForget by remember { mutableStateOf(false) }
    var warnExport by remember { mutableStateOf(false) }
    var pendingImport by remember { mutableStateOf<Pairing?>(null) }

    val resources = LocalResources.current
    fun toast(msg: String) = Toast.makeText(context, msg, Toast.LENGTH_LONG).show()
    fun toast(res: Int, vararg args: Any) = toast(resources.getString(res, *args))

    val locationPermission = rememberLauncherForActivityResult(ActivityResultContracts.RequestMultiplePermissions()) {
        c.gps.onPermissionChanged()
        if (Permissions.locationGranted(context)) {
            scope.launch { c.settingsStore.setLocationEnabled(true) }
        } else {
            toast(R.string.location_denied)
        }
    }
    fun setLocation(on: Boolean) {
        when {
            !on -> scope.launch { c.settingsStore.setLocationEnabled(false) }
            Permissions.locationGranted(context) -> scope.launch { c.settingsStore.setLocationEnabled(true) }
            else -> locationPermission.launch(Permissions.location.toTypedArray())
        }
    }

    val importMap = rememberLauncherForActivityResult(ActivityResultContracts.OpenDocument()) { uri ->
        if (uri == null) return@rememberLauncherForActivityResult
        importing = true
        scope.launch {
            c.maps.import(uri)
                .onSuccess { toast(R.string.map_imported, it.name) }
                .onFailure { toast(it.message ?: resources.getString(R.string.import_failed)) }
            importing = false
        }
    }
    val exportBackup = rememberLauncherForActivityResult(ActivityResultContracts.CreateDocument("application/octet-stream")) { uri ->
        if (uri == null) return@rememberLauncherForActivityResult
        scope.launch {
            runCatching { Backup.export(context, c.db, uri) }
                .onSuccess { toast(R.string.backup_saved) }
                .onFailure { toast(R.string.backup_failed, it.message.orEmpty()) }
        }
    }
    val pickBackup = rememberLauncherForActivityResult(ActivityResultContracts.OpenDocument()) { uri -> confirmRestore = uri }

    val exportPairing = rememberLauncherForActivityResult(ActivityResultContracts.CreateDocument("application/json")) { uri ->
        if (uri == null) return@rememberLauncherForActivityResult
        scope.launch {
            runCatching {
                val text = PairingFile.encode(c.currentPairing())
                withContext(Dispatchers.IO) {
                    requireNotNull(context.contentResolver.openOutputStream(uri, "wt")).use { it.write(text.toByteArray()) }
                }
            }
                .onSuccess { toast(R.string.pairing_saved) }
                .onFailure { toast(R.string.export_failed) }
        }
    }
    val importPairing = rememberLauncherForActivityResult(ActivityResultContracts.OpenDocument()) { uri ->
        if (uri == null) return@rememberLauncherForActivityResult
        scope.launch {
            runCatching {
                val text = withContext(Dispatchers.IO) {
                    requireNotNull(context.contentResolver.openInputStream(uri)).use { input ->
                        // A pairing file is tiny; read at most 8 KB and let the parser reject anything bigger.
                        val buf = ByteArray(8192)
                        var n = 0
                        while (n < buf.size) {
                            val r = input.read(buf, n, buf.size - n)
                            if (r < 0) break
                            n += r
                        }
                        String(buf, 0, n)
                    }
                }
                PairingFile.decode(text)
            }
                .onSuccess { pendingImport = it }
                .onFailure { toast(R.string.pairing_invalid, it.message.orEmpty()) }
        }
    }

    Scaffold(topBar = { TopAppBar(title = { Text(stringResource(R.string.tab_settings)) }) }) { padding ->
        Column(Modifier.padding(padding).verticalScroll(rememberScrollState())) {
            LanguagePicker()

            Section(stringResource(R.string.section_scooter)) {
                val address = s.lastAddress
                ListItem(
                    headlineContent = { Text(s.lastName ?: status.name ?: stringResource(R.string.no_scooter)) },
                    supportingContent = { Text(address?.let { "$it · ${linkStateText(status.state)}" } ?: stringResource(R.string.no_scooter_sub)) },
                    leadingContent = { Icon(Icons.Filled.ElectricScooter, null) },
                    colors = ListItemDefaults.colors(containerColor = Color.Transparent),
                )
                Row(Modifier.padding(start = 16.dp, end = 16.dp, bottom = 12.dp), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    when {
                        address == null -> OutlinedButton(onClick = onConnect) { Text(stringResource(R.string.find_scooter)) }
                        status.isWanted -> OutlinedButton(onClick = { c.disconnect() }) { Text(stringResource(R.string.disconnect)) }
                        else -> OutlinedButton(onClick = { c.connect(address, s.lastName) }) { Text(stringResource(R.string.connect)) }
                    }
                    if (address != null) TextButton(onClick = { confirmForget = true }) { Text(stringResource(R.string.forget)) }
                }
                Divider()
                LinkRow(stringResource(R.string.scooter_info), Icons.Filled.Info, onInfo)
                Divider()
                LinkRow(stringResource(R.string.all_controls), Icons.Filled.Tune, onControls)
                if (address != null) {
                    Divider()
                    LinkRow(stringResource(R.string.other_scooter), Icons.AutoMirrored.Filled.BluetoothSearching, onConnect)
                }
                Divider()
                SwitchRow(
                    stringResource(R.string.auto_reconnect), s.autoReconnect, { scope.launch { c.settingsStore.setAutoReconnect(it) } },
                    subtitle = stringResource(R.string.auto_reconnect_sub),
                )
                Divider()
                SwitchRow(
                    stringResource(R.string.auto_lock), s.autoLock, { scope.launch { c.settingsStore.setAutoLock(it) } },
                    subtitle = stringResource(R.string.auto_lock_sub),
                    help = stringResource(R.string.auto_lock_help),
                    icon = Icons.Filled.Lock,
                )
                if (s.autoLock) {
                    Choice(
                        stringResource(R.string.auto_lock_distance),
                        listOf(
                            AutoLockRange.NEAR to stringResource(R.string.range_near),
                            AutoLockRange.MEDIUM to stringResource(R.string.range_medium),
                            AutoLockRange.FAR to stringResource(R.string.range_far),
                        ),
                        s.autoLockRange,
                    ) { scope.launch { c.settingsStore.setAutoLockRange(it) } }
                }
            }

            Section(stringResource(R.string.section_location)) {
                SwitchRow(
                    stringResource(R.string.location_features), s.locationEnabled, ::setLocation,
                    subtitle = stringResource(if (s.locationEnabled) R.string.location_on_sub else R.string.location_off_sub),
                    help = stringResource(R.string.location_help),
                    icon = Icons.Filled.LocationOn,
                )
                Divider()
                SwitchRow(
                    stringResource(R.string.auto_record), s.autoRecord, { scope.launch { c.settingsStore.setAutoRecord(it) } },
                    subtitle = stringResource(if (s.locationEnabled) R.string.auto_record_sub else R.string.auto_record_sub_no_location),
                )
                Divider()
                Choice(
                    stringResource(R.string.idle_stop),
                    listOf(3 to "3 min", 5 to "5 min", 10 to "10 min", 15 to "15 min"),
                    s.idleStopMinutes,
                ) { scope.launch { c.settingsStore.setIdleStopMinutes(it) } }
                if (s.locationEnabled) {
                    Divider()
                    Choice(stringResource(R.string.gps_interval), listOf(1 to "1 s", 2 to "2 s", 5 to "5 s", 10 to "10 s"), s.gpsIntervalSec) {
                        scope.launch { c.settingsStore.setGpsInterval(it) }
                    }
                    Divider()
                    SwitchRow(
                        stringResource(R.string.park_on_lock), s.parkOnLock, { scope.launch { c.settingsStore.setParkOnLock(it) } },
                        subtitle = stringResource(R.string.park_on_lock_sub),
                    )
                    Divider()
                    SwitchRow(
                        stringResource(R.string.network_location), s.networkLocation, { scope.launch { c.settingsStore.setNetworkLocation(it) } },
                        subtitle = stringResource(R.string.network_location_sub),
                        help = stringResource(R.string.network_location_help),
                    )
                }
            }

            Section(stringResource(R.string.section_look)) {
                Choice(
                    stringResource(R.string.units),
                    listOf(Units.METRIC to "km, km/h", Units.IMPERIAL to "mi, mph"),
                    s.units,
                    subtitle = stringResource(R.string.units_sub),
                ) { c.setUnits(it) }
                Divider()
                Choice(
                    stringResource(R.string.theme),
                    listOf(
                        ThemeMode.SYSTEM to stringResource(R.string.theme_system),
                        ThemeMode.LIGHT to stringResource(R.string.theme_light),
                        ThemeMode.DARK to stringResource(R.string.theme_dark),
                    ),
                    s.theme,
                ) { scope.launch { c.settingsStore.setTheme(it) } }
                if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
                    Divider()
                    SwitchRow(
                        stringResource(R.string.dynamic_color), s.dynamicColor, { scope.launch { c.settingsStore.setDynamicColor(it) } },
                        subtitle = stringResource(R.string.dynamic_color_sub),
                    )
                }
                Divider()
                SwitchRow(stringResource(R.string.dark_map), s.darkMap, { scope.launch { c.settingsStore.setDarkMap(it) } })
            }

            Section(stringResource(R.string.section_maps)) {
                ListItem(
                    headlineContent = { Text(stringResource(R.string.maps_hint)) },
                    trailingContent = { HelpButton(stringResource(R.string.section_maps), stringResource(R.string.maps_help)) },
                    colors = ListItemDefaults.colors(containerColor = Color.Transparent),
                )
                maps.forEach { f ->
                    ListItem(
                        headlineContent = { Text(f.name) },
                        supportingContent = { Text("${f.length() / (1024 * 1024)} MB") },
                        leadingContent = { Icon(Icons.Filled.Map, null) },
                        trailingContent = {
                            IconButton(onClick = { deleteMap = f }) { Icon(Icons.Filled.Delete, stringResource(R.string.delete)) }
                        },
                        colors = ListItemDefaults.colors(containerColor = Color.Transparent),
                    )
                }
                if (importing) LinearProgressIndicator(Modifier.fillMaxWidth().padding(16.dp))
                OutlinedButton(
                    onClick = { importMap.launch(arrayOf("*/*")) },
                    enabled = !importing,
                    modifier = Modifier.padding(16.dp),
                ) { Text(stringResource(R.string.import_map)) }
            }

            Section(stringResource(R.string.section_pairing)) {
                ListItem(
                    headlineContent = { Text(stringResource(R.string.pairing_hint)) },
                    leadingContent = { Icon(Icons.Filled.Key, null) },
                    trailingContent = { HelpButton(stringResource(R.string.section_pairing), stringResource(R.string.pairing_help)) },
                    colors = ListItemDefaults.colors(containerColor = Color.Transparent),
                )
                Row(Modifier.padding(start = 16.dp, end = 16.dp, bottom = 16.dp), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    OutlinedButton(onClick = { warnExport = true }) { Text(stringResource(R.string.pairing_export)) }
                    OutlinedButton(onClick = { importPairing.launch(arrayOf("application/json", "text/plain", "application/octet-stream")) }) {
                        Text(stringResource(R.string.pairing_import))
                    }
                }
            }

            Section(stringResource(R.string.section_data)) {
                ListItem(
                    headlineContent = { Text(stringResource(R.string.data_hint)) },
                    colors = ListItemDefaults.colors(containerColor = Color.Transparent),
                )
                Row(Modifier.padding(start = 16.dp, end = 16.dp, bottom = 16.dp), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    OutlinedButton(onClick = { exportBackup.launch("navee-offline-backup.db") }) { Text(stringResource(R.string.backup_export)) }
                    OutlinedButton(onClick = { pickBackup.launch(arrayOf("*/*")) }) { Text(stringResource(R.string.backup_restore)) }
                }
            }

            Section(stringResource(R.string.about)) {
                ListItem(
                    headlineContent = { Text(stringResource(R.string.about_app)) },
                    supportingContent = { Text(stringResource(R.string.about_sub)) },
                    trailingContent = { Icon(Icons.AutoMirrored.Filled.KeyboardArrowRight, null) },
                    colors = ListItemDefaults.colors(containerColor = Color.Transparent),
                    modifier = Modifier.clickable(onClick = onAbout),
                )
            }
            Spacer(Modifier.height(24.dp))
        }
    }

    deleteMap?.let { f ->
        ConfirmDialog(
            title = stringResource(R.string.delete_map_q, f.name),
            text = null,
            confirm = stringResource(R.string.delete),
            onConfirm = { c.maps.delete(f) },
            onDismiss = { deleteMap = null },
        )
    }
    if (confirmForget) {
        ConfirmDialog(
            title = stringResource(R.string.forget_q),
            text = stringResource(R.string.forget_text),
            confirm = stringResource(R.string.forget),
            onConfirm = {
                c.disconnect()
                scope.launch { c.settingsStore.forgetDevice() }
            },
            onDismiss = { confirmForget = false },
        )
    }
    if (warnExport) {
        ConfirmDialog(
            title = stringResource(R.string.pairing_export_q),
            text = stringResource(R.string.pairing_export_warning),
            confirm = stringResource(R.string.pairing_export_go),
            onConfirm = { exportPairing.launch(PairingFile.FILE_NAME) },
            onDismiss = { warnExport = false },
        )
    }
    pendingImport?.let { p ->
        ConfirmDialog(
            title = stringResource(R.string.pairing_import_q),
            text = stringResource(
                R.string.pairing_import_text,
                listOfNotNull(p.name, p.address).joinToString(" · ").ifEmpty { "–" },
            ),
            confirm = stringResource(R.string.pairing_import_go),
            onConfirm = {
                scope.launch {
                    runCatching { c.importPairing(p) }
                        .onSuccess { toast(R.string.pairing_imported) }
                        .onFailure { toast(R.string.import_failed) }
                }
            },
            onDismiss = { pendingImport = null },
        )
    }
    confirmRestore?.let { uri ->
        ConfirmDialog(
            title = stringResource(R.string.restore_q),
            text = stringResource(R.string.restore_text),
            confirm = stringResource(R.string.backup_restore),
            onConfirm = {
                scope.launch {
                    c.disconnect()
                    runCatching { Backup.import(context, c.db, uri) }
                        .onSuccess {
                            val intent = context.packageManager.getLaunchIntentForPackage(context.packageName)
                            context.startActivity(Intent.makeRestartActivityTask(intent!!.component))
                            exitProcess(0)
                        }
                        .onFailure { toast(it.message ?: resources.getString(R.string.restore_failed)) }
                }
            },
            onDismiss = { confirmRestore = null },
        )
    }
}

/** 🇳🇴 / 🇬🇧 picker. "System" follows the phone's language. AppCompat stores the choice and restarts the screen. */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun LanguagePicker() {
    val current = remember {
        val tags = AppCompatDelegate.getApplicationLocales().toLanguageTags()
        when {
            tags.isEmpty() -> Language.SYSTEM
            tags.startsWith("nb") || tags.startsWith("no") || tags.startsWith("nn") -> Language.NB
            else -> Language.EN
        }
    }
    val options = listOf(
        Language.SYSTEM to stringResource(R.string.language_system),
        Language.NB to "🇳🇴 Norsk",
        Language.EN to "🇬🇧 English",
    )
    SingleChoiceSegmentedButtonRow(Modifier.fillMaxWidth().padding(horizontal = 12.dp, vertical = 8.dp)) {
        options.forEachIndexed { i, (lang, label) ->
            SegmentedButton(
                selected = lang == current,
                onClick = {
                    if (lang != current) AppCompatDelegate.setApplicationLocales(LocaleListCompat.forLanguageTags(lang.tag))
                },
                shape = SegmentedButtonDefaults.itemShape(i, options.size),
            ) { Text(label, maxLines = 1) }
        }
    }
}

@Composable
private fun LinkRow(title: String, icon: ImageVector, onClick: () -> Unit) {
    ListItem(
        headlineContent = { Text(title) },
        leadingContent = { Icon(icon, null) },
        trailingContent = { Icon(Icons.AutoMirrored.Filled.KeyboardArrowRight, null) },
        colors = ListItemDefaults.colors(containerColor = Color.Transparent),
        modifier = Modifier.clickable(onClick = onClick),
    )
}

@Composable
private fun ConfirmDialog(title: String, text: String?, confirm: String, onConfirm: () -> Unit, onDismiss: () -> Unit) {
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(title) },
        text = text?.let { { Text(it) } },
        confirmButton = { TextButton(onClick = { onDismiss(); onConfirm() }) { Text(confirm) } },
        dismissButton = { TextButton(onClick = onDismiss) { Text(stringResource(R.string.cancel)) } },
    )
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun <T> Choice(
    title: String,
    options: List<Pair<T, String>>,
    selected: T,
    enabled: Boolean = true,
    subtitle: String? = null,
    onSelect: (T) -> Unit,
) {
    ListItem(
        headlineContent = { Text(title) },
        supportingContent = {
            Column {
                subtitle?.let { Text(it) }
                SingleChoiceSegmentedButtonRow(Modifier.fillMaxWidth().padding(top = 8.dp)) {
                    options.forEachIndexed { i, (v, label) ->
                        SegmentedButton(
                            selected = v == selected,
                            onClick = { onSelect(v) },
                            shape = SegmentedButtonDefaults.itemShape(i, options.size),
                            enabled = enabled,
                        ) { Text(label, maxLines = 1) }
                    }
                }
            }
        },
        colors = ListItemDefaults.colors(containerColor = Color.Transparent),
    )
}
