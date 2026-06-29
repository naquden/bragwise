package se.atte.bragwise.ui.components

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.size
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import bragwise.shared.generated.resources.Res
import bragwise.shared.generated.resources.cc_done
import bragwise.shared.generated.resources.cc_info_a11y
import com.composables.icons.lucide.Info
import com.composables.icons.lucide.Lucide
import org.jetbrains.compose.resources.stringResource

/**
 * Small (i) icon button that shows an [AlertDialog] explaining a section.
 * Intended for Create Challenge to surface contextual help without cluttering
 * the form layout.
 */
@Composable
fun InfoIcon(
    title: String,
    body: String,
    modifier: Modifier = Modifier,
) {
    var showDialog by remember { mutableStateOf(false) }

    IconButton(
        onClick = { showDialog = true },
        modifier = modifier.size(28.dp),
    ) {
        Icon(
            imageVector = Lucide.Info,
            contentDescription = stringResource(Res.string.cc_info_a11y, title),
            tint = MaterialTheme.colorScheme.onSurfaceVariant,
            modifier = Modifier.size(18.dp),
        )
    }

    if (showDialog) {
        AlertDialog(
            onDismissRequest = { showDialog = false },
            title = { Text(title) },
            text = {
                Text(
                    text = body,
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            },
            confirmButton = {
                AppButton(onClick = { showDialog = false }) {
                    Text(stringResource(Res.string.cc_done))
                }
            },
        )
    }
}

/**
 * A full-width row with a section [title] on the left and an [InfoIcon] on the
 * right. Drop this in place of a bare [Text] wherever a SectionCard renders its
 * own header (i.e. when passing no title to [SectionCard] and rendering the
 * header manually so the info icon sits beside it).
 */
@Composable
fun SectionTitleRow(
    title: String,
    infoTitle: String,
    infoBody: String,
    modifier: Modifier = Modifier,
) {
    Row(
        modifier = modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.SpaceBetween,
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Text(
            text = title,
            style = MaterialTheme.typography.titleLarge,
            color = MaterialTheme.colorScheme.onSurface,
        )
        InfoIcon(title = infoTitle, body = infoBody)
    }
}
