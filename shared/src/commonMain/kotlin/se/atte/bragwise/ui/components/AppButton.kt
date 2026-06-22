package se.atte.bragwise.ui.components

import androidx.compose.animation.core.animateDpAsState
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.interaction.collectIsPressedAsState
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.FilterChip
import androidx.compose.material3.FilterChipDefaults
import androidx.compose.material3.LocalTextStyle
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.SelectableChipColors
import androidx.compose.material3.Surface
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import se.atte.bragwise.theme.LocalIsDark

/**
 * Duolingo-style "pressed-up" primary CTA button. Uses tertiary (green) as
 * the default container. A flat 4dp bottom-border drawn behind the surface
 * gives the tactile 3-D lifted look; on press it collapses to 2dp and the
 * button shifts 2dp down via `offset`, mimicking a physical key press.
 *
 * Shape is `MaterialTheme.shapes.small` (8dp) so the rounded theme applies.
 * Pass `role = AppButtonRole.Secondary` to use `primary` (sky-blue) instead.
 */
enum class AppButtonRole { Primary, Secondary }

@Composable
fun AppButton(
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    enabled: Boolean = true,
    role: AppButtonRole = AppButtonRole.Primary,
    content: @Composable androidx.compose.foundation.layout.RowScope.() -> Unit,
) {
    val isDark = LocalIsDark.current
    val interactionSource = remember { MutableInteractionSource() }
    val isPressed by interactionSource.collectIsPressedAsState()

    val containerColor = when {
        !enabled -> MaterialTheme.colorScheme.onSurface.copy(alpha = 0.12f)
        role == AppButtonRole.Secondary -> MaterialTheme.colorScheme.primary
        else -> MaterialTheme.colorScheme.tertiary
    }
    val contentColor = when {
        !enabled -> MaterialTheme.colorScheme.onSurface.copy(alpha = 0.38f)
        role == AppButtonRole.Secondary -> MaterialTheme.colorScheme.onPrimary
        else -> MaterialTheme.colorScheme.onTertiary
    }
    val shadowColor = when {
        !enabled -> Color.Transparent
        role == AppButtonRole.Secondary -> MaterialTheme.colorScheme.primary.copy(alpha = if (isDark) 0.6f else 0.4f)
        else -> MaterialTheme.colorScheme.tertiary.copy(alpha = if (isDark) 0.6f else 0.4f)
    }

    val bottomShadowHeight: Dp by animateDpAsState(
        targetValue = if (isPressed || !enabled) 0.dp else 4.dp,
        label = "bottomShadow",
    )
    val pressOffset: Dp by animateDpAsState(
        targetValue = if (isPressed && enabled) 2.dp else 0.dp,
        label = "pressOffset",
    )

    val shape = MaterialTheme.shapes.small
    val cornerRadius = 8.dp

    Surface(
        modifier = modifier
            .heightIn(min = 44.dp)
            .offset(y = pressOffset)
            .drawBehind {
                val cr = cornerRadius.toPx()
                val shadowPx = bottomShadowHeight.toPx()
                if (shadowPx > 0f) {
                    drawRoundRect(
                        color = shadowColor,
                        topLeft = Offset(0f, size.height - cr + shadowPx * 0.25f),
                        size = Size(size.width, cr + shadowPx),
                        cornerRadius = CornerRadius(cr, cr),
                    )
                }
            }
            .clickable(
                interactionSource = interactionSource,
                indication = null,
                enabled = enabled,
                onClick = onClick,
            ),
        color = containerColor,
        contentColor = contentColor,
        shape = shape,
    ) {
        CompositionLocalProvider(LocalTextStyle provides MaterialTheme.typography.labelLarge) {
            Row(
                modifier = Modifier.padding(horizontal = 24.dp, vertical = 12.dp),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.Center,
                content = content,
            )
        }
    }
}

@Composable
fun AppTextButton(
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    enabled: Boolean = true,
    color: androidx.compose.ui.graphics.Color = MaterialTheme.colorScheme.primary,
    content: @Composable androidx.compose.foundation.layout.RowScope.() -> Unit,
) {
    val resolved = if (enabled) color else MaterialTheme.colorScheme.onSurface.copy(alpha = 0.38f)
    Surface(
        modifier = modifier
            .heightIn(min = 40.dp)
            .clickable(enabled = enabled, onClick = onClick),
        color = androidx.compose.ui.graphics.Color.Transparent,
        shape = MaterialTheme.shapes.small,
    ) {
        CompositionLocalProvider(
            LocalTextStyle provides MaterialTheme.typography.labelLarge.copy(color = resolved),
        ) {
            Row(
                modifier = Modifier.padding(horizontal = 16.dp, vertical = 10.dp),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.Center,
                content = content,
            )
        }
    }
}

@Composable
fun AppOutlinedButton(
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    enabled: Boolean = true,
    content: @Composable androidx.compose.foundation.layout.RowScope.() -> Unit,
) {
    val outline = if (enabled) MaterialTheme.colorScheme.outline
                  else MaterialTheme.colorScheme.outline.copy(alpha = 0.38f)
    val contentColor = if (enabled) MaterialTheme.colorScheme.primary
                       else MaterialTheme.colorScheme.onSurface.copy(alpha = 0.38f)
    val shape = MaterialTheme.shapes.small

    Box(
        contentAlignment = Alignment.Center,
        modifier = modifier
            .heightIn(min = 44.dp)
            .border(BorderStroke(1.dp, outline), shape)
            .clickable(enabled = enabled, onClick = onClick),
    ) {
        CompositionLocalProvider(
            LocalTextStyle provides MaterialTheme.typography.labelLarge.copy(color = contentColor),
        ) {
            Row(
                modifier = Modifier.padding(horizontal = 24.dp, vertical = 10.dp),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.Center,
                content = content,
            )
        }
    }
}

@Composable
fun AppFilterChip(
    selected: Boolean,
    onClick: () -> Unit,
    label: @Composable () -> Unit,
    modifier: Modifier = Modifier,
    enabled: Boolean = true,
    colors: SelectableChipColors = FilterChipDefaults.filterChipColors(),
) {
    FilterChip(
        selected = selected,
        onClick = onClick,
        label = label,
        modifier = modifier,
        enabled = enabled,
        shape = MaterialTheme.shapes.small,
        colors = colors,
    )
}
