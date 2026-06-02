package se.atte.bragwise.theme

import androidx.compose.runtime.staticCompositionLocalOf
import androidx.compose.ui.graphics.Color

/**
 * Per-section background + tinted-card palette for the Challenges screen.
 * Sits alongside the MD3 scheme — used to group related cards inside one
 * colored container so the screen background never bleeds between cards.
 */
data class SectionColors(
    val mineBg: Color, val mineCard: Color, val onMine: Color,
    val promotedBg: Color, val promotedCard: Color, val onPromoted: Color,
    val friendsBg: Color, val friendsCard: Color, val onFriends: Color,
    val invitesBg: Color, val invitesCard: Color, val onInvites: Color,
    val historyBg: Color, val historyCard: Color, val onHistory: Color,
)

val LightSectionColors = SectionColors(
    mineBg = Color(0xFFE8DFFF), mineCard = Color(0xFFF3EEFF), onMine = Color(0xFF2A1B5C),
    promotedBg = Color(0xFFFFE4C2), promotedCard = Color(0xFFFFF1DC), onPromoted = Color(0xFF5A3A00),
    friendsBg = Color(0xFFCFEFF7), friendsCard = Color(0xFFE6F7FB), onFriends = Color(0xFF003A52),
    invitesBg = Color(0xFFD4F5A8), invitesCard = Color(0xFFE8FBC9), onInvites = Color(0xFF1A4200),
    historyBg = Color(0xFFEAEAEA), historyCard = Color(0xFFF5F5F5), onHistory = Color(0xFF3A3A3A),
)

val DarkSectionColors = SectionColors(
    mineBg = Color(0xFF2E2257), mineCard = Color(0xFF3D2E70), onMine = Color(0xFFE8DFFF),
    promotedBg = Color(0xFF4A3300), promotedCard = Color(0xFF5C4200), onPromoted = Color(0xFFFFE4C2),
    friendsBg = Color(0xFF003A52), friendsCard = Color(0xFF00536F), onFriends = Color(0xFFCFEFF7),
    invitesBg = Color(0xFF265E00), invitesCard = Color(0xFF347C00), onInvites = Color(0xFFD4F5A8),
    historyBg = Color(0xFF2A2A2A), historyCard = Color(0xFF383838), onHistory = Color(0xFFD0D0D0),
)

val LocalSectionColors = staticCompositionLocalOf { LightSectionColors }
