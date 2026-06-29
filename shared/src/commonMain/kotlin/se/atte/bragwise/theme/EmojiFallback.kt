package se.atte.bragwise.theme

import androidx.compose.ui.text.font.FontFamily

/**
 * Returns a FontFamily built from raw font bytes that the Skia/wasmJs resolver
 * can use as an emoji fallback, or null on platforms that don't need it.
 *
 * The composable-resource Font() wrapper produced by Font(Res.font.x) is not
 * a LoadedFont on wasmJs, so preload() is a no-op and emojis tofu.  The fix
 * is to construct the Font via the skiko-only Font(identity, ByteArray) path
 * (androidx.compose.ui.text.platform.Font) which produces a LoadedFont that
 * the skia font cache registers correctly.
 */
expect suspend fun emojiFallbackFamily(): FontFamily?
