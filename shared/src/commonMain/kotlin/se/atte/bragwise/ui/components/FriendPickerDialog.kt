package se.atte.bragwise.ui.components

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Checkbox
import androidx.compose.material3.HorizontalDivider
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
import bragwise.shared.generated.resources.cc_cancel
import bragwise.shared.generated.resources.cc_invite_dialog_title
import bragwise.shared.generated.resources.cc_no_friends_yet
import bragwise.shared.generated.resources.friend_picker_select_all
import org.jetbrains.compose.resources.stringResource
import se.atte.bragwise.domain.CloudFriend

@Composable
fun FriendPickerDialog(
    friends: List<CloudFriend>,
    initial: Set<String>,
    confirmLabel: String,
    confirmEnabled: Boolean = true,
    onDismiss: () -> Unit,
    onConfirm: (Set<String>) -> Unit,
) {
    var selected by remember(initial) { mutableStateOf(initial) }
    val allSelected = friends.isNotEmpty() && friends.all { it.id in selected }

    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(stringResource(Res.string.cc_invite_dialog_title)) },
        text = {
            if (friends.isEmpty()) {
                Text(stringResource(Res.string.cc_no_friends_yet))
            } else {
                LazyColumn {
                    item {
                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .clickable {
                                    selected = if (allSelected) emptySet() else friends.map { it.id }.toSet()
                                }
                                .padding(horizontal = 4.dp, vertical = 8.dp),
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.SpaceBetween,
                        ) {
                            Text(
                                stringResource(Res.string.friend_picker_select_all),
                                style = MaterialTheme.typography.bodyLarge,
                            )
                            Checkbox(
                                checked = allSelected,
                                onCheckedChange = {
                                    selected = if (allSelected) emptySet() else friends.map { it.id }.toSet()
                                },
                            )
                        }
                        HorizontalDivider()
                    }
                    items(items = friends, key = { it.id }) { friend ->
                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .clickable {
                                    selected = if (friend.id in selected) selected - friend.id else selected + friend.id
                                }
                                .padding(horizontal = 4.dp, vertical = 8.dp),
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.SpaceBetween,
                        ) {
                            Text(friend.displayName, style = MaterialTheme.typography.bodyLarge)
                            Checkbox(
                                checked = friend.id in selected,
                                onCheckedChange = {
                                    selected = if (friend.id in selected) selected - friend.id else selected + friend.id
                                },
                            )
                        }
                    }
                }
            }
        },
        confirmButton = {
            AppTextButton(
                enabled = confirmEnabled,
                onClick = { onConfirm(selected) },
            ) { Text(confirmLabel) }
        },
        dismissButton = {
            AppTextButton(onClick = onDismiss) { Text(stringResource(Res.string.cc_cancel)) }
        },
    )
}
