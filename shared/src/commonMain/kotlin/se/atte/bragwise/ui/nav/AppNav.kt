package se.atte.bragwise.ui.nav

import androidx.compose.animation.Crossfade
import androidx.compose.animation.core.tween
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.material3.FloatingActionButton
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.NavigationBar
import androidx.compose.material3.NavigationBarItem
import androidx.compose.material3.NavigationBarItemDefaults
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.mutableStateListOf
import androidx.compose.runtime.remember
import se.atte.bragwise.ui.LocalSnackbarHost
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.platform.LocalDensity
import androidx.navigationevent.NavigationEvent
import androidx.navigationevent.NavigationEventInfo
import androidx.navigationevent.NavigationEventTransitionState
import androidx.navigationevent.compose.NavigationBackHandler
import androidx.navigationevent.compose.rememberNavigationEventState
import bragwise.shared.generated.resources.Res
import bragwise.shared.generated.resources.nav_back
import bragwise.shared.generated.resources.nav_tab_challenges
import bragwise.shared.generated.resources.nav_tab_me
import com.composables.icons.lucide.ArrowLeft
import com.composables.icons.lucide.Lucide
import com.composables.icons.lucide.Plus
import com.composables.icons.lucide.Target
import com.composables.icons.lucide.User
import org.jetbrains.compose.resources.stringResource
import org.koin.compose.koinInject
import org.koin.compose.viewmodel.koinViewModel
import org.koin.core.parameter.parametersOf
import se.atte.bragwise.data.OnboardingPrefs
import se.atte.bragwise.data.SocialRepository
import se.atte.bragwise.platform.PlatformShare
import se.atte.bragwise.push.PushNotifications
import se.atte.bragwise.ui.screens.auth.SignInScreen
import se.atte.bragwise.ui.screens.auth.SignInViewModel
import se.atte.bragwise.ui.screens.bets.BetListScreen
import se.atte.bragwise.ui.screens.bets.BetListViewModel
import se.atte.bragwise.ui.screens.bets.ChallengeSummaryScreen
import se.atte.bragwise.ui.screens.invite.InviteFriendsScreen
import se.atte.bragwise.ui.screens.invite.InviteFriendsViewModel
import se.atte.bragwise.ui.screens.manage.ManageChallengeScreen
import se.atte.bragwise.ui.screens.manage.ManageChallengeViewModel
import se.atte.bragwise.ui.screens.postresults.PostResultsScreen
import se.atte.bragwise.ui.screens.postresults.PostResultsViewModel
import se.atte.bragwise.ui.screens.challenges.ChallengesScreen
import se.atte.bragwise.ui.screens.challenges.ChallengesViewModel
import se.atte.bragwise.ui.screens.create.CreateChallengeScreen
import se.atte.bragwise.ui.screens.create.CreateChallengeViewModel
import se.atte.bragwise.ui.screens.detail.ChallengeDetailScreen
import se.atte.bragwise.ui.screens.detail.ChallengeDetailViewModel
import se.atte.bragwise.ui.screens.friends.FriendRequestsScreen
import se.atte.bragwise.ui.screens.friends.FriendRequestsViewModel
import se.atte.bragwise.ui.screens.friends.FriendsScreen
import se.atte.bragwise.ui.screens.friends.FriendsViewModel
import se.atte.bragwise.ui.screens.friends.LocalFriendEditorScreen
import se.atte.bragwise.ui.screens.about.AboutScreen
import se.atte.bragwise.ui.screens.profile.EditProfileScreen
import se.atte.bragwise.ui.screens.profile.EditProfileViewModel
import se.atte.bragwise.ui.screens.profile.PlayerProfileScreen
import se.atte.bragwise.ui.screens.profile.PlayerProfileViewModel
import se.atte.bragwise.ui.screens.leaderboard.LeaderboardScreen
import se.atte.bragwise.ui.screens.leaderboard.LeaderboardViewModel
import se.atte.bragwise.ui.screens.me.MeScreen
import se.atte.bragwise.ui.screens.me.MeViewModel
import se.atte.bragwise.ui.screens.onboarding.MigrationDialog
import se.atte.bragwise.ui.screens.onboarding.ReconcileFriendsScreen
import se.atte.bragwise.ui.screens.onboarding.ReconcileFriendsViewModel
import se.atte.bragwise.ui.screens.onboarding.WelcomeScreen
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
    data object Welcome : Route
    data object Migration : Route
    data class BetList(val challengeId: String) : Route
    data class ChallengeSummary(val challengeId: String) : Route
    data class Manage(val challengeId: String) : Route
    data class Invite(val challengeId: String) : Route
    data class PostResults(val challengeId: String) : Route
    data object FriendRequests : Route
    data class PlayerProfile(val uid: String) : Route
    data object EditProfile : Route
    data object About : Route
}

/** Maps a trusted deep-link URL to a [Route], or null if unrecognised. */
private fun parseDeepLink(url: String): Route? {
    val slash = url.indexOf('/', url.indexOf("://") + 3)
    if (slash < 0) return null
    val path = url.substring(slash)
    val challengeMatch = Regex("^/c/([a-zA-Z0-9_-]+)$").find(path)
    if (challengeMatch != null) return Route.ChallengeDetail(challengeMatch.groupValues[1])
    return null
}

/**
 * 2-tab bottom nav + global `+` FAB per plan §4. Hand-rolled nav state —
 * navigation-compose-multiplatform isn't wired yet. Replace when Phase 1
 * routing matures (deep links, predictive back, type-safe destinations).
 */
@Composable
fun AppNav() {
    val onboardingPrefs: OnboardingPrefs = koinInject()
    val startRoute = if (onboardingPrefs.hasSeenWelcome) Route.Tabs(Tab.Challenges) else Route.Welcome
    val backStack = remember { mutableStateListOf<Route>(startRoute) }
    val platformShare: PlatformShare = koinInject()
    val social: SocialRepository = koinInject()
    val pushNotifications: PushNotifications = koinInject()
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

    LaunchedEffect(Unit) {
        pushNotifications.incomingDeepLinks.collect { url ->
            parseDeepLink(url)?.let { push(it) }
        }
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
                IconButton(
                    onClick = { pop() },
                    modifier = Modifier.statusBarsPadding(),
                ) {
                    Icon(
                        imageVector = Lucide.ArrowLeft,
                        contentDescription = stringResource(Res.string.nav_back),
                    )
                }
            }
        },
        snackbarHost = { SnackbarHost(hostState = snackbarHostState) },
        bottomBar = {
            if (isAtTabs) {
                val navItemColors = NavigationBarItemDefaults.colors(
                    indicatorColor = MaterialTheme.colorScheme.primaryContainer,
                    selectedIconColor = MaterialTheme.colorScheme.onPrimaryContainer,
                    selectedTextColor = MaterialTheme.colorScheme.primary,
                    unselectedIconColor = MaterialTheme.colorScheme.onSurfaceVariant,
                    unselectedTextColor = MaterialTheme.colorScheme.onSurfaceVariant,
                )
                NavigationBar {
                    NavigationBarItem(
                        selected = currentTab == Tab.Challenges,
                        onClick = { replaceTop(Route.Tabs(Tab.Challenges)) },
                        icon = { Icon(imageVector = Lucide.Target, contentDescription = null) },
                        label = { Text(stringResource(Res.string.nav_tab_challenges)) },
                        colors = navItemColors,
                    )
                    NavigationBarItem(
                        selected = currentTab == Tab.Me,
                        onClick = { replaceTop(Route.Tabs(Tab.Me)) },
                        icon = { Icon(imageVector = Lucide.User, contentDescription = null) },
                        label = { Text(stringResource(Res.string.nav_tab_me)) },
                        colors = navItemColors,
                    )
                }
            }
        },
        floatingActionButton = {
            if (currentTab == Tab.Challenges) {
                FloatingActionButton(
                    onClick = { push(Route.Create) },
                    shape = MaterialTheme.shapes.extraLarge,
                ) {
                    Icon(imageVector = Lucide.Plus, contentDescription = "Create challenge")
                }
            }
        },
        contentWindowInsets = WindowInsets(0),
    ) { padding ->
        CompositionLocalProvider(LocalSnackbarHost provides snackbarHostState) {
            val transition = navEventState.transitionState
            BoxWithConstraints(
                modifier = Modifier
                    .fillMaxSize()
                    .background(MaterialTheme.colorScheme.background)
                    .padding(padding),
            ) {
                val widthPx = with(LocalDensity.current) { maxWidth.toPx() }
                if (
                    transition is NavigationEventTransitionState.InProgress &&
                    transition.direction == NavigationEventTransitionState.TRANSITIONING_BACK &&
                    backStack.size > 1
                ) {
                    val event: NavigationEvent = transition.latestEvent
                    val progress = event.progress.coerceIn(0f, 1f)
                    val previous = backStack[backStack.lastIndex - 1]
                    val edgeSign = if (event.swipeEdge == NavigationEvent.EDGE_RIGHT) -1f else 1f
                    Box(
                        modifier = Modifier
                            .fillMaxSize()
                            .graphicsLayer { translationX = -edgeSign * (1f - progress) * widthPx * 0.25f }
                            .background(MaterialTheme.colorScheme.background),
                    ) {
                        RouteContent(
                            route = previous,
                            push = ::push,
                            replaceTop = ::replaceTop,
                            pop = ::pop,
                            platformShare = platformShare,
                            social = social,
                            snackbarHostState = snackbarHostState,
                            onboardingPrefs = onboardingPrefs,
                        )
                    }
                    Box(
                        modifier = Modifier
                            .fillMaxSize()
                            .background(Color.Black.copy(alpha = (1f - progress) * 0.15f)),
                    )
                    Box(
                        modifier = Modifier
                            .fillMaxSize()
                            .graphicsLayer { translationX = edgeSign * progress * widthPx }
                            .background(MaterialTheme.colorScheme.background),
                    ) {
                        RouteContent(
                            route = current,
                            push = ::push,
                            replaceTop = ::replaceTop,
                            pop = ::pop,
                            platformShare = platformShare,
                            social = social,
                            snackbarHostState = snackbarHostState,
                            onboardingPrefs = onboardingPrefs,
                        )
                    }
                } else {
                    Crossfade(
                        targetState = current,
                        modifier = Modifier.fillMaxSize(),
                        animationSpec = tween(durationMillis = 220),
                        label = "route",
                    ) { r ->
                        RouteContent(
                            route = r,
                            push = ::push,
                            replaceTop = ::replaceTop,
                            pop = ::pop,
                            platformShare = platformShare,
                            social = social,
                            snackbarHostState = snackbarHostState,
                            onboardingPrefs = onboardingPrefs,
                        )
                    }
                }
            }
        }
    }
}

@Composable
private fun RouteContent(
    route: Route,
    push: (Route) -> Unit,
    replaceTop: (Route) -> Unit,
    pop: () -> Unit,
    platformShare: PlatformShare,
    social: SocialRepository,
    snackbarHostState: SnackbarHostState,
    onboardingPrefs: OnboardingPrefs,
) {
    when (route) {
        is Route.Tabs -> when (route.tab) {
            Tab.Challenges -> ChallengesScreen(
                viewModel = koinViewModel<ChallengesViewModel>(),
                onNavigateToChallenge = { push(Route.ChallengeDetail(it)) },
                onNavigateToCreate = { push(Route.Create) },
            )
            Tab.Me -> MeScreen(
                viewModel = koinViewModel<MeViewModel>(),
                snackbarHostState = snackbarHostState,
                onNavigateToFriends = { push(Route.Friends) },
                onNavigateToSignIn = { push(Route.SignIn) },
                onNavigateToEditProfile = { push(Route.EditProfile) },
                onNavigateToAbout = { push(Route.About) },
                onDeleted = { replaceTop(Route.Welcome) },
            )
        }
        is Route.ChallengeDetail -> ChallengeDetailScreen(
            viewModel = koinViewModel<ChallengeDetailViewModel>(key = route.id) { parametersOf(route.id) },
            platformShare = platformShare,
            snackbarHostState = snackbarHostState,
            onNavigateToBet = { push(Route.Predict(route.id)) },
            onNavigateToLeaderboard = { push(Route.Leaderboard(challengeId = route.id, isPromoted = false)) },
            onNavigateToBetList = { push(Route.BetList(route.id)) },
            onNavigateToSummary = { push(Route.ChallengeSummary(route.id)) },
            onNavigateToManage = { push(Route.Manage(route.id)) },
        )
        is Route.Predict -> PredictScreen(
            viewModel = koinViewModel<PredictViewModel>(key = route.challengeId) { parametersOf(route.challengeId) },
            snackbarHostState = snackbarHostState,
            onSubmitted = { replaceTop(Route.ChallengeSummary(route.challengeId)) },
        )
        is Route.Leaderboard -> LeaderboardScreen(
            viewModel = koinViewModel<LeaderboardViewModel>(key = "${route.challengeId}:${route.isPromoted}") {
                parametersOf(route.challengeId, route.isPromoted)
            },
            onOpenProfile = { uid -> push(Route.PlayerProfile(uid)) },
        )
        Route.Create -> CreateChallengeScreen(
            viewModel = koinViewModel<CreateChallengeViewModel>(),
            snackbarHostState = snackbarHostState,
            onPublished = { id -> replaceTop(Route.ChallengeDetail(id)) },
            onDraftSaved = { pop() },
        )
        Route.SignIn -> SignInScreen(
            viewModel = koinViewModel<SignInViewModel>(),
            snackbarHostState = snackbarHostState,
            onSignedIn = {
                // Sign-in success → run guest-data migration first
                // (OB-05); on completion the dialog routes onward
                // to ReconcileFriends (OB-06) or Me as appropriate.
                replaceTop(Route.Migration)
            },
            onGuest = { replaceTop(Route.Tabs(Tab.Challenges)) },
        )
        Route.Friends -> FriendsScreen(
            viewModel = koinViewModel<FriendsViewModel>(),
            snackbarHostState = snackbarHostState,
            onLocalAddOrEdit = { id -> push(Route.FriendEditor(id)) },
            onOpenCloudProfile = { uid -> push(Route.PlayerProfile(uid)) },
            onOpenReconcile = { push(Route.ReconcileFriends) },
            onOpenFriendRequests = { push(Route.FriendRequests) },
        )
        Route.FriendRequests -> FriendRequestsScreen(
            viewModel = koinViewModel<FriendRequestsViewModel>(),
            snackbarHostState = snackbarHostState,
        )
        is Route.PlayerProfile -> PlayerProfileScreen(
            viewModel = koinViewModel<PlayerProfileViewModel>(key = "profile:${route.uid}") {
                parametersOf(route.uid)
            },
        )
        Route.EditProfile -> EditProfileScreen(
            viewModel = koinViewModel<EditProfileViewModel>(),
            snackbarHostState = snackbarHostState,
            onSaved = { pop() },
        )
        Route.About -> AboutScreen()
        is Route.FriendEditor -> LocalFriendEditorScreen(
            social = social,
            localId = route.localId,
            onSaved = { pop() },
            onCancel = { pop() },
        )
        Route.ReconcileFriends -> ReconcileFriendsScreen(
            viewModel = koinViewModel<ReconcileFriendsViewModel>(),
            snackbarHostState = snackbarHostState,
            onDone = { replaceTop(Route.Tabs(Tab.Me)) },
        )
        is Route.Manage -> ManageChallengeScreen(
            viewModel = koinViewModel<ManageChallengeViewModel>(key = "manage:${route.challengeId}") {
                parametersOf(route.challengeId)
            },
            onInvite = { id -> push(Route.Invite(id)) },
            onPostResults = { id -> push(Route.PostResults(id)) },
        )
        is Route.Invite -> InviteFriendsScreen(
            viewModel = koinViewModel<InviteFriendsViewModel>(key = "invite:${route.challengeId}") {
                parametersOf(route.challengeId)
            },
            snackbarHostState = snackbarHostState,
            onSent = { pop() },
        )
        is Route.PostResults -> PostResultsScreen(
            viewModel = koinViewModel<PostResultsViewModel>(key = "postresults:${route.challengeId}") {
                parametersOf(route.challengeId)
            },
            snackbarHostState = snackbarHostState,
            onPosted = { replaceTop(Route.ChallengeDetail(route.challengeId)) },
        )
        is Route.BetList -> BetListScreen(
            viewModel = koinViewModel<BetListViewModel>(key = "betlist:${route.challengeId}") {
                parametersOf(route.challengeId)
            },
            onOpenPredict = { id -> push(Route.Predict(id)) },
        )
        is Route.ChallengeSummary -> ChallengeSummaryScreen(
            viewModel = koinViewModel<BetListViewModel>(key = "summary:${route.challengeId}") {
                parametersOf(route.challengeId)
            },
            onEdit = { id -> push(Route.Predict(id)) },
            onLeaderboard = { id ->
                push(Route.Leaderboard(challengeId = id, isPromoted = false))
            },
        )
        Route.Welcome -> WelcomeScreen(
            onSignIn = {
                onboardingPrefs.hasSeenWelcome = true
                replaceTop(Route.SignIn)
            },
            onContinueAsGuest = {
                onboardingPrefs.hasSeenWelcome = true
                replaceTop(Route.Tabs(Tab.Challenges))
            },
        )
        Route.Migration -> MigrationDialog(
            onComplete = {
                replaceTop(
                    if (social.localFriendSnapshot().isNotEmpty()) {
                        Route.ReconcileFriends
                    } else {
                        Route.Tabs(Tab.Me)
                    },
                )
            },
            onSkip = { replaceTop(Route.Tabs(Tab.Me)) },
        )
    }
}
