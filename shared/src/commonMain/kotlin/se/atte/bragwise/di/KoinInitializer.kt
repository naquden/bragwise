package se.atte.bragwise.di

import org.koin.core.KoinApplication
import org.koin.core.context.startKoin
import org.koin.dsl.KoinAppDeclaration

fun initKoin(useMock: Boolean = false, appDeclaration: KoinAppDeclaration = {}) {
    startKoin {
        appDeclaration()
        val repoModule = if (useMock) mockDataModule else dataModule
        modules(platformModule, repoModule, viewModelModule)
    }
}

/** Exposed to Swift as `KoinInitializerKt.doInitKoin()`. */
fun doInitKoin() = initKoin()
