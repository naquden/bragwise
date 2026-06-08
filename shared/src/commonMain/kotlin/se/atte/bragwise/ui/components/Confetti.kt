package se.atte.bragwise.ui.components

import androidx.compose.animation.core.LinearEasing
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.tween
import androidx.compose.foundation.Canvas
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.drawscope.withTransform
import kotlin.math.absoluteValue
import kotlin.math.cos
import kotlin.math.sin
import kotlin.random.Random

private val confettiColors = listOf(
    Color(0xFFFDD835), // gold
    Color(0xFFE91E63), // pink
    Color(0xFF2196F3), // blue
    Color(0xFF4CAF50), // green
    Color(0xFFFF5722), // deep orange
    Color(0xFF9C27B0), // purple
    Color(0xFF00BCD4), // cyan
    Color(0xFFFF9800), // amber
)

private data class Particle(
    val startXFraction: Float,
    val startYFraction: Float,
    val velocityX: Float,
    val velocityY: Float,
    val angularVelocity: Float,
    val startRotation: Float,
    val color: Color,
    val width: Float,
    val height: Float,
)

private fun generateParticles(count: Int, seed: Int): List<Particle> {
    val random = Random(seed = seed)
    return List(count) { index ->
        val angle = (index.toFloat() / count) * 360f
        val speed = 0.25f + random.nextFloat() * 0.45f
        val angleRad = angle * kotlin.math.PI.toFloat() / 180f
        Particle(
            startXFraction = 0.5f,
            startYFraction = 0.25f,
            velocityX = cos(angleRad) * speed,
            velocityY = sin(angleRad) * speed - 0.35f,
            angularVelocity = (random.nextFloat() - 0.5f) * 720f,
            startRotation = random.nextFloat() * 360f,
            color = confettiColors[index % confettiColors.size],
            width = 6f + random.nextFloat() * 8f,
            height = 10f + random.nextFloat() * 6f,
        )
    }
}

/**
 * One-shot confetti burst. Plays once when composed, then fades out.
 * Position the canvas to cover the entire screen (or relevant area).
 */
@Composable
fun Confetti(modifier: Modifier = Modifier) {
    val seed = remember { Random.nextInt() }
    val particles = remember(seed) { generateParticles(count = 80, seed = seed) }
    var triggered by remember { mutableStateOf(false) }
    LaunchedEffect(Unit) { triggered = true }

    val progress by animateFloatAsState(
        targetValue = if (triggered) 1f else 0f,
        animationSpec = tween(durationMillis = 2400, easing = LinearEasing),
        label = "confetti",
    )

    Canvas(modifier = modifier) {
        val timeSeconds = progress * 2.4f
        val gravity = size.height * 0.4f

        particles.forEach { particle ->
            val drag = 1f - (timeSeconds * 0.25f).coerceIn(0f, 0.7f)
            val px = size.width * particle.startXFraction + particle.velocityX * size.width * timeSeconds * drag
            val py = (size.height * particle.startYFraction
                + particle.velocityY * size.height * timeSeconds * drag
                + 0.5f * gravity * timeSeconds * timeSeconds)
            val rotation = particle.startRotation + particle.angularVelocity * timeSeconds
            val alpha = (1f - progress * 1.2f).coerceIn(0f, 1f)

            if (alpha <= 0f) return@forEach

            withTransform({
                translate(left = px, top = py)
                rotate(degrees = rotation, pivot = Offset.Zero)
            }) {
                drawRect(
                    color = particle.color.copy(alpha = alpha),
                    topLeft = Offset(x = -particle.width / 2f, y = -particle.height / 2f),
                    size = Size(width = particle.width, height = particle.height),
                )
            }
        }
    }
}
