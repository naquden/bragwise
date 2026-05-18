package se.atte.bragwise.ui.components

import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import se.atte.bragwise.theme.Elevation
import se.atte.bragwise.ui.standardPadding
import se.atte.bragwise.theme.appShadow

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
    contentPadding: androidx.compose.foundation.layout.PaddingValues =
        androidx.compose.foundation.layout.PaddingValues(standardPadding),
    content: @Composable ColumnScope.() -> Unit,
) {
    val isDark = isSystemInDarkTheme()
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
    val isDark = isSystemInDarkTheme()
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
            modifier = Modifier.fillMaxWidth().padding(standardPadding),
            horizontalArrangement = Arrangement.spacedBy(12.dp),
            verticalAlignment = androidx.compose.ui.Alignment.CenterVertically,
            content = content,
        )
    }
}
