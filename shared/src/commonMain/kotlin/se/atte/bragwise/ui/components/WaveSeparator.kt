package se.atte.bragwise.ui.components

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp

@Composable
fun WaveSeparator(
    topColor: Color,
    bottomColor: Color,
    modifier: Modifier = Modifier,
    height: Dp = 24.dp,
    waves: Int = 3,
) {
    Canvas(modifier = modifier.fillMaxWidth().height(height)) {
        val w = size.width
        val h = size.height
        val midY = h / 2f
        val amp = h * 0.9f

        // Fill entire rect with bottom color first
        drawRect(color = bottomColor)

        // Paint top-color region above the sine curve
        val path = Path().apply {
            moveTo(0f, midY)
            val segW = w / (waves * 2)
            var x = 0f
            var peakUp = true
            repeat(waves * 2) {
                val nextX = x + segW
                val cy = if (peakUp) midY - amp else midY + amp
                quadraticTo(x + segW / 2f, cy, nextX, midY)
                x = nextX
                peakUp = !peakUp
            }
            lineTo(w, 0f)
            lineTo(0f, 0f)
            close()
        }
        drawPath(path = path, color = topColor)
    }
}
