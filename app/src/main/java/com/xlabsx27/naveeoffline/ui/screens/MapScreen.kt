package com.xlabsx27.naveeoffline.ui.screens

import android.location.Location
import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.background
import androidx.compose.foundation.combinedClickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.KeyboardArrowLeft
import androidx.compose.material.icons.automirrored.filled.KeyboardArrowRight
import androidx.compose.material.icons.filled.FiberManualRecord
import androidx.compose.material.icons.filled.LocalParking
import androidx.compose.material.icons.filled.MyLocation
import androidx.compose.material.icons.filled.Navigation
import androidx.compose.material.icons.filled.SettingsInputAntenna
import androidx.compose.material.icons.filled.ZoomOutMap
import androidx.compose.material3.AssistChip
import androidx.compose.material3.AssistChipDefaults
import androidx.compose.material3.Card
import androidx.compose.material3.DatePicker
import androidx.compose.material3.DatePickerDialog
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilterChip
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.ListItem
import androidx.compose.material3.ListItemDefaults
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.SelectableDates
import androidx.compose.material3.SmallFloatingActionButton
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.rememberDatePickerState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableLongStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.rotate
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalResources
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.xlabsx27.naveeoffline.AppContainer
import com.xlabsx27.naveeoffline.R
import com.xlabsx27.naveeoffline.ble.LinkState
import com.xlabsx27.naveeoffline.data.ParkReason
import com.xlabsx27.naveeoffline.data.ParkedPosition
import com.xlabsx27.naveeoffline.data.TrackPoint
import com.xlabsx27.naveeoffline.data.Trip
import com.xlabsx27.naveeoffline.data.Units
import com.xlabsx27.naveeoffline.location.isGps
import com.xlabsx27.naveeoffline.location.rememberHeading
import com.xlabsx27.naveeoffline.map.MapPath
import com.xlabsx27.naveeoffline.map.MyPosition
import com.xlabsx27.naveeoffline.map.OfflineMap
import com.xlabsx27.naveeoffline.service.LatLon
import com.xlabsx27.naveeoffline.ui.Format
import com.xlabsx27.naveeoffline.ui.Permissions
import kotlinx.coroutines.flow.catch
import kotlinx.coroutines.flow.filter
import java.time.Instant
import java.time.LocalDate
import java.time.ZoneId
import java.time.ZoneOffset

/** One colour per trip of the day, in order. Picked to stay apart on light and dark maps. */
private val TRIP_COLORS = intArrayOf(
    0xFF1E88E5.toInt(), 0xFFF4511E.toInt(), 0xFF43A047.toInt(), 0xFF8E24AA.toInt(),
    0xFF00897B.toInt(), 0xFFD81B60.toInt(), 0xFFFFB300.toInt(),
)
private const val LIVE_COLOR = 0xFFE53935.toInt()
/** Longer tracks are thinned out for drawing; the stored track keeps every point. */
private const val MAX_DRAWN_POINTS = 1500

private fun tripColor(index: Int) = TRIP_COLORS[index % TRIP_COLORS.size]

/**
 * Timeline: every trip of one day on the map (one colour each) with a list below, like a ride
 * diary. With location features on it also shows where you are and where the scooter is parked.
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun MapScreen(c: AppContainer, onOpenSettings: () -> Unit, onOpenTrip: (Long) -> Unit) {
    val context = LocalContext.current
    val dao = c.db.trips()
    val zone = ZoneId.systemDefault()
    val today = LocalDate.now(zone)
    val settings by c.settings.collectAsStateWithLifecycle()
    val files by c.maps.files.collectAsStateWithLifecycle()
    val livePath by c.recorder.livePath.collectAsStateWithLifecycle()
    val active by c.recorder.active.collectAsStateWithLifecycle()
    val parked by c.parked.collectAsStateWithLifecycle()
    val status by c.connection.status.collectAsStateWithLifecycle()
    val locationOn = settings.locationEnabled

    var dayEpoch by rememberSaveable { mutableLongStateOf(today.toEpochDay()) }
    val day = LocalDate.ofEpochDay(dayEpoch)
    var all by rememberSaveable { mutableStateOf(false) }
    var selected by rememberSaveable { mutableStateOf<Long?>(null) }
    var fitTick by remember { mutableIntStateOf(0) }
    var pickDate by remember { mutableStateOf(false) }
    // null = automatic: follow my position only when the day has no tracks to show.
    var follow by rememberSaveable { mutableStateOf<Boolean?>(null) }

    val tripDays by remember { dao.observeDays() }.collectAsStateWithLifecycle(initialValue = emptyList())
    val days = remember(tripDays) { tripDays.mapNotNull { runCatching { LocalDate.parse(it) }.getOrNull() }.toSortedSet() }

    val trips by remember(all, dayEpoch) {
        if (all) {
            dao.observeAll()
        } else {
            dao.observeBetween(
                day.atStartOfDay(zone).toInstant().toEpochMilli(),
                day.plusDays(1).atStartOfDay(zone).toInstant().toEpochMilli(),
            )
        }
    }.collectAsStateWithLifecycle(initialValue = emptyList())
    // The trip being recorded is drawn from the live path instead of the database.
    val doneIds = trips.filter { it.endedAt != null }.map { it.id }
    val points by remember(all, doneIds) {
        if (all) dao.observeAllPoints() else dao.observePointsForTrips(doneIds)
    }.collectAsStateWithLifecycle(initialValue = emptyList())

    val colors = remember(trips) { trips.sortedBy { it.startedAt }.withIndex().associate { (i, t) -> t.id to tripColor(i) } }
    val tracks = remember(points) { points.groupBy { it.tripId }.mapValues { (_, p) -> thin(p) } }
    val showLive = locationOn && (all || day == today) && livePath.isNotEmpty()
    val paths = remember(tracks, colors, selected, livePath, showLive) {
        buildList {
            // The selected trip goes last so it is drawn on top.
            tracks.entries.sortedBy { it.key == selected }.forEach { (id, pts) ->
                add(MapPath(pts, colors[id] ?: tripColor(0), emphasis = id == selected))
            }
            if (showLive) add(MapPath(livePath, LIVE_COLOR, emphasis = active?.id == selected))
        }
    }
    val fitPoints = selected?.let { id -> tracks[id] ?: livePath.takeIf { active?.id == id } }

    // Collecting the live flow keeps GPS running while the map is visible (only with location on).
    val live by c.gps.fixes.collectAsStateWithLifecycle(initialValue = null)
    val last by c.gps.lastFix.collectAsStateWithLifecycle()
    val fix = if (locationOn) live ?: last else null

    Column(Modifier.fillMaxSize()) {
        DayBar(
            label = if (all) stringResource(R.string.all_trips) else dayLabel(day, today),
            all = all,
            onAll = { all = !all; selected = null; follow = null },
            canBack = !all && days.any { it < day },
            canForward = !all && day < today,
            onBack = { days.lastOrNull { it < day }?.let { dayEpoch = it.toEpochDay(); selected = null; follow = null } },
            onForward = {
                val next = days.firstOrNull { it > day }?.takeIf { it <= today } ?: today
                dayEpoch = next.toEpochDay()
                selected = null
                follow = null
            },
            onPick = { pickDate = true },
        )

        Box(Modifier.weight(1f).fillMaxWidth()) {
            OfflineMap(
                mapFiles = files,
                modifier = Modifier.fillMaxSize(),
                paths = paths,
                me = fix?.let { MyPosition(it.latitude, it.longitude, if (it.hasAccuracy()) it.accuracy else null) },
                parked = if (locationOn) parked?.let { LatLon(it.lat, it.lon) } else null,
                follow = follow ?: paths.isEmpty(),
                fitKey = if (paths.isEmpty()) null else listOf(dayEpoch, all, selected, fitTick),
                fitPoints = fitPoints,
                dark = settings.darkMap,
                cacheId = "live",
                metric = settings.units == Units.METRIC,
                onUserPan = { follow = false },
            )

            Column(Modifier.padding(12.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
                if (files.isEmpty()) {
                    MapChip(onClick = onOpenSettings, label = { Text(stringResource(R.string.map_no_file)) })
                }
                when {
                    !locationOn -> MapChip(onClick = onOpenSettings, label = { Text(stringResource(R.string.map_location_off)) })
                    !Permissions.locationGranted(context) ->
                        MapChip(onClick = onOpenSettings, label = { Text(stringResource(R.string.map_need_permission)) })
                    fix == null -> MapChip(onClick = {}, label = {
                        Text(stringResource(if (c.gps.isGpsEnabled) R.string.map_waiting_gps else R.string.map_gps_off))
                    })
                    !fix.isGps || System.currentTimeMillis() - fix.time > 60_000 -> MapChip(onClick = {}, label = {
                        Text(
                            stringResource(
                                if (fix.isGps) R.string.map_last_fix else R.string.map_approximate,
                                fix.accuracy.toInt(),
                                Format.ago(LocalResources.current, fix.time),
                            ),
                        )
                    })
                }
            }

            Column(
                Modifier.align(Alignment.BottomEnd).padding(12.dp),
                horizontalAlignment = Alignment.End,
                verticalArrangement = Arrangement.spacedBy(10.dp),
            ) {
                if (paths.isNotEmpty()) {
                    SmallFloatingActionButton(onClick = { selected = null; follow = false; fitTick++ }) {
                        Icon(Icons.Filled.ZoomOutMap, stringResource(R.string.fit_day))
                    }
                }
                if (locationOn) {
                    SmallFloatingActionButton(onClick = { follow = true }) {
                        Icon(Icons.Filled.MyLocation, stringResource(R.string.my_position))
                    }
                }
            }

            if (locationOn && !all && day == today) {
                ParkedCard(
                    c, parked, fix, settings.units,
                    scooterNearby = status.state == LinkState.CONNECTED,
                    lastAddress = settings.lastAddress,
                    modifier = Modifier.align(Alignment.BottomStart).padding(12.dp).fillMaxWidth(0.78f),
                )
            }
        }

        TripList(
            trips = if (all) trips else trips.sortedBy { it.startedAt },
            colors = colors,
            liveColor = LIVE_COLOR,
            showDate = all,
            selected = selected,
            units = settings.units,
            onSelect = { selected = if (selected == it) null else it; follow = false },
            onOpen = onOpenTrip,
        )
    }

    if (pickDate) {
        val state = rememberDatePickerState(
            initialSelectedDateMillis = day.atStartOfDay(ZoneOffset.UTC).toInstant().toEpochMilli(),
            selectableDates = object : SelectableDates {
                // The picker works in UTC midnights; only days with trips (and today) can be picked.
                override fun isSelectableDate(utcTimeMillis: Long): Boolean {
                    val d = Instant.ofEpochMilli(utcTimeMillis).atZone(ZoneOffset.UTC).toLocalDate()
                    return d == today || d in days
                }

                override fun isSelectableYear(year: Int) = year == today.year || days.any { it.year == year }
            },
        )
        DatePickerDialog(
            onDismissRequest = { pickDate = false },
            confirmButton = {
                TextButton(onClick = {
                    state.selectedDateMillis?.let {
                        dayEpoch = Instant.ofEpochMilli(it).atZone(ZoneOffset.UTC).toLocalDate().toEpochDay()
                        all = false
                        selected = null
                        follow = null
                    }
                    pickDate = false
                }) { Text(stringResource(R.string.ok)) }
            },
            dismissButton = { TextButton(onClick = { pickDate = false }) { Text(stringResource(R.string.cancel)) } },
        ) { DatePicker(state) }
    }
}

private fun thin(points: List<TrackPoint>): List<LatLon> {
    val step = (points.size + MAX_DRAWN_POINTS - 1) / MAX_DRAWN_POINTS
    return points.filterIndexed { i, _ -> step <= 1 || i % step == 0 || i == points.size - 1 }.map { LatLon(it.lat, it.lon) }
}

@Composable
private fun dayLabel(day: LocalDate, today: LocalDate): String = when (day) {
    today -> stringResource(R.string.today)
    today.minusDays(1) -> stringResource(R.string.yesterday)
    else -> Format.day(LocalResources.current, day)
}

@Composable
private fun DayBar(
    label: String,
    all: Boolean,
    onAll: () -> Unit,
    canBack: Boolean,
    canForward: Boolean,
    onBack: () -> Unit,
    onForward: () -> Unit,
    onPick: () -> Unit,
) {
    Surface(tonalElevation = 2.dp) {
        Row(
            Modifier.fillMaxWidth().statusBarsPadding().padding(horizontal = 4.dp, vertical = 2.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            IconButton(onClick = onBack, enabled = canBack) {
                Icon(Icons.AutoMirrored.Filled.KeyboardArrowLeft, stringResource(R.string.previous_day))
            }
            TextButton(onClick = onPick, modifier = Modifier.weight(1f)) {
                Text(label, style = MaterialTheme.typography.titleMedium)
            }
            IconButton(onClick = onForward, enabled = canForward) {
                Icon(Icons.AutoMirrored.Filled.KeyboardArrowRight, stringResource(R.string.next_day))
            }
            FilterChip(selected = all, onClick = onAll, label = { Text(stringResource(R.string.all)) }, modifier = Modifier.padding(end = 8.dp))
        }
    }
}

@OptIn(ExperimentalFoundationApi::class)
@Composable
private fun TripList(
    trips: List<Trip>,
    colors: Map<Long, Int>,
    liveColor: Int,
    showDate: Boolean,
    selected: Long?,
    units: Units,
    onSelect: (Long) -> Unit,
    onOpen: (Long) -> Unit,
) {
    val res = LocalResources.current
    Surface(tonalElevation = 1.dp) {
        if (trips.isEmpty()) {
            Text(
                stringResource(R.string.no_trips_day),
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier.fillMaxWidth().padding(20.dp),
            )
            return@Surface
        }
        LazyColumn(Modifier.heightIn(max = 220.dp), contentPadding = PaddingValues(vertical = 4.dp)) {
            item {
                val done = trips.filter { it.endedAt != null }
                Text(
                    stringResource(
                        R.string.day_summary,
                        res.getQuantityString(R.plurals.trips, trips.size, trips.size),
                        Format.distance(done.sumOf { it.distanceM }, units),
                        Format.duration(res, done.sumOf { it.durationMs }),
                    ),
                    style = MaterialTheme.typography.labelLarge,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier.padding(horizontal = 16.dp, vertical = 6.dp),
                )
            }
            items(trips, key = { it.id }) { trip ->
                val recording = trip.endedAt == null
                val color = Color(if (recording) liveColor else colors[trip.id] ?: tripColor(0))
                ListItem(
                    headlineContent = {
                        Text(
                            buildString {
                                if (showDate) append(Format.day(res, Instant.ofEpochMilli(trip.startedAt).atZone(ZoneId.systemDefault()).toLocalDate())).append(" · ")
                                append(Format.time(trip.startedAt)).append("–")
                                trip.endedAt?.let { append(Format.time(it)) }
                            },
                            fontWeight = if (trip.id == selected) FontWeight.Bold else null,
                        )
                    },
                    supportingContent = {
                        Text(
                            if (recording) {
                                stringResource(R.string.recording)
                            } else {
                                "${Format.distance(trip.distanceM, units)} · ${Format.duration(res, trip.durationMs)}"
                            },
                        )
                    },
                    leadingContent = { Box(Modifier.size(14.dp).background(color, CircleShape)) },
                    trailingContent = {
                        IconButton(onClick = { onOpen(trip.id) }) {
                            Icon(Icons.AutoMirrored.Filled.KeyboardArrowRight, stringResource(R.string.open_trip))
                        }
                    },
                    colors = ListItemDefaults.colors(
                        containerColor = if (trip.id == selected) MaterialTheme.colorScheme.secondaryContainer else Color.Transparent,
                    ),
                    modifier = Modifier.combinedClickable(onClick = { onSelect(trip.id) }, onLongClick = { onOpen(trip.id) }),
                )
                HorizontalDivider(Modifier.padding(start = 56.dp))
            }
        }
    }
}

@Composable
private fun ParkedCard(
    c: AppContainer,
    parked: ParkedPosition?,
    fix: Location?,
    units: Units,
    scooterNearby: Boolean,
    lastAddress: String?,
    modifier: Modifier = Modifier,
) {
    val res = LocalResources.current
    val heading by rememberHeading()
    var searching by remember { mutableStateOf(false) }
    var rssi by remember { mutableStateOf<Int?>(null) }

    if (searching && lastAddress != null) {
        LaunchedEffect(lastAddress) {
            c.scanner.scan()
                .catch { searching = false }
                .filter { it.address == lastAddress }
                .collect { rssi = it.rssi }
        }
    }

    Card(modifier) {
        Column(Modifier.padding(12.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
            if (parked == null) {
                Text(stringResource(R.string.parked_none), style = MaterialTheme.typography.titleSmall)
            } else {
                val target = Location("parked").apply { latitude = parked.lat; longitude = parked.lon }
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Icon(Icons.Filled.LocalParking, null, tint = MaterialTheme.colorScheme.primary)
                    Spacer(Modifier.size(8.dp))
                    Column(Modifier.weight(1f)) {
                        val ago = Format.ago(res, parked.time)
                        Text(
                            when (parked.reason) {
                                ParkReason.LOCKED -> stringResource(R.string.parked_locked, ago)
                                ParkReason.OUT_OF_RANGE -> stringResource(R.string.parked_last_seen, ago)
                                else -> stringResource(R.string.parked_here, ago)
                            },
                            style = MaterialTheme.typography.titleSmall,
                        )
                        Text(
                            buildString {
                                append(Format.time(parked.time))
                                parked.accuracyM?.let { append(" · ±${it.toInt()} m") }
                                parked.soc?.let { append(" · $it %") }
                            },
                            style = MaterialTheme.typography.bodySmall,
                        )
                    }
                    if (fix != null) {
                        Column(horizontalAlignment = Alignment.CenterHorizontally) {
                            // Arrow relative to where the phone points; north-up if there is no compass.
                            Icon(
                                Icons.Filled.Navigation,
                                stringResource(R.string.direction),
                                Modifier.size(28.dp).rotate(fix.bearingTo(target) - (heading ?: 0f)),
                                tint = MaterialTheme.colorScheme.primary,
                            )
                            Text(Format.distance(fix.distanceTo(target).toDouble(), units), style = MaterialTheme.typography.labelMedium)
                        }
                    }
                }
            }
            if (searching) {
                val strength = rssi?.let { ((it + 100) / 60f).coerceIn(0f, 1f) } ?: 0f
                Text(
                    rssi?.let {
                        stringResource(
                            R.string.signal_strength, it,
                            stringResource(if (strength > 0.66f) R.string.very_close else if (strength > 0.33f) R.string.near else R.string.far),
                        )
                    } ?: stringResource(R.string.listening),
                    style = MaterialTheme.typography.bodySmall,
                )
                LinearProgressIndicator(progress = { strength }, modifier = Modifier.fillMaxWidth())
            }
            Row {
                TextButton(onClick = { c.markParked(ParkReason.MANUAL) }) { Text(stringResource(R.string.parked_here_button)) }
                if (lastAddress != null && !scooterNearby) {
                    TextButton(onClick = { searching = !searching; rssi = null }) {
                        Icon(Icons.Filled.SettingsInputAntenna, null, Modifier.size(16.dp))
                        Spacer(Modifier.size(4.dp))
                        Text(stringResource(if (searching) R.string.stop else R.string.find_by_signal))
                    }
                }
            }
        }
    }
}

/** Chip with a solid surface so its text stays readable on top of the map. */
@Composable
private fun MapChip(onClick: () -> Unit, label: @Composable () -> Unit) {
    AssistChip(
        onClick = onClick,
        label = label,
        colors = AssistChipDefaults.assistChipColors(containerColor = MaterialTheme.colorScheme.surfaceContainerHigh),
        border = null,
        elevation = AssistChipDefaults.assistChipElevation(elevation = 3.dp),
    )
}
