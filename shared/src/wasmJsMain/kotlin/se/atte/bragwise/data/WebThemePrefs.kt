package se.atte.bragwise.data

import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import se.atte.bragwise.theme.ThemeMode

private const val KEY_THEME_MODE = "bragwise.theme.mode"

class WebThemePrefs : ThemePrefs {
    private val _mode = MutableStateFlow(readMode())
    override val mode: StateFlow<ThemeMode> = _mode.asStateFlow()

    override fun set(mode: ThemeMode) {
        lsSet(KEY_THEME_MODE, mode.name)
        _mode.value = mode
    }

    private fun readMode(): ThemeMode {
        val name = lsGet(KEY_THEME_MODE) ?: return ThemeMode.System
        return runCatching { ThemeMode.valueOf(name) }.getOrDefault(ThemeMode.System)
    }
}
