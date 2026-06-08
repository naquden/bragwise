package se.atte.bragwise.di

import kotlinx.coroutines.Dispatchers
import org.koin.dsl.module
import se.atte.bragwise.data.ActivityRegistrar
import se.atte.bragwise.data.AuthRepository
import se.atte.bragwise.data.ChallengeRepository
import se.atte.bragwise.data.EnsureNamedAccount
import se.atte.bragwise.data.ProfileRepository
import se.atte.bragwise.data.ResultsSeenStore
import se.atte.bragwise.data.SocialRepository
import se.atte.bragwise.data.mock.MockAuthRepository
import se.atte.bragwise.data.mock.MockChallengeRepository
import se.atte.bragwise.data.mock.MockProfileRepository
import se.atte.bragwise.data.mock.MockSocialRepository
import se.atte.bragwise.data.LocalPredictionStore
import se.atte.bragwise.db.BragwiseDatabase
import se.atte.bragwise.push.PushTokenRegistrar

val mockDataModule = module {
    single { se.atte.bragwise.mvi.ErrorReporter() }
    single<AuthRepository> { MockAuthRepository() }
    single<ChallengeRepository> { MockChallengeRepository(auth = get()) }
    single { LocalPredictionStore(get<BragwiseDatabase>()) }
    single { ResultsSeenStore(get<BragwiseDatabase>(), Dispatchers.Default) }
    single { EnsureNamedAccount(auth = get(), profile = get(), onboardingPrefs = get()) }
    single<SocialRepository> { MockSocialRepository() }
    single<ProfileRepository> { MockProfileRepository() }
    // App() injects this unconditionally; .start() gates on SignedIn and all
    // callable invocations are runCatching-wrapped, so it's inert under mock.
    single { PushTokenRegistrar(push = get(), auth = get()) }
    single { ActivityRegistrar(auth = get(), profile = get()) }
}
