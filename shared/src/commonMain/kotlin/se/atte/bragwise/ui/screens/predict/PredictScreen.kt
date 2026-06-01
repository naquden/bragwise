package se.atte.bragwise.ui.screens.predict

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.material3.Icon
import com.composables.icons.lucide.ChevronDown
import com.composables.icons.lucide.ChevronUp
import com.composables.icons.lucide.Lucide
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import se.atte.bragwise.mvi.ObserveEffects
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import se.atte.bragwise.domain.Bet
import se.atte.bragwise.ui.standardPadding
import se.atte.bragwise.ui.standardPaddingSmall
import se.atte.bragwise.domain.BetOption
import se.atte.bragwise.domain.OptionType
import se.atte.bragwise.domain.PredictionPayload
import se.atte.bragwise.mvi.UiState
import se.atte.bragwise.theme.ThemePreview
import se.atte.bragwise.ui.LocalSnackbarHost
import se.atte.bragwise.ui.components.AppButton
import se.atte.bragwise.ui.components.AppFilterChip
import se.atte.bragwise.ui.components.BottomActionBar
import se.atte.bragwise.ui.components.RankingDragList
import se.atte.bragwise.ui.components.SectionCard
import se.atte.bragwise.ui.components.flagEmoji
import se.atte.bragwise.verify.VerifyAutomation

@Composable
fun PredictScreen(
    viewModel: PredictViewModel,
    snackbarHostState: SnackbarHostState,
    onSubmitted: () -> Unit,
) {
    val state by viewModel.state.collectAsStateWithLifecycle()

    ObserveEffects(viewModel.effects) { effect ->
        when (effect) {
            PredictViewModel.Effect.Submitted -> onSubmitted()
            is PredictViewModel.Effect.Snackbar -> snackbarHostState.showSnackbar(effect.text)
        }
    }

    when (val ui = state.ui) {
        UiState.Loading -> Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
            CircularProgressIndicator()
        }
        is UiState.Failed -> Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
            Text(
                text = ui.cause.toUserMessage(),
                style = MaterialTheme.typography.bodyLarge,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
        is UiState.Empty -> Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
            Text("No bets")
        }
        is UiState.Ready -> PredictContent(
            bets = ui.data.bets,
            drafts = state.drafts,
            submitting = state.submitting,
            onSinglePick = { betId, optionId ->
                viewModel.onIntent(PredictViewModel.Intent.SetSinglePick(betId, optionId))
            },
            onBoolean = { betId, value ->
                viewModel.onIntent(PredictViewModel.Intent.SetBoolean(betId, value))
            },
            onRanking = { betId, orderedIds ->
                viewModel.onIntent(PredictViewModel.Intent.SetRanking(betId, orderedIds))
            },
            onSubmit = { viewModel.onIntent(PredictViewModel.Intent.Submit) },
        )
    }
}

@Composable
private fun PredictContent(
    bets: List<Bet>,
    drafts: Map<String, PredictionPayload>,
    submitting: Boolean,
    onSinglePick: (String, String) -> Unit,
    onBoolean: (String, Boolean) -> Unit,
    onRanking: (String, List<String>) -> Unit,
    onSubmit: () -> Unit,
) {
    LaunchedEffect(bets) {
        VerifyAutomation.consumePendingRankingFill()?.let { (betId, orderedOptionIds) ->
            onRanking(betId, orderedOptionIds)
        }
    }

    LaunchedEffect(bets, drafts, submitting) {
        if (!VerifyAutomation.autoSubmitPredictions || submitting) return@LaunchedEffect
        val allComplete = bets.isNotEmpty() && bets.all { drafts[it.id].isCompleteFor(it) }
        if (allComplete) {
            VerifyAutomation.clearAutoSubmitPredictions()
            println("BRAGWISE_VERIFY eurovision_ranking: auto-submitting predictions")
            onSubmit()
        }
    }

    Column(Modifier.fillMaxSize()) {
        val hasRanking = bets.any { it is Bet.Ranking }
        Box(modifier = Modifier.weight(1f).fillMaxWidth()) {
            LazyColumn(
                modifier = Modifier.fillMaxSize(),
                contentPadding = PaddingValues(
                    start = standardPadding,
                    top = standardPadding,
                    end = if (hasRanking) 40.dp else standardPadding,
                    bottom = standardPadding,
                ),
                verticalArrangement = Arrangement.spacedBy(standardPadding),
            ) {
                items(items = bets, key = { it.id }) { bet ->
                    BetCard(
                        bet = bet,
                        draft = drafts[bet.id],
                        onSinglePick = onSinglePick,
                        onBoolean = onBoolean,
                        onRanking = onRanking,
                    )
                }
            }
            if (hasRanking) {
                ScrollHintIcon(
                    modifier = Modifier
                        .align(Alignment.CenterEnd)
                        .padding(end = standardPaddingSmall),
                )
            }
        }

        val completed = bets.count { drafts[it.id].isCompleteFor(it) }
        BottomActionBar {
            AppButton(
                modifier = Modifier.fillMaxWidth().testTag("predict_submit"),
                onClick = onSubmit,
                enabled = !submitting && completed == bets.size,
            ) {
                Text(
                    text = when {
                        submitting -> "Submitting…"
                        completed < bets.size -> "Predict $completed/${bets.size}"
                        else -> "Save predictions"
                    },
                )
            }
        }
    }
}

@Composable
private fun BetCard(
    bet: Bet,
    draft: PredictionPayload?,
    onSinglePick: (String, String) -> Unit,
    onBoolean: (String, Boolean) -> Unit,
    onRanking: (String, List<String>) -> Unit,
) {
    SectionCard(title = bet.title) {
        when (bet) {
            is Bet.SinglePick -> {
                if (bet.optionType == OptionType.COUNTRY) {
                    // Vertical list with flag leading icon — mirrors Duolingo course list
                    Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
                        bet.options.forEach { option ->
                            val selected = (draft as? PredictionPayload.SinglePick)?.optionId == option.id
                            CountryOptionRow(
                                option = option,
                                selected = selected,
                                onClick = { onSinglePick(bet.id, option.id) },
                            )
                        }
                    }
                } else {
                    Row(horizontalArrangement = Arrangement.spacedBy(standardPaddingSmall)) {
                        bet.options.forEach { option ->
                            val selected = (draft as? PredictionPayload.SinglePick)?.optionId == option.id
                            AppFilterChip(
                                selected = selected,
                                onClick = { onSinglePick(bet.id, option.id) },
                                label = { Text(option.label) },
                            )
                        }
                    }
                }
            }
            is Bet.BooleanProp -> Row(
                horizontalArrangement = Arrangement.spacedBy(standardPaddingSmall),
            ) {
                val current = (draft as? PredictionPayload.BooleanProp)?.value
                AppFilterChip(
                    selected = current == true,
                    onClick = { onBoolean(bet.id, true) },
                    label = { Text("Yes") },
                )
                AppFilterChip(
                    selected = current == false,
                    onClick = { onBoolean(bet.id, false) },
                    label = { Text("No") },
                )
            }
            is Bet.Ranking -> RankingDragList(
                options = bet.options,
                topN = bet.topN,
                orderedOptionIds = (draft as? PredictionPayload.Ranking)?.orderedOptionIds ?: emptyList(),
                showFlag = bet.optionType == OptionType.COUNTRY,
                onReorder = { ids -> onRanking(bet.id, ids) },
            )
        }
    }
}

@Composable
private fun ScrollHintIcon(modifier: Modifier = Modifier) {
    Column(
        modifier = modifier
            .height(160.dp)
            .width(24.dp),
        verticalArrangement = Arrangement.Center,
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        Icon(
            imageVector = Lucide.ChevronUp,
            contentDescription = null,
            tint = MaterialTheme.colorScheme.onSurfaceVariant,
            modifier = Modifier.size(20.dp),
        )
        Box(
            modifier = Modifier
                .width(2.dp)
                .weight(1f)
                .background(MaterialTheme.colorScheme.onSurfaceVariant),
        )
        Icon(
            imageVector = Lucide.ChevronDown,
            contentDescription = null,
            tint = MaterialTheme.colorScheme.onSurfaceVariant,
            modifier = Modifier.size(20.dp),
        )
    }
}

@Composable
private fun CountryOptionRow(
    option: BetOption,
    selected: Boolean,
    onClick: () -> Unit,
) {
    val bg = if (selected) MaterialTheme.colorScheme.primaryContainer
             else MaterialTheme.colorScheme.surfaceVariant
    val borderColor = if (selected) MaterialTheme.colorScheme.primary
                      else MaterialTheme.colorScheme.outline

    Box(
        modifier = Modifier
            .fillMaxWidth()
            .border(2.dp, borderColor, MaterialTheme.shapes.medium)
            .background(bg, MaterialTheme.shapes.medium)
            .clickable { onClick() }
            .padding(horizontal = standardPadding, vertical = 14.dp),
    ) {
        Row(
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            if (option.countryCode != null) {
                Text(
                    text = flagEmoji(option.countryCode),
                    style = MaterialTheme.typography.titleLarge,
                )
            }
            Text(
                text = option.label,
                style = MaterialTheme.typography.bodyLarge,
                fontWeight = FontWeight.SemiBold,
                color = if (selected) MaterialTheme.colorScheme.onPrimaryContainer
                        else MaterialTheme.colorScheme.onSurface,
                modifier = Modifier.weight(1f),
            )
            if (selected) {
                Text(
                    text = "✓",
                    style = MaterialTheme.typography.titleMedium,
                    color = MaterialTheme.colorScheme.primary,
                )
            } else {
                Text(
                    text = ">",
                    style = MaterialTheme.typography.titleMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
        }
    }
}

internal fun PredictionPayload?.isCompleteFor(bet: Bet): Boolean = when (bet) {
    is Bet.Ranking -> (this as? PredictionPayload.Ranking)?.orderedOptionIds?.size == bet.topN
    is Bet.SinglePick, is Bet.BooleanProp -> this != null
}

// region Previews

private val previewBets = listOf(
    Bet.BooleanProp(id = "b1", title = "Will Argentina win the final?"),
    Bet.SinglePick(
        id = "b2",
        title = "Top scorer",
        options = listOf(BetOption("o1", "Mbappe"), BetOption("o2", "Messi"), BetOption("o3", "Haaland")),
    ),
    Bet.SinglePick(
        id = "b3",
        title = "Winner",
        optionType = OptionType.COUNTRY,
        options = listOf(
            BetOption("o1", "France", "FR"),
            BetOption("o2", "Brazil", "BR"),
            BetOption("o3", "Germany", "DE"),
        ),
    ),
    Bet.Ranking(
        id = "b4",
        title = "World Cup top 3",
        optionType = OptionType.COUNTRY,
        topN = 3,
        options = listOf(
            BetOption("o1", "Argentina", "AR"),
            BetOption("o2", "Spain", "ES"),
            BetOption("o3", "England", "GB"),
        ),
    ),
)

@Preview
@Composable
private fun Predict_Ready_Empty_Preview() {
    ThemePreview {
        CompositionLocalProvider(LocalSnackbarHost provides remember { SnackbarHostState() }) {
            PredictContent(
                bets = previewBets,
                drafts = emptyMap(),
                submitting = false,
                onSinglePick = { _, _ -> },
                onBoolean = { _, _ -> },
                onRanking = { _, _ -> },
                onSubmit = {},
            )
        }
    }
}

@Preview
@Composable
private fun Predict_Ready_Partial_Preview() {
    ThemePreview {
        CompositionLocalProvider(LocalSnackbarHost provides remember { SnackbarHostState() }) {
            PredictContent(
                bets = previewBets,
                drafts = mapOf("b1" to PredictionPayload.BooleanProp(true)),
                submitting = false,
                onSinglePick = { _, _ -> },
                onBoolean = { _, _ -> },
                onRanking = { _, _ -> },
                onSubmit = {},
            )
        }
    }
}

@Preview
@Composable
private fun Predict_Ranking_Active_Preview() {
    ThemePreview {
        CompositionLocalProvider(LocalSnackbarHost provides remember { SnackbarHostState() }) {
            PredictContent(
                bets = previewBets,
                drafts = mapOf(
                    "b1" to PredictionPayload.BooleanProp(true),
                    "b4" to PredictionPayload.Ranking(orderedOptionIds = listOf("o1")),
                ),
                submitting = false,
                onSinglePick = { _, _ -> },
                onBoolean = { _, _ -> },
                onRanking = { _, _ -> },
                onSubmit = {},
            )
        }
    }
}

// endregion

