package se.atte.bragwise.ui.screens.about

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import se.atte.bragwise.platform.AppInfo
import se.atte.bragwise.theme.ThemePreview
import se.atte.bragwise.ui.components.SectionCard
import org.jetbrains.compose.resources.stringResource
import bragwise.shared.generated.resources.Res
import bragwise.shared.generated.resources.about_open_source
import bragwise.shared.generated.resources.about_open_source_body
import bragwise.shared.generated.resources.about_section_title
import bragwise.shared.generated.resources.about_tagline
import bragwise.shared.generated.resources.about_version

@Composable
fun AboutScreen() {
    AboutContent(version = AppInfo.version)
}

@Composable
private fun AboutContent(version: String) {
    LazyColumn(
        modifier = Modifier.fillMaxSize(),
        contentPadding = PaddingValues(16.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        item {
            SectionCard(title = stringResource(Res.string.about_section_title)) {
                Text(stringResource(Res.string.about_tagline), style = MaterialTheme.typography.bodyLarge)
                Spacer(Modifier.height(8.dp))
                Text(stringResource(Res.string.about_version, version), style = MaterialTheme.typography.bodyMedium)
            }
        }
        item {
            SectionCard(title = stringResource(Res.string.about_open_source)) {
                Text(
                    stringResource(Res.string.about_open_source_body),
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
        }
    }
}

// region Previews

@Preview
@Composable
private fun About_Preview() {
    ThemePreview {
        AboutContent(version = "1.2.3")
    }
}

// endregion
