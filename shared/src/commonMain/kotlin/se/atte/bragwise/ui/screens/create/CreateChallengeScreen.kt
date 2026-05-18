package se.atte.bragwise.ui.screens.create

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import bragwise.shared.generated.resources.Res
import se.atte.bragwise.ui.standardPadding
import se.atte.bragwise.ui.standardPaddingSmall
import bragwise.shared.generated.resources.ic_close
import org.jetbrains.compose.resources.painterResource
import se.atte.bragwise.domain.Bet
import se.atte.bragwise.domain.BetOption
import se.atte.bragwise.domain.Visibility
import se.atte.bragwise.ui.components.AppButton
import se.atte.bragwise.ui.components.AppFilterChip
import se.atte.bragwise.ui.components.AppOutlinedButton
import se.atte.bragwise.ui.components.AppTextButton
import se.atte.bragwise.ui.components.BottomActionBar
import se.atte.bragwise.ui.components.SectionCard
import se.atte.bragwise.ui.components.SectionGap

@Composable
fun CreateChallengeScreen(
    viewModel: CreateChallengeViewModel,
    snackbarHostState: SnackbarHostState,
    onPublished: (String) -> Unit,
    onDraftSaved: (String) -> Unit,
) {
    val state by viewModel.state.collectAsStateWithLifecycle()

    LaunchedEffect(viewModel) {
        viewModel.effects.collect { effect ->
            when (effect) {
                is CreateChallengeViewModel.Effect.Published -> onPublished(effect.challengeId)
                is CreateChallengeViewModel.Effect.DraftSaved -> onDraftSaved(effect.challengeId)
                is CreateChallengeViewModel.Effect.Snackbar -> snackbarHostState.showSnackbar(effect.text)
            }
        }
    }

    when (state.step) {
        CreateChallengeViewModel.Step.Metadata -> MetadataStep(
            state = state,
            onTitle = { viewModel.onIntent(CreateChallengeViewModel.Intent.SetTitle(it)) },
            onVisibility = { viewModel.onIntent(CreateChallengeViewModel.Intent.SetVisibility(it)) },
            onContinue = { viewModel.onIntent(CreateChallengeViewModel.Intent.NextStep) },
        )
        CreateChallengeViewModel.Step.Bets -> BetsStep(
            state = state,
            onAddBoolean = { title -> viewModel.onIntent(CreateChallengeViewModel.Intent.AddBoolean(title)) },
            onAddSinglePick = { title, opts ->
                viewModel.onIntent(CreateChallengeViewModel.Intent.AddSinglePick(title, opts))
            },
            onRemoveBet = { id -> viewModel.onIntent(CreateChallengeViewModel.Intent.RemoveBet(id)) },
            onBack = { viewModel.onIntent(CreateChallengeViewModel.Intent.PrevStep) },
            onSaveDraft = { viewModel.onIntent(CreateChallengeViewModel.Intent.SaveDraft) },
            onPublish = { viewModel.onIntent(CreateChallengeViewModel.Intent.Publish) },
        )
    }
}

@Composable
private fun MetadataStep(
    state: CreateChallengeViewModel.State,
    onTitle: (String) -> Unit,
    onVisibility: (Visibility) -> Unit,
    onContinue: () -> Unit,
) {
    Column(Modifier.fillMaxSize()) {
        Column(
            modifier = Modifier
                .weight(1f)
                .verticalScroll(rememberScrollState())
                .padding(standardPadding),
        ) {
            SectionCard(title = "Details") {
                OutlinedTextField(
                    value = state.title,
                    onValueChange = onTitle,
                    label = { Text("Title") },
                    modifier = Modifier.fillMaxWidth(),
                    singleLine = true,
                )
            }

            SectionGap()

            SectionCard(title = "Who can join") {
                Row(horizontalArrangement = Arrangement.spacedBy(standardPaddingSmall)) {
                    AppFilterChip(
                        selected = state.visibility == Visibility.FRIENDS,
                        onClick = { onVisibility(Visibility.FRIENDS) },
                        label = { Text("Friends") },
                    )
                    AppFilterChip(
                        selected = state.visibility == Visibility.INVITE_ONLY,
                        onClick = { onVisibility(Visibility.INVITE_ONLY) },
                        label = { Text("Invite only") },
                    )
                }
                Text(
                    text = when (state.visibility) {
                        Visibility.FRIENDS -> "Auto-invites all your current and future friends."
                        Visibility.INVITE_ONLY -> "Only people you explicitly invite can join."
                        Visibility.PROMOTED -> ""
                    },
                    style = MaterialTheme.typography.bodyLarge,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier.padding(top = 12.dp),
                )
            }
        }

        BottomActionBar {
            AppButton(
                modifier = Modifier.fillMaxWidth(),
                onClick = onContinue,
                enabled = state.title.isNotBlank(),
            ) { Text("Continue to bets") }
        }
    }
}

@Composable
private fun BetsStep(
    state: CreateChallengeViewModel.State,
    onAddBoolean: (String) -> Unit,
    onAddSinglePick: (String, List<BetOption>) -> Unit,
    onRemoveBet: (String) -> Unit,
    @Suppress("UNUSED_PARAMETER") onBack: () -> Unit,
    onSaveDraft: () -> Unit,
    onPublish: () -> Unit,
) {
    var newBetTitle by remember { mutableStateOf("") }
    var options by remember { mutableStateOf(listOf("", "")) }
    var betType by remember { mutableStateOf(BetType.YesNo) }

    val validOptions = options.map { it.trim() }.filter { it.isNotEmpty() }

    LazyColumn(
        modifier = Modifier.fillMaxSize(),
        contentPadding = androidx.compose.foundation.layout.PaddingValues(standardPadding),
        verticalArrangement = Arrangement.spacedBy(standardPadding),
    ) {
        item {
            SectionCard(title = "Bets (${state.bets.size})") {
                if (state.bets.isEmpty()) {
                    Text(
                        text = "Add at least one bet to publish.",
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        style = MaterialTheme.typography.bodyLarge,
                    )
                } else {
                    Column(verticalArrangement = Arrangement.spacedBy(standardPaddingSmall)) {
                        state.bets.forEach { bet ->
                            BetRow(bet = bet, onRemove = { onRemoveBet(bet.id) })
                        }
                    }
                }
            }
        }

        item {
            SectionCard(title = "Add bet") {
                Row(horizontalArrangement = Arrangement.spacedBy(standardPaddingSmall)) {
                    AppFilterChip(
                        selected = betType == BetType.YesNo,
                        onClick = {
                            betType = BetType.YesNo
                            options = listOf("", "")
                        },
                        label = { Text("Yes / No") },
                    )
                    AppFilterChip(
                        selected = betType == BetType.SinglePick,
                        onClick = { betType = BetType.SinglePick },
                        label = { Text("Single pick") },
                    )
                }
                OutlinedTextField(
                    value = newBetTitle,
                    onValueChange = { newBetTitle = it },
                    label = { Text("Question") },
                    modifier = Modifier.fillMaxWidth().padding(top = 12.dp),
                    singleLine = true,
                )
                if (betType == BetType.SinglePick) {
                    Column(
                        modifier = Modifier.padding(top = standardPaddingSmall),
                        verticalArrangement = Arrangement.spacedBy(standardPaddingSmall),
                    ) {
                        options.forEachIndexed { index, value ->
                            Row(
                                verticalAlignment = Alignment.CenterVertically,
                                horizontalArrangement = Arrangement.spacedBy(4.dp),
                            ) {
                                OutlinedTextField(
                                    value = value,
                                    onValueChange = { updated ->
                                        options = options.toMutableList().also { it[index] = updated }
                                    },
                                    label = { Text("Option ${index + 1}") },
                                    modifier = Modifier.weight(1f),
                                    singleLine = true,
                                )
                                IconButton(
                                    onClick = { options = options.toMutableList().also { it.removeAt(index) } },
                                    enabled = options.size > 2,
                                ) {
                                    Icon(
                                        painter = painterResource(Res.drawable.ic_close),
                                        contentDescription = "Remove option",
                                        tint = if (options.size > 2)
                                            MaterialTheme.colorScheme.onSurfaceVariant
                                        else
                                            MaterialTheme.colorScheme.onSurface.copy(alpha = 0.38f),
                                    )
                                }
                            }
                        }
                        AppTextButton(
                            onClick = { options = options + "" },
                        ) { Text("+ Add option") }
                    }
                }
                AppOutlinedButton(
                    modifier = Modifier.fillMaxWidth().padding(top = 12.dp),
                    onClick = {
                        when (betType) {
                            BetType.YesNo -> if (newBetTitle.isNotBlank()) {
                                onAddBoolean(newBetTitle)
                                newBetTitle = ""
                            }
                            BetType.SinglePick -> if (newBetTitle.isNotBlank() && validOptions.size >= 2) {
                                onAddSinglePick(
                                    newBetTitle,
                                    validOptions.mapIndexed { i, label -> BetOption(id = "o$i", label = label) },
                                )
                                newBetTitle = ""
                                options = listOf("", "")
                            }
                        }
                    },
                    enabled = newBetTitle.isNotBlank() &&
                        (betType == BetType.YesNo || validOptions.size >= 2),
                ) { Text("+ Add bet") }
            }
        }

        item {
            Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                AppOutlinedButton(
                    modifier = Modifier.weight(1f),
                    onClick = onSaveDraft,
                    enabled = !state.submitting,
                ) { Text("Save draft") }
                AppButton(
                    modifier = Modifier.weight(1f),
                    onClick = onPublish,
                    enabled = !state.submitting && state.bets.isNotEmpty(),
                ) { Text("Publish") }
            }
        }
    }
}

private enum class BetType { YesNo, SinglePick }

@Composable
private fun BetRow(bet: Bet, onRemove: () -> Unit) {
    Row(
        modifier = Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.SpaceBetween,
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Column(modifier = Modifier.weight(1f)) {
            Text(
                text = bet.title,
                style = MaterialTheme.typography.bodyLarge,
                fontWeight = FontWeight.Medium,
                color = MaterialTheme.colorScheme.onSurface,
            )
            Text(
                text = bet.kindLabel(),
                style = MaterialTheme.typography.bodyLarge,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
        AppTextButton(
            onClick = onRemove,
            color = MaterialTheme.colorScheme.error,
        ) { Text("Remove") }
    }
}

private fun Bet.kindLabel(): String = when (this) {
    is Bet.SinglePick -> "Single pick · ${options.size} options"
    is Bet.BooleanProp -> "Yes / No"
    is Bet.Ranking -> "Ranking · top $topN"
}

