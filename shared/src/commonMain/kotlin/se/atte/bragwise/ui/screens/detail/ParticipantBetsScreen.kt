package se.atte.bragwise.ui.screens.detail

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.material3.Button
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Text
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import org.jetbrains.compose.resources.getString
import org.jetbrains.compose.resources.stringResource
import bragwise.shared.generated.resources.Res
import bragwise.shared.generated.resources.pb_add_friend
import bragwise.shared.generated.resources.pb_already_friends
import bragwise.shared.generated.resources.pb_friend_requested
import se.atte.bragwise.domain.Bet
import se.atte.bragwise.domain.BetOption
import se.atte.bragwise.domain.ParticipantInfo
import se.atte.bragwise.domain.PredictionPayload
import se.atte.bragwise.mvi.ObserveEffects
import se.atte.bragwise.mvi.UiState
import se.atte.bragwise.theme.ThemePreview
import se.atte.bragwise.ui.components.AvatarBubble
import se.atte.bragwise.ui.components.ListGroup
import se.atte.bragwise.ui.components.ListGroupDivider
import se.atte.bragwise.ui.components.ListRow
import se.atte.bragwise.ui.components.SectionCard
import se.atte.bragwise.ui.fullPick
import se.atte.bragwise.ui.standardPadding
import se.atte.bragwise.ui.standardPaddingSmall

@Composable
fun ParticipantBetsScreen(
    viewModel: ParticipantBetsViewModel,
    snackbarHostState: SnackbarHostState,
) {
    val state by viewModel.state.collectAsStateWithLifecycle()

    ObserveEffects(effects = viewModel.effects) { effect ->
        when (effect) {
            is ParticipantBetsViewModel.Effect.Snackbar ->
                snackbarHostState.showSnackbar(getString(effect.message.res, *effect.message.args.toTypedArray()))
        }
    }

    when (val ui = state.ui) {
        UiState.Loading -> Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
            CircularProgressIndicator()
        }
        is UiState.Empty -> Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
            Text("Bets are hidden")
        }
        is UiState.Failed -> Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
            Text(
                text = "Bets are hidden",
                style = MaterialTheme.typography.bodyLarge,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
        is UiState.Ready -> ParticipantBetsContent(
            data = ui.data,
            onAddFriend = { viewModel.onIntent(ParticipantBetsViewModel.Intent.SendFriendRequest) },
        )
    }
}

@Composable
private fun ParticipantBetsContent(
    data: ParticipantBetsViewModel.Data,
    onAddFriend: () -> Unit,
) {
    LazyColumn(
        modifier = Modifier
            .fillMaxSize()
            .navigationBarsPadding(),
        contentPadding = PaddingValues(standardPadding),
        verticalArrangement = Arrangement.spacedBy(standardPadding),
    ) {
        item {
            SectionCard {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(12.dp),
                ) {
                    AvatarBubble(
                        displayName = data.participant.displayName,
                        avatarSeed = data.participant.avatarSeed,
                        size = 40.dp,
                    )
                    Column(modifier = Modifier.weight(1f)) {
                        Text(
                            text = data.participant.displayName,
                            style = MaterialTheme.typography.headlineSmall,
                        )
                        if (!data.username.isNullOrBlank()) {
                            Text(
                                text = "@${data.username}",
                                style = MaterialTheme.typography.bodyMedium,
                                color = MaterialTheme.colorScheme.onSurfaceVariant,
                            )
                        }
                    }
                    when (data.friendState) {
                        ParticipantBetsViewModel.FriendState.CAN_ADD -> {
                            Button(onClick = onAddFriend) {
                                Text(stringResource(Res.string.pb_add_friend))
                            }
                        }
                        ParticipantBetsViewModel.FriendState.REQUESTED -> {
                            Button(onClick = {}, enabled = false) {
                                Text(stringResource(Res.string.pb_friend_requested))
                            }
                        }
                        ParticipantBetsViewModel.FriendState.FRIENDS -> {
                            Text(
                                text = stringResource(Res.string.pb_already_friends),
                                style = MaterialTheme.typography.labelLarge,
                                color = MaterialTheme.colorScheme.primary,
                                modifier = Modifier.padding(horizontal = standardPaddingSmall),
                            )
                        }
                        ParticipantBetsViewModel.FriendState.SELF,
                        ParticipantBetsViewModel.FriendState.NOT_FRIENDABLE -> Unit
                    }
                }
            }
        }

        if (data.bets.isEmpty()) {
            item {
                Box(modifier = Modifier.fillMaxWidth().padding(vertical = 32.dp), contentAlignment = Alignment.Center) {
                    Text(
                        text = "No bets in this challenge",
                        style = MaterialTheme.typography.bodyLarge,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
            }
        } else {
            item {
                ListGroup {
                    data.bets.forEachIndexed { index, bet ->
                        val pick = fullPick(bet = bet, payload = data.predictions[bet.id])
                        val result = data.results?.let { fullPick(bet = bet, payload = it[bet.id]) }
                        BetPredictionRow(
                            number = index + 1,
                            betTitle = bet.title,
                            pick = pick,
                            result = result,
                        )
                        if (index < data.bets.size - 1) ListGroupDivider()
                    }
                }
            }
        }

        item { Spacer(Modifier.height(standardPadding)) }
    }
}

@Composable
private fun BetPredictionRow(
    number: Int,
    betTitle: String,
    pick: String,
    result: String?,
) {
    ListRow(
        title = betTitle,
        subtitle = if (result != null) "$pick · Result: $result" else pick,
        leading = number.toString(),
        trailing = null,
        titleFontWeight = FontWeight.Bold,
    )
}

// region Previews

@Preview
@Composable
private fun ParticipantBets_Preview() {
    val bets = listOf(
        Bet.BooleanProp(id = "b1", title = "Will the home side win?"),
        Bet.SinglePick(
            id = "b2",
            title = "Top scorer",
            options = listOf(BetOption("o1", "Player A"), BetOption("o2", "Player B")),
        ),
    )
    val predictions = mapOf(
        "b1" to PredictionPayload.BooleanProp(true),
        "b2" to PredictionPayload.SinglePick("o2"),
    )
    ThemePreview {
        ParticipantBetsContent(
            data = ParticipantBetsViewModel.Data(
                participant = ParticipantInfo(uid = "u1", displayName = "Alice", avatarSeed = "alice"),
                bets = bets,
                predictions = predictions,
                results = mapOf("b1" to PredictionPayload.BooleanProp(true)),
                username = "alice",
                friendState = ParticipantBetsViewModel.FriendState.CAN_ADD,
            ),
            onAddFriend = {},
        )
    }
}

// endregion
