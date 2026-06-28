package se.atte.bragwise.di

import kotlinx.coroutines.Dispatchers
import org.koin.android.ext.koin.androidContext
import org.koin.core.module.Module
import org.koin.dsl.module
import se.atte.bragwise.data.AndroidAuthLocalDataSource
import se.atte.bragwise.data.AndroidOnboardingPrefs
import se.atte.bragwise.data.AndroidLanguagePrefs
import se.atte.bragwise.data.AndroidThemePrefs
import se.atte.bragwise.data.AuthLocalDataSource
import se.atte.bragwise.data.AuthRemote
import se.atte.bragwise.data.AuthRemoteDataSource
import se.atte.bragwise.data.ChallengeRemote
import se.atte.bragwise.data.ChallengeRemoteDataSource
import se.atte.bragwise.data.FirebasePushTokenRemote
import se.atte.bragwise.data.LanguagePrefs
import se.atte.bragwise.data.LocalDraftStore
import se.atte.bragwise.data.LocalPredictionStore
import se.atte.bragwise.data.ProfileRemote
import se.atte.bragwise.data.ProfileRemoteDataSource
import se.atte.bragwise.data.PushTokenRemote
import se.atte.bragwise.data.ResultsSeenStore
import se.atte.bragwise.data.SocialRemote
import se.atte.bragwise.data.SocialRemoteDataSource
import se.atte.bragwise.data.OnboardingPrefs
import se.atte.bragwise.data.ThemePrefs
import se.atte.bragwise.data.SqlDelightLocalDraftStore
import se.atte.bragwise.data.SqlDelightLocalPredictionStore
import se.atte.bragwise.data.SqlDelightResultsSeenStore
import se.atte.bragwise.data.db.DatabaseDriverFactory
import se.atte.bragwise.db.BragwiseDatabase
import se.atte.bragwise.platform.Analytics
import se.atte.bragwise.platform.AndroidAnalytics
import se.atte.bragwise.platform.AndroidPlatformShare
import se.atte.bragwise.push.PushNotifications
import se.atte.bragwise.platform.PlatformShare

actual val platformModule: Module = module {
    single<AuthLocalDataSource> { AndroidAuthLocalDataSource(context = androidContext()) }
    single<AuthRemote> {
        AuthRemoteDataSource(
            actionCodeSettings = AuthRemoteDataSource.defaultActionCodeSettings(
                packageName = androidContext().packageName,
            ),
        )
    }
    single<ChallengeRemote> { ChallengeRemoteDataSource() }
    single<ProfileRemote> { ProfileRemoteDataSource() }
    single<SocialRemote> { SocialRemoteDataSource() }
    single<PushTokenRemote> { FirebasePushTokenRemote() }
    single<OnboardingPrefs> { AndroidOnboardingPrefs(context = androidContext()) }
    single<ThemePrefs> { AndroidThemePrefs(context = androidContext()) }
    single<LanguagePrefs> { AndroidLanguagePrefs(context = androidContext()) }
    single<PlatformShare> { AndroidPlatformShare(context = androidContext()) }
    single<Analytics> { AndroidAnalytics() }
    single { PushNotifications() }
    single { DatabaseDriverFactory(context = androidContext()) }
    single { BragwiseDatabase(get<DatabaseDriverFactory>().create()) }
    single<LocalPredictionStore> { SqlDelightLocalPredictionStore(get<BragwiseDatabase>()) }
    single<LocalDraftStore> { SqlDelightLocalDraftStore(get<BragwiseDatabase>()) }
    single<ResultsSeenStore> { SqlDelightResultsSeenStore(get<BragwiseDatabase>(), Dispatchers.Default) }
}
