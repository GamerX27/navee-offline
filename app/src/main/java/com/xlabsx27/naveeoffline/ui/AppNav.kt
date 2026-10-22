package com.xlabsx27.naveeoffline.ui

import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.annotation.StringRes
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.consumeWindowInsets
import androidx.compose.foundation.layout.padding
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Map
import androidx.compose.material.icons.filled.Settings
import androidx.compose.material.icons.filled.Speed
import androidx.compose.material.icons.outlined.Map
import androidx.compose.material.icons.outlined.Settings
import androidx.compose.material.icons.outlined.Speed
import androidx.compose.material3.Icon
import androidx.compose.material3.NavigationBar
import androidx.compose.material3.NavigationBarItem
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.navigation.NavGraph.Companion.findStartDestination
import androidx.navigation.NavType
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.compose.currentBackStackEntryAsState
import androidx.navigation.compose.rememberNavController
import androidx.navigation.navArgument
import com.xlabsx27.naveeoffline.AppContainer
import com.xlabsx27.naveeoffline.R
import com.xlabsx27.naveeoffline.ble.LinkState
import com.xlabsx27.naveeoffline.ui.screens.AboutScreen
import com.xlabsx27.naveeoffline.ui.screens.ConnectScreen
import com.xlabsx27.naveeoffline.ui.screens.ControlsScreen
import com.xlabsx27.naveeoffline.ui.screens.DashboardScreen
import com.xlabsx27.naveeoffline.ui.screens.InfoScreen
import com.xlabsx27.naveeoffline.ui.screens.MapScreen
import com.xlabsx27.naveeoffline.ui.screens.SettingsScreen
import com.xlabsx27.naveeoffline.ui.screens.TripDetailScreen
import kotlinx.coroutines.flow.first

private data class Tab(val route: String, @StringRes val label: Int, val icon: ImageVector, val selectedIcon: ImageVector)

private val tabs = listOf(
    Tab("dashboard", R.string.tab_ride, Icons.Outlined.Speed, Icons.Filled.Speed),
    Tab("map", R.string.tab_map, Icons.Outlined.Map, Icons.Filled.Map),
    Tab("settings", R.string.tab_settings, Icons.Outlined.Settings, Icons.Filled.Settings),
)

@Composable
fun AppNav(c: AppContainer) {
    val nav = rememberNavController()
    val context = LocalContext.current
    var permissionTick by remember { mutableIntStateOf(0) }
    val permissionLauncher = rememberLauncherForActivityResult(ActivityResultContracts.RequestMultiplePermissions()) {
        permissionTick++
    }
    // Bluetooth and notifications only. Location is asked for when the user turns on location features.
    val requestPermissions = { permissionLauncher.launch(Permissions.startup) }

    // Ask once on first start; afterwards screens show a card with a button instead of nagging.
    var askedOnce by rememberSaveable { mutableStateOf(false) }
    LaunchedEffect(Unit) {
        if (!askedOnce && Permissions.missing(context, Permissions.startup.toList()).isNotEmpty()) {
            askedOnce = true
            requestPermissions()
        }
    }

    // Reconnect to the remembered scooter when the app opens, unless lock sleep let go of it.
    LaunchedEffect(permissionTick) {
        if (!Permissions.bluetoothGranted(context)) return@LaunchedEffect
        val s = c.settingsStore.settings.first()
        val address = s.lastAddress ?: return@LaunchedEffect
        if (s.autoReconnect && s.sleepingSince == null && c.connection.status.value.state == LinkState.IDLE && c.scanner.isEnabled) {
            c.connect(address, s.lastName)
        }
    }

    val entry by nav.currentBackStackEntryAsState()
    val route = entry?.destination?.route
    fun go(tab: String) = nav.navigate(tab) {
        popUpTo(nav.graph.findStartDestination().id) { saveState = true }
        launchSingleTop = true
        restoreState = true
    }
    Scaffold(
        bottomBar = {
            if (tabs.any { it.route == route }) {
                NavigationBar {
                    tabs.forEach { tab ->
                        val selected = route == tab.route
                        NavigationBarItem(
                            selected = selected,
                            onClick = { go(tab.route) },
                            icon = { Icon(if (selected) tab.selectedIcon else tab.icon, null) },
                            label = { Text(stringResource(tab.label)) },
                        )
                    }
                }
            }
        },
    ) { padding ->
        // Only the bottom bar's space is taken here; each screen's own top bar handles the status bar.
        val bottom = PaddingValues(bottom = padding.calculateBottomPadding())
        NavHost(nav, startDestination = "dashboard", modifier = Modifier.padding(bottom).consumeWindowInsets(bottom)) {
            composable("dashboard") {
                DashboardScreen(
                    c,
                    permissionTick = permissionTick,
                    onRequestPermissions = requestPermissions,
                    onConnect = { nav.navigate("connect") },
                    onInfo = { nav.navigate("info") },
                    onControls = { nav.navigate("controls") },
                )
            }
            composable("map") {
                MapScreen(c, onOpenSettings = { go("settings") }, onOpenTrip = { nav.navigate("trip/$it") })
            }
            composable("settings") {
                SettingsScreen(
                    c,
                    onConnect = { nav.navigate("connect") },
                    onInfo = { nav.navigate("info") },
                    onControls = { nav.navigate("controls") },
                    onAbout = { nav.navigate("about") },
                )
            }
            composable("connect") {
                ConnectScreen(c, permissionTick, requestPermissions, onBack = { nav.popBackStack() })
            }
            composable("controls") {
                ControlsScreen(c, onConnect = { nav.navigate("connect") }, onBack = { nav.popBackStack() })
            }
            composable("info") { InfoScreen(c, onBack = { nav.popBackStack() }) }
            composable("about") { AboutScreen(onBack = { nav.popBackStack() }) }
            composable("trip/{id}", arguments = listOf(navArgument("id") { type = NavType.LongType })) {
                TripDetailScreen(c, it.arguments?.getLong("id") ?: 0L, onBack = { nav.popBackStack() })
            }
        }
    }
}
