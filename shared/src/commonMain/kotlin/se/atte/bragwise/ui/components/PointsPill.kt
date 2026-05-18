package se.atte.bragwise.ui.components

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp

/**
 * Tabular figures + small "pts". Uses the body type slot since 14sp ≈ M3
 * `bodyMedium`; tabular figures inherited via the parent theme typography
 * is non-trivial, so we apply `tnum` directly via fontFeatureSettings.
 */
@Composable
fun PointsPill(points: Int, modifier: Modifier = Modifier) {
    Row(
        modifier = modifier
            .background(
                color = MaterialTheme.colorScheme.secondaryContainer,
                shape = RoundedCornerShape(50),
            )
            .padding(horizontal = 12.dp, vertical = 4.dp),
    ) {
        Text(
            text = points.toString(),
            color = MaterialTheme.colorScheme.onSecondaryContainer,
            fontWeight = FontWeight.Bold,
            style = MaterialTheme.typography.bodyLarge.copy(fontFeatureSettings = "tnum"),
        )
        Text(
            text = " pts",
            color = MaterialTheme.colorScheme.onSecondaryContainer,
            style = MaterialTheme.typography.bodyLarge,
        )
    }
}
