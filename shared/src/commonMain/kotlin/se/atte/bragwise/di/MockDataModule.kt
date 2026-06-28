package se.atte.bragwise.di

import org.koin.dsl.module
import se.atte.bragwise.data.ActivityRegistrar
import se.atte.bragwise.data.AuthRepository
import se.atte.bragwise.data.ChallengeRepository
import se.atte.bragwise.data.EnsureNamedAccount
import se.atte.bragwise.data.LocalPredictionStore
import se.atte.bragwise.data.ProfileRepository
import se.atte.bragwise.data.ResultsSeenStore
import se.atte.bragwise.data.SocialRepository
import se.atte.bragwise.data.mock.InMemoryLocalPredictionStore
import se.atte.bragwise.data.mock.InMemoryResultsSeenStore
import se.atte.bragwise.data.mock.MockAuthRepository
import se.atte.bragwise.data.mock.MockChallengeRepository
import se.atte.bragwise.data.mock.MockProfileRepository
import se.atte.bragwise.data.mock.MockSocialRepository
import se.atte.bragwise.push.PushTokenRegistrar
import se.atte.bragwise.crash.CrashReporter
import se.atte.bragwise.crash.NoopCrashReporter

val mockDataModule = module {
    single<CrashReporter> { NoopCrashReporter }
    single { se.atte.bragwise.mvi.ErrorReporter(crash = get()) }
    single<AuthRepository> { MockAuthRepository() }
    single<ChallengeRepository> { MockChallengeRepository(auth = get()) }
    single<LocalPredictionStore> { InMemoryLocalPredictionStore() }
    single<ResultsSeenStore> { InMemoryResultsSeenStore() }
    single { EnsureNamedAccount(auth = get(), profile = get(), onboardingPrefs = get()) }
    single<SocialRepository> { MockSocialRepository() }
    single<ProfileRepository> { MockProfileRepository() }
    // App() injects this unconditionally; .start() gates on SignedIn and all
    // callable invocations are runCatching-wrapped, so it's inert under mock.
    single<se.atte.bragwise.data.PushTokenRemote> { se.atte.bragwise.data.NoopPushTokenRemote() }
    single { PushTokenRegistrar(push = get(), auth = get(), pushRemote = get()) }
    single { ActivityRegistrar(auth = get(), profile = get()) }
}
