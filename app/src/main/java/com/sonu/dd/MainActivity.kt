package com.sonu.dd

import android.os.Bundle
import android.util.Log
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.compose.foundation.layout.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.navigation.NavGraph.Companion.findStartDestination
import androidx.navigation.NavType
import androidx.navigation.compose.*
import androidx.navigation.navArgument
import com.sonu.dd.core.ui.components.DDBottomNavBar
import com.sonu.dd.core.ui.navigation.BottomNavTab
import com.sonu.dd.core.ui.theme.DDAppTheme
import com.sonu.dd.feature.download.ui.ActiveDownloadsScreen
import com.sonu.dd.feature.download.ui.DownloadDetailScreen
import com.sonu.dd.feature.download.ui.DownloadViewModel
import com.sonu.dd.feature.library.ui.LibraryScreen
import com.sonu.dd.feature.search.ui.SearchResultsScreen
import com.sonu.dd.feature.search.ui.SearchScreen
import com.sonu.dd.feature.settings.ui.SettingsScreen
import com.sonu.dd.feature.settings.ui.SettingsViewModel
import com.sonu.dd.feature.splash.SplashScreen
import dagger.hilt.android.AndroidEntryPoint
import java.net.URLDecoder
import java.net.URLEncoder

@AndroidEntryPoint
class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        setContent {
            val settingsVm: SettingsViewModel = hiltViewModel()
            val settings by settingsVm.uiState.collectAsStateWithLifecycle()

            DDAppTheme(theme = settings.theme, followSystem = settings.followSystem) {
                DDApp()
            }
        }
    }
}

@Composable
fun DDApp() {
    val navController = rememberNavController()
    var showSplash by remember { mutableStateOf(true) }
    var selectedTab by remember { mutableStateOf(BottomNavTab.SEARCH) }

    // Hoist DownloadViewModel at root level so it's shared across all screens
    val downloadViewModel: DownloadViewModel = hiltViewModel()

    val currentBackStackEntry by navController.currentBackStackEntryAsState()
    val currentRoute = currentBackStackEntry?.destination?.route ?: ""

    // Update selected tab based on current route
    LaunchedEffect(currentRoute) {
        val tab = when {
            currentRoute == "search" -> BottomNavTab.SEARCH
            currentRoute == "downloads" -> BottomNavTab.DOWNLOADS
            currentRoute == "library" -> BottomNavTab.LIBRARY
            currentRoute == "settings" -> BottomNavTab.SETTINGS
            else -> null
        }
        if (tab != null) selectedTab = tab
    }

    val showBottomBar = !showSplash &&
            !currentRoute.startsWith("search_results") &&
            !currentRoute.startsWith("download_detail")

    if (showSplash) {
        SplashScreen { showSplash = false }
    } else {
        Box(Modifier.fillMaxSize()) {
            NavHost(navController = navController, startDestination = "search") {
                composable("search") {
                    SearchScreen(
                        onSearch = { query ->
                            if (query.isNotBlank()) {
                                val encoded = URLEncoder.encode(query, "UTF-8")
                                navController.navigate("search_results/$encoded")
                            }
                        }
                    )
                }
                composable(
                    "search_results/{query}",
                    arguments = listOf(navArgument("query") { type = NavType.StringType })
                ) { backStackEntry ->
                    val rawQuery = backStackEntry.arguments?.getString("query") ?: ""
                    val query = try {
                        URLDecoder.decode(rawQuery, "UTF-8")
                    } catch (e: Exception) { rawQuery }
                    SearchResultsScreen(
                        query = query,
                        onResultClick = { result ->
                            try {
                                val encodedMagnet = URLEncoder.encode(result.magnetUri, "UTF-8")
                                val encodedName = URLEncoder.encode(result.name, "UTF-8")
                                navController.navigate(
                                    "download_detail/$encodedMagnet/$encodedName/${result.size}/${result.source.name}"
                                )
                            } catch (e: Exception) {
                                Log.e("DDApp", "Navigation error: ${e.message}")
                            }
                        },
                        onBack = { navController.popBackStack() }
                    )
                }
                composable(
                    "download_detail/{magnetUri}/{name}/{size}/{source}",
                    arguments = listOf(
                        navArgument("magnetUri") { type = NavType.StringType },
                        navArgument("name") { type = NavType.StringType },
                        navArgument("size") { type = NavType.StringType },
                        navArgument("source") { type = NavType.StringType },
                    )
                ) { entry ->
                    val magnetUri = try {
                        URLDecoder.decode(entry.arguments?.getString("magnetUri") ?: "", "UTF-8")
                    } catch (e: Exception) { "" }
                    val name = try {
                        URLDecoder.decode(entry.arguments?.getString("name") ?: "", "UTF-8")
                    } catch (e: Exception) { "" }
                    val size = entry.arguments?.getString("size")?.toLongOrNull() ?: 0L
                    val source = entry.arguments?.getString("source") ?: "TPB"

                    // Pass the shared downloadViewModel
                    DownloadDetailScreen(
                        magnetUri = magnetUri,
                        name = name,
                        size = size,
                        source = source,
                        viewModel = downloadViewModel,
                        onBack = { navController.popBackStack() },
                        onStartDownload = {
                            // Navigate to downloads tab, clearing intermediate screens
                            navController.navigate("downloads") {
                                popUpTo("search") { inclusive = false }
                            }
                        }
                    )
                }
                composable("downloads") {
                    // Pass the shared downloadViewModel
                    ActiveDownloadsScreen(viewModel = downloadViewModel)
                }
                composable("library") {
                    LibraryScreen()
                }
                composable("settings") {
                    SettingsScreen()
                }
            }

            // Bottom Nav
            if (showBottomBar) {
                DDBottomNavBar(
                    selectedTab = selectedTab,
                    onTabSelected = { tab ->
                        selectedTab = tab
                        val route = when (tab) {
                            BottomNavTab.SEARCH -> "search"
                            BottomNavTab.DOWNLOADS -> "downloads"
                            BottomNavTab.LIBRARY -> "library"
                            BottomNavTab.SETTINGS -> "settings"
                        }
                        navController.navigate(route) {
                            // Pop back to start destination to avoid stacking
                            popUpTo(navController.graph.findStartDestination().id) {
                                saveState = true
                            }
                            launchSingleTop = true
                            // Only restore state for non-search tabs
                            // (search should always show fresh home, not stale search_results)
                            restoreState = (tab != BottomNavTab.SEARCH)
                        }
                    },
                    modifier = Modifier.align(Alignment.BottomCenter)
                )
            }
        }
    }
}
