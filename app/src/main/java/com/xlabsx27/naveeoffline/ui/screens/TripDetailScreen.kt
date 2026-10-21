package com.xlabsx27.naveeoffline.ui.screens

import android.widget.Toast
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.Share
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Card
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
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
import androidx.compose.ui.graphics.toArgb
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalResources
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.xlabsx27.naveeoffline.AppContainer
import com.xlabsx27.naveeoffline.R
import com.xlabsx27.naveeoffline.export.GpxWriter
import com.xlabsx27.naveeoffline.map.MapPath
import com.xlabsx27.naveeoffline.map.OfflineMap
import com.xlabsx27.naveeoffline.service.LatLon
import com.xlabsx27.naveeoffline.ui.Format
import com.xlabsx27.naveeoffline.ui.components.BackButton
import com.xlabsx27.naveeoffline.ui.components.LineChart
import com.xlabsx27.naveeoffline.ui.components.StatTile
import com.xlabsx27.naveeoffline.ui.components.TileRow
import com.xlabsx27.naveeoffline.ui.theme.StatusColors
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.time.Instant
import java.time.ZoneId
import java.time.format.DateTimeFormatter

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun TripDetailScreen(c: AppContainer, tripId: Long, onBack: () -> Unit) {
    val context = LocalContext.current
    val res = LocalResources.current
    val scope = rememberCoroutineScope()
    val trip by c.db.trips().observe(tripId).collectAsStateWithLifecycle(initialValue = null)
    val points by c.db.trips().observePoints(tripId).collectAsStateWithLifecycle(initialValue = emptyList())
    val settings by c.settings.collectAsStateWithLifecycle()
    val files by c.maps.files.collectAsStateWithLifecycle()
    val units = settings.units
    var confirmDelete by remember { mutableStateOf(false) }

    val exportGpx = rememberLauncherForActivityResult(ActivityResultContracts.CreateDocument("application/gpx+xml")) { uri ->
        val t = trip ?: return@rememberLauncherForActivityResult
        if (uri == null) return@rememberLauncherForActivityResult
        scope.launch {
            val result = withContext(Dispatchers.IO) {
                runCatching {
                    context.contentResolver.openOutputStream(uri, "wt").use { out ->
                        GpxWriter.write(t, c.db.trips().points(t.id), requireNotNull(out))
                    }
                }
            }
            Toast.makeText(context, if (result.isSuccess) R.string.gpx_saved else R.string.export_failed, Toast.LENGTH_SHORT).show()
        }
    }

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text(trip?.let { Format.dateTime(it.startedAt) } ?: stringResource(R.string.trip)) },
                navigationIcon = { BackButton(onBack) },
                actions = {
                    IconButton(onClick = {
                        val start = trip?.startedAt ?: return@IconButton
                        val stamp = DateTimeFormatter.ofPattern("yyyy-MM-dd_HHmm")
                            .format(Instant.ofEpochMilli(start).atZone(ZoneId.systemDefault()))
                        exportGpx.launch("trip_$stamp.gpx")
                    }) { Icon(Icons.Filled.Share, stringResource(R.string.export_gpx)) }
                    // A trip still being recorded cannot be deleted: the recorder is writing to it.
                    if (trip?.endedAt != null) {
                        IconButton(onClick = { confirmDelete = true }) { Icon(Icons.Filled.Delete, stringResource(R.string.delete)) }
                    }
                },
            )
        },
    ) { padding ->
        val t = trip ?: return@Scaffold
        Column(Modifier.padding(padding).verticalScroll(rememberScrollState())) {
            val path = remember(points.size) { points.map { LatLon(it.lat, it.lon) } }
            val color = MaterialTheme.colorScheme.primary.toArgb()
            Card(Modifier.fillMaxWidth().height(300.dp).padding(horizontal = 12.dp)) {
                OfflineMap(
                    mapFiles = files,
                    modifier = Modifier.fillMaxWidth().height(300.dp),
                    paths = listOf(MapPath(path, color)),
                    // Fit once the track has loaded, and again when a trip still recording finishes.
                    fitKey = if (path.isEmpty()) null else tripId to (t.endedAt != null),
                    dark = settings.darkMap,
                    cacheId = "trip",
                    metric = units == com.xlabsx27.naveeoffline.data.Units.METRIC,
                )
            }
            Column(Modifier.padding(12.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                TileRow {
                    StatTile(stringResource(R.string.distance), Format.distance(t.distanceM, units), Modifier.weight(1f))
                    StatTile(stringResource(R.string.duration), Format.duration(res, t.durationMs), Modifier.weight(1f))
                }
                TileRow {
                    StatTile(stringResource(R.string.avg_speed), Format.speed(t.avgSpeedKmh, units), Modifier.weight(1f))
                    StatTile(stringResource(R.string.max_speed), Format.speed(t.maxSpeedKmh, units), Modifier.weight(1f))
                }
                TileRow {
                    StatTile(stringResource(R.string.energy), "${Format.num(t.energyWh, 1)} Wh", Modifier.weight(1f))
                    StatTile(stringResource(R.string.consumption), Format.whPerDistance(t.whPerKm, units), Modifier.weight(1f))
                }
                TileRow {
                    StatTile(stringResource(R.string.battery_used), t.socUsed?.let { "$it %" } ?: "–", Modifier.weight(1f))
                    StatTile(stringResource(R.string.points), points.size.toString(), Modifier.weight(1f))
                }
            }
            val xs = points.map { it.time }
            LineChart(
                stringResource(R.string.speed),
                xs,
                points.map { p -> (p.scooterSpeedKmh ?: p.gpsSpeedKmh)?.let { Format.speedValue(it.toDouble(), units) } },
                MaterialTheme.colorScheme.primary,
                label = { "${Format.num(it, 0)} ${Format.speedUnit(units)}" },
            )
            LineChart(stringResource(R.string.battery_chart), xs, points.map { it.soc?.toDouble() }, StatusColors.ok, label = { "${Format.num(it, 0)} %" })
            LineChart(stringResource(R.string.altitude), xs, points.map { it.altitude }, MaterialTheme.colorScheme.tertiary, label = { "${Format.num(it, 0)} m" })
            Spacer(Modifier.height(24.dp))
        }
    }

    if (confirmDelete) {
        AlertDialog(
            onDismissRequest = { confirmDelete = false },
            title = { Text(stringResource(R.string.delete_trip_q)) },
            text = { Text(stringResource(R.string.delete_trip_text)) },
            confirmButton = {
                TextButton(onClick = {
                    confirmDelete = false
                    scope.launch {
                        c.db.trips().delete(tripId)
                        onBack()
                    }
                }) { Text(stringResource(R.string.delete)) }
            },
            dismissButton = { TextButton(onClick = { confirmDelete = false }) { Text(stringResource(R.string.cancel)) } },
        )
    }
}
