package com.sonu.dd.feature.splash

import androidx.compose.animation.core.*
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.draw.blur
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.scale
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.drawscope.rotate
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.sonu.dd.core.ui.theme.DDThemeColors
import kotlinx.coroutines.delay
import kotlin.math.cos
import kotlin.math.sin
import kotlin.random.Random

data class SplashParticle(
    val x: Float, val y: Float,
    val radius: Float, val alpha: Float,
    val speed: Float, val angle: Float,
    val color: Int // 0 = accent, 1 = secondary
)

@Composable
fun SplashScreen(onFinished: () -> Unit) {
    val colors = DDThemeColors.current
    var logoVisible by remember { mutableStateOf(false) }
    var taglineVisible by remember { mutableStateOf(false) }
    var brandingVisible by remember { mutableStateOf(false) }
    var ringVisible by remember { mutableStateOf(false) }

    // Particles
    val particles = remember {
        List(80) {
            SplashParticle(
                x = 0.5f + (Random.nextFloat() - 0.5f) * 0.1f,
                y = 0.5f + (Random.nextFloat() - 0.5f) * 0.1f,
                radius = Random.nextFloat() * 5f + 1f,
                alpha = Random.nextFloat() * 0.8f + 0.2f,
                speed = Random.nextFloat() * 4f + 1.5f,
                angle = Random.nextFloat() * 360f,
                color = if (Random.nextFloat() > 0.6f) 1 else 0
            )
        }
    }

    val infiniteTransition = rememberInfiniteTransition(label = "splash")
    val particleTime by infiniteTransition.animateFloat(
        0f, 1f,
        infiniteRepeatable(tween(4000, easing = LinearEasing)),
        label = "pt"
    )
    val ringRotation by infiniteTransition.animateFloat(
        0f, 360f,
        infiniteRepeatable(tween(6000, easing = LinearEasing)),
        label = "ring"
    )
    val pulseScale by infiniteTransition.animateFloat(
        0.95f, 1.05f,
        infiniteRepeatable(tween(1200), RepeatMode.Reverse),
        label = "pulse"
    )

    LaunchedEffect(Unit) {
        delay(200)
        ringVisible = true
        delay(400)
        logoVisible = true
        delay(500)
        taglineVisible = true
        delay(400)
        brandingVisible = true
        delay(1500)
        onFinished()
    }

    val logoScale by animateFloatAsState(
        if (logoVisible) 1f else 0.1f,
        spring(dampingRatio = Spring.DampingRatioMediumBouncy, stiffness = Spring.StiffnessLow),
        label = "ls"
    )
    val logoAlpha by animateFloatAsState(if (logoVisible) 1f else 0f, tween(600), label = "la")
    val taglineAlpha by animateFloatAsState(if (taglineVisible) 1f else 0f, tween(500), label = "ta")
    val brandingAlpha by animateFloatAsState(if (brandingVisible) 1f else 0f, tween(600), label = "ba")
    val ringAlpha by animateFloatAsState(if (ringVisible) 0.6f else 0f, tween(1000), label = "ra")
    val ringScale by animateFloatAsState(
        if (ringVisible) 1f else 0.3f,
        spring(dampingRatio = 0.6f, stiffness = 100f),
        label = "rs"
    )

    val accentColor = colors.accent
    val secondaryColor = Color(0xFF6C63FF)

    Box(
        Modifier
            .fillMaxSize()
            .background(
                Brush.radialGradient(
                    colors = listOf(
                        colors.materialScheme.background,
                        colors.materialScheme.background,
                        Color(0xFF050810)
                    ),
                    radius = 1200f
                )
            ),
        contentAlignment = Alignment.Center
    ) {
        // Background glow blobs
        Canvas(Modifier.fillMaxSize().blur(80.dp)) {
            // Accent glow
            drawCircle(
                color = accentColor.copy(alpha = 0.08f),
                radius = 300f,
                center = Offset(size.width * 0.3f, size.height * 0.4f)
            )
            // Secondary glow
            drawCircle(
                color = secondaryColor.copy(alpha = 0.06f),
                radius = 250f,
                center = Offset(size.width * 0.7f, size.height * 0.6f)
            )
        }

        // Particle burst from center
        Canvas(Modifier.fillMaxSize().alpha(logoAlpha)) {
            particles.forEach { p ->
                val dist = p.speed * particleTime * 220f
                val rad = Math.toRadians(p.angle.toDouble())
                val px = size.width * p.x + dist * cos(rad).toFloat()
                val py = size.height * p.y + dist * sin(rad).toFloat()
                val alpha = (p.alpha * (1f - particleTime * 0.8f)).coerceIn(0f, 1f)
                val particleColor = if (p.color == 0) accentColor else secondaryColor
                drawCircle(
                    color = particleColor.copy(alpha = alpha),
                    radius = p.radius * (1f - particleTime * 0.4f),
                    center = Offset(px, py)
                )
            }
        }

        // Rotating ring
        Canvas(
            Modifier
                .size(200.dp)
                .scale(ringScale)
                .alpha(ringAlpha)
        ) {
            rotate(ringRotation) {
                drawArc(
                    brush = Brush.sweepGradient(
                        colors = listOf(
                            accentColor.copy(alpha = 0.8f),
                            secondaryColor.copy(alpha = 0.4f),
                            Color.Transparent,
                            Color.Transparent,
                            accentColor.copy(alpha = 0.8f)
                        )
                    ),
                    startAngle = 0f,
                    sweepAngle = 270f,
                    useCenter = false,
                    style = Stroke(width = 3.dp.toPx(), cap = StrokeCap.Round),
                    topLeft = Offset.Zero,
                    size = size
                )
            }
        }

        // Inner glow circle
        Canvas(
            Modifier
                .size(160.dp)
                .scale(pulseScale * logoScale)
                .alpha(logoAlpha * 0.15f)
        ) {
            drawCircle(
                brush = Brush.radialGradient(
                    colors = listOf(accentColor, Color.Transparent),
                    radius = size.minDimension / 2
                )
            )
        }

        // Content
        Column(
            horizontalAlignment = Alignment.CenterHorizontally,
            modifier = Modifier.padding(horizontal = 48.dp)
        ) {
            // Logo DD
            Text(
                text = "DD",
                style = MaterialTheme.typography.displayLarge.copy(
                    fontSize = 72.sp,
                    fontWeight = FontWeight.ExtraBold,
                    letterSpacing = (-3).sp
                ),
                color = accentColor,
                modifier = Modifier
                    .scale(logoScale)
                    .alpha(logoAlpha)
            )

            Spacer(Modifier.height(8.dp))

            // Full name
            Text(
                text = "Deep Downloader",
                style = MaterialTheme.typography.headlineMedium,
                color = colors.materialScheme.onBackground,
                fontWeight = FontWeight.SemiBold,
                modifier = Modifier.alpha(logoAlpha)
            )

            Spacer(Modifier.height(16.dp))

            // Tagline
            Text(
                text = "Download Smarter, Deeper.",
                style = MaterialTheme.typography.bodyLarge,
                color = accentColor.copy(alpha = 0.8f),
                fontWeight = FontWeight.Medium,
                textAlign = TextAlign.Center,
                modifier = Modifier.alpha(taglineAlpha)
            )

            Spacer(Modifier.height(40.dp))

            // Loading indicator
            Box(
                modifier = Modifier
                    .alpha(taglineAlpha)
                    .width(120.dp)
                    .height(3.dp)
                    .clip(RoundedCornerShape(2.dp))
                    .background(colors.materialScheme.surface)
            ) {
                val loadProgress by infiniteTransition.animateFloat(
                    0f, 1f,
                    infiniteRepeatable(tween(1200, easing = LinearEasing)),
                    label = "load"
                )
                Box(
                    Modifier
                        .fillMaxHeight()
                        .fillMaxWidth(loadProgress)
                        .clip(RoundedCornerShape(2.dp))
                        .background(
                            Brush.horizontalGradient(
                                colors = listOf(accentColor, secondaryColor)
                            )
                        )
                )
            }

            Spacer(Modifier.height(60.dp))

            // Branding
            Text(
                text = "Made with ❤\uFE0F by Sonu Verma",
                style = MaterialTheme.typography.bodyMedium,
                color = colors.textSecondary.copy(alpha = 0.7f),
                fontWeight = FontWeight.Medium,
                modifier = Modifier.alpha(brandingAlpha)
            )
        }
    }
}
