package com.sonu.dd.feature.library.ui

import android.content.ActivityNotFoundException
import android.content.Context
import android.content.Intent
import android.util.Log
import android.widget.Toast
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.items
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.core.content.FileProvider
import androidx.hilt.navigation.compose.hiltViewModel
import com.sonu.dd.core.data.db.LibraryItemEntity
import com.sonu.dd.core.domain.model.FileCategory
import com.sonu.dd.core.ui.theme.DDThemeColors
import com.sonu.dd.core.util.FileUtils
import java.io.File

@Composable
fun LibraryScreen(
    viewModel: LibraryViewModel = hiltViewModel(),
    modifier: Modifier = Modifier
) {
    val colors = DDThemeColors.current
    val context = LocalContext.current
    val items by viewModel.filteredItems.collectAsState()
    val totalSize by viewModel.totalSize.collectAsState()
    val selectedTab by viewModel.selectedTab.collectAsState()
    val viewMode by viewModel.libraryView.collectAsState()
    val tabs = listOf("All", "Videos", "Music", "Books", "Other")

    Column(modifier.fillMaxSize().background(colors.materialScheme.background)) {
        // Header
        Row(Modifier.fillMaxWidth().padding(start = 24.dp, end = 16.dp, top = 48.dp, bottom = 4.dp), verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.SpaceBetween) {
            Text("Library", style = MaterialTheme.typography.displaySmall, color = colors.materialScheme.onBackground, fontWeight = FontWeight.Bold)
            IconButton(onClick = { viewModel.toggleView() }) {
                Icon(if (viewMode == 0) Icons.Default.ViewList else Icons.Default.GridView, "Toggle view", tint = colors.accent)
            }
        }

        // Storage bar
        if (totalSize != null && totalSize!! > 0) {
            Box(Modifier.fillMaxWidth().padding(horizontal = 20.dp, vertical = 8.dp).clip(RoundedCornerShape(12.dp)).background(colors.materialScheme.surface).padding(16.dp)) {
                Column {
                    Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                        Text("Storage Used", style = MaterialTheme.typography.labelMedium, color = colors.textSecondary)
                        Text(FileUtils.formatSize(totalSize ?: 0L), style = MaterialTheme.typography.labelMedium, color = colors.accent, fontWeight = FontWeight.Bold)
                    }
                    Spacer(Modifier.height(8.dp))
                    LinearProgressIndicator(progress = { 0.3f }, Modifier.fillMaxWidth().height(6.dp).clip(RoundedCornerShape(3.dp)), color = colors.accent, trackColor = colors.materialScheme.surfaceVariant)
                }
            }
        }

        // Tab row
        ScrollableTabRow(selectedTabIndex = tabs.indexOf(selectedTab), containerColor = colors.materialScheme.background, contentColor = colors.accent, edgePadding = 20.dp,
            divider = {}, indicator = {}) {
            tabs.forEach { tab ->
                Tab(selected = selectedTab == tab, onClick = { viewModel.selectTab(tab) },
                    text = { Text(tab, fontWeight = if (selectedTab == tab) FontWeight.Bold else FontWeight.Normal, color = if (selectedTab == tab) colors.accent else colors.textTertiary) })
            }
        }

        if (items.isEmpty()) {
            Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                Column(horizontalAlignment = Alignment.CenterHorizontally) {
                    Text("📂", style = MaterialTheme.typography.displayLarge)
                    Spacer(Modifier.height(16.dp))
                    Text("Your library is empty", style = MaterialTheme.typography.titleMedium, color = colors.textSecondary)
                    Spacer(Modifier.height(8.dp))
                    Text("Downloaded files will appear here", style = MaterialTheme.typography.bodySmall, color = colors.textTertiary)
                }
            }
        } else if (viewMode == 0) {
            LazyVerticalGrid(columns = GridCells.Fixed(2), contentPadding = PaddingValues(horizontal = 16.dp, vertical = 8.dp), horizontalArrangement = Arrangement.spacedBy(10.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
                items(items, key = { it.id }) { item ->
                    LibraryGridItem(item) { openFile(context, item) }
                }
                item { Spacer(Modifier.height(100.dp)) }
            }
        } else {
            LazyColumn(contentPadding = PaddingValues(horizontal = 16.dp, vertical = 8.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                items(items, key = { it.id }) { item ->
                    LibraryListItem(item) { openFile(context, item) }
                }
                item { Spacer(Modifier.height(100.dp)) }
            }
        }
    }
}

/**
 * Open a downloaded file using the appropriate system app.
 * Uses FileProvider for Android 7+ compatibility.
 */
private fun openFile(context: Context, item: LibraryItemEntity) {
    try {
        val file = File(item.filePath)
        if (!file.exists()) {
            Toast.makeText(context, "File not found: ${item.name}", Toast.LENGTH_SHORT).show()
            return
        }

        val uri = FileProvider.getUriForFile(
            context,
            "${context.packageName}.fileprovider",
            file
        )

        val intent = Intent(Intent.ACTION_VIEW).apply {
            setDataAndType(uri, item.mimeType.ifEmpty { "application/octet-stream" })
            addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
            addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
        }

        try {
            context.startActivity(intent)
        } catch (e: ActivityNotFoundException) {
            // Fallback: try without specific MIME type
            val fallbackIntent = Intent(Intent.ACTION_VIEW).apply {
                setDataAndType(uri, "*/*")
                addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
                addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
            }
            try {
                context.startActivity(Intent.createChooser(fallbackIntent, "Open ${item.name}"))
            } catch (e2: Exception) {
                Toast.makeText(context, "No app available to open this file", Toast.LENGTH_SHORT).show()
            }
        }
    } catch (e: Exception) {
        Log.e("LibraryScreen", "Failed to open file: ${e.message}")
        Toast.makeText(context, "Error opening file: ${e.message}", Toast.LENGTH_SHORT).show()
    }
}

@Composable
private fun LibraryGridItem(item: LibraryItemEntity, onClick: () -> Unit) {
    val colors = DDThemeColors.current
    val emoji = when (item.category) { FileCategory.VIDEO.name -> "🎬"; FileCategory.AUDIO.name -> "🎵"; FileCategory.BOOK.name -> "📚"; FileCategory.IMAGE.name -> "🖼️"; else -> "📦" }
    Box(Modifier.fillMaxWidth().clip(RoundedCornerShape(16.dp)).background(colors.materialScheme.surface).clickable(onClick = onClick).padding(14.dp)) {
        Column {
            Box(Modifier.fillMaxWidth().height(80.dp).clip(RoundedCornerShape(12.dp)).background(Brush.linearGradient(listOf(colors.accent.copy(alpha = 0.1f), colors.accent.copy(alpha = 0.03f)))), contentAlignment = Alignment.Center) { Text(emoji, style = MaterialTheme.typography.displaySmall) }
            Spacer(Modifier.height(10.dp))
            Text(item.name, style = MaterialTheme.typography.labelMedium, color = colors.materialScheme.onSurface, fontWeight = FontWeight.SemiBold, maxLines = 2, overflow = TextOverflow.Ellipsis)
            Spacer(Modifier.height(4.dp))
            Row(horizontalArrangement = Arrangement.SpaceBetween, modifier = Modifier.fillMaxWidth()) {
                Text(FileUtils.formatSize(item.size), style = MaterialTheme.typography.labelSmall, color = colors.textTertiary)
                Box(Modifier.clip(RoundedCornerShape(4.dp)).background(colors.accent.copy(alpha = 0.12f)).padding(horizontal = 6.dp, vertical = 2.dp)) { Text(item.format, style = MaterialTheme.typography.labelSmall, color = colors.accent) }
            }
        }
    }
}

@Composable
private fun LibraryListItem(item: LibraryItemEntity, onClick: () -> Unit) {
    val colors = DDThemeColors.current
    val emoji = when (item.category) { FileCategory.VIDEO.name -> "🎬"; FileCategory.AUDIO.name -> "🎵"; FileCategory.BOOK.name -> "📚"; else -> "📦" }
    Row(Modifier.fillMaxWidth().clip(RoundedCornerShape(14.dp)).background(colors.materialScheme.surface).clickable(onClick = onClick).padding(14.dp), verticalAlignment = Alignment.CenterVertically) {
        Box(Modifier.size(48.dp).clip(RoundedCornerShape(12.dp)).background(colors.accent.copy(alpha = 0.1f)), contentAlignment = Alignment.Center) { Text(emoji, style = MaterialTheme.typography.titleLarge) }
        Spacer(Modifier.width(14.dp))
        Column(Modifier.weight(1f)) {
            Text(item.name, style = MaterialTheme.typography.titleSmall, color = colors.materialScheme.onSurface, fontWeight = FontWeight.SemiBold, maxLines = 1, overflow = TextOverflow.Ellipsis)
            Text("${FileUtils.formatSize(item.size)} · ${FileUtils.formatDate(item.downloadedAt)}", style = MaterialTheme.typography.labelSmall, color = colors.textTertiary)
        }
        Icon(Icons.Default.OpenInNew, "Open", tint = colors.accent, modifier = Modifier.size(20.dp))
    }
}
