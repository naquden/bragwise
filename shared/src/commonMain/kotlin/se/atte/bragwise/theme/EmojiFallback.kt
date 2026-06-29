package se.atte.bragwise.theme

import androidx.compose.ui.text.font.FontFamily

/**
 * Font families to register as Skia resolver fallbacks on web — emoji plus scripts
 * (Devanagari, Han) not reliably covered by browser system fonts.  Empty on mobile
 * where native font fallback handles all scripts.
 *
 * On wasmJs, Font(Res.font.x) does not produce a LoadedFont, so preload() is a
 * no-op.  Fonts are loaded via Font(identity, ByteArray) (skiko-only) which creates
 * a real LoadedFont that the Skia cache registers correctly.
 */
expect suspend fun webFallbackFamilies(): List<FontFamily>
