package se.atte.bragwise.di

import org.koin.core.module.Module
import org.koin.dsl.module
import se.atte.bragwise.data.AuthLocalDataSource
import se.atte.bragwise.data.AuthRemoteDataSource
import se.atte.bragwise.data.IosAuthLocalDataSource
import se.atte.bragwise.data.IosOnboardingPrefs
import se.atte.bragwise.data.IosLanguagePrefs
import se.atte.bragwise.data.IosThemePrefs
import se.atte.bragwise.data.LanguagePrefs
import se.atte.bragwise.data.OnboardingPrefs
import se.atte.bragwise.data.ThemePrefs
import se.atte.bragwise.data.db.DatabaseDriverFactory
import se.atte.bragwise.db.BragwiseDatabase
import se.atte.bragwise.platform.IosPlatformShare
import se.atte.bragwise.push.PushNotifications
import se.atte.bragwise.platform.PlatformShare

actual val platformModule: Module = module {
    single<AuthLocalDataSource> { IosAuthLocalDataSource() }
    single { AuthRemoteDataSource() }
    single<OnboardingPrefs> { IosOnboardingPrefs() }
    single<ThemePrefs> { IosThemePrefs() }
    single<LanguagePrefs> { IosLanguagePrefs() }
    single<PlatformShare> { IosPlatformShare() }
    single { PushNotifications() }
    single { DatabaseDriverFactory() }
    single { BragwiseDatabase(get<DatabaseDriverFactory>().create()) }
}
