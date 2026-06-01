package se.atte.bragwise.ui.screens.friends

import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import se.atte.bragwise.data.SocialRepository
import se.atte.bragwise.ui.standardPadding
import se.atte.bragwise.domain.LocalFriend
import se.atte.bragwise.theme.ThemePreview
import se.atte.bragwise.ui.components.AppButton
import se.atte.bragwise.ui.components.AppOutlinedButton
import se.atte.bragwise.ui.components.AppTextButton
import se.atte.bragwise.ui.components.BottomActionBar
import se.atte.bragwise.ui.components.SectionCard
import se.atte.bragwise.ui.components.SectionGap

/**
 * Add / edit a local-only friend. `localId == null` means add. Backed
 * directly by [SocialRepository]; the screen has no separate VM since
 * it's a one-shot form (state lives in `remember`-backed UI state).
 *
 * Bottom sheet would be nicer but the project hasn't wired
 * `ModalBottomSheet` yet — full screen keeps the navigation graph flat
 * and the keyboard interaction simpler.
 */
@Composable
fun LocalFriendEditorScreen(
    social: SocialRepository,
    localId: String?,
    onSaved: () -> Unit,
    onCancel: () -> Unit,
) {
    val existing: LocalFriend? = remember(localId) {
        localId?.let { safeLocalId -> social.localFriendSnapshot().firstOrNull { it.localId == safeLocalId } }
    }

    LocalFriendEditorForm(
        initialName = existing?.displayName.orEmpty(),
        onSave = { name ->
            if (localId == null) {
                social.addLocalFriend(displayName = name)
            } else {
                social.editLocalFriend(localId = localId, displayName = name)
            }
            onSaved()
        },
        onCancel = onCancel,
        onRemove = if (localId != null) {
            {
                social.removeLocalFriend(localId)
                onSaved()
            }
        } else null,
    )
}

@Composable
private fun LocalFriendEditorForm(
    initialName: String,
    onSave: (name: String) -> Unit,
    onCancel: () -> Unit,
    onRemove: (() -> Unit)?,
) {
    var name by remember { mutableStateOf(initialName) }

    Column(Modifier.fillMaxSize()) {
        Column(
            modifier = Modifier
                .weight(1f)
                .verticalScroll(rememberScrollState())
                .padding(standardPadding),
        ) {
            SectionCard(title = if (onRemove == null) "Add a local friend" else "Edit friend") {
                Text(
                    text = "Local friends are stored only on this device. Once you sign up, you can map them to real accounts.",
                    style = MaterialTheme.typography.bodyLarge,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
                Spacer(Modifier.height(12.dp))
                OutlinedTextField(
                    value = name,
                    onValueChange = { name = it },
                    label = { Text("Display name") },
                    modifier = Modifier.fillMaxWidth(),
                    singleLine = true,
                )
            }

            if (onRemove != null) {
                SectionGap()
                AppTextButton(
                    onClick = onRemove,
                    color = MaterialTheme.colorScheme.error,
                ) { Text("Remove friend") }
            }
        }

        BottomActionBar {
            AppOutlinedButton(
                modifier = Modifier.weight(1f),
                onClick = onCancel,
            ) { Text("Cancel") }
            AppButton(
                modifier = Modifier.weight(1f),
                onClick = { onSave(name.trim()) },
                enabled = name.isNotBlank(),
            ) { Text("Save") }
        }
    }
}

// region Previews

@Preview
@Composable
private fun FriendEditor_Add_Preview() {
    ThemePreview {
        LocalFriendEditorForm(
            initialName = "",
            onSave = {},
            onCancel = {},
            onRemove = null,
        )
    }
}

@Preview
@Composable
private fun FriendEditor_Edit_Preview() {
    ThemePreview {
        LocalFriendEditorForm(
            initialName = "Alice",
            onSave = {},
            onCancel = {},
            onRemove = {},
        )
    }
}

// endregion
