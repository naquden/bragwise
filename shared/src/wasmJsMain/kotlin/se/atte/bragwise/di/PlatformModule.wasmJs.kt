package se.atte.bragwise.di

import org.koin.core.module.Module
import org.koin.dsl.module
import se.atte.bragwise.data.AuthLocalDataSource
import se.atte.bragwise.data.AuthRemote
import se.atte.bragwise.data.ChallengeRemote
import se.atte.bragwise.data.JsAuthRemote
import se.atte.bragwise.data.JsChallengeRemote
import se.atte.bragwise.data.JsProfileRemote
import se.atte.bragwise.data.JsSocialRemote
import se.atte.bragwise.data.LanguagePrefs
import se.atte.bragwise.data.LocalDraftStore
import se.atte.bragwise.data.LocalPredictionStore
import se.atte.bragwise.data.NoopPushTokenRemote
import se.atte.bragwise.data.OnboardingPrefs
import se.atte.bragwise.data.ProfileRemote
import se.atte.bragwise.data.PushTokenRemote
import se.atte.bragwise.data.ResultsSeenStore
import se.atte.bragwise.data.SocialRemote
import se.atte.bragwise.data.ThemePrefs
import se.atte.bragwise.data.WebAuthLocalDataSource
import se.atte.bragwise.data.WebLanguagePrefs
import se.atte.bragwise.data.WebLocalDraftStore
import se.atte.bragwise.data.WebLocalPredictionStore
import se.atte.bragwise.data.WebOnboardingPrefs
import se.atte.bragwise.data.WebResultsSeenStore
import se.atte.bragwise.data.WebThemePrefs
import se.atte.bragwise.platform.Analytics
import se.atte.bragwise.platform.PlatformShare
import se.atte.bragwise.platform.WebAnalytics
import se.atte.bragwise.platform.WebPlatformShare
import se.atte.bragwise.push.PushNotifications

actual val platformModule: Module = module {
    single<AuthLocalDataSource> { WebAuthLocalDataSource() }
    single<OnboardingPrefs> { WebOnboardingPrefs() }
    single<ThemePrefs> { WebThemePrefs() }
    single<LanguagePrefs> { WebLanguagePrefs() }
    single<PlatformShare> { WebPlatformShare() }
    single<Analytics> { WebAnalytics() }
    single { PushNotifications() }
    single<AuthRemote> { JsAuthRemote() }
    single<ChallengeRemote> { JsChallengeRemote() }
    single<ProfileRemote> { JsProfileRemote() }
    single<SocialRemote> { JsSocialRemote() }
    single<PushTokenRemote> { NoopPushTokenRemote() }
    single<LocalPredictionStore> { WebLocalPredictionStore() }
    single<LocalDraftStore> { WebLocalDraftStore() }
    single<ResultsSeenStore> { WebResultsSeenStore() }
    // NOTE: no DatabaseDriverFactory / BragwiseDatabase binding — web has no SQLite.
}
