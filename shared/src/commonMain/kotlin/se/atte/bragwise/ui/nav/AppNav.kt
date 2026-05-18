package se.atte.bragwise.ui.nav

import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.FloatingActionButton
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.NavigationBar
import androidx.compose.material3.NavigationBarItem
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.runtime.snapshots.SnapshotStateList
import androidx.compose.runtime.toMutableStateList
import androidx.compose.ui.Modifier
import androidx.navigationevent.NavigationEventInfo
import androidx.navigationevent.compose.NavigationBackHandler
import androidx.navigationevent.compose.rememberNavigationEventState
import com.composables.icons.lucide.Lucide
import com.composables.icons.lucide.Plus
import com.composables.icons.lucide.Target
import com.composables.icons.lucide.User
import se.atte.bragwise.data.AuthRepository
import se.atte.bragwise.data.ChallengeRepository
import se.atte.bragwise.data.ProfileRepository
import se.atte.bragwise.data.SocialRepository
import se.atte.bragwise.platform.createPlatformShare
import se.atte.bragwise.ui.screens.auth.SignInScreen
import se.atte.bragwise.ui.screens.auth.SignInViewModel
import se.atte.bragwise.ui.screens.challenges.ChallengesScreen
import se.atte.bragwise.ui.screens.challenges.ChallengesViewModel
import se.atte.bragwise.ui.screens.create.CreateChallengeScreen
import se.atte.bragwise.ui.screens.create.CreateChallengeViewModel
import se.atte.bragwise.ui.screens.detail.ChallengeDetailScreen
import se.atte.bragwise.ui.screens.detail.ChallengeDetailViewModel
import se.atte.bragwise.ui.screens.friends.FriendsScreen
import se.atte.bragwise.ui.screens.friends.FriendsViewModel
import se.atte.bragwise.ui.screens.friends.LocalFriendEditorScreen
import se.atte.bragwise.ui.screens.leaderboard.LeaderboardScreen
import se.atte.bragwise.ui.screens.leaderboard.LeaderboardViewModel
import se.atte.bragwise.ui.screens.me.MeScreen
import se.atte.bragwise.ui.screens.me.MeViewModel
import se.atte.bragwise.ui.screens.onboarding.ReconcileFriendsScreen
import se.atte.bragwise.ui.screens.onboarding.ReconcileFriendsViewModel
import se.atte.bragwise.ui.screens.predict.PredictScreen
import se.atte.bragwise.ui.screens.predict.PredictViewModel

private enum class Tab { Challenges, Me }

private sealed interface Route {
    data class Tabs(val tab: Tab) : Route
    data class ChallengeDetail(val id: String) : Route
    data class Predict(val challengeId: String) : Route
    data class Leaderboard(val challengeId: String, val isPromoted: Boolean) : Route
    data object Create : Route
    data object SignIn : Route
    data object Friends : Route
    data class FriendEditor(val localId: String?) : Route
    data object ReconcileFriends : Route
}

/**
 * 2-tab bottom nav + global `+` FAB per plan §4. Hand-rolled nav state —
 * navigation-compose-multiplatform isn't wired yet. Replace when Phase 1
 * routing matures (deep links, predictive back, type-safe destinations).
 */
@Composable
fun AppNav(
    deps: AppDeps = remember { AppDeps.stub() },
) {
    val backStack: SnapshotStateList<Route> = remember { listOf<Route>(Route.Tabs(Tab.Challenges)).toMutableStateList() }
    val platformShare = remember { createPlatformShare() }
    val snackbarHostState = remember { SnackbarHostState() }

    fun push(next: Route) {
        if (backStack.last() != next) backStack.add(next)
    }
    fun replaceTop(next: Route) {
        backStack[backStack.lastIndex] = next
    }
    fun pop() {
        if (backStack.size > 1) backStack.removeAt(backStack.lastIndex)
    }

    val current: Route = backStack.last()
    val isAtTabs = current is Route.Tabs
    val currentTab = if (current is Route.Tabs) current.tab else null

    val navEventState = rememberNavigationEventState<NavigationEventInfo>(currentInfo = NavigationEventInfo.None)
    NavigationBackHandler(
        state = navEventState,
        isBackEnabled = backStack.size > 1,
        onBackCancelled = {},
        onBackCompleted = { pop() },
    )

    Scaffold(
        topBar = {
            if (!isAtTabs) {
                TextButton(onClick = { pop() }) {
                    Text("Back")
                }
            }
        },
        snackbarHost = { SnackbarHost(hostState = snackbarHostState) },
        bottomBar = {
            if (isAtTabs) {
                NavigationBar {
                    NavigationBarItem(
                        selected = currentTab == Tab.Challenges,
                        onClick = { push(Route.Tabs(Tab.Challenges)) },
                        icon = { Icon(imageVector = Lucide.Target, contentDescription = null) },
                        label = { Text("Challenges") },
                    )
                    NavigationBarItem(
                        selected = currentTab == Tab.Me,
                        onClick = { push(Route.Tabs(Tab.Me)) },
                        icon = { Icon(imageVector = Lucide.User, contentDescription = null) },
                        label = { Text("Me") },
                    )
                }
            }
        },
        floatingActionButton = {
            if (isAtTabs) {
                FloatingActionButton(
                    onClick = { push(Route.Create) },
                    shape = MaterialTheme.shapes.extraLarge,
                ) {
                    Icon(imageVector = Lucide.Plus, contentDescription = "Create challenge")
                }
            }
        },
    ) { padding ->
        androidx.compose.animation.Crossfade(
            targetState = current,
            modifier = Modifier.fillMaxSize().padding(padding),
            animationSpec = androidx.compose.animation.core.tween(durationMillis = 220),
            label = "route",
        ) { r ->
            when (r) {
                is Route.Tabs -> when (r.tab) {
                    Tab.Challenges -> ChallengesScreen(
                        viewModel = remember { ChallengesViewModel(deps.challenges, deps.social) },
                        onNavigateToChallenge = { push(Route.ChallengeDetail(it)) },
                        onNavigateToCreate = { push(Route.Create) },
                    )
                    Tab.Me -> MeScreen(
                        viewModel = remember { MeViewModel(deps.profile, deps.auth) },
                        onNavigateToSettings = {},
                        onNavigateToFriends = { push(Route.Friends) },
                        onNavigateToSignIn = { push(Route.SignIn) },
                    )
                }
                is Route.ChallengeDetail -> ChallengeDetailScreen(
                        viewModel = remember(r.id) {
                        ChallengeDetailViewModel(r.id, deps.challenges, deps.auth)
                    },
                    platformShare = platformShare,
                    onNavigateToBet = { push(Route.Predict(r.id)) },
                    onNavigateToLeaderboard = { push(Route.Leaderboard(challengeId = r.id, isPromoted = false)) },
                )
                is Route.Predict -> PredictScreen(
                    viewModel = remember(r.challengeId) {
                        PredictViewModel(r.challengeId, deps.challenges)
                    },
                    snackbarHostState = snackbarHostState,
                    onSubmitted = { pop() },
                )
                is Route.Leaderboard -> LeaderboardScreen(
                    viewModel = remember(r.challengeId, r.isPromoted) {
                        LeaderboardViewModel(r.challengeId, r.isPromoted, deps.challenges)
                    },
                )
                Route.Create -> CreateChallengeScreen(
                    viewModel = remember { CreateChallengeViewModel(deps.challenges) },
                    snackbarHostState = snackbarHostState,
                    onPublished = { id -> replaceTop(Route.ChallengeDetail(id)) },
                    onDraftSaved = { pop() },
                )
                Route.SignIn -> SignInScreen(
                    viewModel = remember { SignInViewModel(deps.auth) },
                    onSignedIn = {
                        // OB-06 follow-on: if any local-friend rows survive,
                        // route to ReconcileFriends; otherwise straight back to
                        // the Me tab (where the user originated the sign-in
                        // flow). ReconcileFriends.onDone also returns to Me.
                        replaceTop(
                            if (deps.social.localFriendSnapshot().isNotEmpty()) {
                                Route.ReconcileFriends
                            } else {
                                Route.Tabs(Tab.Me)
                            },
                        )
                    },
                    onGuest = { replaceTop(Route.Tabs(Tab.Challenges)) },
                )
                Route.Friends -> FriendsScreen(
                    viewModel = remember { FriendsViewModel(deps.social, deps.auth) },
                    onLocalAddOrEdit = { id -> push(Route.FriendEditor(id)) },
                    onOpenCloudProfile = { /* TODO LB-04 */ },
                    onOpenReconcile = { push(Route.ReconcileFriends) },
                )
                is Route.FriendEditor -> LocalFriendEditorScreen(
                    social = deps.social,
                    localId = r.localId,
                    onSaved = { pop() },
                    onCancel = { pop() },
                )
                Route.ReconcileFriends -> ReconcileFriendsScreen(
                    viewModel = remember { ReconcileFriendsViewModel(deps.social) },
                    onDone = { replaceTop(Route.Tabs(Tab.Me)) },
                )
            }
        }
    }
}

/**
 * Direct construction stand-in for Koin. Plan §5 wires Koin modules; deferred
 * here — see decision.md.
 */
class AppDeps(
    val auth: AuthRepository,
    val challenges: ChallengeRepository,
    val social: SocialRepository,
    val profile: ProfileRepository,
) {
    companion object {
        /**
         * Real Firebase wiring. Auth is fully implemented; the other three
         * repositories still hold `*RemoteDataSource` placeholders and will
         * be wired to real Firestore + callable-backed sources in Phase D.
         *
         * Construct once per process — App Check must already be initialised
         * before first call (Android: `MainActivity.onCreate`).
         */
        fun stub(): AppDeps {
            val authRepo = AuthRepository(
                remote = se.atte.bragwise.data.AuthRemoteDataSource(),
                local = se.atte.bragwise.data.AuthLocalDataSource(),
            )
            return AppDeps(
                auth = authRepo,
                challenges = ChallengeRepository(
                    remote = se.atte.bragwise.data.ChallengeRemoteDataSource(),
                    local = se.atte.bragwise.data.ChallengeLocalDataSource(),
                    auth = authRepo,
                ),
                social = SocialRepository(
                    remote = se.atte.bragwise.data.SocialRemoteDataSource(),
                    local = se.atte.bragwise.data.SocialLocalDataSource(),
                    auth = authRepo,
                ),
                profile = ProfileRepository(
                    remote = se.atte.bragwise.data.ProfileRemoteDataSource(),
                    local = se.atte.bragwise.data.ProfileLocalDataSource(),
                    auth = authRepo,
                ),
            )
        }
    }
}
