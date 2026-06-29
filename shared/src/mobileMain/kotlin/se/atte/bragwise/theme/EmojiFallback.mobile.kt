package se.atte.bragwise.theme

import androidx.compose.ui.text.font.FontFamily

// Android and iOS have native emoji support; no custom fallback needed.
actual suspend fun emojiFallbackFamily(): FontFamily? = null
