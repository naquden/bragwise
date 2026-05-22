package se.atte.bragwise.data

import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import platform.Foundation.NSUserDefaults
import se.atte.bragwise.theme.ThemeMode

private const val KEY_MODE = "bragwise.theme.mode"

class IosThemePrefs : ThemePrefs {
    private val defaults = NSUserDefaults.standardUserDefaults
    private val _mode = MutableStateFlow(read())
    override val mode: StateFlow<ThemeMode> = _mode.asStateFlow()

    override fun set(mode: ThemeMode) {
        defaults.setObject(mode.name, KEY_MODE)
        _mode.value = mode
    }

    private fun read(): ThemeMode {
        val name = defaults.stringForKey(KEY_MODE) ?: return ThemeMode.System
        return runCatching { ThemeMode.valueOf(name) }.getOrDefault(ThemeMode.System)
    }
}
