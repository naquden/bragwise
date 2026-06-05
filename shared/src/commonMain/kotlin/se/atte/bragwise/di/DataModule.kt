package se.atte.bragwise.di

import org.koin.core.module.dsl.singleOf
import org.koin.dsl.module
import se.atte.bragwise.data.ActivityRegistrar
import se.atte.bragwise.data.AuthRemoteDataSource
import se.atte.bragwise.data.EnsureNamedAccount
import se.atte.bragwise.data.AuthRepository
import se.atte.bragwise.data.ChallengeLocalDataSource
import se.atte.bragwise.data.ChallengeRemoteDataSource
import se.atte.bragwise.data.ChallengeRepository
import se.atte.bragwise.data.FirebaseAuthRepository
import se.atte.bragwise.data.FirebaseChallengeRepository
import se.atte.bragwise.data.FirebaseProfileRepository
import se.atte.bragwise.data.FirebaseSocialRepository
import se.atte.bragwise.data.LocalPredictionStore
import se.atte.bragwise.data.ResultsSeenStore
import se.atte.bragwise.db.BragwiseDatabase
import se.atte.bragwise.push.PushTokenRegistrar
import se.atte.bragwise.data.ProfileLocalDataSource
import se.atte.bragwise.data.ProfileRemoteDataSource
import se.atte.bragwise.data.ProfileRepository
import se.atte.bragwise.data.SocialLocalDataSource
import se.atte.bragwise.data.SocialRemoteDataSource
import se.atte.bragwise.data.SocialRepository

val dataModule = module {
    // Data sources — use no-arg construction so each class relies on its own
    // default Firebase singleton (Firebase.auth / Firebase.firestore / Firebase.functions).
    single { AuthRemoteDataSource() }
    single { ChallengeRemoteDataSource() }
    singleOf(::ChallengeLocalDataSource)
    single { SocialRemoteDataSource() }
    singleOf(::SocialLocalDataSource)
    single { LocalPredictionStore(get<BragwiseDatabase>()) }
    single { ResultsSeenStore(get<BragwiseDatabase>()) }
    single { PushTokenRegistrar(push = get(), auth = get()) }
    single { ActivityRegistrar(auth = get(), profile = get()) }
    single { EnsureNamedAccount(auth = get(), profile = get(), onboardingPrefs = get()) }
    single { ProfileRemoteDataSource() }
    singleOf(::ProfileLocalDataSource)

    // AuthLocalDataSource is platform-specific and lives in platformModule.
    // Repositories bound to their interfaces so the mock module can swap them out.
    single<AuthRepository> {
        FirebaseAuthRepository(
            remote = get(),
            local = get(),
            localPredictions = get(),
            challengeRemote = get(),
        )
    }
    single<ChallengeRepository> { FirebaseChallengeRepository(remote = get(), local = get(), auth = get(), social = get()) }
    single<SocialRepository> { FirebaseSocialRepository(remote = get(), local = get(), auth = get()) }
    single<ProfileRepository> { FirebaseProfileRepository(remote = get(), local = get(), auth = get()) }
}
