package se.atte.bragwise.ui.components

import androidx.compose.runtime.Composable
import androidx.compose.ui.text.input.PlatformImeOptions

/** Android soft keyboards always offer a back/return affordance — no accessory bar needed. */
@Composable
actual fun rememberKeyboardDoneAccessory(doneLabel: String): PlatformImeOptions? = null
