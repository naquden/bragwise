package se.atte.bragwise.ui

import androidx.compose.runtime.Composable
import androidx.compose.runtime.ProvidedValue
import androidx.compose.ui.platform.LocalConfiguration
import java.util.Locale

actual object LocalAppLocale {
    actual val current: String
        @Composable get() = Locale.getDefault().toString()

    @Composable
    actual infix fun provides(value: String?): ProvidedValue<*> {
        // Android applies locale via LocaleManager.applicationLocales in
        // AndroidLanguagePrefs, which triggers a config recreate. No-op here.
        return LocalConfiguration.provides(LocalConfiguration.current)
    }
}
