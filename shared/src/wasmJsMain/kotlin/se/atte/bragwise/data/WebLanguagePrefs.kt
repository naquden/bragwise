package se.atte.bragwise.data

import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow

private const val KEY_LANGUAGE = "bragwise.language"

class WebLanguagePrefs : LanguagePrefs {
    private val _language = MutableStateFlow(readLanguage())
    override val language: StateFlow<AppLanguage> = _language.asStateFlow()

    override fun set(language: AppLanguage) {
        lsSet(KEY_LANGUAGE, language.name)
        _language.value = language
    }

    private fun readLanguage(): AppLanguage {
        val name = lsGet(KEY_LANGUAGE) ?: return AppLanguage.System
        return runCatching { AppLanguage.valueOf(name) }.getOrDefault(AppLanguage.System)
    }
}
