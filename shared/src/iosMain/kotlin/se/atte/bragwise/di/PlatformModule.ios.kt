package se.atte.bragwise.di

import org.koin.core.module.Module
import org.koin.dsl.module
import se.atte.bragwise.data.AuthLocalDataSource
import se.atte.bragwise.data.IosAuthLocalDataSource
import se.atte.bragwise.data.IosLocalFriendPersistence
import se.atte.bragwise.data.IosOnboardingPrefs
import se.atte.bragwise.data.LocalFriendPersistence
import se.atte.bragwise.data.OnboardingPrefs
import se.atte.bragwise.platform.IosPlatformShare
import se.atte.bragwise.push.PushNotifications
import se.atte.bragwise.platform.PlatformShare

actual val platformModule: Module = module {
    single<AuthLocalDataSource> { IosAuthLocalDataSource() }
    single<LocalFriendPersistence> { IosLocalFriendPersistence() }
    single<OnboardingPrefs> { IosOnboardingPrefs() }
    single<PlatformShare> { IosPlatformShare() }
    single { PushNotifications() }
}
