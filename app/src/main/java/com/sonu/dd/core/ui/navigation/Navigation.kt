package com.sonu.dd.core.ui.navigation

import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Download
import androidx.compose.material.icons.filled.Folder
import androidx.compose.material.icons.filled.Search
import androidx.compose.material.icons.filled.Settings
import androidx.compose.material.icons.outlined.Download
import androidx.compose.material.icons.outlined.Folder
import androidx.compose.material.icons.outlined.Search
import androidx.compose.material.icons.outlined.Settings
import androidx.compose.ui.graphics.vector.ImageVector
import kotlinx.serialization.Serializable

/**
 * Type-safe navigation routes.
 */
sealed interface DDRoute {
    @Serializable data object Splash : DDRoute
    @Serializable data object Search : DDRoute
    @Serializable data class SearchResults(val query: String, val category: String = "") : DDRoute
    @Serializable data class DownloadDetail(val magnetUri: String, val name: String, val size: Long, val source: String) : DDRoute
    @Serializable data object ActiveDownloads : DDRoute
    @Serializable data object Library : DDRoute
    @Serializable data object Settings : DDRoute
    @Serializable data class VideoPlayer(val filePath: String) : DDRoute
    @Serializable data class AudioPlayer(val filePath: String) : DDRoute
    @Serializable data class Reader(val filePath: String) : DDRoute
    @Serializable data class ImageViewer(val filePath: String) : DDRoute
}

/**
 * Bottom navigation tab definitions.
 */
enum class BottomNavTab(
    val label: String,
    val selectedIcon: ImageVector,
    val unselectedIcon: ImageVector,
    val route: DDRoute
) {
    SEARCH(
        label = "Search",
        selectedIcon = Icons.Filled.Search,
        unselectedIcon = Icons.Outlined.Search,
        route = DDRoute.Search
    ),
    DOWNLOADS(
        label = "Downloads",
        selectedIcon = Icons.Filled.Download,
        unselectedIcon = Icons.Outlined.Download,
        route = DDRoute.ActiveDownloads
    ),
    LIBRARY(
        label = "Library",
        selectedIcon = Icons.Filled.Folder,
        unselectedIcon = Icons.Outlined.Folder,
        route = DDRoute.Library
    ),
    SETTINGS(
        label = "Settings",
        selectedIcon = Icons.Filled.Settings,
        unselectedIcon = Icons.Outlined.Settings,
        route = DDRoute.Settings
    );
}
