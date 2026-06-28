package se.atte.bragwise.di

import kotlinx.coroutines.Dispatchers
import org.koin.core.module.Module
import org.koin.dsl.module
import se.atte.bragwise.data.AuthLocalDataSource
import se.atte.bragwise.data.AuthRemote
import se.atte.bragwise.data.AuthRemoteDataSource
import se.atte.bragwise.data.ChallengeRemote
import se.atte.bragwise.data.ChallengeRemoteDataSource
import se.atte.bragwise.data.FirebasePushTokenRemote
import se.atte.bragwise.data.IosAuthLocalDataSource
import se.atte.bragwise.data.LocalDraftStore
import se.atte.bragwise.data.LocalPredictionStore
import se.atte.bragwise.data.ProfileRemote
import se.atte.bragwise.data.ProfileRemoteDataSource
import se.atte.bragwise.data.PushTokenRemote
import se.atte.bragwise.data.ResultsSeenStore
import se.atte.bragwise.data.SocialRemote
import se.atte.bragwise.data.SocialRemoteDataSource
import se.atte.bragwise.data.IosOnboardingPrefs
import se.atte.bragwise.data.IosLanguagePrefs
import se.atte.bragwise.data.IosThemePrefs
import se.atte.bragwise.data.LanguagePrefs
import se.atte.bragwise.data.OnboardingPrefs
import se.atte.bragwise.data.SqlDelightLocalDraftStore
import se.atte.bragwise.data.SqlDelightLocalPredictionStore
import se.atte.bragwise.data.SqlDelightResultsSeenStore
import se.atte.bragwise.data.ThemePrefs
import se.atte.bragwise.data.db.DatabaseDriverFactory
import se.atte.bragwise.db.BragwiseDatabase
import se.atte.bragwise.platform.Analytics
import se.atte.bragwise.platform.IosAnalytics
import se.atte.bragwise.platform.IosPlatformShare
import se.atte.bragwise.push.PushNotifications
import se.atte.bragwise.platform.PlatformShare

actual val platformModule: Module = module {
    single<AuthLocalDataSource> { IosAuthLocalDataSource() }
    single<AuthRemote> { AuthRemoteDataSource() }
    single<ChallengeRemote> { ChallengeRemoteDataSource() }
    single<ProfileRemote> { ProfileRemoteDataSource() }
    single<SocialRemote> { SocialRemoteDataSource() }
    single<PushTokenRemote> { FirebasePushTokenRemote() }
    single<OnboardingPrefs> { IosOnboardingPrefs() }
    single<ThemePrefs> { IosThemePrefs() }
    single<LanguagePrefs> { IosLanguagePrefs() }
    single<PlatformShare> { IosPlatformShare() }
    single<Analytics> { IosAnalytics() }
    single { PushNotifications() }
    single { DatabaseDriverFactory() }
    single { BragwiseDatabase(get<DatabaseDriverFactory>().create()) }
    single<LocalPredictionStore> { SqlDelightLocalPredictionStore(get<BragwiseDatabase>()) }
    single<LocalDraftStore> { SqlDelightLocalDraftStore(get<BragwiseDatabase>()) }
    single<ResultsSeenStore> { SqlDelightResultsSeenStore(get<BragwiseDatabase>(), Dispatchers.Default) }
}
