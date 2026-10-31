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
import app.gallerydl.ui.components.ViewerState
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
import app.gallerydl.ui.library.LibraryViewModel
import app.gallerydl.ui.AppViewModelProvider
import androidx.lifecycle.ViewModelProvider
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
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
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

/** What the bottom bar and the side rail show. Queue and Activity are reached from the menu on Home instead. */
private val TABS = listOf(Destination.Home, Destination.Library)

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

private const val TAB_FADE_MS = 150

@Composable
private fun AppRoot() {
    RequestNotificationPermission()

    val navController = rememberNavController()
    val backStack by navController.currentBackStackEntryAsState()
    val currentDestination = backStack?.destination

    val wide = LocalConfiguration.current.screenWidthDp.dp >= RailMinWidth
    // Settings is a full screen of its own, so the navigation bar and rail step aside.
    val onSettings = currentDestination?.route == SETTINGS_ROUTE
    // Queue and Activity open from Home's menu and have no tab of their own, so Home stays marked while they are open.
    val isSelected: (Destination) -> Boolean = { destination ->
        val route = currentDestination?.route
        if (destination == Destination.Home) route == Destination.Home.route || route == Destination.Queue.route || route == Destination.Activity.route
        else currentDestination?.hierarchy?.any { it.route == destination.route } == true
    }
    val navigateTo: (Destination) -> Unit = navigate@{ destination ->
        if (destination == Destination.Library && isSelected(destination)) {
            // The Library tab pressed again leaves the open folder and shows the list of folders.
            navController.runCatching { getBackStackEntry(Destination.Library.route) }.getOrNull()?.let { entry ->
                ViewModelProvider(entry, AppViewModelProvider.Factory)[LibraryViewModel::class.java].showFolders()
            }
            return@navigate
        }
        if (destination == Destination.Home && (currentDestination?.route == Destination.Queue.route || currentDestination?.route == Destination.Activity.route)) {
            // Home pressed while Queue or Activity is open (they belong to Home): back to Home itself.
            navController.popBackStack(Destination.Home.route, inclusive = false)
            return@navigate
        }
        navController.navigate(destination.route) {
            popUpTo(navController.graph.findStartDestination().id) { saveState = true }
            launchSingleTop = true
            restoreState = true
        }
    }

    // Full screen while the viewer is open: the system bars are hidden until swiped in from the edge.
    val view = androidx.compose.ui.platform.LocalView.current
    androidx.compose.runtime.LaunchedEffect(ViewerState.open) {
        val window = (view.context as? android.app.Activity)?.window ?: return@LaunchedEffect
        val controller = androidx.core.view.WindowCompat.getInsetsController(window, view)
        controller.systemBarsBehavior = androidx.core.view.WindowInsetsControllerCompat.BEHAVIOR_SHOW_TRANSIENT_BARS_BY_SWIPE
        if (ViewerState.open) controller.hide(androidx.core.view.WindowInsetsCompat.Type.systemBars())
        else controller.show(androidx.core.view.WindowInsetsCompat.Type.systemBars())
    }
    androidx.compose.foundation.layout.Box {
    Scaffold(
        // Each screen draws its own top bar, which handles the status-bar inset.
        contentWindowInsets = WindowInsets(0),
        bottomBar = {
            if (!wide && !onSettings && !ViewerState.open) {
                NavigationBar {
                    TABS.forEach { destination ->
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
                    TABS.forEach { destination ->
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
                // Switching tabs and opening Settings is a short fade: no slide, and quick enough not to be waited on.
                enterTransition = { fadeIn(tween(TAB_FADE_MS)) },
                exitTransition = { fadeOut(tween(TAB_FADE_MS)) },
                popEnterTransition = { fadeIn(tween(TAB_FADE_MS)) },
                popExitTransition = { fadeOut(tween(TAB_FADE_MS)) },
            ) {
                composable(Destination.Home.route) {
                    HomeScreen(
                        onOpenSettings = { navController.navigate(SETTINGS_ROUTE) },
                        onOpenQueue = { navController.navigate(Destination.Queue.route) { launchSingleTop = true } },
                        onOpenActivity = { navController.navigate(Destination.Activity.route) { launchSingleTop = true } },
                    )
                }
                composable(Destination.Queue.route) { QueueScreen(onBack = { navController.popBackStack() }) }
                composable(Destination.Library.route) { LibraryScreen() }
                composable(Destination.Activity.route) { ActivityScreen(onBack = { navController.popBackStack() }) }
                composable(SETTINGS_ROUTE) { SettingsScreen(onBack = { navController.popBackStack() }) }
            }
        }
    }
    ViewerState.content?.invoke()
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
