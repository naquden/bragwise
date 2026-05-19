package se.atte.bragwise.di

import org.koin.android.ext.koin.androidContext
import org.koin.core.module.Module
import org.koin.dsl.module
import se.atte.bragwise.data.AndroidAuthLocalDataSource
import se.atte.bragwise.data.AuthLocalDataSource
import se.atte.bragwise.platform.AndroidPlatformShare
import se.atte.bragwise.platform.PlatformShare

actual val platformModule: Module = module {
    single<AuthLocalDataSource> { AndroidAuthLocalDataSource(context = androidContext()) }
    single<PlatformShare> { AndroidPlatformShare(context = androidContext()) }
}
