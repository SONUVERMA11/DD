package com.sonu.dd.feature.settings.ui

import androidx.compose.animation.core.*
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.scale
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import com.sonu.dd.core.ui.theme.*

@Composable
fun SettingsScreen(
    viewModel: SettingsViewModel = hiltViewModel(),
    modifier: Modifier = Modifier
) {
    val state by viewModel.uiState.collectAsState()
    val colors = DDThemeColors.current

    LazyColumn(modifier.fillMaxSize().background(colors.materialScheme.background), contentPadding = PaddingValues(bottom = 120.dp)) {
        // Header
        item {
            Text("Settings", style = MaterialTheme.typography.displaySmall, color = colors.materialScheme.onBackground, fontWeight = FontWeight.Bold, modifier = Modifier.padding(start = 24.dp, top = 48.dp, bottom = 16.dp))
        }

        // ━━━ APPEARANCE ━━━
        item { SectionHeader("Appearance", Icons.Default.Palette) }
        item {
            Text("Theme", style = MaterialTheme.typography.titleSmall, color = colors.materialScheme.onBackground, fontWeight = FontWeight.SemiBold, modifier = Modifier.padding(horizontal = 20.dp, vertical = 8.dp))
            LazyRow(contentPadding = PaddingValues(horizontal = 16.dp), horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                items(DDTheme.entries.toList()) { theme ->
                    ThemePreviewCard(theme = theme, isSelected = state.theme == theme, onClick = { viewModel.setTheme(theme) })
                }
            }
        }
        item { SettingsToggle("Follow System Theme", "Auto switch dark/light", state.followSystem) { viewModel.setFollowSystem(it) } }
        item {
            val labels = listOf("Full", "Reduced", "Off")
            SettingsSegment("Animations", labels, state.animationLevel) { viewModel.setAnimationLevel(it) }
        }

        // ━━━ DOWNLOADS ━━━
        item { SectionHeader("Downloads", Icons.Default.Download) }
        item { SettingsToggle("Auto-save to Gallery", "Save media files to device gallery", state.autoSaveGallery) { viewModel.setAutoSaveGallery(it) } }
        item { SettingsSlider("Simultaneous Downloads", state.simultaneousDownloads, 1, 4) { viewModel.setSimultaneousDownloads(it) } }
        item { SettingsToggle("Auto-resume on Reconnect", "Resume downloads when network returns", state.autoResume) { viewModel.setAutoResume(it) } }

        // ━━━ FILE FORMATS ━━━
        item { SectionHeader("File Formats", Icons.Default.InsertDriveFile) }
        item { SettingsToggle("Smart Convert", "Auto-detect best output format", state.smartConvert) { viewModel.setSmartConvert(it) } }

        // ━━━ NETWORK & PRIVACY ━━━
        item { SectionHeader("Network & Privacy", Icons.Default.Security) }
        item { SettingsToggle("VPN Reminder", "Show reminder to use VPN", state.vpnReminder) { viewModel.setVpnReminder(it) } }
        item { SettingsToggle("DHT", "Distributed Hash Table for peer discovery", state.dhtEnabled) { viewModel.setDhtEnabled(it) } }
        item { SettingsToggle("PEX", "Peer Exchange protocol", state.pexEnabled) { viewModel.setPexEnabled(it) } }
        item { SettingsToggle("Anonymous Mode", "Hide your identity from peers", state.anonymousMode) { viewModel.setAnonymousMode(it) } }

        // ━━━ SEARCH SOURCES ━━━
        item { SectionHeader("Search Sources", Icons.Default.TravelExplore) }
        item { SettingsToggle("YTS", "Movie torrents", state.sourceYts) { viewModel.setSourceYts(it) } }
        item { SettingsToggle("1337x", "General torrents", state.source1337x) { viewModel.setSource1337x(it) } }
        item { SettingsToggle("TPB", "The Pirate Bay", state.sourceTpb) { viewModel.setSourceTpb(it) } }
        item { SettingsToggle("EZTV", "TV show torrents", state.sourceEztv) { viewModel.setSourceEztv(it) } }
        item { SettingsToggle("Nyaa", "Anime & Japanese media", state.sourceNyaa) { viewModel.setSourceNyaa(it) } }
        item { SettingsToggle("Academic", "Academic datasets & papers", state.sourceAcademic) { viewModel.setSourceAcademic(it) } }
        item { SettingsToggle("TorrentGalaxy", "General torrents index", state.sourceTorrentGalaxy) { viewModel.setSourceTorrentGalaxy(it) } }
        item { SettingsToggle("LimeTorrents", "Verified torrents", state.sourceLimeTorrents) { viewModel.setSourceLimeTorrents(it) } }
        item { SettingsToggle("SolidTorrents", "DHT-based search", state.sourceSolidTorrents) { viewModel.setSourceSolidTorrents(it) } }
        item { SettingsToggle("Bitsearch", "Aggregated torrent search", state.sourceBitsearch) { viewModel.setSourceBitsearch(it) } }

        // ━━━ NOTIFICATIONS ━━━
        item { SectionHeader("Notifications", Icons.Default.Notifications) }
        item { SettingsToggle("Progress Notifications", "Show download progress in notification bar", state.progressNotification) { viewModel.setProgressNotification(it) } }

        // ━━━ SECURITY ━━━
        item { SectionHeader("Security", Icons.Default.Lock) }
        item { SettingsToggle("App Lock", "Require authentication to open", state.appLockEnabled) { viewModel.setAppLockEnabled(it) } }
        item { SettingsToggle("Incognito Downloads", "Don't save search or download history", state.incognitoDownloads) { viewModel.setIncognitoDownloads(it) } }
        item {
            SettingsButton("Clear Search History", Icons.Default.DeleteSweep) { viewModel.clearSearchHistory() }
        }

        // ━━━ ABOUT ━━━
        item { SectionHeader("About", Icons.Default.Info) }
        item {
            Box(Modifier.fillMaxWidth().padding(horizontal = 20.dp, vertical = 8.dp).clip(RoundedCornerShape(16.dp)).background(colors.materialScheme.surface).padding(24.dp)) {
                Column(horizontalAlignment = Alignment.CenterHorizontally, modifier = Modifier.fillMaxWidth()) {
                    Text("DD", style = MaterialTheme.typography.displayMedium, color = colors.accent, fontWeight = FontWeight.ExtraBold)
                    Text("Deep Downloader", style = MaterialTheme.typography.bodyMedium, color = colors.textSecondary)
                    Spacer(Modifier.height(8.dp))
                    Text("Version 1.0.0", style = MaterialTheme.typography.labelMedium, color = colors.textTertiary)
                    Spacer(Modifier.height(16.dp))
                    // Animated heart
                    val pulse = rememberInfiniteTransition(label = "heart")
                    val scale by pulse.animateFloat(1f, 1.2f, infiniteRepeatable(tween(600), RepeatMode.Reverse), label = "hs")
                    Text("Made with ❤️ by Sonu Verma", style = MaterialTheme.typography.bodyMedium, color = colors.accent, fontWeight = FontWeight.Medium, modifier = Modifier.scale(scale))
                    Spacer(Modifier.height(16.dp))
                    Text("DD is a tool for downloading legal content.\nUsers are responsible for the content they access.", style = MaterialTheme.typography.bodySmall, color = colors.textTertiary)
                }
            }
        }
    }
}

@Composable
private fun SectionHeader(title: String, icon: ImageVector) {
    val colors = DDThemeColors.current
    Row(Modifier.fillMaxWidth().padding(horizontal = 20.dp, vertical = 16.dp), verticalAlignment = Alignment.CenterVertically) {
        Icon(icon, null, tint = colors.accent, modifier = Modifier.size(20.dp))
        Spacer(Modifier.width(10.dp))
        Text(title, style = MaterialTheme.typography.titleMedium, color = colors.materialScheme.onBackground, fontWeight = FontWeight.Bold)
    }
}

@Composable
private fun SettingsToggle(title: String, subtitle: String, checked: Boolean, onCheckedChange: (Boolean) -> Unit) {
    val colors = DDThemeColors.current
    Row(Modifier.fillMaxWidth().clickable { onCheckedChange(!checked) }.padding(horizontal = 20.dp, vertical = 12.dp), verticalAlignment = Alignment.CenterVertically) {
        Column(Modifier.weight(1f)) {
            Text(title, style = MaterialTheme.typography.titleSmall, color = colors.materialScheme.onBackground, fontWeight = FontWeight.Medium)
            Text(subtitle, style = MaterialTheme.typography.bodySmall, color = colors.textTertiary)
        }
        Switch(checked = checked, onCheckedChange = onCheckedChange, colors = SwitchDefaults.colors(checkedTrackColor = colors.accent, checkedThumbColor = colors.onAccent))
    }
}

@Composable
private fun SettingsSlider(title: String, value: Int, min: Int, max: Int, onValueChange: (Int) -> Unit) {
    val colors = DDThemeColors.current
    Column(Modifier.fillMaxWidth().padding(horizontal = 20.dp, vertical = 8.dp)) {
        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
            Text(title, style = MaterialTheme.typography.titleSmall, color = colors.materialScheme.onBackground, fontWeight = FontWeight.Medium)
            Text("$value", style = MaterialTheme.typography.titleSmall, color = colors.accent, fontWeight = FontWeight.Bold)
        }
        Slider(value = value.toFloat(), onValueChange = { onValueChange(it.toInt()) }, valueRange = min.toFloat()..max.toFloat(), steps = max - min - 1, colors = SliderDefaults.colors(thumbColor = colors.accent, activeTrackColor = colors.accent))
    }
}

@Composable
private fun SettingsSegment(title: String, options: List<String>, selected: Int, onSelect: (Int) -> Unit) {
    val colors = DDThemeColors.current
    Column(Modifier.fillMaxWidth().padding(horizontal = 20.dp, vertical = 8.dp)) {
        Text(title, style = MaterialTheme.typography.titleSmall, color = colors.materialScheme.onBackground, fontWeight = FontWeight.Medium)
        Spacer(Modifier.height(8.dp))
        Row(Modifier.fillMaxWidth().clip(RoundedCornerShape(12.dp)).background(colors.materialScheme.surface)) {
            options.forEachIndexed { i, opt ->
                Box(Modifier.weight(1f).clip(RoundedCornerShape(12.dp)).background(if (i == selected) colors.accent else colors.materialScheme.surface).clickable { onSelect(i) }.padding(vertical = 10.dp), contentAlignment = Alignment.Center) {
                    Text(opt, style = MaterialTheme.typography.labelMedium, color = if (i == selected) colors.onAccent else colors.textSecondary, fontWeight = if (i == selected) FontWeight.Bold else FontWeight.Normal)
                }
            }
        }
    }
}

@Composable
private fun SettingsButton(title: String, icon: ImageVector, onClick: () -> Unit) {
    val colors = DDThemeColors.current
    Row(Modifier.fillMaxWidth().clickable(onClick = onClick).padding(horizontal = 20.dp, vertical = 14.dp), verticalAlignment = Alignment.CenterVertically) {
        Icon(icon, null, tint = colors.materialScheme.error, modifier = Modifier.size(20.dp))
        Spacer(Modifier.width(12.dp))
        Text(title, style = MaterialTheme.typography.titleSmall, color = colors.materialScheme.error, fontWeight = FontWeight.Medium)
    }
}

@Composable
private fun ThemePreviewCard(theme: DDTheme, isSelected: Boolean, onClick: () -> Unit) {
    val scheme = theme.toColorScheme()
    Box(
        Modifier.width(100.dp).height(130.dp).clip(RoundedCornerShape(16.dp))
            .background(scheme.materialScheme.background)
            .then(if (isSelected) Modifier.border(2.dp, scheme.accent, RoundedCornerShape(16.dp)) else Modifier)
            .clickable(onClick = onClick).padding(10.dp)
    ) {
        Column {
            Box(Modifier.fillMaxWidth().height(20.dp).clip(RoundedCornerShape(6.dp)).background(scheme.materialScheme.surface))
            Spacer(Modifier.height(6.dp))
            Box(Modifier.fillMaxWidth(0.7f).height(8.dp).clip(RoundedCornerShape(4.dp)).background(scheme.accent))
            Spacer(Modifier.height(4.dp))
            Box(Modifier.fillMaxWidth(0.5f).height(6.dp).clip(RoundedCornerShape(3.dp)).background(scheme.textSecondary.copy(alpha = 0.3f)))
            Spacer(Modifier.weight(1f))
            Text(theme.displayName, style = MaterialTheme.typography.labelSmall, color = scheme.materialScheme.onBackground, fontWeight = FontWeight.Medium)
            if (isSelected) Text("Active", style = MaterialTheme.typography.labelSmall, color = scheme.accent, fontWeight = FontWeight.Bold)
        }
    }
}
