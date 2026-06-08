package se.atte.bragwise.ui.screens.create

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Checkbox
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
import se.atte.bragwise.data.OnboardingPrefs
import se.atte.bragwise.mvi.ObserveEffects
import se.atte.bragwise.ui.components.LoadingDialog
import se.atte.bragwise.ui.components.NameGateDialog
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
import se.atte.bragwise.domain.CloudFriend
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
import se.atte.bragwise.ui.components.SectionCard
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
            is CreateChallengeViewModel.Effect.Snackbar -> snackbarHostState.showSnackbar(effect.text)
        }
    }

    if (state.submitting) {
        LoadingDialog(message = "Saving challenge…")
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
            }
            is Bet.SinglePick -> {
                betType = BetType.SinglePick
                optionType = bet.optionType
                options = bet.options.map { it.label }
                countryOptions = bet.options
                topN = 3
            }
            is Bet.Ranking -> {
                betType = BetType.Ranking
                optionType = bet.optionType
                options = bet.options.map { it.label }
                countryOptions = bet.options
                topN = bet.topN
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
    val duplicateOptionError = if (hasDuplicateOptions) "Remove duplicate options before saving" else null

    if (showFriendPicker) {
        FriendPickerDialog(
            friends = friends,
            initial = state.invitedUids,
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
                        onValueChange = { viewModel.onIntent(CreateChallengeViewModel.Intent.SetTitle(it)) },
                        label = { Text("Title") },
                        modifier = Modifier.fillMaxWidth().testTag("create_title"),
                        singleLine = true,
                    )
                }
            }

            item {
                SectionCard(title = "Deadline") {
                    DeadlinePickerField(
                        locksAt = state.locksAt,
                        onLocksAtChange = { viewModel.onIntent(CreateChallengeViewModel.Intent.SetLocksAt(it)) },
                    )
                }
            }

            item {
                SectionCard(title = "Who can join") {
                    Row(horizontalArrangement = Arrangement.spacedBy(standardPaddingSmall)) {
                        AppFilterChip(
                            selected = state.visibility == Visibility.FRIENDS,
                            onClick = { viewModel.onIntent(CreateChallengeViewModel.Intent.SetVisibility(Visibility.FRIENDS)) },
                            label = { Text("Friends") },
                        )
                        AppFilterChip(
                            selected = state.visibility == Visibility.INVITE_ONLY,
                            onClick = {
                                viewModel.onIntent(CreateChallengeViewModel.Intent.SetVisibility(Visibility.INVITE_ONLY))
                                showFriendPicker = true
                            },
                            label = { Text("Invite only") },
                        )
                    }
                    Text(
                        text = when (state.visibility) {
                            Visibility.FRIENDS -> "Any of your friends can join with the link."
                            Visibility.INVITE_ONLY -> "Only people you explicitly invite can join."
                            else -> "" // PROMOTED is server-only; unreachable in the creator form
                        },
                        style = MaterialTheme.typography.bodyLarge,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        modifier = Modifier.padding(top = 12.dp),
                    )
                    if (state.visibility == Visibility.INVITE_ONLY) {
                        val inviteLabel = if (state.invitedUids.isNotEmpty()) {
                            "${state.invitedUids.size} friends invited — Edit"
                        } else {
                            "Pick friends"
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
                                text = "Bets visible to others",
                                style = MaterialTheme.typography.bodyLarge,
                            )
                            Text(
                                text = "Participants can see each other's predictions",
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant,
                            )
                        }
                        Switch(
                            checked = state.betsVisible,
                            onCheckedChange = { viewModel.onIntent(CreateChallengeViewModel.Intent.SetBetsVisible(it)) },
                        )
                    }
                }
            }

            items(items = state.bets, key = { it.id }) { bet ->
                if (editingBetId == bet.id) {
                    BetEditor(
                        title = "Edit bet",
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
                        },
                        saveLabel = "Save bet",
                        duplicateOptionError = duplicateOptionError,
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

            item {
                if (editingBetId == "") {
                    BetEditor(
                        title = "Add bet",
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
                        },
                        saveLabel = "Save bet",
                        duplicateOptionError = duplicateOptionError,
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
                            }
                            editingBetId = null
                            resetEditor()
                        },
                    )
                } else if (!isEditing) {
                    AppOutlinedButton(
                        modifier = Modifier.fillMaxWidth().testTag("create_add_bet"),
                        onClick = { editingBetId = "" },
                    ) { Text("+ Add bet") }
                }
            }
        }

        BottomActionBar {
            AppOutlinedButton(
                modifier = Modifier.weight(1f),
                onClick = { viewModel.onIntent(CreateChallengeViewModel.Intent.SaveDraft) },
                enabled = !state.submitting,
            ) { Text("Save draft") }
            AppButton(
                modifier = Modifier.weight(1f).testTag("create_publish"),
                onClick = { viewModel.onIntent(CreateChallengeViewModel.Intent.Publish) },
                enabled = !state.submitting && state.bets.isNotEmpty() && state.title.isNotBlank(),
            ) { Text("Publish") }
        }
    }
}

private enum class BetType { YesNo, SinglePick, Ranking }

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
                    contentDescription = "Cancel",
                    tint = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
        }
        Row(
            horizontalArrangement = Arrangement.spacedBy(standardPaddingSmall),
            modifier = Modifier.padding(top = standardPaddingSmall),
        ) {
            AppFilterChip(
                selected = betType == BetType.YesNo,
                onClick = { onBetTypeChange(BetType.YesNo) },
                label = { Text("Yes / No") },
            )
            AppFilterChip(
                selected = betType == BetType.SinglePick,
                onClick = { onBetTypeChange(BetType.SinglePick) },
                label = { Text("Single pick") },
            )
            AppFilterChip(
                selected = betType == BetType.Ranking,
                onClick = { onBetTypeChange(BetType.Ranking) },
                label = { Text("Ranking") },
                modifier = Modifier.testTag("create_bet_ranking"),
            )
        }
        if (betType == BetType.SinglePick || betType == BetType.Ranking) {
            Row(
                horizontalArrangement = Arrangement.spacedBy(standardPaddingSmall),
                modifier = Modifier.padding(top = standardPaddingSmall),
            ) {
                AppFilterChip(
                    selected = optionType == OptionType.NONE,
                    onClick = { onOptionTypeChange(OptionType.NONE) },
                    label = { Text("Custom options") },
                )
                AppFilterChip(
                    selected = optionType == OptionType.COUNTRY,
                    onClick = {
                        onOptionTypeChange(OptionType.COUNTRY)
                        if (countryOptions.size < 2) onCountryOptionsChange(defaultCountryOptions())
                    },
                    label = { Text("Countries") },
                    modifier = Modifier.testTag("create_bet_countries"),
                )
            }
        }
        OutlinedTextField(
            value = question,
            onValueChange = onQuestionChange,
            label = { Text("Question") },
            modifier = Modifier.fillMaxWidth().padding(top = 12.dp).testTag("create_bet_question"),
            singleLine = true,
        )
        if (betType == BetType.SinglePick || betType == BetType.Ranking) {
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
        is Bet.BooleanProp -> emptyList()
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
                    contentDescription = "Remove bet",
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
                    Text(if (expanded) "Show less" else "… $remaining more · Tap to expand")
                }
            }
        }
    }
}

@Composable
private fun FriendPickerDialog(
    friends: List<CloudFriend>,
    initial: Set<String>,
    onDismiss: () -> Unit,
    onConfirm: (Set<String>) -> Unit,
) {
    var selected by remember(initial) { mutableStateOf(initial) }

    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("Invite friends") },
        text = {
            if (friends.isEmpty()) {
                Text("No friends yet — add some first.")
            } else {
                LazyColumn {
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
            AppTextButton(onClick = { onConfirm(selected) }) { Text("Done") }
        },
        dismissButton = {
            AppTextButton(onClick = onDismiss) { Text("Cancel") }
        },
    )
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
                        onOptionsChange(options.toMutableList().also { it[index] = updated })
                    },
                    label = { Text("Option ${index + 1}") },
                    modifier = Modifier.weight(1f),
                    singleLine = true,
                )
                IconButton(
                    onClick = { onOptionsChange(options.toMutableList().also { it.removeAt(index) }) },
                    enabled = options.size > 2,
                ) {
                    Icon(
                        imageVector = Lucide.X,
                        contentDescription = "Remove option",
                        tint = if (options.size > 2)
                            MaterialTheme.colorScheme.onSurfaceVariant
                        else
                            MaterialTheme.colorScheme.onSurface.copy(alpha = 0.38f),
                    )
                }
            }
        }
        AppTextButton(onClick = { onOptionsChange(options + "") }) { Text("+ Add option") }
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
                    placeholder = "Country ${index + 1}",
                )
                IconButton(
                    onClick = { onOptionsChange(options.toMutableList().also { it.removeAt(index) }) },
                    enabled = options.size > 2,
                ) {
                    Icon(
                        imageVector = Lucide.X,
                        contentDescription = "Remove option",
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
        }) { Text("+ Add country") }
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
            text = "Top N ranked",
            style = MaterialTheme.typography.bodyLarge,
            modifier = Modifier.weight(1f),
        )
        IconButton(
            onClick = { if (value > 2) onValueChange(value - 1) },
            enabled = value > 2,
        ) {
            Icon(imageVector = Lucide.Minus, contentDescription = "Decrease top N")
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
            Icon(imageVector = Lucide.Plus, contentDescription = "Increase top N")
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
