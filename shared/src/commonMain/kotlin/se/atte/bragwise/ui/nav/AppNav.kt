package se.atte.bragwise.ui.nav

import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.slideInHorizontally
import androidx.compose.animation.slideOutHorizontally
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.shape.CircleShape
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
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.unit.dp
import androidx.navigation.NavController
import androidx.navigation.NavDestination.Companion.hasRoute
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.compose.currentBackStackEntryAsState
import androidx.navigation.compose.rememberNavController
import androidx.navigation.toRoute
import bragwise.shared.generated.resources.Res
import bragwise.shared.generated.resources.nav_back
import bragwise.shared.generated.resources.nav_tab_challenges
import bragwise.shared.generated.resources.nav_tab_me
import bragwise.shared.generated.resources.nav_create_challenge_a11y
import bragwise.shared.generated.resources.nav_tab_results
import com.composables.icons.lucide.ArrowLeft
import com.composables.icons.lucide.Lucide
import com.composables.icons.lucide.Plus
import com.composables.icons.lucide.Target
import com.composables.icons.lucide.Trophy
import com.composables.icons.lucide.User
import kotlinx.coroutines.launch
import kotlinx.serialization.Serializable
import org.jetbrains.compose.resources.stringResource
import org.koin.compose.koinInject
import org.koin.compose.viewmodel.koinViewModel
import org.koin.core.parameter.parametersOf
import se.atte.bragwise.data.AuthRepository
import se.atte.bragwise.data.AuthState
import se.atte.bragwise.data.ChallengeRepository
import se.atte.bragwise.data.LocalPredictionStore
import se.atte.bragwise.data.OnboardingPrefs
import se.atte.bragwise.data.ResultsSeenStore
import se.atte.bragwise.data.isFullyAuthed
import se.atte.bragwise.mvi.AppError
import se.atte.bragwise.mvi.ErrorReporter
import se.atte.bragwise.platform.PlatformShare
import se.atte.bragwise.push.PushNotifications
import se.atte.bragwise.ui.LocalSnackbarHost
import se.atte.bragwise.ui.components.ErrorDialog
import se.atte.bragwise.ui.enableTestTagsAsResourceId
import se.atte.bragwise.ui.screens.about.AboutScreen
import se.atte.bragwise.ui.screens.auth.SignInScreen
import se.atte.bragwise.ui.screens.auth.SignInViewModel
import se.atte.bragwise.ui.screens.bets.BetListViewModel
import se.atte.bragwise.ui.screens.bets.ChallengeSummaryScreen
import se.atte.bragwise.ui.screens.challenges.ChallengesScreen
import se.atte.bragwise.ui.screens.challenges.ChallengesViewModel
import se.atte.bragwise.ui.screens.create.CreateChallengeScreen
import se.atte.bragwise.ui.screens.create.CreateChallengeViewModel
import se.atte.bragwise.ui.screens.detail.ChallengeDetailScreen
import se.atte.bragwise.ui.screens.detail.ChallengeDetailViewModel
import se.atte.bragwise.ui.screens.detail.ParticipantBetsScreen
import se.atte.bragwise.ui.screens.detail.ParticipantBetsViewModel
import se.atte.bragwise.ui.screens.friends.FriendRequestsScreen
import se.atte.bragwise.ui.screens.friends.FriendRequestsViewModel
import se.atte.bragwise.ui.screens.friends.FriendsScreen
import se.atte.bragwise.ui.screens.friends.FriendsViewModel
import se.atte.bragwise.ui.screens.invite.InviteFriendsScreen
import se.atte.bragwise.ui.screens.invite.InviteFriendsViewModel
import se.atte.bragwise.ui.screens.me.MeScreen
import se.atte.bragwise.ui.screens.me.MeViewModel
import se.atte.bragwise.ui.screens.onboarding.WelcomeScreen
import se.atte.bragwise.ui.screens.postresults.PostResultsScreen
import se.atte.bragwise.ui.screens.postresults.PostResultsViewModel
import se.atte.bragwise.ui.screens.predict.PredictScreen
import se.atte.bragwise.ui.screens.predict.PredictViewModel
import se.atte.bragwise.ui.screens.profile.EditProfileScreen
import se.atte.bragwise.ui.screens.profile.EditProfileViewModel
import se.atte.bragwise.ui.screens.profile.PlayerProfileScreen
import se.atte.bragwise.ui.screens.profile.PlayerProfileViewModel
import se.atte.bragwise.ui.screens.results.ResultsRevealScreen
import se.atte.bragwise.ui.screens.results.ResultsRevealViewModel
import se.atte.bragwise.ui.screens.results.ResultsScreen
import se.atte.bragwise.ui.screens.results.ResultsViewModel
import se.atte.bragwise.verify.VerifyAutomation

// ---------- Type-safe route definitions ----------

@Serializable data object RouteChallenges
@Serializable data object RouteResults
@Serializable data object RouteMe
@Serializable data class RouteChallengeDetail(val id: String)
@Serializable data class RoutePredict(val challengeId: String)
@Serializable data class RouteCreate(val draftId: String? = null)
@Serializable data object RouteSignIn
@Serializable data object RouteFriends
@Serializable data object RouteWelcome
@Serializable data class RouteChallengeSummary(val challengeId: String)
@Serializable data class RouteInvite(val challengeId: String)
@Serializable data class RouteParticipantBets(val challengeId: String, val uid: String)
@Serializable data class RoutePostResults(val challengeId: String)
@Serializable data object RouteFriendRequests
@Serializable data class RoutePlayerProfile(val uid: String)
@Serializable data class RouteResultsReveal(val challengeId: String)
@Serializable data object RouteEditProfile
@Serializable data object RouteAbout

private val tabRoutes = listOf(RouteChallenges, RouteResults, RouteMe)

private fun NavController.isAtTab(): Boolean {
    val dest = currentBackStackEntry?.destination ?: return false
    return tabRoutes.any { dest.hasRoute(it::class) }
}

private fun NavController.isAtChallengesTab(): Boolean {
    val dest = currentBackStackEntry?.destination ?: return false
    return dest.hasRoute(RouteChallenges::class)
}

@Composable
fun AppNav() {
    val onboardingPrefs: OnboardingPrefs = koinInject()
    val platformShare: PlatformShare = koinInject()
    val pushNotifications: PushNotifications = koinInject()
    val auth: AuthRepository = koinInject()
    val challengeRepository: ChallengeRepository = koinInject()
    val seenStore: ResultsSeenStore = koinInject()
    val localPredictions: LocalPredictionStore = koinInject()

    val authState by auth.authState.collectAsState(AuthState.Loading)
    val unseenResultsCount by kotlinx.coroutines.flow.combine(
        challengeRepository.observeFinished(),
        seenStore.seenIds,
    ) { finished, seen -> finished.count { it.id !in seen } }
        .collectAsState(initial = 0)

    val snackbarHostState = remember { SnackbarHostState() }
    val errorReporter: ErrorReporter = koinInject()
    var appError by remember { mutableStateOf<AppError?>(null) }
    LaunchedEffect(errorReporter) {
        errorReporter.errors.collect { appError = it }
    }

    // Computed once and remembered: NavHost rebuilds its graph (resetting the
    // back stack to the start destination) whenever `startDestination` changes.
    // `hasSeenWelcome` flips to true the moment the user taps Sign in, so an
    // unremembered read would recompute to RouteChallenges on the next
    // recomposition and bounce the user off the sign-in screen onto Challenges.
    val startDestination: Any = remember { if (onboardingPrefs.hasSeenWelcome) RouteChallenges else RouteWelcome }
    val navController = rememberNavController()
    val coroutineScope = rememberCoroutineScope()

    LaunchedEffect(Unit) {
        pushNotifications.incomingDeepLinks.collect { url ->
            val deepLink = parseDeepLink(url) ?: return@collect
            pushNotifications.markDeepLinkConsumed()
            when (deepLink) {
                is DeepLink.Challenge -> navController.navigate(RouteChallengeDetail(deepLink.id))
            }
        }
    }

    LaunchedEffect(Unit) {
        VerifyAutomation.openPredictChallengeId.collect { challengeId ->
            navController.navigate(RoutePredict(challengeId = challengeId))
        }
    }

    val currentBackStackEntry by navController.currentBackStackEntryAsState()
    val currentDest = currentBackStackEntry?.destination
    val isAtTab = tabRoutes.any { currentDest?.hasRoute(it::class) == true }
    val isAtChallengesTab = currentDest?.hasRoute(RouteChallenges::class) == true

    fun navigateToTab(route: Any) {
        navController.navigate(route) {
            popUpTo(navController.graph.id) {
                saveState = true
            }
            launchSingleTop = true
            restoreState = true
        }
    }

    Scaffold(
        modifier = Modifier.enableTestTagsAsResourceId(),
        topBar = {
            if (!isAtTab) {
                IconButton(
                    onClick = { navController.navigateUp() },
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
            if (isAtTab) {
                val navItemColors = NavigationBarItemDefaults.colors(
                    indicatorColor = MaterialTheme.colorScheme.primaryContainer,
                    selectedIconColor = MaterialTheme.colorScheme.onPrimaryContainer,
                    selectedTextColor = MaterialTheme.colorScheme.primary,
                    unselectedIconColor = MaterialTheme.colorScheme.onSurfaceVariant,
                    unselectedTextColor = MaterialTheme.colorScheme.onSurfaceVariant,
                )
                NavigationBar {
                    NavigationBarItem(
                        selected = isAtChallengesTab,
                        onClick = { navigateToTab(RouteChallenges) },
                        icon = { Icon(imageVector = Lucide.Target, contentDescription = null) },
                        label = { Text(stringResource(Res.string.nav_tab_challenges)) },
                        colors = navItemColors,
                        modifier = Modifier.testTag("nav_tab_challenges"),
                    )
                    NavigationBarItem(
                        selected = currentDest?.hasRoute(RouteResults::class) == true,
                        onClick = { navigateToTab(RouteResults) },
                        icon = {
                            Box {
                                Icon(imageVector = Lucide.Trophy, contentDescription = null)
                                if (unseenResultsCount > 0) {
                                    Box(
                                        modifier = Modifier
                                            .size(8.dp)
                                            .offset(x = 10.dp, y = (-2).dp)
                                            .background(
                                                color = MaterialTheme.colorScheme.error,
                                                shape = CircleShape,
                                            )
                                            .align(Alignment.TopEnd),
                                    )
                                }
                            }
                        },
                        label = { Text(stringResource(Res.string.nav_tab_results)) },
                        colors = navItemColors,
                        modifier = Modifier.testTag("nav_tab_results"),
                    )
                    NavigationBarItem(
                        selected = currentDest?.hasRoute(RouteMe::class) == true,
                        onClick = { navigateToTab(RouteMe) },
                        icon = { Icon(imageVector = Lucide.User, contentDescription = null) },
                        label = { Text(stringResource(Res.string.nav_tab_me)) },
                        colors = navItemColors,
                        modifier = Modifier.testTag("nav_tab_me"),
                    )
                }
            }
        },
        floatingActionButton = {
            if (isAtChallengesTab) {
                FloatingActionButton(
                    onClick = {
                        if (authState.isFullyAuthed) navController.navigate(RouteCreate()) else navController.navigate(RouteSignIn)
                    },
                    shape = MaterialTheme.shapes.extraLarge,
                    modifier = Modifier.testTag("fab_create"),
                ) {
                    Icon(imageVector = Lucide.Plus, contentDescription = stringResource(Res.string.nav_create_challenge_a11y))
                }
            }
        },
        contentWindowInsets = WindowInsets(0),
    ) { padding ->
        CompositionLocalProvider(LocalSnackbarHost provides snackbarHostState) {
            NavHost(
                navController = navController,
                startDestination = startDestination,
                modifier = Modifier
                    .background(MaterialTheme.colorScheme.background)
                    .padding(padding),
                enterTransition = {
                    val fromTab = tabRoutes.indexOfFirst { initialState.destination.hasRoute(it::class) }
                    val toTab = tabRoutes.indexOfFirst { targetState.destination.hasRoute(it::class) }
                    val dir = if (fromTab != -1 && toTab != -1 && toTab < fromTab) -1 else 1
                    slideInHorizontally(initialOffsetX = { it * dir }) + fadeIn()
                },
                exitTransition = {
                    val fromTab = tabRoutes.indexOfFirst { initialState.destination.hasRoute(it::class) }
                    val toTab = tabRoutes.indexOfFirst { targetState.destination.hasRoute(it::class) }
                    val dir = if (fromTab != -1 && toTab != -1 && toTab < fromTab) 1 else -1
                    slideOutHorizontally(targetOffsetX = { it / 4 * dir }) + fadeOut()
                },
                popEnterTransition = { slideInHorizontally(initialOffsetX = { -it / 4 }) + fadeIn() },
                popExitTransition = { slideOutHorizontally(targetOffsetX = { it }) + fadeOut() },
            ) {
                composable<RouteChallenges> {
                    ChallengesScreen(
                        viewModel = koinViewModel<ChallengesViewModel>(),
                        onNavigateToChallenge = { navController.navigate(RouteChallengeDetail(it)) },
                        onNavigateToCreate = {
                            if (authState.isFullyAuthed) navController.navigate(RouteCreate()) else navController.navigate(RouteSignIn)
                        },
                        onNavigateToDraft = { draftId ->
                            if (authState.isFullyAuthed) navController.navigate(RouteCreate(draftId = draftId)) else navController.navigate(RouteSignIn)
                        },
                    )
                }
                composable<RouteResults> {
                    ResultsScreen(
                        viewModel = koinViewModel<ResultsViewModel>(),
                        onNavigateToReveal = { navController.navigate(RouteResultsReveal(challengeId = it)) },
                    )
                }
                composable<RouteMe> {
                    MeScreen(
                        viewModel = koinViewModel<MeViewModel>(),
                        snackbarHostState = snackbarHostState,
                        onNavigateToFriends = {
                            if (authState.isFullyAuthed) navController.navigate(RouteFriends) else navController.navigate(RouteSignIn)
                        },
                        onNavigateToSignIn = { navController.navigate(RouteSignIn) },
                        onNavigateToEditProfile = { navController.navigate(RouteEditProfile) },
                        onNavigateToAbout = { navController.navigate(RouteAbout) },
                        onSignedOut = {
                            navController.navigate(RouteChallenges) {
                                popUpTo(startDestination) { inclusive = true }
                            }
                        },
                        onDeleted = {
                            navController.navigate(RouteWelcome) {
                                popUpTo(startDestination) { inclusive = true }
                            }
                        },
                    )
                }
                composable<RouteChallengeDetail> { entry ->
                    val route = entry.toRoute<RouteChallengeDetail>()
                    ChallengeDetailScreen(
                        viewModel = koinViewModel<ChallengeDetailViewModel> { parametersOf(route.id) },
                        platformShare = platformShare,
                        snackbarHostState = snackbarHostState,
                        onNavigateToBet = { navController.navigate(RoutePredict(route.id)) },
                        onNavigateToSummary = { navController.navigate(RouteChallengeSummary(route.id)) },
                        onNavigateToPostResults = { id -> navController.navigate(RoutePostResults(id)) },
                        onNavigateToParticipant = { challengeId, uid -> navController.navigate(RouteParticipantBets(challengeId = challengeId, uid = uid)) },
                        onNavigateToInvite = { id -> navController.navigate(RouteInvite(id)) },
                        onDeleted = { navController.popBackStack() },
                    )
                }
                composable<RoutePredict> { entry ->
                    val route = entry.toRoute<RoutePredict>()
                    PredictScreen(
                        viewModel = koinViewModel<PredictViewModel> { parametersOf(route.challengeId) },
                        snackbarHostState = snackbarHostState,
                        onSubmitted = { navController.popBackStack() },
                    )
                }
                composable<RouteCreate> { entry ->
                    val route = entry.toRoute<RouteCreate>()
                    CreateChallengeScreen(
                        viewModel = koinViewModel<CreateChallengeViewModel> { parametersOf(route.draftId) },
                        snackbarHostState = snackbarHostState,
                        onPublished = { id ->
                            navController.navigate(RouteChallengeDetail(id)) {
                                popUpTo(navController.currentBackStackEntry!!.destination.id) { inclusive = true }
                            }
                        },
                        onDraftSaved = { navController.popBackStack() },
                    )
                }
                composable<RouteSignIn> {
                    SignInScreen(
                        viewModel = koinViewModel<SignInViewModel>(),
                        snackbarHostState = snackbarHostState,
                        onSignedIn = {
                            navController.navigate(RouteMe) {
                                popUpTo(navController.graph.id) { inclusive = false }
                                launchSingleTop = true
                            }
                            val pending = localPredictions.snapshot()
                            if (pending.isNotEmpty()) {
                                coroutineScope.launch {
                                    val summary = auth.migrateLocalToCloud().getOrNull()
                                    if (summary != null && summary.failed > 0) {
                                        snackbarHostState.showSnackbar(
                                            "${summary.failed} guest prediction(s) couldn't be imported — their deadline had passed."
                                        )
                                    }
                                }
                            }
                        },
                        onGuest = {
                            navController.navigate(RouteChallenges) {
                                popUpTo(startDestination) { inclusive = true }
                            }
                        },
                    )
                }
                composable<RouteFriends> {
                    FriendsScreen(
                        viewModel = koinViewModel<FriendsViewModel>(),
                        snackbarHostState = snackbarHostState,
                        onOpenCloudProfile = { uid -> navController.navigate(RoutePlayerProfile(uid)) },
                        onOpenFriendRequests = { navController.navigate(RouteFriendRequests) },
                    )
                }
                composable<RouteFriendRequests> {
                    FriendRequestsScreen(
                        viewModel = koinViewModel<FriendRequestsViewModel>(),
                        snackbarHostState = snackbarHostState,
                    )
                }
                composable<RoutePlayerProfile> { entry ->
                    val route = entry.toRoute<RoutePlayerProfile>()
                    PlayerProfileScreen(
                        viewModel = koinViewModel<PlayerProfileViewModel> { parametersOf(route.uid) },
                    )
                }
                composable<RouteEditProfile> {
                    EditProfileScreen(
                        viewModel = koinViewModel<EditProfileViewModel>(),
                        snackbarHostState = snackbarHostState,
                        onSaved = { navController.popBackStack() },
                    )
                }
                composable<RouteAbout> {
                    AboutScreen()
                }
                composable<RouteParticipantBets> { entry ->
                    val route = entry.toRoute<RouteParticipantBets>()
                    ParticipantBetsScreen(
                        viewModel = koinViewModel<ParticipantBetsViewModel> { parametersOf(route.challengeId, route.uid) },
                    )
                }
                composable<RouteInvite> { entry ->
                    val route = entry.toRoute<RouteInvite>()
                    InviteFriendsScreen(
                        viewModel = koinViewModel<InviteFriendsViewModel> { parametersOf(route.challengeId) },
                        snackbarHostState = snackbarHostState,
                        onSent = { navController.popBackStack() },
                    )
                }
                composable<RoutePostResults> { entry ->
                    val route = entry.toRoute<RoutePostResults>()
                    PostResultsScreen(
                        viewModel = koinViewModel<PostResultsViewModel> { parametersOf(route.challengeId) },
                        snackbarHostState = snackbarHostState,
                        onPosted = {
                            navController.navigate(RouteResultsReveal(challengeId = route.challengeId)) {
                                popUpTo(RouteChallengeDetail::class) { inclusive = true }
                            }
                        },
                    )
                }
                composable<RouteChallengeSummary> { entry ->
                    val route = entry.toRoute<RouteChallengeSummary>()
                    ChallengeSummaryScreen(
                        viewModel = koinViewModel<BetListViewModel> { parametersOf(route.challengeId) },
                        onEdit = { id -> navController.navigate(RoutePredict(id)) },
                        onOpenParticipant = { uid ->
                            navController.navigate(RouteParticipantBets(challengeId = route.challengeId, uid = uid))
                        },
                    )
                }
                composable<RouteResultsReveal> { entry ->
                    val route = entry.toRoute<RouteResultsReveal>()
                    ResultsRevealScreen(
                        viewModel = koinViewModel<ResultsRevealViewModel> { parametersOf(route.challengeId) },
                    )
                }
                composable<RouteWelcome> {
                    WelcomeScreen(
                        onSignIn = {
                            onboardingPrefs.hasSeenWelcome = true
                            navController.navigate(RouteSignIn) {
                                popUpTo(RouteWelcome) { inclusive = true }
                            }
                        },
                        onContinueAsGuest = {
                            onboardingPrefs.hasSeenWelcome = true
                            navController.navigate(RouteChallenges) {
                                popUpTo(RouteWelcome) { inclusive = true }
                            }
                        },
                    )
                }
            }
        }
    }

    appError?.let { error ->
        ErrorDialog(error = error, onDismiss = { appError = null })
    }
}
