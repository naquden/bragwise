package se.atte.bragwise.ui.components

import androidx.compose.animation.core.LinearEasing
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.tween
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.drawscope.DrawScope
import androidx.compose.ui.graphics.drawscope.rotate
import androidx.compose.ui.tooling.preview.Preview
import kotlin.math.PI
import kotlin.math.abs
import kotlin.math.sin
import kotlin.random.Random
import se.atte.bragwise.theme.LocalIsDark
import se.atte.bragwise.theme.ThemePreview

private data class Sparkle(
    val xFraction: Float,
    val yFraction: Float,
    val baseSize: Float,   // px at full twinkle
    val phase: Float,      // 0..1 offset into the twinkle cycle
    val speed: Float,      // cycles multiplier
    val rotation: Float,   // static rotation of the 4-point star
)

private fun generateSparkles(count: Int, seed: Int): List<Sparkle> {
    val random = Random(seed = seed)
    return List(count) {
        Sparkle(
            xFraction = random.nextFloat(),
            yFraction = random.nextFloat(),
            baseSize = 8f + random.nextFloat() * 22f,
            phase = random.nextFloat(),
            speed = 0.6f + random.nextFloat() * 1.4f,
            rotation = random.nextFloat() * 90f,
        )
    }
}

@Composable
fun PlatinumBackground(modifier: Modifier = Modifier) {
    val isDark = LocalIsDark.current
    val seed = remember { 0x5A1A } // deterministic field (avoids Math.random in script)
    val sparkles = remember(seed) { generateSparkles(count = 34, seed = seed) }

    val infiniteTransition = rememberInfiniteTransition(label = "platinum_bg")

    // Master clock — drives all sparkle twinkles, 4s loop.
    val clock = infiniteTransition.animateFloat(
        initialValue = 0f,
        targetValue = 1f,
        animationSpec = infiniteRepeatable(
            animation = tween(durationMillis = 4000, easing = LinearEasing),
        ),
    ).value

    // Slow ambient sheen drift (gives the metal subtle life under the stars).
    val sheen = infiniteTransition.animateFloat(
        initialValue = 0f,
        targetValue = 1f,
        animationSpec = infiniteRepeatable(
            animation = tween(durationMillis = 9000, easing = LinearEasing),
        ),
    ).value

    val baseColors: List<Color>
    val sheenColor: Color
    val starColor: Color

    if (isDark) {
        baseColors = listOf(
            Color(0xFF12161B),
            Color(0xFF222933),
            Color(0xFF181D24),
            Color(0xFF2B333E),
        )
        sheenColor = Color(0xFFAEC2DA).copy(alpha = 0.10f)
        starColor = Color(0xFFE8F1FF)
    } else {
        baseColors = listOf(
            Color(0xFFEFF2F6),
            Color(0xFFD8DDE6),
            Color(0xFFEAEDF2),
            Color(0xFFCFD6E0),
        )
        sheenColor = Color(0xFFFFFFFF).copy(alpha = 0.35f)
        starColor = Color(0xFFFFFFFF)
    }

    Canvas(modifier = modifier) {
        val w = size.width
        val h = size.height

        // ── Base metallic gradient (diagonal brushed platinum) ──
        drawRect(
            brush = Brush.linearGradient(
                colors = baseColors,
                start = Offset(0f, 0f),
                end = Offset(w, h),
            ),
            size = size,
        )

        // ── Ambient drifting sheen (a soft wide radial highlight) ──
        val sheenCx = w * (0.2f + 0.6f * sheen)
        val sheenCy = h * (0.3f + 0.2f * sin(sheen * 2f * PI.toFloat()))
        drawCircle(
            brush = Brush.radialGradient(
                colors = listOf(sheenColor, Color.Transparent),
                center = Offset(sheenCx, sheenCy),
                radius = w * 0.7f,
            ),
            center = Offset(sheenCx, sheenCy),
            radius = w * 0.7f,
        )

        // ── Twinkling sparkle stars ──
        sparkles.forEach { s ->
            // Twinkle = abs(sin) so each star fades 0→1→0 on its own phase.
            val t = (clock * s.speed + s.phase)
            val twinkle = abs(sin(t * PI.toFloat()))
            if (twinkle <= 0.02f) return@forEach

            val cx = s.xFraction * w
            val cy = s.yFraction * h
            val len = s.baseSize * twinkle
            val alpha = (twinkle * twinkle).coerceIn(0f, 1f)

            drawSparkle(
                center = Offset(cx, cy),
                length = len,
                rotation = s.rotation,
                color = starColor.copy(alpha = alpha),
            )
        }
    }
}

/** Four-point sparkle: two crossed tapered strokes + a bright core dot. */
private fun DrawScope.drawSparkle(
    center: Offset,
    length: Float,
    rotation: Float,
    color: Color,
) {
    val core = color
    val thin = (length * 0.10f).coerceAtLeast(1f)
    rotate(degrees = rotation, pivot = center) {
        // vertical spike
        drawLine(
            color = core,
            start = Offset(center.x, center.y - length),
            end = Offset(center.x, center.y + length),
            strokeWidth = thin,
            cap = StrokeCap.Round,
        )
        // horizontal spike
        drawLine(
            color = core,
            start = Offset(center.x - length, center.y),
            end = Offset(center.x + length, center.y),
            strokeWidth = thin,
            cap = StrokeCap.Round,
        )
    }
    // bright glowing core
    drawCircle(
        brush = Brush.radialGradient(
            colors = listOf(core, Color.Transparent),
            center = center,
            radius = length * 0.6f,
        ),
        center = center,
        radius = length * 0.6f,
    )
}

// region Previews

@Preview(showBackground = true)
@Composable
private fun PlatinumBackground_Light_Preview() {
    ThemePreview(darkTheme = false) {
        PlatinumBackground(modifier = Modifier.fillMaxSize())
    }
}

@Preview(showBackground = true)
@Composable
private fun PlatinumBackground_Dark_Preview() {
    ThemePreview(darkTheme = true) {
        PlatinumBackground(modifier = Modifier.fillMaxSize())
    }
}

// endregion
