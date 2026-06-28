package se.atte.bragwise.di

import org.koin.core.module.dsl.singleOf
import org.koin.dsl.module
import se.atte.bragwise.data.ActivityRegistrar
import se.atte.bragwise.data.EnsureNamedAccount
import se.atte.bragwise.data.AuthRepository
import se.atte.bragwise.data.ChallengeLocalDataSource
import se.atte.bragwise.data.ChallengeRepository
import se.atte.bragwise.data.FirebaseAuthRepository
import se.atte.bragwise.data.FirebaseChallengeRepository
import se.atte.bragwise.data.FirebaseProfileRepository
import se.atte.bragwise.data.FirebaseSocialRepository
import se.atte.bragwise.push.PushTokenRegistrar
import se.atte.bragwise.data.ProfileLocalDataSource
import se.atte.bragwise.data.ProfileRepository
import se.atte.bragwise.data.SocialLocalDataSource
import se.atte.bragwise.data.SocialRepository
import se.atte.bragwise.crash.CrashReporter
import se.atte.bragwise.crash.createCrashReporter

val dataModule = module {
    single<CrashReporter> { createCrashReporter() }
    single { se.atte.bragwise.mvi.ErrorReporter(crash = get()) }

    // Data sources — concrete remotes are registered per-platform in platformModule
    // (Android/iOS), bound to their interfaces. AuthRemoteDataSource is there too
    // because it needs the runtime package name for magic-link ActionCodeSettings.
    singleOf(::ChallengeLocalDataSource)
    singleOf(::SocialLocalDataSource)
    single { PushTokenRegistrar(push = get(), auth = get(), pushRemote = get()) }
    single { ActivityRegistrar(auth = get(), profile = get()) }
    single { EnsureNamedAccount(auth = get(), profile = get(), onboardingPrefs = get()) }
    singleOf(::ProfileLocalDataSource)

    // AuthLocalDataSource is platform-specific and lives in platformModule.
    // Repositories bound to their interfaces so the mock module can swap them out.
    single<AuthRepository> {
        FirebaseAuthRepository(
            remote = get(),
            local = get(),
            localPredictions = get(),
            challengeRemote = get(),
            analytics = get(),
        )
    }
    single<ChallengeRepository> { FirebaseChallengeRepository(remote = get(), local = get(), localDrafts = get(), auth = get(), social = get(), analytics = get()) }
    single<SocialRepository> { FirebaseSocialRepository(remote = get(), local = get(), auth = get(), profiles = get(), analytics = get()) }
    single<ProfileRepository> { FirebaseProfileRepository(remote = get(), local = get(), auth = get()) }
}
