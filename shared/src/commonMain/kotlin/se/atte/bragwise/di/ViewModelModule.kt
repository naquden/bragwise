package se.atte.bragwise.di

import org.koin.core.module.dsl.viewModel
import org.koin.core.module.dsl.viewModelOf
import org.koin.dsl.module
import se.atte.bragwise.ui.screens.auth.SignInViewModel
import se.atte.bragwise.ui.screens.challenges.ChallengesViewModel
import se.atte.bragwise.ui.screens.create.CreateChallengeViewModel
import se.atte.bragwise.ui.screens.detail.ChallengeDetailViewModel
import se.atte.bragwise.ui.screens.friends.FriendsViewModel
import se.atte.bragwise.ui.screens.leaderboard.LeaderboardViewModel
import se.atte.bragwise.ui.screens.me.MeViewModel
import se.atte.bragwise.ui.screens.onboarding.ReconcileFriendsViewModel
import se.atte.bragwise.ui.screens.predict.PredictViewModel

val viewModelModule = module {
    // No route params — constructor references resolve all deps from the graph.
    viewModelOf(::ChallengesViewModel)
    viewModelOf(::MeViewModel)
    viewModelOf(::CreateChallengeViewModel)
    viewModelOf(::SignInViewModel)
    viewModelOf(::FriendsViewModel)
    viewModelOf(::ReconcileFriendsViewModel)

    // Route-param VMs — deps injected from the graph, route args via parametersOf().
    viewModel<ChallengeDetailViewModel> { params ->
        ChallengeDetailViewModel(
            challengeId = params.get<String>(),
            challenges = get(),
            auth = get(),
        )
    }
    viewModel<PredictViewModel> { params ->
        PredictViewModel(
            challengeId = params.get<String>(),
            challenges = get(),
        )
    }
    viewModel<LeaderboardViewModel> { params ->
        LeaderboardViewModel(
            challengeId = params.get<String>(),
            isPromoted = params.get<Boolean>(),
            challenges = get(),
        )
    }
}
