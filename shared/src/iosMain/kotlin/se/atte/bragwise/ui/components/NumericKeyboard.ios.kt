package se.atte.bragwise.ui.components

import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.ui.ExperimentalComposeUiApi
import androidx.compose.ui.platform.LocalFocusManager
import androidx.compose.ui.text.input.PlatformImeOptions
import kotlinx.cinterop.ExperimentalForeignApi
import kotlinx.cinterop.useContents
import platform.CoreGraphics.CGRectMake
import platform.UIKit.UIAction
import platform.UIKit.UIBarButtonItem
import platform.UIKit.UIBarButtonSystemItem
import platform.UIKit.UIScreen
import platform.UIKit.UIToolbar
import platform.UIKit.UIView
import platform.UIKit.UIViewAutoresizingFlexibleWidth

/**
 * iOS numeric keypads (`UIKeyboardTypeNumberPad`) have no return key, so an
 * `inputAccessoryView` toolbar with a Done button is the only way to dismiss them.
 *
 * The toolbar is remembered because the resulting [PlatformImeOptions] holds the UIView by
 * reference and participates in `KeyboardOptions.equals`; a new instance per recomposition
 * would restart the input session. The focus manager is read through
 * [rememberUpdatedState] so the remembered toolbar always calls the current one.
 */
@OptIn(ExperimentalComposeUiApi::class)
@Composable
actual fun rememberKeyboardDoneAccessory(doneLabel: String): PlatformImeOptions? {
    val focusManager = rememberUpdatedState(LocalFocusManager.current)
    return remember(doneLabel) {
        PlatformImeOptions {
            inputAccessoryView(doneToolbar(doneLabel) { focusManager.value.clearFocus() })
        }
    }
}

/**
 * Built with an explicit frame rather than `sizeToFit()`: a toolbar with no superview and no
 * items yet sizes to zero width, which would render the Done button invisible and leave the
 * keypad with no dismiss affordance at all. UIKit stretches the accessory view to the
 * keyboard's width via [UIViewAutoresizingFlexibleWidth]; the initial width only has to be
 * non-zero, and 44pt is the standard toolbar height.
 */
@OptIn(ExperimentalForeignApi::class)
private fun doneToolbar(title: String, onDone: () -> Unit): UIView =
    UIToolbar(
        frame = CGRectMake(
            x = 0.0,
            y = 0.0,
            width = UIScreen.mainScreen.bounds.useContents { size.width },
            height = 44.0,
        ),
    ).apply {
        autoresizingMask = UIViewAutoresizingFlexibleWidth
        setItems(
            listOf(
                UIBarButtonItem(
                    barButtonSystemItem = UIBarButtonSystemItem.UIBarButtonSystemItemFlexibleSpace,
                    target = null,
                    action = null,
                ),
                UIBarButtonItem(
                    primaryAction = UIAction.actionWithTitle(
                        title = title,
                        image = null,
                        identifier = null,
                        handler = { _ -> onDone() },
                    ),
                ),
            ),
            animated = false,
        )
    }
