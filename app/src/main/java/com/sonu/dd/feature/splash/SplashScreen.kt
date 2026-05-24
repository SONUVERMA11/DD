package com.sonu.dd.feature.splash

import androidx.compose.animation.core.*
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.draw.scale
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.sonu.dd.core.ui.theme.DDThemeColors
import kotlinx.coroutines.delay
import kotlin.math.cos
import kotlin.math.sin
import kotlin.random.Random

data class Particle(val x: Float, val y: Float, val radius: Float, val alpha: Float, val speed: Float, val angle: Float)

@Composable
fun SplashScreen(onFinished: () -> Unit) {
    val colors = DDThemeColors.current
    var visible by remember { mutableStateOf(false) }
    var brandingVisible by remember { mutableStateOf(false) }

    // Particle state
    val particles = remember {
        List(40) { Particle(x = 0.5f, y = 0.5f, radius = Random.nextFloat() * 6f + 2f, alpha = Random.nextFloat() * 0.7f + 0.3f, speed = Random.nextFloat() * 3f + 1f, angle = Random.nextFloat() * 360f) }
    }
    val animationProgress = rememberInfiniteTransition(label = "particles")
    val particleTime by animationProgress.animateFloat(0f, 1f, infiniteRepeatable(tween(3000, easing = LinearEasing)), label = "pt")

    LaunchedEffect(Unit) {
        visible = true
        delay(600)
        brandingVisible = true
        delay(1900)
        onFinished()
    }

    val logoScale by animateFloatAsState(if (visible) 1f else 0.3f, spring(Spring.DampingRatioMediumBouncy, Spring.StiffnessLow), label = "ls")
    val logoAlpha by animateFloatAsState(if (visible) 1f else 0f, tween(800), label = "la")
    val brandingAlpha by animateFloatAsState(if (brandingVisible) 1f else 0f, tween(600), label = "ba")

    Box(Modifier.fillMaxSize().background(colors.materialScheme.background), contentAlignment = Alignment.Center) {
        // Particle burst
        Canvas(Modifier.fillMaxSize()) {
            particles.forEach { p ->
                val dist = p.speed * particleTime * 200f
                val rad = Math.toRadians(p.angle.toDouble())
                val px = size.width * p.x + dist * cos(rad).toFloat()
                val py = size.height * p.y + dist * sin(rad).toFloat()
                val alpha = (p.alpha * (1f - particleTime)).coerceIn(0f, 1f)
                drawCircle(color = colors.accent.copy(alpha = alpha), radius = p.radius * (1f - particleTime * 0.5f), center = Offset(px, py))
            }
        }

        Column(horizontalAlignment = Alignment.CenterHorizontally) {
            Text("DD", style = MaterialTheme.typography.displayLarge.copy(fontWeight = FontWeight.ExtraBold), color = colors.accent, modifier = Modifier.scale(logoScale).alpha(logoAlpha))
            Spacer(Modifier.height(8.dp))
            Text("Deep Downloader", style = MaterialTheme.typography.titleMedium, color = colors.materialScheme.onBackground, modifier = Modifier.alpha(logoAlpha))
            Spacer(Modifier.height(32.dp))
            Text("Made with ❤\uFE0F by Sonu Verma", style = MaterialTheme.typography.bodyMedium, color = colors.textSecondary, fontWeight = FontWeight.Medium, modifier = Modifier.alpha(brandingAlpha))
        }
    }
}
