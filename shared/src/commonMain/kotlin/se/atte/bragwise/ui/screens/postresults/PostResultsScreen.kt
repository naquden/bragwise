package se.atte.bragwise.ui.screens.postresults

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import se.atte.bragwise.mvi.ObserveEffects
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material3.OutlinedTextField
import androidx.compose.ui.text.input.KeyboardType
import se.atte.bragwise.domain.Bet
import se.atte.bragwise.domain.OptionType
import se.atte.bragwise.domain.PredictionPayload
import se.atte.bragwise.ui.standardPaddingSmall
import se.atte.bragwise.mvi.UiState
import se.atte.bragwise.theme.ThemePreview
import org.jetbrains.compose.resources.stringResource
import bragwise.shared.generated.resources.Res
import bragwise.shared.generated.resources.postresults_cancel
import bragwise.shared.generated.resources.postresults_no
import bragwise.shared.generated.resources.postresults_over
import bragwise.shared.generated.resources.postresults_over_under_actual_label
import bragwise.shared.generated.resources.postresults_post
import bragwise.shared.generated.resources.postresults_under
import bragwise.shared.generated.resources.postresults_post_results
import bragwise.shared.generated.resources.postresults_post_results_body
import bragwise.shared.generated.resources.postresults_post_results_title
import bragwise.shared.generated.resources.postresults_posting
import bragwise.shared.generated.resources.postresults_posting_dialog
import bragwise.shared.generated.resources.postresults_yes
import se.atte.bragwise.ui.components.AppButton
import se.atte.bragwise.ui.components.AppFilterChip
import se.atte.bragwise.ui.components.AppTextButton
import se.atte.bragwise.ui.components.BottomActionBar
import se.atte.bragwise.ui.components.GuessPickerField
import se.atte.bragwise.ui.components.LoadingDialog
import se.atte.bragwise.ui.components.RankingDragList
import se.atte.bragwise.ui.components.SectionCard
import se.atte.bragwise.ui.isCompleteFor
import se.atte.bragwise.ui.preview.sampleBets

/**
 * CR-06 Post results — owner enters the canonical answer per bet.
 * Mirrors PredictScreen layout but with a confirm dialog (postResults
 * is irreversible per Firestore rules + scoring fan-out).
 */
@Composable
fun PostResultsScreen(
    viewModel: PostResultsViewModel,
    snackbarHostState: SnackbarHostState,
    onPosted: () -> Unit,
) {
    val state by viewModel.state.collectAsStateWithLifecycle()

    ObserveEffects(viewModel.effects) { e ->
        when (e) {
            PostResultsViewModel.Effect.Posted -> onPosted()
            is PostResultsViewModel.Effect.Snackbar -> snackbarHostState.showSnackbar(e.text)
        }
    }

    when (val ui = state.ui) {
        UiState.Loading -> Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
            CircularProgressIndicator()
        }
        is UiState.Empty -> Unit
        is UiState.Failed -> Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
            Text(ui.cause.toUserMessage())
        }
        is UiState.Ready -> {
            val bets = ui.data.challenge.bets
            Column(Modifier.fillMaxSize()) {
                LazyColumn(
                    modifier = Modifier.weight(1f).fillMaxWidth(),
                    contentPadding = PaddingValues(16.dp),
                    verticalArrangement = Arrangement.spacedBy(12.dp),
                ) {
                    items(items = bets, key = { it.id }) { bet ->
                        BetRow(
                            bet = bet,
                            current = state.results[bet.id],
                            onSinglePick = { id ->
                                viewModel.onIntent(PostResultsViewModel.Intent.SetSinglePick(bet.id, id))
                            },
                            onBoolean = { value ->
                                viewModel.onIntent(PostResultsViewModel.Intent.SetBoolean(bet.id, value))
                            },
                            onRanking = { ids ->
                                viewModel.onIntent(PostResultsViewModel.Intent.SetRanking(bet.id, ids))
                            },
                            onGuess = { value ->
                                viewModel.onIntent(PostResultsViewModel.Intent.SetGuess(bet.id, value))
                            },
                            onMultiSelect = { ids ->
                                viewModel.onIntent(PostResultsViewModel.Intent.SetMultiSelect(bet.id, ids))
                            },
                            onOverUnderActual = { value ->
                                viewModel.onIntent(PostResultsViewModel.Intent.SetOverUnderActual(bet.id, value))
                            },
                        )
                    }
                }
                val complete = bets.all { state.results[it.id].isCompleteFor(it, isResult = true) }
                BottomActionBar {
                    AppButton(
                        modifier = Modifier.fillMaxWidth(),
                        onClick = { viewModel.onIntent(PostResultsViewModel.Intent.RequestConfirm) },
                        enabled = !state.submitting && complete,
                    ) {
                        Text(if (state.submitting) stringResource(Res.string.postresults_posting) else stringResource(Res.string.postresults_post_results))
                    }
                }
            }
        }
    }

    if (state.submitting) {
        LoadingDialog(
            message = stringResource(Res.string.postresults_posting_dialog),
            onStopWaiting = { viewModel.onIntent(PostResultsViewModel.Intent.StopWaiting) },
        )
    }

    if (state.confirming) {
        AlertDialog(
            onDismissRequest = { viewModel.onIntent(PostResultsViewModel.Intent.Cancel) },
            title = { Text(stringResource(Res.string.postresults_post_results_title)) },
            text = { Text(stringResource(Res.string.postresults_post_results_body)) },
            confirmButton = {
                AppButton(onClick = { viewModel.onIntent(PostResultsViewModel.Intent.Submit) }) {
                    Text(stringResource(Res.string.postresults_post))
                }
            },
            dismissButton = {
                AppTextButton(onClick = { viewModel.onIntent(PostResultsViewModel.Intent.Cancel) }) {
                    Text(stringResource(Res.string.postresults_cancel))
                }
            },
        )
    }
}

@Composable
private fun BetRow(
    bet: Bet,
    current: PredictionPayload?,
    onSinglePick: (String) -> Unit,
    onBoolean: (Boolean) -> Unit,
    onRanking: (List<String>) -> Unit,
    onGuess: (Long) -> Unit,
    onMultiSelect: (List<String>) -> Unit,
    onOverUnderActual: (Long) -> Unit,
) {
    SectionCard(title = bet.title) {
        when (bet) {
            is Bet.SinglePick -> FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                bet.options.forEach { opt ->
                    val sel = (current as? PredictionPayload.SinglePick)?.optionId == opt.id
                    AppFilterChip(
                        selected = sel,
                        onClick = { onSinglePick(opt.id) },
                        label = { Text(opt.label) },
                    )
                }
            }
            is Bet.BooleanProp -> Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                val v = (current as? PredictionPayload.BooleanProp)?.value
                AppFilterChip(selected = v == true, onClick = { onBoolean(true) }, label = { Text(stringResource(Res.string.postresults_yes)) })
                AppFilterChip(selected = v == false, onClick = { onBoolean(false) }, label = { Text(stringResource(Res.string.postresults_no)) })
            }
            is Bet.Ranking -> RankingDragList(
                options = bet.options,
                topN = bet.topN,
                orderedOptionIds = (current as? PredictionPayload.Ranking)?.orderedOptionIds ?: emptyList(),
                showFlag = false,
                onReorder = onRanking,
            )
            is Bet.Guess -> GuessPickerField(
                granularity = bet.granularity,
                value = (current as? PredictionPayload.Guess)?.value,
                onValueChange = onGuess,
            )
            is Bet.MultiSelect -> {
                val selectedIds = (current as? PredictionPayload.MultiSelect)?.selectedOptionIds.orEmpty()
                FlowRow(horizontalArrangement = Arrangement.spacedBy(standardPaddingSmall)) {
                    bet.options.forEach { opt ->
                        val sel = opt.id in selectedIds
                        AppFilterChip(
                            selected = sel,
                            onClick = {
                                val updated = if (sel) selectedIds - opt.id else selectedIds + opt.id
                                onMultiSelect(updated)
                            },
                            label = { Text(opt.label) },
                        )
                    }
                }
            }
            is Bet.OverUnder -> {
                val textState = androidx.compose.runtime.remember(current) {
                    androidx.compose.runtime.mutableStateOf(
                        (current as? PredictionPayload.OverUnder)?.actualValue?.toString() ?: ""
                    )
                }
                OutlinedTextField(
                    value = textState.value,
                    onValueChange = { v ->
                        textState.value = v
                        v.toLongOrNull()?.let { onOverUnderActual(it) }
                    },
                    label = { Text(stringResource(Res.string.postresults_over_under_actual_label, bet.line)) },
                    modifier = Modifier.fillMaxWidth(),
                    singleLine = true,
                    keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
                )
            }
        }
    }
}

// region Previews

@Preview
@Composable
private fun PostResults_Preview() {
    ThemePreview {
        Column(
            modifier = Modifier.fillMaxSize(),
            verticalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            sampleBets.forEach { bet ->
                BetRow(
                    bet = bet,
                    current = null,
                    onSinglePick = {},
                    onBoolean = {},
                    onRanking = {},
                    onGuess = {},
                    onMultiSelect = {},
                    onOverUnderActual = {},
                )
            }
        }
    }
}

// endregion
