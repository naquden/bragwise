package se.atte.bragwise.data

import kotlinx.coroutines.flow.StateFlow

interface LanguagePrefs {
    val language: StateFlow<AppLanguage>
    fun set(language: AppLanguage)
}
