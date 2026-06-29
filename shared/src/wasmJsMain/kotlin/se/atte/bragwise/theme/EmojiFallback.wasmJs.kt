package se.atte.bragwise.theme

import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.platform.Font
import bragwise.shared.generated.resources.Res

actual suspend fun webFallbackFamilies(): List<FontFamily> = listOf(
    FontFamily(Font(identity = "NotoColorEmoji", data = Res.readBytes("font/noto_color_emoji.ttf"))),
    FontFamily(Font(identity = "NotoSansDevanagari", data = Res.readBytes("font/noto_sans_devanagari_subset.ttf"))),
    FontFamily(Font(identity = "NotoSansSC", data = Res.readBytes("font/noto_sans_sc_subset.ttf"))),
)
