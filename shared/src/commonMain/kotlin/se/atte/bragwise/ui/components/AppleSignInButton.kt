package se.atte.bragwise.ui.components

import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier

/**
 * Apple's Human Interface Guidelines require the "Sign in with Apple" button
 * to use Apple's own glyph, drawn by the OS rather than a bundled asset. On
 * iOS this renders the real `ASAuthorizationAppleIDButton` via UIKit interop
 * (see `AppleSignInButton.ios.kt`); other platforms never show this button
 * (`supportsAppleSignIn` gates it in `SignInScreen`) but still need an
 * `actual` to compile.
 */
@Composable
expect fun AppleSignInButton(
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    enabled: Boolean = true,
    isDark: Boolean = false,
)
