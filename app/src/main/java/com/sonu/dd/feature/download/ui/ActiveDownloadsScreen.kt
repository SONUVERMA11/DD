package com.sonu.dd.feature.download.ui

import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.tween
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
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
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp

import com.sonu.dd.core.domain.model.DownloadState
import com.sonu.dd.core.domain.model.DownloadStatus
import com.sonu.dd.core.ui.theme.DDThemeColors
import com.sonu.dd.core.util.FileUtils
import com.sonu.dd.feature.download.ui.components.SpeedometerWidget

@Composable
fun ActiveDownloadsScreen(
    viewModel: DownloadViewModel,
    modifier: Modifier = Modifier
) {
    val downloadsState by viewModel.downloadsState.collectAsState()
    val uiState by viewModel.uiState.collectAsState()
    val totalSpeed by viewModel.totalSpeed.collectAsState()
    val peakSpeed by viewModel.peakSpeed.collectAsState()
    val colors = DDThemeColors.current
    val active = downloadsState.values.filter { it.status != DownloadStatus.COMPLETED && it.status != DownloadStatus.FAILED }.sortedByDescending { it.addedTimestamp }
    val completed = downloadsState.values.filter { it.status == DownloadStatus.COMPLETED }

    Column(modifier = modifier.fillMaxSize().background(colors.materialScheme.background)) {
        Row(Modifier.fillMaxWidth().padding(start = 24.dp, end = 16.dp, top = 48.dp, bottom = 8.dp), verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.SpaceBetween) {
            Column {
                Text("Downloads", style = MaterialTheme.typography.displaySmall, color = colors.materialScheme.onBackground, fontWeight = FontWeight.Bold)
                if (uiState.activeCount > 0) Text("${uiState.activeCount} active", style = MaterialTheme.typography.bodySmall, color = colors.accent)
            }
            if (active.isNotEmpty()) Row {
                TextButton(onClick = { viewModel.pauseAll() }) { Text("Pause All", color = colors.accent) }
                TextButton(onClick = { viewModel.resumeAll() }) { Text("Resume All", color = colors.success) }
            }
        }
        LazyColumn(contentPadding = PaddingValues(horizontal = 16.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
            item {
                Box(Modifier.fillMaxWidth().clip(RoundedCornerShape(24.dp)).background(Brush.verticalGradient(listOf(colors.materialScheme.surface, colors.materialScheme.surface.copy(alpha = 0.5f)))).padding(24.dp), contentAlignment = Alignment.Center) {
                    SpeedometerWidget(speedBytesPerSec = totalSpeed, peakSpeedBytesPerSec = peakSpeed)
                }
            }
            if (active.isNotEmpty()) { item { Text("Active", style = MaterialTheme.typography.titleMedium, color = colors.materialScheme.onBackground, fontWeight = FontWeight.SemiBold, modifier = Modifier.padding(top = 8.dp, start = 4.dp)) } }
            items(active.toList(), key = { it.id }) { dl -> DownloadCard(dl, { viewModel.pauseDownload(dl.id) }, { viewModel.resumeDownload(dl.id) }, { viewModel.cancelDownload(dl.id) }) }
            if (completed.isNotEmpty()) { item { Text("Completed", style = MaterialTheme.typography.titleMedium, color = colors.materialScheme.onBackground, fontWeight = FontWeight.SemiBold, modifier = Modifier.padding(top = 16.dp, start = 4.dp)) } }
            items(completed.toList(), key = { it.id }) { dl -> DownloadCard(dl, {}, {}, { viewModel.cancelDownload(dl.id) }) }
            if (active.isEmpty() && completed.isEmpty()) item {
                Box(Modifier.fillMaxWidth().height(200.dp), contentAlignment = Alignment.Center) {
                    Column(horizontalAlignment = Alignment.CenterHorizontally) { Text("⚡", style = MaterialTheme.typography.displayLarge); Spacer(Modifier.height(12.dp)); Text("No active downloads", style = MaterialTheme.typography.titleMedium, color = colors.textSecondary); Text("Search for something to get started", style = MaterialTheme.typography.bodySmall, color = colors.textTertiary) }
                }
            }
            item { Spacer(Modifier.height(100.dp)) }
        }
    }
}

@Composable
private fun DownloadCard(download: DownloadState, onPause: () -> Unit, onResume: () -> Unit, onCancel: () -> Unit) {
    val colors = DDThemeColors.current
    val progress by animateFloatAsState(download.progress, tween(300), label = "p")
    val statusColor = when (download.status) { DownloadStatus.DOWNLOADING -> colors.accent; DownloadStatus.PAUSED -> colors.warning; DownloadStatus.COMPLETED -> colors.success; DownloadStatus.FAILED -> colors.materialScheme.error; else -> colors.textTertiary }
    Box(Modifier.fillMaxWidth().clip(RoundedCornerShape(16.dp)).background(colors.materialScheme.surface).padding(14.dp)) {
        Column {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Box(Modifier.size(42.dp).clip(RoundedCornerShape(12.dp)).background(statusColor.copy(alpha = 0.12f)), contentAlignment = Alignment.Center) { Text("📦", style = MaterialTheme.typography.titleMedium) }
                Spacer(Modifier.width(12.dp))
                Column(Modifier.weight(1f)) { Text(download.name, style = MaterialTheme.typography.titleSmall, color = colors.materialScheme.onSurface, fontWeight = FontWeight.SemiBold, maxLines = 1, overflow = TextOverflow.Ellipsis); Text(download.status.displayName, style = MaterialTheme.typography.labelSmall, color = statusColor) }
                if (download.status == DownloadStatus.DOWNLOADING) IconButton(onClick = onPause, Modifier.size(36.dp)) { Icon(Icons.Default.Pause, "Pause", tint = colors.textSecondary, modifier = Modifier.size(20.dp)) }
                else if (download.status == DownloadStatus.PAUSED) IconButton(onClick = onResume, Modifier.size(36.dp)) { Icon(Icons.Default.PlayArrow, "Resume", tint = colors.accent, modifier = Modifier.size(20.dp)) }
                if (download.status != DownloadStatus.COMPLETED) IconButton(onClick = onCancel, Modifier.size(36.dp)) { Icon(Icons.Default.Cancel, "Cancel", tint = colors.materialScheme.error.copy(alpha = 0.7f), modifier = Modifier.size(18.dp)) }
            }
            Spacer(Modifier.height(10.dp))
            LinearProgressIndicator(progress = { progress }, modifier = Modifier.fillMaxWidth().height(6.dp).clip(RoundedCornerShape(3.dp)), color = statusColor, trackColor = colors.materialScheme.surfaceVariant, strokeCap = StrokeCap.Round)
            Spacer(Modifier.height(8.dp))
            Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                if (download.status == DownloadStatus.DOWNLOADING) { Text(FileUtils.formatSpeed(download.downloadSpeed), style = MaterialTheme.typography.labelSmall, color = colors.accent, fontWeight = FontWeight.Medium); Text("ETA: ${FileUtils.formatEta(download.eta)}", style = MaterialTheme.typography.labelSmall, color = colors.textTertiary) }
                Text("${(progress * 100).toInt()}%", style = MaterialTheme.typography.labelSmall, color = colors.materialScheme.onSurface, fontWeight = FontWeight.SemiBold)
                Text("${FileUtils.formatSize(download.downloadedSize)} / ${FileUtils.formatSize(download.totalSize)}", style = MaterialTheme.typography.labelSmall, color = colors.textTertiary)
            }
        }
    }
}
