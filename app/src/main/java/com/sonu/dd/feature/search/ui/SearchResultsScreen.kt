package com.sonu.dd.feature.search.ui

import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.tween
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.Download
import androidx.compose.material.icons.filled.FilterList
import androidx.compose.material.icons.filled.People
import androidx.compose.material.icons.filled.SdStorage
import androidx.compose.material.icons.filled.Upload
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.Text
import androidx.compose.material3.rememberModalBottomSheetState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import coil3.compose.AsyncImage
import com.sonu.dd.core.domain.model.TorrentHealth
import com.sonu.dd.core.domain.model.TorrentResult
import com.sonu.dd.core.ui.theme.DDThemeColors

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun SearchResultsScreen(
    query: String,
    category: String = "",
    viewModel: SearchViewModel = hiltViewModel(),
    onResultClick: (TorrentResult) -> Unit,
    onBack: () -> Unit,
    modifier: Modifier = Modifier
) {
    val uiState by viewModel.uiState.collectAsState()
    val colors = DDThemeColors.current
    var showFilterSheet by remember { mutableStateOf(false) }

    LaunchedEffect(query) {
        viewModel.search(query, category)
    }

    Column(
        modifier = modifier
            .fillMaxSize()
            .background(colors.materialScheme.background)
    ) {
        // Top bar
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(start = 8.dp, end = 16.dp, top = 48.dp, bottom = 8.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            IconButton(onClick = onBack) {
                Icon(
                    Icons.AutoMirrored.Filled.ArrowBack,
                    contentDescription = "Back",
                    tint = colors.materialScheme.onBackground
                )
            }
            Column(modifier = Modifier.weight(1f)) {
                Text(
                    text = "Results for",
                    style = MaterialTheme.typography.labelSmall,
                    color = colors.textTertiary
                )
                Text(
                    text = "\"$query\"",
                    style = MaterialTheme.typography.titleLarge,
                    color = colors.materialScheme.onBackground,
                    fontWeight = FontWeight.Bold,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis
                )
            }
            IconButton(onClick = { showFilterSheet = true }) {
                Icon(
                    Icons.Default.FilterList,
                    contentDescription = "Filter",
                    tint = colors.accent
                )
            }
        }

        if (!uiState.hasSearched) {
            // Show nothing yet
        } else if (uiState.isLoading) {
            // Shimmer loading
            LazyColumn(
                contentPadding = PaddingValues(horizontal = 16.dp, vertical = 8.dp),
                verticalArrangement = Arrangement.spacedBy(12.dp)
            ) {
                items(6) {
                    ShimmerCard()
                }
            }
        } else if (uiState.error != null) {
            // Error state
            Box(
                modifier = Modifier.fillMaxSize(),
                contentAlignment = Alignment.Center
            ) {
                Column(horizontalAlignment = Alignment.CenterHorizontally) {
                    Text(
                        text = "⚠️",
                        style = MaterialTheme.typography.displayLarge
                    )
                    Spacer(modifier = Modifier.height(16.dp))
                    Text(
                        text = "Search failed",
                        style = MaterialTheme.typography.headlineSmall,
                        color = colors.materialScheme.onBackground
                    )
                    Spacer(modifier = Modifier.height(8.dp))
                    Text(
                        text = uiState.error ?: "Unknown error",
                        style = MaterialTheme.typography.bodyMedium,
                        color = colors.textSecondary
                    )
                    Spacer(modifier = Modifier.height(24.dp))
                    Box(
                        modifier = Modifier
                            .clip(RoundedCornerShape(12.dp))
                            .background(colors.accent)
                            .clickable { viewModel.search(query, category) }
                            .padding(horizontal = 24.dp, vertical = 12.dp)
                    ) {
                        Text(
                            text = "Retry",
                            style = MaterialTheme.typography.titleSmall,
                            color = colors.onAccent,
                            fontWeight = FontWeight.Bold
                        )
                    }
                }
            }
        } else if (uiState.results.isEmpty()) {
            // Empty state
            Box(
                modifier = Modifier.fillMaxSize(),
                contentAlignment = Alignment.Center
            ) {
                Column(horizontalAlignment = Alignment.CenterHorizontally) {
                    Text(
                        text = "🔍",
                        style = MaterialTheme.typography.displayLarge
                    )
                    Spacer(modifier = Modifier.height(16.dp))
                    Text(
                        text = "No results found",
                        style = MaterialTheme.typography.headlineSmall,
                        color = colors.materialScheme.onBackground
                    )
                    Spacer(modifier = Modifier.height(8.dp))
                    Text(
                        text = "Try different keywords or check your sources in Settings",
                        style = MaterialTheme.typography.bodyMedium,
                        color = colors.textSecondary
                    )
                    Spacer(modifier = Modifier.height(24.dp))
                    Box(
                        modifier = Modifier
                            .clip(RoundedCornerShape(12.dp))
                            .background(colors.accent.copy(alpha = 0.15f))
                            .clickable { viewModel.search(query, category) }
                            .padding(horizontal = 24.dp, vertical = 12.dp)
                    ) {
                        Text(
                            text = "Try Again",
                            style = MaterialTheme.typography.titleSmall,
                            color = colors.accent,
                            fontWeight = FontWeight.Bold
                        )
                    }
                }
            }
        } else {
            // Results count
            Text(
                text = "${uiState.results.size} results found",
                style = MaterialTheme.typography.labelMedium,
                color = colors.textTertiary,
                modifier = Modifier.padding(horizontal = 20.dp, vertical = 4.dp)
            )

            // Results list
            LazyColumn(
                contentPadding = PaddingValues(horizontal = 16.dp, vertical = 8.dp),
                verticalArrangement = Arrangement.spacedBy(12.dp)
            ) {
                items(uiState.results, key = { it.infoHash }) { result ->
                    TorrentResultCard(
                        result = result,
                        onClick = { onResultClick(result) }
                    )
                }
                item { Spacer(modifier = Modifier.height(100.dp)) }
            }
        }
    }

    // Filter bottom sheet
    if (showFilterSheet) {
        ModalBottomSheet(
            onDismissRequest = { showFilterSheet = false },
            containerColor = colors.materialScheme.surface,
            sheetState = rememberModalBottomSheetState()
        ) {
            FilterSheet(
                onSortSelected = { sort ->
                    showFilterSheet = false
                    // Apply sort to results
                }
            )
        }
    }
}

@Composable
private fun TorrentResultCard(
    result: TorrentResult,
    onClick: () -> Unit
) {
    val colors = DDThemeColors.current
    val health = TorrentHealth.from(result.seeds, result.leeches)
    val healthColor = when (health) {
        TorrentHealth.HEALTHY -> colors.success
        TorrentHealth.OK -> colors.warning
        TorrentHealth.DEAD -> colors.materialScheme.error
    }

    Box(
        modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(16.dp))
            .background(colors.materialScheme.surface)
            .clickable(onClick = onClick)
            .padding(14.dp)
    ) {
        Row {
            // Thumbnail
            if (result.thumbnailUrl != null) {
                AsyncImage(
                    model = result.thumbnailUrl,
                    contentDescription = result.name,
                    modifier = Modifier
                        .size(80.dp)
                        .clip(RoundedCornerShape(12.dp)),
                    contentScale = ContentScale.Crop
                )
                Spacer(modifier = Modifier.width(14.dp))
            }

            Column(modifier = Modifier.weight(1f)) {
                // File name
                Text(
                    text = result.name,
                    style = MaterialTheme.typography.titleSmall,
                    color = colors.materialScheme.onSurface,
                    fontWeight = FontWeight.SemiBold,
                    maxLines = 2,
                    overflow = TextOverflow.Ellipsis
                )

                Spacer(modifier = Modifier.height(8.dp))

                // Badges row
                Row(
                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    // Size badge
                    BadgeChip(
                        icon = Icons.Default.SdStorage,
                        text = result.sizeFormatted,
                        color = colors.accent
                    )
                    // Seeds
                    BadgeChip(
                        icon = Icons.Default.Upload,
                        text = "${result.seeds}",
                        color = colors.success
                    )
                    // Leeches
                    BadgeChip(
                        icon = Icons.Default.Download,
                        text = "${result.leeches}",
                        color = colors.warning
                    )
                }

                Spacer(modifier = Modifier.height(8.dp))

                Row(
                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    // Quality badge
                    if (result.quality.isNotBlank()) {
                        Box(
                            modifier = Modifier
                                .clip(RoundedCornerShape(6.dp))
                                .background(colors.accent.copy(alpha = 0.15f))
                                .padding(horizontal = 8.dp, vertical = 3.dp)
                        ) {
                            Text(
                                text = result.quality,
                                style = MaterialTheme.typography.labelSmall,
                                color = colors.accent,
                                fontWeight = FontWeight.Bold
                            )
                        }
                    }

                    // Source chip
                    Box(
                        modifier = Modifier
                            .clip(RoundedCornerShape(6.dp))
                            .background(colors.materialScheme.surfaceVariant)
                            .padding(horizontal = 8.dp, vertical = 3.dp)
                    ) {
                        Text(
                            text = result.source.displayName,
                            style = MaterialTheme.typography.labelSmall,
                            color = colors.textSecondary
                        )
                    }

                    // Health bar
                    Spacer(modifier = Modifier.weight(1f))
                    Box(
                        modifier = Modifier
                            .width(40.dp)
                            .height(4.dp)
                            .clip(RoundedCornerShape(2.dp))
                            .background(colors.materialScheme.surfaceVariant)
                    ) {
                        val progress = when (health) {
                            TorrentHealth.HEALTHY -> 1f
                            TorrentHealth.OK -> 0.5f
                            TorrentHealth.DEAD -> 0.15f
                        }
                        Box(
                            modifier = Modifier
                                .fillMaxWidth(progress)
                                .height(4.dp)
                                .clip(RoundedCornerShape(2.dp))
                                .background(healthColor)
                        )
                    }
                }
            }
        }
    }
}

@Composable
private fun BadgeChip(
    icon: androidx.compose.ui.graphics.vector.ImageVector,
    text: String,
    color: Color
) {
    Row(
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(3.dp)
    ) {
        Icon(
            icon,
            contentDescription = null,
            tint = color,
            modifier = Modifier.size(13.dp)
        )
        Text(
            text = text,
            style = MaterialTheme.typography.labelSmall,
            color = color
        )
    }
}

@Composable
private fun ShimmerCard() {
    val colors = DDThemeColors.current
    val transition = rememberInfiniteTransition(label = "shimmer")
    val shimmerTranslate by transition.animateFloat(
        initialValue = 0f,
        targetValue = 1000f,
        animationSpec = infiniteRepeatable(
            animation = tween(1200),
            repeatMode = RepeatMode.Restart
        ),
        label = "shimmer_translate"
    )

    val shimmerBrush = Brush.linearGradient(
        colors = listOf(
            colors.materialScheme.surfaceVariant,
            colors.materialScheme.surfaceVariant.copy(alpha = 0.3f),
            colors.materialScheme.surfaceVariant,
        ),
        start = Offset(shimmerTranslate - 300f, 0f),
        end = Offset(shimmerTranslate, 0f)
    )

    Box(
        modifier = Modifier
            .fillMaxWidth()
            .height(100.dp)
            .clip(RoundedCornerShape(16.dp))
            .background(shimmerBrush)
    )
}

@Composable
private fun FilterSheet(onSortSelected: (String) -> Unit) {
    val colors = DDThemeColors.current
    val sortOptions = listOf(
        "Seeds (High → Low)",
        "Size (Large → Small)",
        "Size (Small → Large)",
        "Date (Newest)",
        "Quality"
    )

    Column(
        modifier = Modifier
            .fillMaxWidth()
            .padding(24.dp)
    ) {
        Text(
            text = "Sort Results",
            style = MaterialTheme.typography.headlineSmall,
            color = colors.materialScheme.onSurface,
            fontWeight = FontWeight.Bold
        )
        Spacer(modifier = Modifier.height(16.dp))

        sortOptions.forEach { option ->
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .clip(RoundedCornerShape(12.dp))
                    .clickable { onSortSelected(option) }
                    .padding(vertical = 14.dp, horizontal = 8.dp)
            ) {
                Text(
                    text = option,
                    style = MaterialTheme.typography.bodyLarge,
                    color = colors.materialScheme.onSurface
                )
            }
        }

        Spacer(modifier = Modifier.height(32.dp))
    }
}
