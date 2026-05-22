package se.atte.bragwise.data

import kotlinx.coroutines.flow.StateFlow
import se.atte.bragwise.theme.ThemeMode

interface ThemePrefs {
    val mode: StateFlow<ThemeMode>
    fun set(mode: ThemeMode)
}
