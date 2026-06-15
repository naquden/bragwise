package se.atte.bragwise.data

import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import platform.Foundation.NSUserDefaults

private const val KEY_LANGUAGE = "bragwise.language"

class IosLanguagePrefs : LanguagePrefs {
    private val defaults = NSUserDefaults.standardUserDefaults
    private val _language = MutableStateFlow(read())
    override val language: StateFlow<AppLanguage> = _language.asStateFlow()

    override fun set(language: AppLanguage) {
        defaults.setObject(language.name, KEY_LANGUAGE)
        _language.value = language
    }

    private fun read(): AppLanguage {
        val name = defaults.stringForKey(KEY_LANGUAGE) ?: return AppLanguage.System
        return runCatching { AppLanguage.valueOf(name) }.getOrDefault(AppLanguage.System)
    }
}
