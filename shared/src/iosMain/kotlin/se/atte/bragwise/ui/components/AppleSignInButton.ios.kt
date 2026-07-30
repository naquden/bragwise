package se.atte.bragwise.ui.components

import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.runtime.Composable
import androidx.compose.runtime.key
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import androidx.compose.ui.viewinterop.UIKitView
import platform.AuthenticationServices.ASAuthorizationAppleIDButton
import platform.AuthenticationServices.ASAuthorizationAppleIDButtonStyle
import platform.AuthenticationServices.ASAuthorizationAppleIDButtonTypeSignIn
import platform.UIKit.UIAction
import platform.UIKit.UIControlEventTouchUpInside

/**
 * Renders Apple's own `ASAuthorizationAppleIDButton` — the system draws the
 * real Apple glyph, so unlike the other platforms there's no bundled asset
 * to keep in sync with Apple's Human Interface Guidelines.
 */
@Composable
actual fun AppleSignInButton(
    onClick: () -> Unit,
    modifier: Modifier,
    enabled: Boolean,
    isDark: Boolean,
) {
    // ASAuthorizationAppleIDButtonStyle is baked into the button at init time
    // (no setter exists) — `key(isDark)` forces UIKitView to recreate the
    // button via `factory` when the app's theme toggles at runtime.
    key(isDark) {
        UIKitView(
            factory = {
                ASAuthorizationAppleIDButton.buttonWithType(
                    type = ASAuthorizationAppleIDButtonTypeSignIn,
                    style = if (isDark) {
                        ASAuthorizationAppleIDButtonStyle.ASAuthorizationAppleIDButtonStyleWhite
                    } else {
                        ASAuthorizationAppleIDButtonStyle.ASAuthorizationAppleIDButtonStyleBlack
                    },
                ).apply {
                    addAction(
                        UIAction.actionWithHandler { _ -> onClick() },
                        forControlEvents = UIControlEventTouchUpInside,
                    )
                }
            },
            modifier = modifier.fillMaxWidth().heightIn(min = 44.dp),
            update = { button -> button.enabled = enabled },
        )
    }
}
