package se.atte.bragwise.ui.screens.create

import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.togetherWith
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import org.koin.compose.koinInject
import org.jetbrains.compose.resources.getString
import org.jetbrains.compose.resources.stringResource
import bragwise.shared.generated.resources.Res
import bragwise.shared.generated.resources.cc_add_bet
import bragwise.shared.generated.resources.cc_add_bet_title
import bragwise.shared.generated.resources.cc_add_country
import bragwise.shared.generated.resources.cc_add_option
import bragwise.shared.generated.resources.cc_bet_cancel_a11y
import bragwise.shared.generated.resources.cc_bet_remove
import bragwise.shared.generated.resources.cc_bet_type_day
import bragwise.shared.generated.resources.cc_bet_type_multi_select
import bragwise.shared.generated.resources.cc_bet_type_number
import bragwise.shared.generated.resources.cc_bet_type_over_under
import bragwise.shared.generated.resources.cc_bet_type_ranking
import bragwise.shared.generated.resources.cc_bet_type_single_pick
import bragwise.shared.generated.resources.cc_bet_type_time
import bragwise.shared.generated.resources.cc_bet_type_yes_no
import bragwise.shared.generated.resources.cc_over_under_line_label
import bragwise.shared.generated.resources.cc_guess_scoring_closest
import bragwise.shared.generated.resources.cc_guess_scoring_exact
import bragwise.shared.generated.resources.cc_mode_single
import bragwise.shared.generated.resources.cc_mode_multiple
import bragwise.shared.generated.resources.cc_mode_single_hint
import bragwise.shared.generated.resources.cc_single_question_label
import bragwise.shared.generated.resources.cc_bets_visible_hint
import bragwise.shared.generated.resources.cc_bets_visible_label
import bragwise.shared.generated.resources.cc_cancel
import bragwise.shared.generated.resources.cc_countries
import bragwise.shared.generated.resources.cc_country_placeholder
import bragwise.shared.generated.resources.cc_custom_options
import bragwise.shared.generated.resources.cc_deadline_label
import bragwise.shared.generated.resources.cc_decrease_top_n_a11y
import bragwise.shared.generated.resources.cc_done
import bragwise.shared.generated.resources.cc_duplicate_options_error
import bragwise.shared.generated.resources.cc_edit_bet_title
import bragwise.shared.generated.resources.cc_increase_top_n_a11y
import bragwise.shared.generated.resources.cc_invite_dialog_title
import bragwise.shared.generated.resources.cc_invited_summary
import bragwise.shared.generated.resources.cc_no_friends_yet
import bragwise.shared.generated.resources.cc_option_label
import bragwise.shared.generated.resources.cc_pick_friends
import bragwise.shared.generated.resources.cc_publish
import bragwise.shared.generated.resources.cc_question_label
import bragwise.shared.generated.resources.cc_remove_option_a11y
import bragwise.shared.generated.resources.cc_save_bet
import bragwise.shared.generated.resources.cc_save_draft
import bragwise.shared.generated.resources.cc_saving_dialog
import bragwise.shared.generated.resources.cc_show_less
import bragwise.shared.generated.resources.cc_show_more_options
import bragwise.shared.generated.resources.cc_help_mode_title
import bragwise.shared.generated.resources.cc_help_mode_body
import bragwise.shared.generated.resources.cc_help_deadline_title
import bragwise.shared.generated.resources.cc_help_deadline_body
import bragwise.shared.generated.resources.cc_help_visibility_title
import bragwise.shared.generated.resources.cc_help_visibility_body
import bragwise.shared.generated.resources.cc_help_bets_visible_title
import bragwise.shared.generated.resources.cc_help_bets_visible_body
import bragwise.shared.generated.resources.cc_help_bet_type_title
import bragwise.shared.generated.resources.cc_help_bet_type_body
import bragwise.shared.generated.resources.cc_help_option_type_title
import bragwise.shared.generated.resources.cc_help_option_type_body
import bragwise.shared.generated.resources.cc_help_scoring_title
import bragwise.shared.generated.resources.cc_help_scoring_body
import bragwise.shared.generated.resources.cc_label_bet_type
import bragwise.shared.generated.resources.cc_label_scoring
import bragwise.shared.generated.resources.cc_title_field
import bragwise.shared.generated.resources.cc_top_n_ranked
import bragwise.shared.generated.resources.cc_visibility_friends
import bragwise.shared.generated.resources.cc_visibility_friends_hint
import bragwise.shared.generated.resources.cc_visibility_link_note
import bragwise.shared.generated.resources.cc_visibility_invite_only
import bragwise.shared.generated.resources.cc_visibility_invite_only_hint
import bragwise.shared.generated.resources.cc_who_can_join
import se.atte.bragwise.data.OnboardingPrefs
import se.atte.bragwise.mvi.ObserveEffects
import se.atte.bragwise.ui.components.LoadingDialog
import se.atte.bragwise.ui.components.NameGateDialog
import se.atte.bragwise.ui.InputLimits
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.composables.icons.lucide.Lucide
import com.composables.icons.lucide.Minus
import com.composables.icons.lucide.Plus
import com.composables.icons.lucide.X
import se.atte.bragwise.domain.Bet
import se.atte.bragwise.domain.BetOption
import se.atte.bragwise.domain.GuessGranularity
import se.atte.bragwise.domain.OptionType
import se.atte.bragwise.domain.Visibility
import se.atte.bragwise.theme.ThemePreview
import se.atte.bragwise.ui.preview.sampleBets
import se.atte.bragwise.ui.components.AppButton
import se.atte.bragwise.ui.components.CountryAutocompleteField
import se.atte.bragwise.ui.components.AppFilterChip
import se.atte.bragwise.ui.components.AppOutlinedButton
import se.atte.bragwise.ui.components.AppTextButton
import se.atte.bragwise.ui.components.BottomActionBar
import se.atte.bragwise.ui.components.DeadlinePickerField
import se.atte.bragwise.ui.components.FriendPickerDialog
import se.atte.bragwise.ui.components.InfoIcon
import se.atte.bragwise.ui.components.SectionCard
import se.atte.bragwise.ui.components.SectionTitleRow
import se.atte.bragwise.ui.standardPadding
import se.atte.bragwise.ui.standardPaddingSmall

@Composable
fun CreateChallengeScreen(
    viewModel: CreateChallengeViewModel,
    snackbarHostState: SnackbarHostState,
    onPublished: (String) -> Unit,
    onDraftSaved: (String) -> Unit,
) {
    val state by viewModel.state.collectAsStateWithLifecycle()
    val friends by viewModel.friends.collectAsStateWithLifecycle()
    val onboardingPrefs: OnboardingPrefs = koinInject()

    ObserveEffects(viewModel.effects) { effect ->
        when (effect) {
            is CreateChallengeViewModel.Effect.Published -> onPublished(effect.challengeId)
            is CreateChallengeViewModel.Effect.DraftSaved -> onDraftSaved(effect.challengeId)
            is CreateChallengeViewModel.Effect.Snackbar -> snackbarHostState.showSnackbar(
                getString(effect.message.res, *effect.message.args.toTypedArray())
            )
        }
    }

    if (state.submitting) {
        LoadingDialog(message = stringResource(Res.string.cc_saving_dialog))
    }

    if (state.needsName) {
        NameGateDialog(
            initialName = onboardingPrefs.chosenName.orEmpty(),
            onConfirm = { name -> viewModel.onIntent(CreateChallengeViewModel.Intent.ConfirmName(name)) },
            onDismiss = { viewModel.onIntent(CreateChallengeViewModel.Intent.DismissName) },
        )
    }

    var newBetTitle by remember { mutableStateOf("") }
    var options by remember { mutableStateOf(listOf("", "")) }
    var countryOptions by remember { mutableStateOf(defaultCountryOptions()) }
    var optionType by remember { mutableStateOf(OptionType.NONE) }
    var topN by remember { mutableIntStateOf(3) }
    var betType by remember { mutableStateOf(BetType.YesNo) }
    var guessClosest by remember { mutableStateOf(true) }
    var overUnderLine by remember { mutableStateOf("") }
    var editingBetId by rememberSaveable { mutableStateOf<String?>(null) }
    var showFriendPicker by rememberSaveable { mutableStateOf(false) }

    val isEditing = editingBetId != null && editingBetId != ""
    val resetEditor = {
        newBetTitle = ""
        options = listOf("", "")
        countryOptions = defaultCountryOptions()
        optionType = OptionType.NONE
        topN = 3
        betType = BetType.YesNo
        guessClosest = true
        overUnderLine = ""
    }
    val seedEditorFrom = { bet: Bet ->
        newBetTitle = bet.title
        when (bet) {
            is Bet.BooleanProp -> {
                betType = BetType.YesNo
                options = listOf("", "")
                countryOptions = defaultCountryOptions()
                optionType = OptionType.NONE
                topN = 3
                guessClosest = true
                overUnderLine = ""
            }
            is Bet.SinglePick -> {
                betType = BetType.SinglePick
                optionType = bet.optionType
                options = bet.options.map { it.label }
                countryOptions = bet.options
                topN = 3
                guessClosest = true
                overUnderLine = ""
            }
            is Bet.Ranking -> {
                betType = BetType.Ranking
                optionType = bet.optionType
                options = bet.options.map { it.label }
                countryOptions = bet.options
                topN = bet.topN
                guessClosest = true
                overUnderLine = ""
            }
            is Bet.Guess -> {
                betType = when (bet.granularity) {
                    GuessGranularity.TIME -> BetType.Time
                    GuessGranularity.DAY -> BetType.Day
                    GuessGranularity.NUMBER -> BetType.Number
                }
                options = listOf("", "")
                countryOptions = defaultCountryOptions()
                optionType = OptionType.NONE
                topN = 3
                guessClosest = bet.closest
                overUnderLine = ""
            }
            is Bet.MultiSelect -> {
                betType = BetType.MultiSelect
                optionType = bet.optionType
                options = bet.options.map { it.label }
                countryOptions = bet.options
                topN = 3
                guessClosest = true
                overUnderLine = ""
            }
            is Bet.OverUnder -> {
                betType = BetType.OverUnder
                options = listOf("", "")
                countryOptions = defaultCountryOptions()
                optionType = OptionType.NONE
                topN = 3
                guessClosest = true
                overUnderLine = bet.line.toString()
            }
        }
    }

    val resolvedOptions = when (optionType) {
        OptionType.COUNTRY -> countryOptions.filter { it.label.isNotBlank() }
        OptionType.NONE -> options.map { it.trim() }.filter { it.isNotEmpty() }
            .mapIndexed { index, label -> BetOption(id = "o$index", label = label) }
    }
    val hasDuplicateOptions = resolvedOptions
        .map { option -> option.countryCode ?: option.label.trim().lowercase() }
        .let { keys -> keys.size != keys.toSet().size }
    if (showFriendPicker) {
        FriendPickerDialog(
            friends = friends,
            initial = state.invitedUids,
            confirmLabel = stringResource(Res.string.cc_done),
            onDismiss = { showFriendPicker = false },
            onConfirm = { uids ->
                viewModel.onIntent(CreateChallengeViewModel.Intent.SetInvitedUids(uids))
                showFriendPicker = false
            },
        )
    }

    Column(Modifier.fillMaxSize()) {
        LazyColumn(
            modifier = Modifier.weight(1f).fillMaxWidth(),
            contentPadding = PaddingValues(standardPadding),
            verticalArrangement = Arrangement.spacedBy(standardPadding),
        ) {
            item {
                SectionCard {
                    OutlinedTextField(
                        value = state.title,
                        onValueChange = { if (it.length <= InputLimits.CHALLENGE_TITLE) viewModel.onIntent(CreateChallengeViewModel.Intent.SetTitle(it)) },
                        label = { Text(stringResource(Res.string.cc_title_field)) },
                        modifier = Modifier.fillMaxWidth().testTag("create_title"),
                        singleLine = true,
                    )
                }
            }

            item {
                SectionCard {
                    SectionTitleRow(
                        title = stringResource(Res.string.cc_help_mode_title),
                        infoTitle = stringResource(Res.string.cc_help_mode_title),
                        infoBody = stringResource(Res.string.cc_help_mode_body),
                    )
                    Spacer(Modifier.height(12.dp))
                    Row(horizontalArrangement = Arrangement.spacedBy(standardPaddingSmall)) {
                        AppFilterChip(
                            selected = state.mode == CreateMode.MULTI,
                            onClick = { viewModel.onIntent(CreateChallengeViewModel.Intent.SetCreateMode(CreateMode.MULTI)) },
                            label = { Text(stringResource(Res.string.cc_mode_multiple)) },
                            modifier = Modifier.testTag("create_mode_multi"),
                        )
                        AppFilterChip(
                            selected = state.mode == CreateMode.SINGLE,
                            onClick = { viewModel.onIntent(CreateChallengeViewModel.Intent.SetCreateMode(CreateMode.SINGLE)) },
                            label = { Text(stringResource(Res.string.cc_mode_single)) },
                            modifier = Modifier.testTag("create_mode_single"),
                        )
                    }
                    if (state.mode == CreateMode.SINGLE) {
                        Text(
                            text = stringResource(Res.string.cc_mode_single_hint),
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                            modifier = Modifier.padding(top = 8.dp),
                        )
                    }
                }
            }

            item {
                SectionCard {
                    SectionTitleRow(
                        title = stringResource(Res.string.cc_deadline_label),
                        infoTitle = stringResource(Res.string.cc_help_deadline_title),
                        infoBody = stringResource(Res.string.cc_help_deadline_body),
                    )
                    Spacer(Modifier.height(12.dp))
                    DeadlinePickerField(
                        locksAt = state.locksAt,
                        onLocksAtChange = { viewModel.onIntent(CreateChallengeViewModel.Intent.SetLocksAt(it)) },
                    )
                }
            }

            item {
                SectionCard {
                    SectionTitleRow(
                        title = stringResource(Res.string.cc_who_can_join),
                        infoTitle = stringResource(Res.string.cc_help_visibility_title),
                        infoBody = stringResource(Res.string.cc_help_visibility_body),
                    )
                    Spacer(Modifier.height(12.dp))
                    Row(horizontalArrangement = Arrangement.spacedBy(standardPaddingSmall)) {
                        AppFilterChip(
                            selected = state.visibility == Visibility.FRIENDS,
                            onClick = { viewModel.onIntent(CreateChallengeViewModel.Intent.SetVisibility(Visibility.FRIENDS)) },
                            label = { Text(stringResource(Res.string.cc_visibility_friends)) },
                        )
                        AppFilterChip(
                            selected = state.visibility == Visibility.INVITE_ONLY,
                            onClick = {
                                viewModel.onIntent(CreateChallengeViewModel.Intent.SetVisibility(Visibility.INVITE_ONLY))
                                showFriendPicker = true
                            },
                            label = { Text(stringResource(Res.string.cc_visibility_invite_only)) },
                        )
                    }
                    Text(
                        text = when (state.visibility) {
                            Visibility.FRIENDS -> stringResource(Res.string.cc_visibility_friends_hint)
                            Visibility.INVITE_ONLY -> stringResource(Res.string.cc_visibility_invite_only_hint)
                            else -> ""
                        },
                        style = MaterialTheme.typography.bodyLarge,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        modifier = Modifier.padding(top = 12.dp),
                    )
                    if (state.visibility == Visibility.INVITE_ONLY) {
                        val inviteLabel = if (state.invitedUids.isNotEmpty()) {
                            stringResource(Res.string.cc_invited_summary, state.invitedUids.size)
                        } else {
                            stringResource(Res.string.cc_pick_friends)
                        }
                        Text(
                            text = inviteLabel,
                            style = MaterialTheme.typography.bodyLarge,
                            color = MaterialTheme.colorScheme.primary,
                            modifier = Modifier
                                .padding(top = 8.dp)
                                .clickable { showFriendPicker = true },
                        )
                    }
                    Text(
                        text = stringResource(Res.string.cc_visibility_link_note),
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        modifier = Modifier.padding(top = 8.dp),
                    )
                    HorizontalDivider(modifier = Modifier.padding(top = 12.dp))
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(top = 12.dp),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.SpaceBetween,
                    ) {
                        Column(modifier = Modifier.weight(1f)) {
                            Text(
                                text = stringResource(Res.string.cc_bets_visible_label),
                                style = MaterialTheme.typography.bodyLarge,
                            )
                            Text(
                                text = stringResource(Res.string.cc_bets_visible_hint),
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant,
                            )
                        }
                        InfoIcon(
                            title = stringResource(Res.string.cc_help_bets_visible_title),
                            body = stringResource(Res.string.cc_help_bets_visible_body),
                        )
                        Switch(
                            checked = state.betsVisible,
                            onCheckedChange = { viewModel.onIntent(CreateChallengeViewModel.Intent.SetBetsVisible(it)) },
                        )
                    }
                }
            }

            item {
                AnimatedContent(
                    targetState = state.mode,
                    transitionSpec = { fadeIn() togetherWith fadeOut() },
                    modifier = Modifier.fillMaxWidth(),
                ) { mode ->
                    when (mode) {
                        CreateMode.MULTI -> {
                            Column(verticalArrangement = Arrangement.spacedBy(standardPadding)) {
                                state.bets.forEach { bet ->
                                    if (editingBetId == bet.id) {
                                        BetEditor(
                                            title = stringResource(Res.string.cc_edit_bet_title),
                                            betType = betType,
                                            onBetTypeChange = { newType ->
                                                betType = newType
                                                if (newType == BetType.YesNo) {
                                                    options = listOf("", "")
                                                    optionType = OptionType.NONE
                                                }
                                            },
                                            optionType = optionType,
                                            onOptionTypeChange = { optionType = it },
                                            question = newBetTitle,
                                            onQuestionChange = { newBetTitle = it },
                                            options = options,
                                            onOptionsChange = { options = it },
                                            countryOptions = countryOptions,
                                            onCountryOptionsChange = { countryOptions = it },
                                            topN = topN,
                                            onTopNChange = { topN = it },
                                            canSave = newBetTitle.isNotBlank() && !hasDuplicateOptions && when (betType) {
                                                BetType.YesNo -> true
                                                BetType.SinglePick -> resolvedOptions.size >= 2
                                                BetType.Ranking -> resolvedOptions.size >= topN
                                                BetType.Time, BetType.Day, BetType.Number -> true
                                                BetType.MultiSelect -> resolvedOptions.size >= 2
                                                BetType.OverUnder -> overUnderLine.toLongOrNull() != null
                                            },
                                            saveLabel = stringResource(Res.string.cc_save_bet),
                                            guessClosest = guessClosest,
                                            onGuessClosestChange = { guessClosest = it },
                                            overUnderLine = overUnderLine,
                                            onOverUnderLineChange = { overUnderLine = it },
                                            duplicateOptionError = if (hasDuplicateOptions) stringResource(Res.string.cc_duplicate_options_error) else null,
                                            onCancel = {
                                                editingBetId = null
                                                resetEditor()
                                            },
                                            onSave = {
                                                val updated: Bet = when (betType) {
                                                    BetType.YesNo -> Bet.BooleanProp(id = bet.id, title = newBetTitle)
                                                    BetType.SinglePick -> Bet.SinglePick(
                                                        id = bet.id,
                                                        title = newBetTitle,
                                                        optionType = optionType,
                                                        options = resolvedOptions,
                                                    )
                                                    BetType.Ranking -> Bet.Ranking(
                                                        id = bet.id,
                                                        title = newBetTitle,
                                                        optionType = optionType,
                                                        options = resolvedOptions,
                                                        topN = topN,
                                                    )
                                                    BetType.Time -> Bet.Guess(id = bet.id, title = newBetTitle, granularity = GuessGranularity.TIME, closest = guessClosest)
                                                    BetType.Day -> Bet.Guess(id = bet.id, title = newBetTitle, granularity = GuessGranularity.DAY, closest = guessClosest)
                                                    BetType.Number -> Bet.Guess(id = bet.id, title = newBetTitle, granularity = GuessGranularity.NUMBER, closest = guessClosest)
                                                    BetType.MultiSelect -> Bet.MultiSelect(
                                                        id = bet.id,
                                                        title = newBetTitle,
                                                        optionType = optionType,
                                                        options = resolvedOptions,
                                                    )
                                                    BetType.OverUnder -> Bet.OverUnder(
                                                        id = bet.id,
                                                        title = newBetTitle,
                                                        line = overUnderLine.toLongOrNull() ?: 0L,
                                                    )
                                                }
                                                viewModel.onIntent(CreateChallengeViewModel.Intent.UpdateBet(updated))
                                                editingBetId = null
                                                resetEditor()
                                            },
                                        )
                                    } else {
                                        BetCard(
                                            bet = bet,
                                            onRemove = { viewModel.onIntent(CreateChallengeViewModel.Intent.RemoveBet(bet.id)) },
                                            onClick = {
                                                seedEditorFrom(bet)
                                                editingBetId = bet.id
                                            },
                                        )
                                    }
                                }
                                if (editingBetId == "") {
                                    BetEditor(
                                        title = stringResource(Res.string.cc_add_bet_title),
                                        betType = betType,
                                        onBetTypeChange = { newType ->
                                            betType = newType
                                            if (newType == BetType.YesNo) {
                                                options = listOf("", "")
                                                optionType = OptionType.NONE
                                            }
                                        },
                                        optionType = optionType,
                                        onOptionTypeChange = { optionType = it },
                                        question = newBetTitle,
                                        onQuestionChange = { newBetTitle = it },
                                        options = options,
                                        onOptionsChange = { options = it },
                                        countryOptions = countryOptions,
                                        onCountryOptionsChange = { countryOptions = it },
                                        topN = topN,
                                        onTopNChange = { topN = it },
                                        canSave = newBetTitle.isNotBlank() && !hasDuplicateOptions && when (betType) {
                                            BetType.YesNo -> true
                                            BetType.SinglePick -> resolvedOptions.size >= 2
                                            BetType.Ranking -> resolvedOptions.size >= topN
                                            BetType.Time, BetType.Day, BetType.Number -> true
                                            BetType.MultiSelect -> resolvedOptions.size >= 2
                                            BetType.OverUnder -> overUnderLine.toLongOrNull() != null
                                        },
                                        saveLabel = stringResource(Res.string.cc_save_bet),
                                        guessClosest = guessClosest,
                                        onGuessClosestChange = { guessClosest = it },
                                        overUnderLine = overUnderLine,
                                        onOverUnderLineChange = { overUnderLine = it },
                                        duplicateOptionError = if (hasDuplicateOptions) stringResource(Res.string.cc_duplicate_options_error) else null,
                                        onCancel = {
                                            editingBetId = null
                                            resetEditor()
                                        },
                                        onSave = {
                                            when (betType) {
                                                BetType.YesNo -> viewModel.onIntent(
                                                    CreateChallengeViewModel.Intent.AddBoolean(title = newBetTitle),
                                                )
                                                BetType.SinglePick -> viewModel.onIntent(
                                                    CreateChallengeViewModel.Intent.AddSinglePick(
                                                        title = newBetTitle,
                                                        optionType = optionType,
                                                        options = resolvedOptions,
                                                    ),
                                                )
                                                BetType.Ranking -> viewModel.onIntent(
                                                    CreateChallengeViewModel.Intent.AddRanking(
                                                        title = newBetTitle,
                                                        optionType = optionType,
                                                        options = resolvedOptions,
                                                        topN = topN,
                                                    ),
                                                )
                                                BetType.Time -> viewModel.onIntent(
                                                    CreateChallengeViewModel.Intent.AddGuess(
                                                        title = newBetTitle,
                                                        granularity = GuessGranularity.TIME,
                                                        closest = guessClosest,
                                                    ),
                                                )
                                                BetType.Day -> viewModel.onIntent(
                                                    CreateChallengeViewModel.Intent.AddGuess(
                                                        title = newBetTitle,
                                                        granularity = GuessGranularity.DAY,
                                                        closest = guessClosest,
                                                    ),
                                                )
                                                BetType.Number -> viewModel.onIntent(
                                                    CreateChallengeViewModel.Intent.AddGuess(
                                                        title = newBetTitle,
                                                        granularity = GuessGranularity.NUMBER,
                                                        closest = guessClosest,
                                                    ),
                                                )
                                                BetType.MultiSelect -> viewModel.onIntent(
                                                    CreateChallengeViewModel.Intent.AddMultiSelect(
                                                        title = newBetTitle,
                                                        optionType = optionType,
                                                        options = resolvedOptions,
                                                    ),
                                                )
                                                BetType.OverUnder -> viewModel.onIntent(
                                                    CreateChallengeViewModel.Intent.AddOverUnder(
                                                        title = newBetTitle,
                                                        line = overUnderLine.toLongOrNull() ?: 0L,
                                                    ),
                                                )
                                            }
                                            editingBetId = null
                                            resetEditor()
                                        },
                                    )
                                } else if (!isEditing) {
                                    AppOutlinedButton(
                                        modifier = Modifier.fillMaxWidth().testTag("create_add_bet"),
                                        onClick = { editingBetId = "" },
                                    ) { Text(stringResource(Res.string.cc_add_bet)) }
                                }
                            }
                        }
                        CreateMode.SINGLE -> {
                            SingleBetEditor(
                                singleBet = state.bets.firstOrNull() as? Bet.Guess,
                                onSave = { title, granularity ->
                                    val existing = state.bets.firstOrNull()
                                    if (existing != null) {
                                        viewModel.onIntent(
                                            CreateChallengeViewModel.Intent.UpdateBet(
                                                Bet.Guess(
                                                    id = existing.id,
                                                    title = title,
                                                    granularity = granularity,
                                                    closest = true,
                                                    placement = true,
                                                )
                                            )
                                        )
                                    } else {
                                        viewModel.onIntent(
                                            CreateChallengeViewModel.Intent.AddGuess(
                                                title = title,
                                                granularity = granularity,
                                                closest = true,
                                                placement = true,
                                            )
                                        )
                                    }
                                },
                            )
                        }
                    }
                }
            }
        }

        BottomActionBar {
            AppOutlinedButton(
                modifier = Modifier.weight(1f),
                onClick = { viewModel.onIntent(CreateChallengeViewModel.Intent.SaveDraft) },
                enabled = !state.submitting,
            ) { Text(stringResource(Res.string.cc_save_draft)) }
            AppButton(
                modifier = Modifier.weight(1f).testTag("create_publish"),
                onClick = { viewModel.onIntent(CreateChallengeViewModel.Intent.Publish) },
                enabled = !state.submitting && state.bets.isNotEmpty() && state.title.isNotBlank(),
            ) { Text(stringResource(Res.string.cc_publish)) }
        }
    }
}

private enum class BetType { YesNo, SinglePick, Ranking, Time, Day, Number, MultiSelect, OverUnder }

@Composable
private fun BetEditor(
    title: String,
    betType: BetType,
    onBetTypeChange: (BetType) -> Unit,
    optionType: OptionType,
    onOptionTypeChange: (OptionType) -> Unit,
    question: String,
    onQuestionChange: (String) -> Unit,
    options: List<String>,
    onOptionsChange: (List<String>) -> Unit,
    countryOptions: List<BetOption>,
    onCountryOptionsChange: (List<BetOption>) -> Unit,
    topN: Int,
    onTopNChange: (Int) -> Unit,
    canSave: Boolean,
    saveLabel: String,
    guessClosest: Boolean,
    onGuessClosestChange: (Boolean) -> Unit,
    overUnderLine: String,
    onOverUnderLineChange: (String) -> Unit,
    onSave: () -> Unit,
    onCancel: () -> Unit,
    duplicateOptionError: String? = null,
) {
    SectionCard {
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Text(text = title, style = MaterialTheme.typography.titleMedium)
            IconButton(onClick = onCancel) {
                Icon(
                    imageVector = Lucide.X,
                    contentDescription = stringResource(Res.string.cc_bet_cancel_a11y),
                    tint = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
        }
        Row(
            modifier = Modifier.fillMaxWidth().padding(top = standardPaddingSmall),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Text(
                text = stringResource(Res.string.cc_label_bet_type),
                style = MaterialTheme.typography.bodyLarge,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
            InfoIcon(
                title = stringResource(Res.string.cc_help_bet_type_title),
                body = stringResource(Res.string.cc_help_bet_type_body),
            )
        }
        FlowRow(
            horizontalArrangement = Arrangement.spacedBy(standardPaddingSmall),
            verticalArrangement = Arrangement.spacedBy(standardPaddingSmall),
            modifier = Modifier.padding(top = standardPaddingSmall),
        ) {
            AppFilterChip(
                selected = betType == BetType.YesNo,
                onClick = { onBetTypeChange(BetType.YesNo) },
                label = { Text(stringResource(Res.string.cc_bet_type_yes_no)) },
            )
            AppFilterChip(
                selected = betType == BetType.SinglePick,
                onClick = { onBetTypeChange(BetType.SinglePick) },
                label = { Text(stringResource(Res.string.cc_bet_type_single_pick)) },
            )
            AppFilterChip(
                selected = betType == BetType.Ranking,
                onClick = { onBetTypeChange(BetType.Ranking) },
                label = { Text(stringResource(Res.string.cc_bet_type_ranking)) },
                modifier = Modifier.testTag("create_bet_ranking"),
            )
            AppFilterChip(
                selected = betType == BetType.Time,
                onClick = { onBetTypeChange(BetType.Time) },
                label = { Text(stringResource(Res.string.cc_bet_type_time)) },
                modifier = Modifier.testTag("create_bet_time"),
            )
            AppFilterChip(
                selected = betType == BetType.Day,
                onClick = { onBetTypeChange(BetType.Day) },
                label = { Text(stringResource(Res.string.cc_bet_type_day)) },
                modifier = Modifier.testTag("create_bet_day"),
            )
            AppFilterChip(
                selected = betType == BetType.Number,
                onClick = { onBetTypeChange(BetType.Number) },
                label = { Text(stringResource(Res.string.cc_bet_type_number)) },
                modifier = Modifier.testTag("create_bet_number"),
            )
            AppFilterChip(
                selected = betType == BetType.MultiSelect,
                onClick = { onBetTypeChange(BetType.MultiSelect) },
                label = { Text(stringResource(Res.string.cc_bet_type_multi_select)) },
                modifier = Modifier.testTag("create_bet_multi_select"),
            )
            AppFilterChip(
                selected = betType == BetType.OverUnder,
                onClick = { onBetTypeChange(BetType.OverUnder) },
                label = { Text(stringResource(Res.string.cc_bet_type_over_under)) },
                modifier = Modifier.testTag("create_bet_over_under"),
            )
        }
        if (betType == BetType.SinglePick || betType == BetType.Ranking || betType == BetType.MultiSelect) {
            Row(
                modifier = Modifier.fillMaxWidth().padding(top = standardPaddingSmall),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Text(
                    text = stringResource(Res.string.cc_help_option_type_title),
                    style = MaterialTheme.typography.bodyLarge,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
                InfoIcon(
                    title = stringResource(Res.string.cc_help_option_type_title),
                    body = stringResource(Res.string.cc_help_option_type_body),
                )
            }
            Row(
                horizontalArrangement = Arrangement.spacedBy(standardPaddingSmall),
                modifier = Modifier.padding(top = standardPaddingSmall),
            ) {
                AppFilterChip(
                    selected = optionType == OptionType.NONE,
                    onClick = { onOptionTypeChange(OptionType.NONE) },
                    label = { Text(stringResource(Res.string.cc_custom_options)) },
                )
                AppFilterChip(
                    selected = optionType == OptionType.COUNTRY,
                    onClick = {
                        onOptionTypeChange(OptionType.COUNTRY)
                        if (countryOptions.size < 2) onCountryOptionsChange(defaultCountryOptions())
                    },
                    label = { Text(stringResource(Res.string.cc_countries)) },
                    modifier = Modifier.testTag("create_bet_countries"),
                )
            }
        }
        OutlinedTextField(
            value = question,
            onValueChange = { if (it.length <= InputLimits.BET_TITLE) onQuestionChange(it) },
            label = { Text(stringResource(Res.string.cc_question_label)) },
            modifier = Modifier.fillMaxWidth().padding(top = 12.dp).testTag("create_bet_question"),
            singleLine = true,
        )
        if (betType == BetType.SinglePick || betType == BetType.Ranking || betType == BetType.MultiSelect) {
            if (optionType == OptionType.COUNTRY) {
                CountryOptionsEditor(
                    options = countryOptions,
                    onOptionsChange = onCountryOptionsChange,
                    modifier = Modifier.padding(top = standardPaddingSmall),
                )
            } else {
                OptionsEditor(
                    options = options,
                    onOptionsChange = onOptionsChange,
                    modifier = Modifier.padding(top = standardPaddingSmall),
                )
            }
        }
        if (betType == BetType.Ranking) {
            TopNStepper(
                value = topN,
                onValueChange = onTopNChange,
                modifier = Modifier.padding(top = standardPaddingSmall),
            )
        }
        if (betType == BetType.Time || betType == BetType.Day || betType == BetType.Number) {
            Row(
                modifier = Modifier.fillMaxWidth().padding(top = standardPaddingSmall),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Text(
                    text = stringResource(Res.string.cc_label_scoring),
                    style = MaterialTheme.typography.bodyLarge,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
                InfoIcon(
                    title = stringResource(Res.string.cc_help_scoring_title),
                    body = stringResource(Res.string.cc_help_scoring_body),
                )
            }
            Row(
                horizontalArrangement = Arrangement.spacedBy(standardPaddingSmall),
                modifier = Modifier.padding(top = standardPaddingSmall),
            ) {
                AppFilterChip(
                    selected = guessClosest,
                    onClick = { onGuessClosestChange(true) },
                    label = { Text(stringResource(Res.string.cc_guess_scoring_closest)) },
                )
                AppFilterChip(
                    selected = !guessClosest,
                    onClick = { onGuessClosestChange(false) },
                    label = { Text(stringResource(Res.string.cc_guess_scoring_exact)) },
                )
            }
        }
        if (betType == BetType.OverUnder) {
            OutlinedTextField(
                value = overUnderLine,
                onValueChange = onOverUnderLineChange,
                label = { Text(stringResource(Res.string.cc_over_under_line_label)) },
                modifier = Modifier.fillMaxWidth().padding(top = standardPaddingSmall),
                singleLine = true,
                keyboardOptions = androidx.compose.foundation.text.KeyboardOptions(
                    keyboardType = androidx.compose.ui.text.input.KeyboardType.Number,
                ),
            )
        }
        if (duplicateOptionError != null) {
            Text(
                text = duplicateOptionError,
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.error,
                modifier = Modifier.padding(top = standardPaddingSmall),
            )
        }
        AppOutlinedButton(
            modifier = Modifier.fillMaxWidth().padding(top = 12.dp).testTag("create_bet_save"),
            onClick = onSave,
            enabled = canSave,
        ) { Text(saveLabel) }
    }
}

@Composable
private fun BetCard(bet: Bet, onRemove: () -> Unit, onClick: () -> Unit) {
    var expanded by remember { mutableStateOf(false) }
    val options = when (bet) {
        is Bet.SinglePick -> bet.options
        is Bet.Ranking -> bet.options
        is Bet.MultiSelect -> bet.options
        is Bet.BooleanProp -> emptyList()
        is Bet.Guess -> emptyList()
        is Bet.OverUnder -> emptyList()
    }
    val showExpand = options.size > 4

    SectionCard(modifier = Modifier.clickable(onClick = onClick)) {
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.Top,
        ) {
            Text(
                text = bet.title,
                style = MaterialTheme.typography.bodyLarge,
                fontWeight = FontWeight.Bold,
                modifier = Modifier.weight(1f).padding(top = 4.dp),
            )
            IconButton(onClick = onRemove) {
                Icon(
                    imageVector = Lucide.X,
                    contentDescription = stringResource(Res.string.cc_bet_remove),
                    tint = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
        }
        Text(
            text = bet.kindLabel(),
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            modifier = Modifier.padding(top = 2.dp),
        )
        if (options.isNotEmpty()) {
            val visibleOptions = if (showExpand && !expanded) options.take(4) else options
            Column(
                modifier = Modifier.padding(top = standardPaddingSmall),
                verticalArrangement = Arrangement.spacedBy(2.dp),
            ) {
                visibleOptions.forEach { opt ->
                    Text(
                        text = opt.label,
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
            }
            if (showExpand) {
                val remaining = options.size - 4
                AppTextButton(onClick = { expanded = !expanded }) {
                    Text(if (expanded) stringResource(Res.string.cc_show_less) else stringResource(Res.string.cc_show_more_options, remaining))
                }
            }
        }
    }
}


/**
 * Focused editor for SINGLE mode: one Guess bet with placement=true forced.
 * Shows granularity chips (Time / Day / Number) and a question field.
 * Calls [onSave] each time the question or granularity changes (auto-save style —
 * no explicit Save button needed since there is exactly one bet).
 */
@Composable
private fun SingleBetEditor(
    singleBet: Bet.Guess?,
    onSave: (title: String, granularity: GuessGranularity) -> Unit,
) {
    var question by remember(singleBet?.title) { mutableStateOf(singleBet?.title ?: "") }
    var granularity by remember(singleBet?.granularity) { mutableStateOf(singleBet?.granularity ?: GuessGranularity.NUMBER) }

    SectionCard {
        FlowRow(
            horizontalArrangement = Arrangement.spacedBy(standardPaddingSmall),
            verticalArrangement = Arrangement.spacedBy(standardPaddingSmall),
        ) {
            AppFilterChip(
                selected = granularity == GuessGranularity.TIME,
                onClick = {
                    granularity = GuessGranularity.TIME
                    if (question.isNotBlank()) onSave(question, GuessGranularity.TIME)
                },
                label = { Text(stringResource(Res.string.cc_bet_type_time)) },
            )
            AppFilterChip(
                selected = granularity == GuessGranularity.DAY,
                onClick = {
                    granularity = GuessGranularity.DAY
                    if (question.isNotBlank()) onSave(question, GuessGranularity.DAY)
                },
                label = { Text(stringResource(Res.string.cc_bet_type_day)) },
            )
            AppFilterChip(
                selected = granularity == GuessGranularity.NUMBER,
                onClick = {
                    granularity = GuessGranularity.NUMBER
                    if (question.isNotBlank()) onSave(question, GuessGranularity.NUMBER)
                },
                label = { Text(stringResource(Res.string.cc_bet_type_number)) },
            )
        }
        OutlinedTextField(
            value = question,
            onValueChange = { v ->
                if (v.length <= InputLimits.BET_TITLE) {
                    question = v
                    if (v.isNotBlank()) onSave(v, granularity)
                }
            },
            label = { Text(stringResource(Res.string.cc_single_question_label)) },
            modifier = Modifier.fillMaxWidth().padding(top = 12.dp).testTag("create_single_question"),
            singleLine = true,
        )
    }
}

@Composable
private fun OptionsEditor(
    options: List<String>,
    onOptionsChange: (List<String>) -> Unit,
    modifier: Modifier = Modifier,
) {
    Column(
        modifier = modifier,
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
                        if (updated.length <= InputLimits.BET_OPTION) {
                            onOptionsChange(options.toMutableList().also { it[index] = updated })
                        }
                    },
                    label = { Text(stringResource(Res.string.cc_option_label, index + 1)) },
                    modifier = Modifier.weight(1f),
                    singleLine = true,
                )
                IconButton(
                    onClick = { onOptionsChange(options.toMutableList().also { it.removeAt(index) }) },
                    enabled = options.size > 2,
                ) {
                    Icon(
                        imageVector = Lucide.X,
                        contentDescription = stringResource(Res.string.cc_remove_option_a11y),
                        tint = if (options.size > 2)
                            MaterialTheme.colorScheme.onSurfaceVariant
                        else
                            MaterialTheme.colorScheme.onSurface.copy(alpha = 0.38f),
                    )
                }
            }
        }
        AppTextButton(onClick = { onOptionsChange(options + "") }) { Text(stringResource(Res.string.cc_add_option)) }
    }
}

@Composable
private fun CountryOptionsEditor(
    options: List<BetOption>,
    onOptionsChange: (List<BetOption>) -> Unit,
    modifier: Modifier = Modifier,
) {
    Column(
        modifier = modifier,
        verticalArrangement = Arrangement.spacedBy(standardPaddingSmall),
    ) {
        options.forEachIndexed { index, option ->
            Row(
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(4.dp),
            ) {
                CountryAutocompleteField(
                    value = option,
                    onChange = { updated ->
                        onOptionsChange(options.toMutableList().also { it[index] = updated.copy(id = option.id) })
                    },
                    modifier = Modifier.weight(1f).testTag("create_country_field_$index"),
                    placeholder = stringResource(Res.string.cc_country_placeholder, index + 1),
                )
                IconButton(
                    onClick = { onOptionsChange(options.toMutableList().also { it.removeAt(index) }) },
                    enabled = options.size > 2,
                ) {
                    Icon(
                        imageVector = Lucide.X,
                        contentDescription = stringResource(Res.string.cc_remove_option_a11y),
                        tint = if (options.size > 2)
                            MaterialTheme.colorScheme.onSurfaceVariant
                        else
                            MaterialTheme.colorScheme.onSurface.copy(alpha = 0.38f),
                    )
                }
            }
        }
        AppTextButton(onClick = {
            onOptionsChange(options + BetOption(id = "o${options.size}", label = ""))
        }) { Text(stringResource(Res.string.cc_add_country)) }
    }
}

private fun defaultCountryOptions(count: Int = 2): List<BetOption> =
    List(count) { index -> BetOption(id = "o$index", label = "") }

@Composable
private fun TopNStepper(
    value: Int,
    onValueChange: (Int) -> Unit,
    modifier: Modifier = Modifier,
) {
    Row(
        modifier = modifier.fillMaxWidth(),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(standardPaddingSmall),
    ) {
        Text(
            text = stringResource(Res.string.cc_top_n_ranked),
            style = MaterialTheme.typography.bodyLarge,
            modifier = Modifier.weight(1f),
        )
        IconButton(
            onClick = { if (value > 2) onValueChange(value - 1) },
            enabled = value > 2,
        ) {
            Icon(imageVector = Lucide.Minus, contentDescription = stringResource(Res.string.cc_decrease_top_n_a11y))
        }
        Text(
            text = "$value",
            style = MaterialTheme.typography.titleMedium,
            fontWeight = FontWeight.Bold,
        )
        IconButton(
            onClick = { if (value < 10) onValueChange(value + 1) },
            enabled = value < 10,
        ) {
            Icon(imageVector = Lucide.Plus, contentDescription = stringResource(Res.string.cc_increase_top_n_a11y))
        }
    }
}

private fun Bet.kindLabel(): String = when (this) {
    is Bet.SinglePick -> when (optionType) {
        OptionType.COUNTRY -> "Single pick · ${options.size} countries"
        OptionType.NONE -> "Single pick · ${options.size} options"
    }
    is Bet.BooleanProp -> "Yes / No"
    is Bet.Ranking -> when (optionType) {
        OptionType.COUNTRY -> "Ranking · top $topN of ${options.size} countries"
        OptionType.NONE -> "Ranking · top $topN of ${options.size}"
    }
    is Bet.Guess -> when (granularity) {
        GuessGranularity.TIME -> if (placement) "Time · placement" else if (closest) "Time · closest wins" else "Time · exact"
        GuessGranularity.DAY -> if (placement) "Day · placement" else if (closest) "Day · closest wins" else "Day · exact"
        GuessGranularity.NUMBER -> if (placement) "Number · placement" else if (closest) "Number · closest wins" else "Number · exact"
    }
    is Bet.MultiSelect -> when (optionType) {
        OptionType.COUNTRY -> "Multi-select · ${options.size} countries"
        OptionType.NONE -> "Multi-select · ${options.size} options"
    }
    is Bet.OverUnder -> "Over / Under · line $line"
}

// region Previews

@Preview
@Composable
private fun CreateChallenge_BetEditor_Preview() {
    ThemePreview {
        BetEditor(
            title = "Add bet",
            betType = BetType.SinglePick,
            onBetTypeChange = {},
            optionType = OptionType.NONE,
            onOptionTypeChange = {},
            question = "Top scorer",
            onQuestionChange = {},
            options = listOf("Mbappe", "Messi", "Haaland"),
            onOptionsChange = {},
            countryOptions = emptyList(),
            onCountryOptionsChange = {},
            topN = 3,
            onTopNChange = {},
            canSave = true,
            saveLabel = "Save bet",
            guessClosest = true,
            onGuessClosestChange = {},
            overUnderLine = "",
            onOverUnderLineChange = {},
            onSave = {},
            onCancel = {},
        )
    }
}

@Preview
@Composable
private fun CreateChallenge_BetCard_Preview() {
    ThemePreview {
        BetCard(bet = sampleBets[1], onRemove = {}, onClick = {})
    }
}

// endregion
