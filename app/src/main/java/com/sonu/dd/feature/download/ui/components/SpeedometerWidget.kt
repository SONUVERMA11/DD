package com.sonu.dd.feature.download.ui.components

import androidx.compose.animation.core.Spring
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.spring
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.size
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.drawscope.DrawScope
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.drawscope.rotate
import androidx.compose.ui.graphics.nativeCanvas
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.sonu.dd.core.ui.theme.DDThemeColors
import com.sonu.dd.core.ui.theme.SpeedColors
import com.sonu.dd.core.util.FileUtils
import java.text.DecimalFormat
import kotlin.math.PI
import kotlin.math.cos
import kotlin.math.sin

/**
 * Custom speedometer widget built entirely with Jetpack Compose Canvas.
 *
 * Architecture:
 * - Outer ring: decorative tick marks (major + minor)
 * - Arc background: dark gray semicircle
 * - Arc foreground: colored gradient arc (animates fill)
 * - Needle: thin sharp needle, rotates via animateFloatAsState
 * - Center circle: hub with speed number inside
 * - Speed unit label below center
 * - "Peak: XX MB/s" label at bottom
 *
 * Speed → angle mapping:
 * 0 MB/s    = -150° (far left)
 * 100 MB/s  = +150° (far right)
 */
@Composable
fun SpeedometerWidget(
    speedBytesPerSec: Long,
    peakSpeedBytesPerSec: Long,
    modifier: Modifier = Modifier
) {
    val colors = DDThemeColors.current
    val speedMbps = speedBytesPerSec / (1024.0 * 1024.0)
    val cappedSpeed = speedMbps.coerceIn(0.0, 120.0)

    // Animate the needle
    val animatedAngle by animateFloatAsState(
        targetValue = speedToAngle(cappedSpeed).toFloat(),
        animationSpec = spring(
            dampingRatio = 0.6f,
            stiffness = 200f
        ),
        label = "needle_angle"
    )

    // Animate the arc fill
    val animatedSweep by animateFloatAsState(
        targetValue = ((cappedSpeed / 120.0) * 300.0).toFloat(),
        animationSpec = spring(
            dampingRatio = 0.7f,
            stiffness = 150f
        ),
        label = "arc_sweep"
    )

    val df = remember { DecimalFormat("#0.#") }
    val speedText = df.format(speedMbps)
    val peakText = FileUtils.formatSpeed(peakSpeedBytesPerSec)

    Box(
        modifier = modifier.size(260.dp),
        contentAlignment = Alignment.Center
    ) {
        Canvas(modifier = Modifier.size(260.dp)) {
            val centerX = size.width / 2
            val centerY = size.height / 2
            val radius = size.minDimension / 2 * 0.85f

            // ━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━
            // Background arc
            // ━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━
            drawArc(
                color = colors.materialScheme.surfaceVariant,
                startAngle = 120f,
                sweepAngle = 300f,
                useCenter = false,
                style = Stroke(width = 20f, cap = StrokeCap.Round),
                topLeft = Offset(centerX - radius, centerY - radius),
                size = androidx.compose.ui.geometry.Size(radius * 2, radius * 2)
            )

            // ━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━
            // Foreground colored gradient arc
            // ━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━
            if (animatedSweep > 0) {
                drawArc(
                    brush = Brush.sweepGradient(
                        colorStops = arrayOf(
                            0.0f to SpeedColors.Blue,
                            0.25f to SpeedColors.Green,
                            0.5f to SpeedColors.Yellow,
                            0.75f to SpeedColors.Orange,
                            1.0f to SpeedColors.Red,
                        ),
                        center = Offset(centerX, centerY)
                    ),
                    startAngle = 120f,
                    sweepAngle = animatedSweep,
                    useCenter = false,
                    style = Stroke(width = 20f, cap = StrokeCap.Round),
                    topLeft = Offset(centerX - radius, centerY - radius),
                    size = androidx.compose.ui.geometry.Size(radius * 2, radius * 2)
                )

                // Glow effect for high speeds
                if (speedMbps > 50) {
                    drawArc(
                        color = SpeedColors.Red.copy(alpha = 0.3f),
                        startAngle = 120f,
                        sweepAngle = animatedSweep,
                        useCenter = false,
                        style = Stroke(width = 30f, cap = StrokeCap.Round),
                        topLeft = Offset(centerX - radius, centerY - radius),
                        size = androidx.compose.ui.geometry.Size(radius * 2, radius * 2)
                    )
                }
            }

            // ━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━
            // Tick marks
            // ━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━
            val tickRadius = radius + 12f
            val tickCount = 30
            for (i in 0..tickCount) {
                val angle = 120.0 + (i.toDouble() / tickCount) * 300.0
                val radian = Math.toRadians(angle)
                val isMajor = i % 5 == 0
                val tickLength = if (isMajor) 12f else 6f
                val tickWidth = if (isMajor) 2.5f else 1.2f

                val startX = centerX + (tickRadius - tickLength) * cos(radian).toFloat()
                val startY = centerY + (tickRadius - tickLength) * sin(radian).toFloat()
                val endX = centerX + tickRadius * cos(radian).toFloat()
                val endY = centerY + tickRadius * sin(radian).toFloat()

                drawLine(
                    color = if (isMajor) colors.materialScheme.onSurface.copy(alpha = 0.6f)
                    else colors.materialScheme.onSurface.copy(alpha = 0.2f),
                    start = Offset(startX, startY),
                    end = Offset(endX, endY),
                    strokeWidth = tickWidth
                )
            }

            // ━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━
            // Needle
            // ━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━
            val needleLength = radius * 0.7f
            val needleAngleRad = Math.toRadians(animatedAngle.toDouble())
            val needleEndX = centerX + needleLength * cos(needleAngleRad).toFloat()
            val needleEndY = centerY + needleLength * sin(needleAngleRad).toFloat()

            // Needle shadow
            drawLine(
                color = colors.accent.copy(alpha = 0.2f),
                start = Offset(centerX, centerY),
                end = Offset(needleEndX + 2, needleEndY + 2),
                strokeWidth = 4f,
                cap = StrokeCap.Round
            )

            // Needle
            drawLine(
                color = colors.accent,
                start = Offset(centerX, centerY),
                end = Offset(needleEndX, needleEndY),
                strokeWidth = 3f,
                cap = StrokeCap.Round
            )

            // ━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━
            // Center hub
            // ━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━
            drawCircle(
                color = colors.materialScheme.surface,
                radius = 40f,
                center = Offset(centerX, centerY)
            )
            drawCircle(
                color = colors.accent,
                radius = 8f,
                center = Offset(centerX, centerY)
            )
        }

        // Speed text overlay
        Column(
            horizontalAlignment = Alignment.CenterHorizontally,
            modifier = Modifier.align(Alignment.Center)
        ) {
            // Offset down to sit below the center hub area
        }

        // Speed number below center hub
        Column(
            horizontalAlignment = Alignment.CenterHorizontally,
            modifier = Modifier.align(Alignment.BottomCenter)
        ) {
            Text(
                text = speedText,
                style = MaterialTheme.typography.headlineLarge.copy(
                    fontWeight = FontWeight.ExtraBold,
                    fontSize = 28.sp
                ),
                color = colors.materialScheme.onBackground
            )
            Text(
                text = "MB/s",
                style = MaterialTheme.typography.labelMedium,
                color = colors.textTertiary
            )
            Text(
                text = "Peak: $peakText",
                style = MaterialTheme.typography.labelSmall,
                color = colors.textTertiary
            )
        }
    }
}

/**
 * Maps speed in MB/s to angle in degrees.
 * 0 MB/s = 120° (start), 120 MB/s = 420° (end of 300° sweep)
 */
private fun speedToAngle(speedMbps: Double): Double {
    val normalizedSpeed = (speedMbps / 120.0).coerceIn(0.0, 1.0)
    return 120.0 + normalizedSpeed * 300.0
}
