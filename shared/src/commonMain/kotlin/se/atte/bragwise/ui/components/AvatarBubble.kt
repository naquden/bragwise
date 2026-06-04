package se.atte.bragwise.ui.components

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import kotlin.math.absoluteValue

private val avatarPalette = listOf(
    Color(0xFF6366F1), // indigo
    Color(0xFF8B5CF6), // violet
    Color(0xFFEC4899), // pink
    Color(0xFFEF4444), // red
    Color(0xFFF97316), // orange
    Color(0xFF22C55E), // green
    Color(0xFF14B8A6), // teal
    Color(0xFF3B82F6), // blue
    Color(0xFFEAB308), // yellow
)

@Composable
fun AvatarBubble(
    displayName: String,
    avatarSeed: String,
    size: Dp = 48.dp,
    isHighlighted: Boolean = false,
    modifier: Modifier = Modifier,
) {
    val backgroundColor = remember(avatarSeed) {
        avatarPalette[avatarSeed.hashCode().absoluteValue % avatarPalette.size]
    }
    val borderModifier = if (isHighlighted) {
        Modifier.border(width = 3.dp, color = Color.White, shape = CircleShape)
    } else Modifier

    Box(
        modifier = modifier
            .size(size)
            .clip(CircleShape)
            .background(if (isFlagSeed(avatarSeed)) Color.Transparent else backgroundColor)
            .then(borderModifier),
        contentAlignment = Alignment.Center,
    ) {
        when {
            isFlagSeed(avatarSeed) -> FlagImage(
                code = flagCodeOf(avatarSeed),
                size = size,
                modifier = Modifier.clip(CircleShape),
            )
            !isLegacySeed(avatarSeed) -> Text(
                text = avatarSeed,
                fontSize = (size.value * 0.5f).sp,
            )
            else -> Text(
                text = displayName.firstOrNull()?.uppercaseChar()?.toString() ?: "?",
                style = MaterialTheme.typography.titleMedium,
                color = Color.White,
            )
        }
    }
}
