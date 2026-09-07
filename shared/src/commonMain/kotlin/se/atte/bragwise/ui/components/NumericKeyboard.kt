package se.atte.bragwise.ui.components

import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.input.PlatformImeOptions
import bragwise.shared.generated.resources.Res
import bragwise.shared.generated.resources.keyboard_done
import org.jetbrains.compose.resources.stringResource

/**
 * [KeyboardOptions] for integer input. Use this instead of
 * `KeyboardOptions(keyboardType = KeyboardType.Number)` everywhere: on iOS the numeric
 * keypad has no return key, so without an accessory bar the keyboard cannot be dismissed.
 *
 * The result is remembered so the returned [KeyboardOptions] keeps referential stability —
 * [KeyboardOptions.equals] compares [KeyboardOptions.platformImeOptions], which holds a
 * platform view by reference, and a fresh instance every recomposition restarts the text
 * input session.
 */
@Composable
fun rememberNumericKeyboardOptions(): KeyboardOptions {
    val accessory = rememberKeyboardDoneAccessory(stringResource(Res.string.keyboard_done))
    return remember(accessory) {
        KeyboardOptions(
            keyboardType = KeyboardType.Number,
            platformImeOptions = accessory,
        )
    }
}

/**
 * Platform IME options carrying a "Done" accessory bar above the software keyboard, for
 * keyboard layouts that have no return key. Returns null on platforms that need no such
 * affordance (Android, web).
 */
@Composable
expect fun rememberKeyboardDoneAccessory(doneLabel: String): PlatformImeOptions?
