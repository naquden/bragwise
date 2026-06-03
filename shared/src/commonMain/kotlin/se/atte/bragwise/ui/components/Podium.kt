package se.atte.bragwise.ui.components

import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.animation.core.Spring
import androidx.compose.animation.core.animateIntAsState
import androidx.compose.animation.core.spring
import androidx.compose.animation.core.tween
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.size
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
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import kotlinx.coroutines.launch
import se.atte.bragwise.domain.LeaderboardEntry
import se.atte.bragwise.theme.AppType
import se.atte.bragwise.theme.ThemePreview

private val goldColor = Color(0xFFFDD835)
private val silverColor = Color(0xFFB0BEC5)
private val bronzeColor = Color(0xFFBF8970)

/**
 * Olympic-style podium with staggered entrance animations.
 * - 3rd place enters first (150ms), 2nd next (500ms), winner last (900ms) with bounce.
 * - Score counts up from 0 after each plinth rises.
 * - Crown drops onto 1st place at 1300ms; glow pulses indefinitely.
 * - Co-winners (isTied = true) both receive a crown emoji.
 * - Pass alreadySeen = true to skip animations and show the settled state immediately.
 */
@Composable
fun Podium(
    entries: List<LeaderboardEntry>,
    myUid: String,
    alreadySeen: Boolean,
    modifier: Modifier = Modifier,
) {
    val winner = entries.getOrNull(0)
    val second = entries.getOrNull(1)
    val third = entries.getOrNull(2)

    val initial = if (alreadySeen) 1f else 0f
    val plinthProgress1 = remember { Animatable(initial) }
    val plinthProgress2 = remember { Animatable(initial) }
    val plinthProgress3 = remember { Animatable(initial) }
    val crownOffset = remember { Animatable(if (alreadySeen) 0f else -60f) }
    val glowAlpha = remember { Animatable(if (alreadySeen) 0.35f else 0f) }

    var showScore1 by remember { mutableStateOf(alreadySeen) }
    var showScore2 by remember { mutableStateOf(alreadySeen) }
    var showScore3 by remember { mutableStateOf(alreadySeen) }

    LaunchedEffect(entries) {
        if (entries.isEmpty()) return@LaunchedEffect
        if (!alreadySeen) {
            if (third != null) {
                launch {
                    kotlinx.coroutines.delay(150)
                    plinthProgress3.animateTo(
                        targetValue = 1f,
                        animationSpec = tween(durationMillis = 450, easing = FastOutSlowInEasing),
                    )
                    showScore3 = true
                }
            }
            if (second != null) {
                launch {
                    kotlinx.coroutines.delay(500)
                    plinthProgress2.animateTo(
                        targetValue = 1f,
                        animationSpec = tween(durationMillis = 450, easing = FastOutSlowInEasing),
                    )
                    showScore2 = true
                }
            }
            if (winner != null) {
                launch {
                    kotlinx.coroutines.delay(900)
                    plinthProgress1.animateTo(
                        targetValue = 1f,
                        animationSpec = spring(
                            dampingRatio = Spring.DampingRatioMediumBouncy,
                            stiffness = Spring.StiffnessLow,
                        ),
                    )
                    showScore1 = true
                }
            }
            kotlinx.coroutines.delay(1300)
            crownOffset.animateTo(
                targetValue = 0f,
                animationSpec = spring(
                    dampingRatio = Spring.DampingRatioMediumBouncy,
                    stiffness = Spring.StiffnessMedium,
                ),
            )
        }
        // Glow pulse runs indefinitely for winner
        if (winner != null) {
            launch {
                while (true) {
                    glowAlpha.animateTo(targetValue = 0.65f, animationSpec = tween(durationMillis = 900))
                    glowAlpha.animateTo(targetValue = 0.25f, animationSpec = tween(durationMillis = 900))
                }
            }
        }
    }

    val score1 by animateIntAsState(
        targetValue = if (showScore1 && winner != null) winner.points else 0,
        animationSpec = tween(durationMillis = 700),
        label = "score1",
    )
    val score2 by animateIntAsState(
        targetValue = if (showScore2 && second != null) second.points else 0,
        animationSpec = tween(durationMillis = 600),
        label = "score2",
    )
    val score3 by animateIntAsState(
        targetValue = if (showScore3 && third != null) third.points else 0,
        animationSpec = tween(durationMillis = 600),
        label = "score3",
    )

    val baseHeight = 160.dp

    Row(
        modifier = modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.Center,
        verticalAlignment = Alignment.Bottom,
    ) {
        if (second != null) {
            PodiumSlot(
                entry = second,
                plinthProgress = plinthProgress2.value,
                plinthTargetHeight = baseHeight * 0.72f,
                score = score2,
                plinthColor = silverColor,
                myUid = myUid,
                isWinner = false,
                crownOffset = null,
                glowAlpha = 0f,
            )
            Spacer(Modifier.width(8.dp))
        }

        if (winner != null) {
            PodiumSlot(
                entry = winner,
                plinthProgress = plinthProgress1.value,
                plinthTargetHeight = baseHeight,
                score = score1,
                plinthColor = goldColor,
                myUid = myUid,
                isWinner = true,
                crownOffset = crownOffset.value,
                glowAlpha = glowAlpha.value,
            )
        }

        if (third != null) {
            Spacer(Modifier.width(8.dp))
            PodiumSlot(
                entry = third,
                plinthProgress = plinthProgress3.value,
                plinthTargetHeight = baseHeight * 0.54f,
                score = score3,
                plinthColor = bronzeColor,
                myUid = myUid,
                isWinner = false,
                crownOffset = null,
                glowAlpha = 0f,
            )
        }
    }
}

@Composable
private fun PodiumSlot(
    entry: LeaderboardEntry,
    plinthProgress: Float,
    plinthTargetHeight: Dp,
    score: Int,
    plinthColor: Color,
    myUid: String,
    isWinner: Boolean,
    crownOffset: Float?,
    glowAlpha: Float,
) {
    val isMe = entry.uid == myUid
    val actualHeight = plinthTargetHeight * plinthProgress.coerceIn(0f, 1.15f)
    val avatarSize = if (isWinner) 60.dp else 46.dp

    Column(
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.Bottom,
    ) {
        // Crown row (reserved height regardless so layout stays stable)
        Box(
            modifier = Modifier
                .size(width = avatarSize, height = 36.dp)
                .graphicsLayer { translationY = crownOffset ?: 0f },
            contentAlignment = Alignment.Center,
        ) {
            if (crownOffset != null) {
                val crownText = if (entry.isTied) "👑👑" else "👑"
                Text(text = crownText, fontSize = if (entry.isTied) 14.sp else 22.sp)
            }
        }

        Spacer(Modifier.height(2.dp))

        // Avatar with winner glow
        Box(contentAlignment = Alignment.Center) {
            if (glowAlpha > 0f && isWinner) {
                Box(
                    modifier = Modifier
                        .size(avatarSize + 20.dp)
                        .clip(androidx.compose.foundation.shape.CircleShape)
                        .background(
                            Brush.radialGradient(
                                colors = listOf(
                                    goldColor.copy(alpha = glowAlpha),
                                    Color.Transparent,
                                ),
                            ),
                        ),
                )
            }
            AvatarBubble(
                displayName = entry.displayName,
                avatarSeed = entry.avatarSeed,
                size = avatarSize,
                isHighlighted = isMe,
            )
        }

        Spacer(Modifier.height(4.dp))

        // Name
        val nameText = if (isMe) "${entry.displayName} 🙋" else entry.displayName
        Text(
            text = nameText,
            style = MaterialTheme.typography.labelLarge,
            textAlign = TextAlign.Center,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
            modifier = Modifier.widthIn(max = 88.dp),
        )

        // Score
        val scoreStyle = if (isWinner) {
            AppType.scoreDisplay.copy(fontSize = 42.sp)
        } else {
            MaterialTheme.typography.headlineSmall
        }
        Text(
            text = score.toString(),
            style = scoreStyle,
            color = if (isWinner) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurface,
        )
        Text(
            text = "pts",
            style = MaterialTheme.typography.labelMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )

        Spacer(Modifier.height(4.dp))

        // Plinth
        if (actualHeight > 0.dp) {
            Box(
                modifier = Modifier
                    .width(88.dp)
                    .height(actualHeight)
                    .clip(RoundedCornerShape(topStart = 8.dp, topEnd = 8.dp))
                    .background(plinthColor),
                contentAlignment = Alignment.Center,
            ) {
                val rankEmoji = when {
                    isWinner -> "🥇"
                    entry.rank == 2 -> "🥈"
                    else -> "🥉"
                }
                Text(text = rankEmoji, style = MaterialTheme.typography.titleMedium)
            }
        }
    }
}

// region Previews

@Preview
@Composable
private fun Podium_ThreePlayers_Preview() {
    ThemePreview {
        Podium(
            entries = listOf(
                LeaderboardEntry(uid = "u1", displayName = "Atte", avatarSeed = "a1", points = 92, rank = 1),
                LeaderboardEntry(uid = "u2", displayName = "Alice", avatarSeed = "a3", points = 78, rank = 2),
                LeaderboardEntry(uid = "u3", displayName = "Bob", avatarSeed = "a5", points = 65, rank = 3),
            ),
            myUid = "u1",
            alreadySeen = true,
        )
    }
}

@Preview
@Composable
private fun Podium_Tie_Preview() {
    ThemePreview {
        Podium(
            entries = listOf(
                LeaderboardEntry(uid = "u1", displayName = "Atte", avatarSeed = "a1", points = 92, rank = 1, isTied = true),
                LeaderboardEntry(uid = "u2", displayName = "Alice", avatarSeed = "a3", points = 92, rank = 1, isTied = true),
                LeaderboardEntry(uid = "u3", displayName = "Bob", avatarSeed = "a5", points = 65, rank = 3),
            ),
            myUid = "u1",
            alreadySeen = true,
        )
    }
}

// endregion
