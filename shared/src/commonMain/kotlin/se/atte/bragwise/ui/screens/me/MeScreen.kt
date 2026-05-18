package se.atte.bragwise.ui.screens.me

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import se.atte.bragwise.domain.Player
import se.atte.bragwise.ui.standardPadding
import se.atte.bragwise.ui.standardPaddingLarge
import se.atte.bragwise.mvi.UiState
import se.atte.bragwise.theme.ThemePreview
import se.atte.bragwise.ui.components.AppButton
import se.atte.bragwise.ui.components.AppTextButton
import se.atte.bragwise.ui.components.ListGroup
import se.atte.bragwise.ui.components.ListGroupDivider
import se.atte.bragwise.ui.components.ListRow
import se.atte.bragwise.ui.components.SectionCard
import se.atte.bragwise.ui.components.SectionGap
import kotlin.time.Instant

@Composable
fun MeScreen(
    viewModel: MeViewModel,
    onNavigateToSettings: () -> Unit,
    onNavigateToFriends: () -> Unit,
    onNavigateToSignIn: () -> Unit,
) {
    val state by viewModel.state.collectAsStateWithLifecycle()

    LaunchedEffect(viewModel) {
        viewModel.effects.collect { effect ->
            when (effect) {
                MeViewModel.Effect.GoToSettings -> onNavigateToSettings()
                MeViewModel.Effect.GoToFriends -> onNavigateToFriends()
            }
        }
    }

    when (val ui = state.ui) {
        UiState.Loading -> Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
            CircularProgressIndicator()
        }
        is UiState.Failed -> Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
            Text("Failed: ${ui.cause}")
        }
        is UiState.Empty,
        is UiState.Ready<*> -> {
            val player = (ui as? UiState.Ready)?.data
            MeContent(
                player = player,
                onFriends = { viewModel.onIntent(MeViewModel.Intent.OpenFriends) },
                onSettings = { viewModel.onIntent(MeViewModel.Intent.OpenSettings) },
                onSignOut = { viewModel.onIntent(MeViewModel.Intent.SignOut) },
                onSignIn = onNavigateToSignIn,
            )
        }
    }
}

@Composable
private fun MeContent(
    player: Player?,
    onFriends: () -> Unit,
    onSettings: () -> Unit,
    onSignOut: () -> Unit,
    onSignIn: () -> Unit,
) {
    Column(
        modifier = Modifier
            .fillMaxSize()
            .verticalScroll(rememberScrollState())
            .padding(standardPadding),
    ) {
        SectionCard {
            if (player == null) {
                Text(text = "Guest", style = MaterialTheme.typography.headlineLarge)
                Text(
                    text = "Sign up to save your run, join friends, and appear on leaderboards.",
                    style = MaterialTheme.typography.bodyLarge,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier.padding(top = 4.dp),
                )
                AppButton(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(top = standardPadding),
                    onClick = onSignIn,
                ) { Text("Sign in or sign up") }
            } else {
                Text(text = player.displayName, style = MaterialTheme.typography.headlineLarge)
                Text(
                    text = "@${player.handle}",
                    style = MaterialTheme.typography.bodyLarge,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier.padding(top = 4.dp),
                )
            }
        }

        SectionGap()

        ListGroup {
            ListRow(
                title = "Friends",
                leading = "👥",
                onClick = onFriends,
            )
            ListGroupDivider()
            ListRow(
                title = "Settings",
                leading = "⚙",
                onClick = onSettings,
            )
        }

        if (player != null) {
            SectionGap(standardPaddingLarge)
            AppTextButton(
                modifier = Modifier.align(Alignment.CenterHorizontally),
                onClick = onSignOut,
                color = MaterialTheme.colorScheme.error,
            ) { Text("Sign out") }
        }
    }
}

// region Previews

@Preview
@Composable
private fun MeContent_Guest_Preview() {
    ThemePreview {
        MeContent(player = null, onFriends = {}, onSettings = {}, onSignOut = {}, onSignIn = {})
    }
}

@Preview
@Composable
private fun MeContent_SignedIn_Preview() {
    ThemePreview {
        MeContent(
            player = Player(
                uid = "u1",
                handle = "atte",
                displayName = "Atte Lindqvist",
                avatarSeed = "atte",
                createdAt = Instant.fromEpochSeconds(0),
            ),
            onFriends = {},
            onSettings = {},
            onSignOut = {},
            onSignIn = {},
        )
    }
}

// endregion
