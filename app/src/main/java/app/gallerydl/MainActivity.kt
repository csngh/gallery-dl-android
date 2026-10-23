package app.gallerydl

import android.Manifest
import android.content.pm.PackageManager
import android.graphics.Color
import android.os.Build
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.compose.setContent
import androidx.activity.SystemBarStyle
import androidx.activity.enableEdgeToEdge
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.padding
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Download
import androidx.compose.material.icons.filled.Insights
import androidx.compose.material.icons.filled.PhotoLibrary
import androidx.compose.material3.Icon
import androidx.compose.material3.NavigationBar
import androidx.compose.material3.NavigationBarItem
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.res.stringResource
import androidx.core.content.ContextCompat
import androidx.core.splashscreen.SplashScreen.Companion.installSplashScreen
import androidx.compose.ui.platform.LocalContext
import androidx.navigation.NavDestination.Companion.hierarchy
import androidx.navigation.NavGraph.Companion.findStartDestination
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.compose.currentBackStackEntryAsState
import androidx.navigation.compose.rememberNavController
import app.gallerydl.ui.activity.ActivityScreen
import app.gallerydl.ui.library.LibraryScreen
import app.gallerydl.ui.queue.QueueScreen
import app.gallerydl.ui.theme.GalleryDlTheme
import androidx.compose.material3.NavigationBarItemDefaults
import app.gallerydl.ui.theme.Tone
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.WindowInsetsSides
import androidx.compose.foundation.layout.displayCutout
import androidx.compose.foundation.layout.only
import androidx.compose.foundation.layout.windowInsetsPadding
import androidx.compose.material3.NavigationRail
import androidx.compose.material3.NavigationRailItem
import androidx.compose.material3.NavigationRailItemDefaults
import androidx.compose.material3.MaterialTheme
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.unit.dp
import app.gallerydl.ui.components.RailMinWidth
import androidx.compose.material.icons.filled.Home
import app.gallerydl.ui.home.HomeScreen
import app.gallerydl.ui.settings.SettingsScreen
import androidx.compose.animation.EnterTransition
import androidx.compose.animation.ExitTransition
import androidx.compose.foundation.layout.Box
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import app.gallerydl.ui.components.SplashOverlay

private const val SETTINGS_ROUTE = "settings"

private enum class Destination(val route: String, val label: Int, val icon: ImageVector, val tone: Tone) {
    Home("home", R.string.nav_home, Icons.Filled.Home, Tone.Pink),
    Queue("queue", R.string.nav_queue, Icons.Filled.Download, Tone.Blue),
    Library("library", R.string.nav_library, Icons.Filled.PhotoLibrary, Tone.Violet),
    Activity("activity", R.string.nav_activity, Icons.Filled.Insights, Tone.Teal),
}

class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        // The system splash shows the still logo and disappears with the first frame; the animated intro
        // (logo, name and sound) then runs inside the app, over the same position.
        installSplashScreen()
        enableEdgeToEdge(
            statusBarStyle = SystemBarStyle.dark(Color.TRANSPARENT),
            navigationBarStyle = SystemBarStyle.dark(Color.TRANSPARENT),
        )
        super.onCreate(savedInstanceState)
        val container = (application as GalleryDlApp).container
        setContent {
            GalleryDlTheme {
                // Only on a fresh start: rotating the phone or coming back to the app does not replay it.
                var showSplash by rememberSaveable { mutableStateOf(true) }
                val startupSound by container.startupSound.collectAsStateWithLifecycle()
                Box(Modifier.fillMaxSize()) {
                    AppRoot()
                    if (showSplash) SplashOverlay(playSound = startupSound, onFinished = { showSplash = false })
                }
            }
        }
    }

}

@Composable
private fun AppRoot() {
    RequestNotificationPermission()

    val navController = rememberNavController()
    val backStack by navController.currentBackStackEntryAsState()
    val currentDestination = backStack?.destination

    val wide = LocalConfiguration.current.screenWidthDp.dp >= RailMinWidth
    // Settings is a full screen of its own, so the navigation bar and rail step aside.
    val onSettings = currentDestination?.route == SETTINGS_ROUTE
    val navigateTo: (Destination) -> Unit = { destination ->
        navController.navigate(destination.route) {
            popUpTo(navController.graph.findStartDestination().id) { saveState = true }
            launchSingleTop = true
            restoreState = true
        }
    }
    val isSelected: (Destination) -> Boolean =
        { destination -> currentDestination?.hierarchy?.any { it.route == destination.route } == true }

    Scaffold(
        // Each screen draws its own top bar, which handles the status-bar inset.
        contentWindowInsets = WindowInsets(0),
        bottomBar = {
            if (!wide && !onSettings) {
                NavigationBar {
                    Destination.entries.forEach { destination ->
                        NavigationBarItem(
                            selected = isSelected(destination),
                            onClick = { navigateTo(destination) },
                            icon = { Icon(destination.icon, contentDescription = null) },
                            colors = NavigationBarItemDefaults.colors(
                                selectedIconColor = destination.tone.icon,
                                unselectedIconColor = destination.tone.icon.copy(alpha = 0.75f),
                                indicatorColor = destination.tone.tint,
                            ),
                            label = { Text(stringResource(destination.label)) },
                        )
                    }
                }
            }
        },
    ) { padding ->
        // In landscape a camera cutout can sit at the side, so keep everything clear of it.
        Row(
            Modifier
                .padding(padding)
                .fillMaxSize()
                .windowInsetsPadding(WindowInsets.displayCutout.only(WindowInsetsSides.Horizontal)),
        ) {
            if (wide && !onSettings) {
                NavigationRail(containerColor = MaterialTheme.colorScheme.surfaceContainer) {
                    Destination.entries.forEach { destination ->
                        NavigationRailItem(
                            selected = isSelected(destination),
                            onClick = { navigateTo(destination) },
                            icon = { Icon(destination.icon, contentDescription = null) },
                            colors = NavigationRailItemDefaults.colors(
                                selectedIconColor = destination.tone.icon,
                                unselectedIconColor = destination.tone.icon.copy(alpha = 0.75f),
                                indicatorColor = destination.tone.tint,
                            ),
                            label = { Text(stringResource(destination.label)) },
                        )
                    }
                }
            }
            NavHost(
                navController = navController,
                startDestination = Destination.Home.route,
                modifier = Modifier.weight(1f),
                // Switching tabs and opening Settings is instant: no fade or slide between screens.
                enterTransition = { EnterTransition.None },
                exitTransition = { ExitTransition.None },
                popEnterTransition = { EnterTransition.None },
                popExitTransition = { ExitTransition.None },
            ) {
                composable(Destination.Home.route) { HomeScreen(onOpenSettings = { navController.navigate(SETTINGS_ROUTE) }) }
                composable(Destination.Queue.route) { QueueScreen() }
                composable(Destination.Library.route) { LibraryScreen() }
                composable(Destination.Activity.route) { ActivityScreen() }
                composable(SETTINGS_ROUTE) { SettingsScreen(onBack = { navController.popBackStack() }) }
            }
        }
    }
}

/** The foreground-service notification is only visible on Android 13+ once this is granted. */
@Composable
private fun RequestNotificationPermission() {
    if (Build.VERSION.SDK_INT < Build.VERSION_CODES.TIRAMISU) return
    val context = LocalContext.current
    val launcher = rememberLauncherForActivityResult(ActivityResultContracts.RequestPermission()) {}
    LaunchedEffect(Unit) {
        val granted = ContextCompat.checkSelfPermission(context, Manifest.permission.POST_NOTIFICATIONS) ==
            PackageManager.PERMISSION_GRANTED
        if (!granted) launcher.launch(Manifest.permission.POST_NOTIFICATIONS)
    }
}
