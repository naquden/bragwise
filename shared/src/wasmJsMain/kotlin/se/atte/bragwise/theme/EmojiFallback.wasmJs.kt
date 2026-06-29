package se.atte.bragwise.theme

import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.platform.Font
import bragwise.shared.generated.resources.Res

actual suspend fun emojiFallbackFamily(): FontFamily? {
    val bytes = Res.readBytes("font/noto_color_emoji.ttf")
    return FontFamily(Font(identity = "NotoColorEmoji", data = bytes))
}
