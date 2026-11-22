package dev.hamza.localtube.ui.navigation

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.consumeWindowInsets
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.List
import androidx.compose.material.icons.filled.Settings
import androidx.compose.material3.Icon
import androidx.compose.material3.NavigationBar
import androidx.compose.material3.NavigationBarItem
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.navigation.NavGraph.Companion.findStartDestination
import androidx.navigation.NavHostController
import androidx.navigation.NavType
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.compose.currentBackStackEntryAsState
import androidx.navigation.compose.rememberNavController
import androidx.navigation.navArgument
import dev.hamza.localtube.ui.components.EmptyState
import dev.hamza.localtube.ui.home.HomeScreen
import dev.hamza.localtube.ui.library.LibraryScreen
import dev.hamza.localtube.ui.navigation.Routes.PLAYER
import dev.hamza.localtube.ui.navigation.Routes.PLAYLIST
import dev.hamza.localtube.ui.player.PlayerScreen
import dev.hamza.localtube.ui.playlists.PlaylistDetailScreen
import dev.hamza.localtube.ui.playlists.PlaylistsScreen
import dev.hamza.localtube.ui.settings.FirstRunDialog
import dev.hamza.localtube.ui.settings.SettingsScreen
import dev.hamza.localtube.ui.settings.SettingsViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import dev.hamza.localtube.ui.icons.Download
import dev.hamza.localtube.ui.icons.PlaylistPlay

object Routes {
    const val HOME = "home"
    const val LIBRARY = "library"
    const val PLAYLISTS = "playlists"
    const val SETTINGS = "settings"

    /** [scope] is a playlist id, or -1 when the queue is the whole library. */
    const val PLAYER = "player/{scope}/{id}/{start}"
    const val PLAYLIST = "playlist/{id}"

    const val ARG_SCOPE = "scope"
    const val ARG_ID = "id"
    const val ARG_START = "start"

    val TABS = setOf(HOME, LIBRARY, PLAYLISTS, SETTINGS)

    fun player(scope: Long, id: Long, startMs: Long = 0L) = "player/$scope/$id/$startMs"

    fun playlist(id: Long) = "playlist/$id"
}

/**
 * The whole app shell: four tabs, a fullscreen player route that hides them, and the first-run
 * storage question on top of everything.
 */
@Composable
fun LocalTubeRoot(
    pendingUrl: String?,
    onPendingConsumed: () -> Unit,
    startTab: String?,
    onStartTabConsumed: () -> Unit,
    settingsViewModel: SettingsViewModel,
) {
    val navController = rememberNavController()
    val backStackEntry by navController.currentBackStackEntryAsState()
    val currentRoute = backStackEntry?.destination?.route
    val settings by settingsViewModel.settings.collectAsStateWithLifecycle()

    Scaffold(
        bottomBar = {
            if (currentRoute == null || currentRoute in Routes.TABS) {
                NavigationBar {
                    NavigationBarItem(
                        selected = currentRoute == Routes.HOME,
                        onClick = { navController.navigateToTab(Routes.HOME) },
                        icon = { Icon(Download, contentDescription = null) },
                        label = { Text("Download") },
                    )
                    NavigationBarItem(
                        selected = currentRoute == Routes.LIBRARY,
                        onClick = { navController.navigateToTab(Routes.LIBRARY) },
                        icon = { Icon(Icons.Default.List, contentDescription = null) },
                        label = { Text("Library") },
                    )
                    NavigationBarItem(
                        selected = currentRoute == Routes.PLAYLISTS,
                        onClick = { navController.navigateToTab(Routes.PLAYLISTS) },
                        icon = { Icon(PlaylistPlay, contentDescription = null) },
                        label = { Text("Playlists") },
                    )
                    NavigationBarItem(
                        selected = currentRoute == Routes.SETTINGS,
                        onClick = { navController.navigateToTab(Routes.SETTINGS) },
                        icon = { Icon(Icons.Default.Settings, contentDescription = null) },
                        label = { Text("Settings") },
                    )
                }
            }
        },
    ) { innerPadding ->
        Column(
            Modifier
                .fillMaxSize()
                .padding(innerPadding)
                .consumeWindowInsets(innerPadding),
        ) {
            AppNavHost(
                navController = navController,
                pendingUrl = pendingUrl,
                onPendingConsumed = onPendingConsumed,
                startTab = startTab,
                onStartTabConsumed = onStartTabConsumed,
            )
        }
    }

    if (!settings.onboarded) {
        FirstRunDialog(settingsViewModel)
    }
}

/** Standard bottom-bar behaviour: no duplicate entry on the stack, keep the tab's own state. */
private fun NavHostController.navigateToTab(route: String) {
    if (currentDestination?.route == route) return
    navigate(route) {
        popUpTo(graph.findStartDestination().id) { saveState = true }
        launchSingleTop = true
        restoreState = true
    }
}

@Composable
private fun AppNavHost(
    navController: NavHostController,
    pendingUrl: String?,
    onPendingConsumed: () -> Unit,
    startTab: String?,
    onStartTabConsumed: () -> Unit,
) {
    LaunchedEffect(startTab) {
        if (startTab != null) {
            if (startTab in Routes.TABS) navController.navigateToTab(startTab)
            onStartTabConsumed()
        }
    }

    NavHost(
        navController = navController,
        startDestination = Routes.HOME,
        modifier = Modifier.fillMaxSize(),
    ) {
        composable(Routes.HOME) {
            HomeScreen(
                pendingUrl = pendingUrl,
                onPendingConsumed = onPendingConsumed,
                onOpenDownload = { id -> navController.navigate(Routes.player(-1L, id)) },
                onOpenLibrary = { navController.navigateToTab(Routes.LIBRARY) },
            )
        }

        composable(Routes.LIBRARY) {
            LibraryScreen(onPlay = { id -> navController.navigate(Routes.player(-1L, id)) })
        }

        composable(Routes.PLAYLISTS) {
            PlaylistsScreen(onOpenPlaylist = { id -> navController.navigate(Routes.playlist(id)) })
        }

        composable(Routes.SETTINGS) {
            SettingsScreen()
        }

        composable(
            route = PLAYLIST,
            arguments = listOf(navArgument(Routes.ARG_ID) { type = NavType.LongType }),
        ) { entry ->
            val id = entry.arguments?.getLong(Routes.ARG_ID) ?: return@composable NotFound(onBack = navController::popBackStack)
            PlaylistDetailScreen(
                playlistId = id,
                onPlay = { playlistId, downloadId ->
                    navController.navigate(Routes.player(playlistId, downloadId))
                },
                onBack = { navController.popBackStack() },
            )
        }

        composable(
            route = PLAYER,
            arguments = listOf(
                navArgument(Routes.ARG_SCOPE) { type = NavType.LongType },
                navArgument(Routes.ARG_ID) { type = NavType.LongType },
                navArgument(Routes.ARG_START) { type = NavType.LongType; defaultValue = 0L },
            ),
        ) { entry ->
            val args = entry.arguments
            if (args == null) {
                NotFound(onBack = navController::popBackStack)
            } else {
                PlayerScreen(
                    playlistId = args.getLong(Routes.ARG_SCOPE),
                    downloadId = args.getLong(Routes.ARG_ID),
                    startAtMs = args.getLong(Routes.ARG_START),
                    onBack = { navController.popBackStack() },
                )
            }
        }
    }
}

@Composable
private fun NotFound(onBack: () -> Unit) {
    Column(
        modifier = Modifier.fillMaxSize(),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.Center,
    ) {
        EmptyState(
            icon = Icons.Default.List,
            title = "That is not here any more",
            subtitle = "The download was probably deleted. Go back and pick another one.",
        )
        TextButton(onClick = onBack) { Text("Back") }
    }
}
