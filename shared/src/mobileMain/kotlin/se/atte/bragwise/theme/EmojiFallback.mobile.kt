package se.atte.bragwise.theme

import androidx.compose.ui.text.font.FontFamily

// Android and iOS use native font fallback for all scripts; no custom families needed.
actual suspend fun webFallbackFamilies(): List<FontFamily> = emptyList()
