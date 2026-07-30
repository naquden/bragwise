package se.atte.bragwise.ui.components

import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.ColorFilter
import androidx.compose.ui.graphics.painter.Painter
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import se.atte.bragwise.theme.Elevation
import se.atte.bragwise.theme.LocalIsDark
import se.atte.bragwise.ui.standardPadding
import se.atte.bragwise.ui.standardPaddingSmall
import se.atte.bragwise.theme.appShadow
import org.jetbrains.compose.resources.stringResource
import bragwise.shared.generated.resources.Res
import bragwise.shared.generated.resources.divider_or

/**
 * Group related controls into a single elevated rectangle. Replaces the
 * "buttons-everywhere" pattern with a stadium-board panel: title + content
 * inside one card, content padded uniformly. Use across forms (Create
 * metadata, Bets composer, SignIn) and stat groupings (Me identity, detail
 * hero).
 */
@Composable
fun SectionCard(
    title: String? = null,
    modifier: Modifier = Modifier,
    titleTextAlign: TextAlign = TextAlign.Unspecified,
    contentPadding: androidx.compose.foundation.layout.PaddingValues =
        androidx.compose.foundation.layout.PaddingValues(standardPadding),
    content: @Composable ColumnScope.() -> Unit,
) {
    val isDark = LocalIsDark.current
    val shape = MaterialTheme.shapes.medium
    Surface(
        modifier = modifier
            .fillMaxWidth()
            .appShadow(Elevation.Card, isDark = isDark, shape = shape),
        color = MaterialTheme.colorScheme.surface,
        shape = shape,
    ) {
        Column(modifier = Modifier.padding(contentPadding)) {
            if (title != null) {
                Text(
                    text = title,
                    style = MaterialTheme.typography.titleLarge,
                    color = MaterialTheme.colorScheme.onSurface,
                    textAlign = titleTextAlign,
                    modifier = Modifier.fillMaxWidth(),
                )
                Spacer(Modifier.height(12.dp))
            }
            content()
        }
    }
}

/**
 * Vertical stack of related rows inside a SectionCard, separated by hairlines.
 * Use for nav-list patterns (Settings, Friends, About) where each row is a
 * tap target.
 */
@Composable
fun ListGroup(
    modifier: Modifier = Modifier,
    content: @Composable ColumnScope.() -> Unit,
) {
    val isDark = LocalIsDark.current
    val shape = MaterialTheme.shapes.medium
    Surface(
        modifier = modifier
            .fillMaxWidth()
            .appShadow(Elevation.Card, isDark = isDark, shape = shape),
        color = MaterialTheme.colorScheme.surface,
        shape = shape,
    ) {
        Column { content() }
    }
}

/** Hairline divider for ListGroup rows. */
@Composable
fun ListGroupDivider() {
    HorizontalDivider(color = MaterialTheme.colorScheme.outline.copy(alpha = 0.3f))
}

/** Vertical breathing room between SectionCard / ListGroup blocks. */
@Composable
fun SectionGap(height: androidx.compose.ui.unit.Dp = standardPadding) {
    Spacer(Modifier.height(height))
}

/** Centered "or" between two mutually exclusive actions (e.g. email vs Apple sign-in). */
@Composable
fun OrDivider(modifier: Modifier = Modifier) {
    Row(
        modifier = modifier.fillMaxWidth().padding(vertical = standardPadding),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(standardPaddingSmall),
    ) {
        HorizontalDivider(
            modifier = Modifier.weight(1f),
            color = MaterialTheme.colorScheme.outline.copy(alpha = 0.3f),
        )
        Text(
            text = stringResource(Res.string.divider_or),
            style = MaterialTheme.typography.labelMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
        HorizontalDivider(
            modifier = Modifier.weight(1f),
            color = MaterialTheme.colorScheme.outline.copy(alpha = 0.3f),
        )
    }
}

/**
 * One full-width colored band that owns a Challenges-screen group.
 * No rounded corners — stacks edge-to-edge with WaveSeparator between bands.
 * Cards inside receive horizontal padding so they don't touch screen edges.
 * Header + cards are centered within ContentMaxWidth on wide screens.
 */
@Composable
fun ColoredSection(
    bg: Color,
    title: String,
    icon: String,
    onTitleColor: Color,
    modifier: Modifier = Modifier,
    iconVector: ImageVector? = null,
    iconPainter: Painter? = null,
    trailing: String? = null,
    topInset: Boolean = false,
    content: @Composable ColumnScope.() -> Unit,
) {
    Box(modifier = modifier.fillMaxWidth().background(bg)) {
        Box(
            modifier = Modifier.fillMaxWidth(),
            contentAlignment = Alignment.TopCenter,
        ) {
            Box(modifier = Modifier.widthIn(max = se.atte.bragwise.ui.ContentMaxWidth).fillMaxWidth()) {
                Column(modifier = Modifier
                    .then(if (topInset) Modifier.statusBarsPadding() else Modifier)
                    .padding(vertical = standardPadding)) {
                    Row(
                        modifier = Modifier.padding(horizontal = standardPadding),
                        verticalAlignment = Alignment.CenterVertically,
                    ) {
                        when {
                            iconPainter != null -> Image(painter = iconPainter, contentDescription = null, modifier = Modifier.size(24.dp), colorFilter = ColorFilter.tint(onTitleColor))
                            iconVector != null -> Icon(imageVector = iconVector, contentDescription = null, modifier = Modifier.size(24.dp), tint = onTitleColor)
                            else -> Text(text = icon, style = MaterialTheme.typography.titleLarge)
                        }
                        Spacer(Modifier.width(standardPaddingSmall))
                        Column {
                            Text(
                                text = title,
                                style = MaterialTheme.typography.titleLarge,
                                color = onTitleColor,
                            )
                            if (trailing != null) {
                                Text(
                                    text = trailing,
                                    style = MaterialTheme.typography.bodyLarge,
                                    color = onTitleColor.copy(alpha = 0.75f),
                                )
                            }
                        }
                    }
                    Spacer(Modifier.height(standardPaddingSmall))
                    Column(
                        modifier = Modifier.padding(horizontal = standardPadding),
                        verticalArrangement = Arrangement.spacedBy(standardPaddingSmall),
                        content = content,
                    )
                }
            }
        }
    }
}

/**
 * Multi-column card grid that avoids FlowRow's stretching-of-lone-last-items problem.
 * Items are chunked into rows of [effectiveCols]; each item gets equal weight.
 * effectiveCols = min(columns, items.size) so a lone item never becomes a full-width
 * slab or a 1/4 sliver — it renders as one centered, width-capped card.
 * If the last row is short, empty Spacer weight slots fill the remaining positions.
 *
 * When [columns] == 1 (mobile default) each Row contains one weight(1f) Box = full
 * width, visually identical to a plain Column forEach.
 */
@Composable
fun <T> CardGrid(
    items: List<T>,
    columns: Int,
    modifier: Modifier = Modifier,
    maxItemWidth: androidx.compose.ui.unit.Dp = 400.dp,
    item: @Composable (T) -> Unit,
) {
    val effectiveCols = columns.coerceAtMost(items.size).coerceAtLeast(1)
    Column(
        modifier = Modifier.fillMaxWidth().then(modifier),
        verticalArrangement = Arrangement.spacedBy(standardPaddingSmall),
    ) {
        items.chunked(effectiveCols).forEach { rowItems ->
            Row(
                horizontalArrangement = Arrangement.spacedBy(standardPaddingSmall, Alignment.CenterHorizontally),
                modifier = Modifier.fillMaxWidth(),
            ) {
                rowItems.forEach { data ->
                    Box(Modifier.weight(1f).widthIn(max = maxItemWidth)) { item(data) }
                }
                val missing = effectiveCols - rowItems.size
                repeat(missing) {
                    Spacer(Modifier.weight(1f).widthIn(max = maxItemWidth))
                }
            }
        }
    }
}

/** Pinned bottom action bar — single primary CTA, optionally with neutral secondary. */
@Composable
fun BottomActionBar(
    modifier: Modifier = Modifier,
    content: @Composable androidx.compose.foundation.layout.RowScope.() -> Unit,
) {
    Surface(
        modifier = modifier.fillMaxWidth(),
        color = MaterialTheme.colorScheme.surface,
        tonalElevation = 2.dp,
    ) {
        androidx.compose.foundation.layout.Row(
            modifier = Modifier.fillMaxWidth().navigationBarsPadding().padding(standardPadding),
            horizontalArrangement = Arrangement.spacedBy(12.dp),
            verticalAlignment = androidx.compose.ui.Alignment.CenterVertically,
            content = content,
        )
    }
}
