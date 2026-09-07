package se.atte.bragwise.ui.components

import androidx.compose.runtime.Composable
import androidx.compose.ui.text.input.PlatformImeOptions

/** Web has no software-keyboard accessory bar to install. */
@Composable
actual fun rememberKeyboardDoneAccessory(doneLabel: String): PlatformImeOptions? = null
