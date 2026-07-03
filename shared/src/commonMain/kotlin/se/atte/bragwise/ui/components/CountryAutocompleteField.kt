package se.atte.bragwise.ui.components

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.derivedStateOf
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.composables.icons.lucide.Lucide
import com.composables.icons.lucide.X
import se.atte.bragwise.ui.standardPadding
import se.atte.bragwise.ui.InputLimits
import se.atte.bragwise.domain.BetOption
import se.atte.bragwise.domain.searchCountries
import org.jetbrains.compose.resources.stringResource
import bragwise.shared.generated.resources.Res
import bragwise.shared.generated.resources.cc_country_pick_from_list
import bragwise.shared.generated.resources.country_clear_a11y

/**
 * A text field that offers country autocomplete (top-3 matches) when the
 * user types. Selecting a suggestion locks the field to that country and
 * displays its flag emoji in the leading slot.
 *
 * Rules (per plan §phase-1.5):
 *  - No auto-lock on exact text match — the user must tap a suggestion.
 *  - Typing anything that doesn't end in a locked selection keeps countryCode null.
 *  - A trailing ✕ clears the lock, restoring free-text mode.
 */
@Composable
fun CountryAutocompleteField(
    value: BetOption,
    onChange: (BetOption) -> Unit,
    modifier: Modifier = Modifier,
    placeholder: String = "Country or custom text",
) {
    val isLocked = value.countryCode != null
    val isError = value.label.isNotBlank() && !isLocked
    val suggestions by remember(value.label, isLocked) {
        derivedStateOf {
            if (isLocked || value.label.isBlank()) emptyList()
            else searchCountries(value.label, limit = 3)
        }
    }

    Column(modifier = modifier) {
        OutlinedTextField(
            value = value.label,
            onValueChange = { text ->
                if (text.length <= InputLimits.BET_OPTION) {
                    // Typing always clears the country lock — user must re-select
                    onChange(value.copy(label = text, countryCode = null))
                }
            },
            modifier = Modifier.fillMaxWidth(),
            singleLine = true,
            isError = isError,
            supportingText = if (isError) {
                { Text(stringResource(Res.string.cc_country_pick_from_list)) }
            } else null,
            placeholder = { Text(placeholder) },
            leadingIcon = if (isLocked && value.countryCode != null) {
                val code = value.countryCode
                {
                    FlagImage(code = code, size = 24.dp, modifier = Modifier.padding(start = 4.dp))
                }
            } else {
                {
                    Box(Modifier.size(28.dp))
                }
            },
            trailingIcon = if (isLocked) {
                {
                    IconButton(onClick = {
                        onChange(value.copy(countryCode = null))
                    }) {
                        Icon(
                            imageVector = Lucide.X,
                            contentDescription = stringResource(Res.string.country_clear_a11y),
                            tint = MaterialTheme.colorScheme.onSurfaceVariant,
                        )
                    }
                }
            } else null,
        )

        AnimatedVisibility(
            visible = suggestions.isNotEmpty(),
            enter = fadeIn(),
            exit = fadeOut(),
        ) {
            Surface(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(top = 2.dp),
                shape = MaterialTheme.shapes.small,
                color = MaterialTheme.colorScheme.surface,
                tonalElevation = 4.dp,
                shadowElevation = 2.dp,
            ) {
                Column {
                    suggestions.forEachIndexed { index, country ->
                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .testTag("create_country_suggestion_${country.code}")
                                .clickable {
                                    onChange(
                                        BetOption(
                                            id = value.id,
                                            label = country.name,
                                            countryCode = country.code,
                                        ),
                                    )
                                }
                                .padding(horizontal = standardPadding, vertical = 12.dp),
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.spacedBy(12.dp),
                        ) {
                            FlagImage(code = country.code, size = 20.dp)
                            Text(
                                text = country.name,
                                style = MaterialTheme.typography.bodyLarge,
                                fontWeight = FontWeight.Medium,
                                color = MaterialTheme.colorScheme.onSurface,
                            )
                        }
                        if (index < suggestions.lastIndex) {
                            androidx.compose.material3.HorizontalDivider(
                                color = MaterialTheme.colorScheme.outline.copy(alpha = 0.3f),
                            )
                        }
                    }
                }
            }
        }
    }
}
