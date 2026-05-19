package se.atte.bragwise.ui

import androidx.compose.material3.SnackbarHostState
import androidx.compose.runtime.staticCompositionLocalOf

val LocalSnackbarHost = staticCompositionLocalOf<SnackbarHostState> {
    error("LocalSnackbarHost not provided")
}
