package com.sonu.dd.feature.download.ui

import androidx.compose.animation.core.*
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.scale
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp

import com.sonu.dd.core.ui.theme.DDThemeColors
import com.sonu.dd.core.util.FileUtils

@Composable
fun DownloadDetailScreen(
    magnetUri: String, name: String, size: Long, source: String,
    viewModel: DownloadViewModel,
    onBack: () -> Unit, onStartDownload: () -> Unit,
    modifier: Modifier = Modifier
) {
    val colors = DDThemeColors.current
    var selectedFormat by remember { mutableStateOf("Auto") }
    var smartConvert by remember { mutableStateOf(true) }
    val formats = listOf("Auto", "MP4", "MKV", "AVI", "Original")
    val audioFormats = listOf("Auto", "MP3", "AAC", "FLAC", "Original")
    val isVideo = name.let { n -> listOf(".mkv", ".mp4", ".avi", ".webm", ".mov").any { n.contains(it, true) } || !n.contains(".", false) }
    val currentFormats = if (isVideo) formats else audioFormats

    // Pulse animation for download button
    val infiniteTransition = rememberInfiniteTransition(label = "pulse")
    val pulseScale by infiniteTransition.animateFloat(1f, 1.06f, infiniteRepeatable(tween(800), RepeatMode.Reverse), label = "ps")

    Column(modifier.fillMaxSize().background(colors.materialScheme.background)) {
        // Top bar
        Row(Modifier.fillMaxWidth().padding(start = 8.dp, end = 16.dp, top = 48.dp, bottom = 8.dp), verticalAlignment = Alignment.CenterVertically) {
            IconButton(onClick = onBack) { Icon(Icons.AutoMirrored.Filled.ArrowBack, "Back", tint = colors.materialScheme.onBackground) }
            Text("Download Detail", style = MaterialTheme.typography.titleLarge, color = colors.materialScheme.onBackground, fontWeight = FontWeight.Bold)
        }

        Column(Modifier.padding(horizontal = 20.dp)) {
            // File info card
            Box(Modifier.fillMaxWidth().clip(RoundedCornerShape(20.dp)).background(colors.materialScheme.surface).padding(20.dp)) {
                Column {
                    Text(name, style = MaterialTheme.typography.titleMedium, color = colors.materialScheme.onSurface, fontWeight = FontWeight.Bold, maxLines = 3)
                    Spacer(Modifier.height(12.dp))
                    Row(horizontalArrangement = Arrangement.spacedBy(16.dp)) {
                        InfoChip("📦", "Size", FileUtils.formatSize(size))
                        InfoChip("🌐", "Source", source)
                        InfoChip("📄", "Format", if (isVideo) "Video" else "Audio")
                    }
                }
            }

            Spacer(Modifier.height(20.dp))

            // Format selector
            Text("Output Format", style = MaterialTheme.typography.titleSmall, color = colors.materialScheme.onBackground, fontWeight = FontWeight.SemiBold)
            Spacer(Modifier.height(10.dp))
            Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                currentFormats.forEach { fmt ->
                    val selected = fmt == selectedFormat
                    Box(
                        Modifier.weight(1f).clip(RoundedCornerShape(12.dp))
                            .background(if (selected) colors.accent else colors.materialScheme.surface)
                            .then(if (!selected) Modifier.border(1.dp, colors.cardBorder, RoundedCornerShape(12.dp)) else Modifier)
                            .clickable { selectedFormat = fmt }.padding(vertical = 12.dp),
                        contentAlignment = Alignment.Center
                    ) { Text(fmt, style = MaterialTheme.typography.labelMedium, color = if (selected) colors.onAccent else colors.materialScheme.onSurface, fontWeight = if (selected) FontWeight.Bold else FontWeight.Normal) }
                }
            }

            Spacer(Modifier.height(20.dp))

            // Smart Convert toggle
            Row(Modifier.fillMaxWidth().clip(RoundedCornerShape(14.dp)).background(colors.materialScheme.surface).padding(horizontal = 16.dp, vertical = 12.dp), verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.SpaceBetween) {
                Column(Modifier.weight(1f)) {
                    Text("Smart Convert", style = MaterialTheme.typography.titleSmall, color = colors.materialScheme.onSurface, fontWeight = FontWeight.SemiBold)
                    Text("Auto-detect best format for your device", style = MaterialTheme.typography.bodySmall, color = colors.textTertiary)
                }
                Switch(checked = smartConvert, onCheckedChange = { smartConvert = it }, colors = SwitchDefaults.colors(checkedTrackColor = colors.accent, checkedThumbColor = colors.onAccent))
            }

            Spacer(Modifier.height(32.dp))

            // Download button
            Box(Modifier.fillMaxWidth(), contentAlignment = Alignment.Center) {
                Button(
                    onClick = { viewModel.addDownload(magnetUri, name, size, source, selectedFormat); onStartDownload() },
                    modifier = Modifier.fillMaxWidth().height(58.dp).scale(pulseScale),
                    shape = RoundedCornerShape(18.dp),
                    colors = ButtonDefaults.buttonColors(containerColor = colors.accent, contentColor = colors.onAccent),
                    elevation = ButtonDefaults.buttonElevation(defaultElevation = 8.dp)
                ) {
                    Icon(Icons.Default.Download, "Download", Modifier.size(24.dp))
                    Spacer(Modifier.width(10.dp))
                    Text("Start Download", style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold)
                }
            }
        }
    }
}

@Composable
private fun InfoChip(emoji: String, label: String, value: String) {
    val colors = DDThemeColors.current
    Column(horizontalAlignment = Alignment.CenterHorizontally) {
        Text(emoji, style = MaterialTheme.typography.titleMedium)
        Text(label, style = MaterialTheme.typography.labelSmall, color = colors.textTertiary)
        Text(value, style = MaterialTheme.typography.labelMedium, color = colors.materialScheme.onSurface, fontWeight = FontWeight.SemiBold)
    }
}
