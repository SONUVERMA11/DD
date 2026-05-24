package com.sonu.dd

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.compose.animation.*
import androidx.compose.foundation.layout.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.navigation.NavGraph.Companion.findStartDestination
import androidx.navigation.compose.*
import com.sonu.dd.core.ui.components.DDBottomNavBar
import com.sonu.dd.core.ui.navigation.BottomNavTab
import com.sonu.dd.core.ui.navigation.DDRoute
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

    val currentBackStackEntry by navController.currentBackStackEntryAsState()
    val currentRoute = currentBackStackEntry?.destination?.route ?: ""

    val showBottomBar = !showSplash && !currentRoute.contains("SearchResults") &&
            !currentRoute.contains("DownloadDetail") && !currentRoute.contains("VideoPlayer") &&
            !currentRoute.contains("AudioPlayer") && !currentRoute.contains("Reader") &&
            !currentRoute.contains("ImageViewer")

    if (showSplash) {
        SplashScreen { showSplash = false }
    } else {
        Box(Modifier.fillMaxSize()) {
            NavHost(navController = navController, startDestination = "search") {
                composable("search") {
                    SearchScreen(
                        onSearch = { query ->
                            navController.navigate("search_results/$query")
                        }
                    )
                }
                composable("search_results/{query}") { backStackEntry ->
                    val query = backStackEntry.arguments?.getString("query") ?: ""
                    SearchResultsScreen(
                        query = query,
                        onResultClick = { result ->
                            navController.navigate("download_detail/${java.net.URLEncoder.encode(result.magnetUri, "UTF-8")}/${java.net.URLEncoder.encode(result.name, "UTF-8")}/${result.size}/${result.source.name}")
                        },
                        onBack = { navController.popBackStack() }
                    )
                }
                composable("download_detail/{magnetUri}/{name}/{size}/{source}") { entry ->
                    val magnetUri = java.net.URLDecoder.decode(entry.arguments?.getString("magnetUri") ?: "", "UTF-8")
                    val name = java.net.URLDecoder.decode(entry.arguments?.getString("name") ?: "", "UTF-8")
                    val size = entry.arguments?.getString("size")?.toLongOrNull() ?: 0L
                    val source = entry.arguments?.getString("source") ?: "TPB"
                    DownloadDetailScreen(
                        magnetUri = magnetUri, name = name, size = size, source = source,
                        onBack = { navController.popBackStack() },
                        onStartDownload = {
                            navController.navigate("downloads") {
                                popUpTo("search") { inclusive = false }
                            }
                            selectedTab = BottomNavTab.DOWNLOADS
                        }
                    )
                }
                composable("downloads") {
                    ActiveDownloadsScreen()
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
                            popUpTo(navController.graph.findStartDestination().id) { saveState = true }
                            launchSingleTop = true
                            restoreState = true
                        }
                    },
                    modifier = Modifier.align(Alignment.BottomCenter)
                )
            }
        }
    }
}
