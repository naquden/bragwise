package se.atte.bragwise.di

import org.koin.core.module.dsl.singleOf
import org.koin.dsl.module
import se.atte.bragwise.data.AuthRemoteDataSource
import se.atte.bragwise.data.AuthRepository
import se.atte.bragwise.data.ChallengeLocalDataSource
import se.atte.bragwise.data.ChallengeRemoteDataSource
import se.atte.bragwise.data.ChallengeRepository
import se.atte.bragwise.data.FirebaseAuthRepository
import se.atte.bragwise.data.FirebaseChallengeRepository
import se.atte.bragwise.data.FirebaseProfileRepository
import se.atte.bragwise.data.FirebaseSocialRepository
import se.atte.bragwise.data.LocalFriendStore
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
    single { LocalFriendStore(persistence = get()) }
    single { PushTokenRegistrar(push = get(), auth = get()) }
    single { ProfileRemoteDataSource() }
    singleOf(::ProfileLocalDataSource)

    // AuthLocalDataSource is platform-specific and lives in platformModule.
    // Repositories bound to their interfaces so the mock module can swap them out.
    single<AuthRepository> { FirebaseAuthRepository(remote = get(), local = get()) }
    single<ChallengeRepository> { FirebaseChallengeRepository(remote = get(), local = get(), auth = get()) }
    single<SocialRepository> { FirebaseSocialRepository(remote = get(), local = get(), auth = get(), localFriends = get()) }
    single<ProfileRepository> { FirebaseProfileRepository(remote = get(), local = get(), auth = get()) }
}
