package se.atte.bragwise.di

import org.koin.core.module.dsl.singleOf
import org.koin.dsl.module
import se.atte.bragwise.data.AuthRemoteDataSource
import se.atte.bragwise.data.AuthRepository
import se.atte.bragwise.data.ChallengeLocalDataSource
import se.atte.bragwise.data.ChallengeRemoteDataSource
import se.atte.bragwise.data.ChallengeRepository
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
    single { ProfileRemoteDataSource() }
    singleOf(::ProfileLocalDataSource)

    // AuthLocalDataSource is platform-specific and lives in platformModule.
    // Repositories — lambda form used where optional constructor params exist.
    single { AuthRepository(remote = get(), local = get()) }
    singleOf(::ChallengeRepository)
    single { SocialRepository(remote = get(), local = get(), auth = get()) }
    singleOf(::ProfileRepository)
}
