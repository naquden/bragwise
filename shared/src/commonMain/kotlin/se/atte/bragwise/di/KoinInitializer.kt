package se.atte.bragwise.di

import org.koin.core.KoinApplication
import org.koin.core.context.startKoin
import org.koin.dsl.KoinAppDeclaration

fun initKoin(appDeclaration: KoinAppDeclaration = {}) {
    startKoin {
        appDeclaration()
        modules(platformModule, dataModule, viewModelModule)
    }
}

/** Exposed to Swift as `KoinInitializerKt.doInitKoin()`. */
fun doInitKoin() = initKoin()
