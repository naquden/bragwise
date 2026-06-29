package se.atte.bragwise.ui

import androidx.compose.runtime.Composable
import androidx.compose.runtime.ProvidedValue
import androidx.compose.runtime.staticCompositionLocalOf

@JsFun("(tag) => { Object.defineProperty(window.navigator, 'languages', { get: function() { return [tag]; }, configurable: true }); }")
private external fun overrideNavigatorLanguages(tag: String)

@JsFun("() => window.navigator.language")
private external fun getNavigatorLanguage(): String

actual object LocalAppLocale {
    private val default: String = getNavigatorLanguage()
    private val local = staticCompositionLocalOf { default }

    actual val current: String
        @Composable get() = local.current

    @Composable
    actual infix fun provides(value: String?): ProvidedValue<*> {
        val new = value ?: default
        overrideNavigatorLanguages(new)
        return local.provides(new)
    }
}
