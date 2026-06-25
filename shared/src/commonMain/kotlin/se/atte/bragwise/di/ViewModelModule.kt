package se.atte.bragwise.di

import org.koin.core.module.dsl.viewModel
import org.koin.core.module.dsl.viewModelOf
import org.koin.dsl.module
import se.atte.bragwise.ui.screens.auth.SignInViewModel
import se.atte.bragwise.ui.screens.bets.BetListViewModel
import se.atte.bragwise.ui.screens.friends.FriendRequestsViewModel
import se.atte.bragwise.ui.screens.challenges.ChallengesViewModel
import se.atte.bragwise.ui.screens.create.CreateChallengeViewModel
import se.atte.bragwise.ui.screens.detail.ChallengeDetailViewModel
import se.atte.bragwise.ui.screens.detail.ParticipantBetsViewModel
import se.atte.bragwise.ui.screens.friends.FriendsViewModel
import se.atte.bragwise.ui.screens.postresults.PostResultsViewModel
import se.atte.bragwise.ui.screens.profile.EditProfileViewModel
import se.atte.bragwise.ui.screens.profile.PlayerProfileViewModel
import se.atte.bragwise.ui.screens.me.MeViewModel
import se.atte.bragwise.ui.screens.predict.PredictViewModel
import se.atte.bragwise.ui.screens.results.ResultsRevealViewModel
import se.atte.bragwise.ui.screens.results.ResultsViewModel

val viewModelModule = module {
    // No route params — constructor references resolve all deps from the graph.
    viewModelOf(::ChallengesViewModel)
    viewModelOf(::MeViewModel)
    viewModel<CreateChallengeViewModel> { params ->
        CreateChallengeViewModel(
            challenges = get(),
            social = get(),
            ensureNamedAccount = get(),
            errorReporter = get(),
            draftId = params.get<String?>(0),
            cloneSourceId = params.get<String?>(1),
        )
    }
    viewModelOf(::SignInViewModel)
    viewModelOf(::FriendsViewModel)
    viewModelOf(::FriendRequestsViewModel)
    viewModelOf(::EditProfileViewModel)
    viewModel<PlayerProfileViewModel> { params ->
        PlayerProfileViewModel(
            uid = params.get<String>(),
            profiles = get(),
            social = get(),
            errorReporter = get(),
        )
    }

    // Route-param VMs — deps injected from the graph, route args via parametersOf().
    viewModel<ChallengeDetailViewModel> { params ->
        ChallengeDetailViewModel(
            challengeId = params.get<String>(),
            challenges = get(),
            auth = get(),
            profile = get(),
            social = get(),
            errorReporter = get(),
            analytics = get(),
        )
    }
    viewModel<PredictViewModel> { params ->
        PredictViewModel(
            challengeId = params.get<String>(),
            challenges = get(),
            auth = get(),
            localPredictions = get(),
            ensureNamedAccount = get(),
            errorReporter = get(),
            analytics = get(),
        )
    }
    viewModel<BetListViewModel> { params ->
        BetListViewModel(
            challengeId = params.get<String>(),
            challenges = get(),
            errorReporter = get(),
        )
    }
    viewModel<PostResultsViewModel> { params ->
        PostResultsViewModel(
            challengeId = params.get<String>(),
            challenges = get(),
            errorReporter = get(),
        )
    }
    viewModel<ParticipantBetsViewModel> { params ->
        ParticipantBetsViewModel(
            challengeId = params.get<String>(),
            uid = params.get<String>(),
            challenges = get(),
            profiles = get(),
            social = get(),
            auth = get(),
            errorReporter = get(),
        )
    }
    viewModelOf(::ResultsViewModel)
    viewModel<ResultsRevealViewModel> { params ->
        ResultsRevealViewModel(
            challengeId = params.get<String>(),
            challenges = get(),
            auth = get(),
            seenStore = get(),
            social = get(),
            errorReporter = get(),
        )
    }
}
