package se.atte.bragwise.data

import android.app.LocaleManager
import android.content.Context
import android.os.LocaleList
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow

class AndroidLanguagePrefs(private val context: Context) : LanguagePrefs {
    private val _language = MutableStateFlow(read())
    override val language: StateFlow<AppLanguage> = _language.asStateFlow()

    override fun set(language: AppLanguage) {
        val localeManager = context.getSystemService(LocaleManager::class.java)
        localeManager.applicationLocales = if (language == AppLanguage.System) {
            LocaleList.getEmptyLocaleList()
        } else {
            LocaleList.forLanguageTags(language.tag)
        }
        _language.value = language
    }

    private fun read(): AppLanguage {
        val localeManager = context.getSystemService(LocaleManager::class.java)
        val tag = localeManager.applicationLocales.get(0)?.toLanguageTag() ?: return AppLanguage.System
        return AppLanguage.entries.firstOrNull { it.tag == tag }
            ?: AppLanguage.entries.firstOrNull { tag.startsWith(it.tag) }
            ?: AppLanguage.System
    }
}
