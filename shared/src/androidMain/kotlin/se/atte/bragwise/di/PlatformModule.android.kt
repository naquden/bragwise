package se.atte.bragwise.di

import org.koin.android.ext.koin.androidContext
import org.koin.core.module.Module
import org.koin.dsl.module
import se.atte.bragwise.data.AndroidAuthLocalDataSource
import se.atte.bragwise.data.AndroidLocalFriendPersistence
import se.atte.bragwise.data.AndroidLocalPredictionPersistence
import se.atte.bragwise.data.AndroidOnboardingPrefs
import se.atte.bragwise.data.AndroidThemePrefs
import se.atte.bragwise.data.AuthLocalDataSource
import se.atte.bragwise.data.LocalFriendPersistence
import se.atte.bragwise.data.LocalPredictionPersistence
import se.atte.bragwise.data.OnboardingPrefs
import se.atte.bragwise.data.ThemePrefs
import se.atte.bragwise.platform.AndroidPlatformShare
import se.atte.bragwise.push.PushNotifications
import se.atte.bragwise.platform.PlatformShare

actual val platformModule: Module = module {
    single<AuthLocalDataSource> { AndroidAuthLocalDataSource(context = androidContext()) }
    single<LocalFriendPersistence> { AndroidLocalFriendPersistence(context = androidContext()) }
    single<LocalPredictionPersistence> { AndroidLocalPredictionPersistence(context = androidContext()) }
    single<OnboardingPrefs> { AndroidOnboardingPrefs(context = androidContext()) }
    single<ThemePrefs> { AndroidThemePrefs(context = androidContext()) }
    single<PlatformShare> { AndroidPlatformShare(context = androidContext()) }
    single { PushNotifications() }
}
