package se.atte.bragwise.ui.components

import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.animation.core.tween
import kotlinx.coroutines.launch
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import se.atte.bragwise.domain.HeadToHead
import se.atte.bragwise.theme.ThemePreview

private val BASE_HEIGHT = 100.dp
private val MIN_HEIGHT = 16.dp

@Composable
fun HeadToHeadPodium(
    myDisplayName: String,
    myAvatarSeed: String,
    theirDisplayName: String,
    theirAvatarSeed: String,
    record: HeadToHead.Record,
    modifier: Modifier = Modifier,
) {
    val myWins = record.wins
    val theirWins = record.losses
    val ties = record.ties
    val maxWins = maxOf(myWins, theirWins, 1)

    val myProgress = remember { Animatable(0f) }
    val theirProgress = remember { Animatable(0f) }
    var showCounts by remember { mutableStateOf(false) }

    LaunchedEffect(myWins, theirWins) {
        showCounts = false
        myProgress.snapTo(0f)
        theirProgress.snapTo(0f)
        launch {
            myProgress.animateTo(
                targetValue = myWins.toFloat() / maxWins,
                animationSpec = tween(durationMillis = 500, easing = FastOutSlowInEasing),
            )
        }
        theirProgress.animateTo(
            targetValue = theirWins.toFloat() / maxWins,
            animationSpec = tween(durationMillis = 500, easing = FastOutSlowInEasing),
        )
        showCounts = true
    }

    Column(
        modifier = modifier.fillMaxWidth(),
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.Center,
            verticalAlignment = Alignment.Bottom,
        ) {
            H2HSlot(
                displayName = myDisplayName,
                avatarSeed = myAvatarSeed,
                wins = if (showCounts) myWins else 0,
                barProgress = myProgress.value,
                isHighlighted = true,
                barColor = MaterialTheme.colorScheme.primary,
            )
            Spacer(Modifier.width(24.dp))
            H2HSlot(
                displayName = theirDisplayName,
                avatarSeed = theirAvatarSeed,
                wins = if (showCounts) theirWins else 0,
                barProgress = theirProgress.value,
                isHighlighted = false,
                barColor = MaterialTheme.colorScheme.secondary,
            )
        }

        if (ties > 0) {
            Spacer(Modifier.height(8.dp))
            Text(
                text = "$ties ${if (ties == 1) "tie" else "ties"}",
                style = MaterialTheme.typography.labelMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
    }
}

@Composable
private fun H2HSlot(
    displayName: String,
    avatarSeed: String,
    wins: Int,
    barProgress: Float,
    isHighlighted: Boolean,
    barColor: androidx.compose.ui.graphics.Color,
) {
    val barHeight = (BASE_HEIGHT * barProgress.coerceIn(0f, 1f)).coerceAtLeast(MIN_HEIGHT)

    Column(
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.Bottom,
    ) {
        AvatarBubble(
            displayName = displayName,
            avatarSeed = avatarSeed,
            size = 44.dp,
            isHighlighted = isHighlighted,
        )

        Spacer(Modifier.height(4.dp))

        Text(
            text = displayName,
            style = MaterialTheme.typography.labelLarge,
            textAlign = TextAlign.Center,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
            modifier = Modifier.widthIn(max = 88.dp),
        )

        Text(
            text = "$wins ${if (wins == 1) "win" else "wins"}",
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )

        Spacer(Modifier.height(4.dp))

        Box(
            modifier = Modifier
                .width(72.dp)
                .height(barHeight)
                .clip(RoundedCornerShape(topStart = 6.dp, topEnd = 6.dp))
                .background(barColor),
        )
    }
}

// region Previews

@Preview
@Composable
private fun H2HPodium_MeWinning_Preview() {
    ThemePreview {
        HeadToHeadPodium(
            myDisplayName = "You",
            myAvatarSeed = "a1",
            theirDisplayName = "Alice",
            theirAvatarSeed = "a2",
            record = HeadToHead.Record(wins = 5, losses = 2, ties = 1),
        )
    }
}

@Preview
@Composable
private fun H2HPodium_ThemWinning_Preview() {
    ThemePreview {
        HeadToHeadPodium(
            myDisplayName = "You",
            myAvatarSeed = "a1",
            theirDisplayName = "Bob",
            theirAvatarSeed = "a3",
            record = HeadToHead.Record(wins = 1, losses = 4, ties = 0),
        )
    }
}

@Preview
@Composable
private fun H2HPodium_Tie_Preview() {
    ThemePreview {
        HeadToHeadPodium(
            myDisplayName = "You",
            myAvatarSeed = "a1",
            theirDisplayName = "Charlie",
            theirAvatarSeed = "a4",
            record = HeadToHead.Record(wins = 3, losses = 3, ties = 2),
        )
    }
}

@Preview
@Composable
private fun H2HPodium_NoHistory_Preview() {
    ThemePreview {
        HeadToHeadPodium(
            myDisplayName = "You",
            myAvatarSeed = "a1",
            theirDisplayName = "Dave",
            theirAvatarSeed = "a5",
            record = HeadToHead.Record(wins = 0, losses = 0, ties = 0),
        )
    }
}

// endregion
