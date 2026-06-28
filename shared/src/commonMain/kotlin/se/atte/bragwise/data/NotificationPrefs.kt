package se.atte.bragwise.data

data class NotificationPrefs(
    val master: Boolean = true,
    val social: Boolean = true,
    val results: Boolean = true,
    val participations: Boolean = true,
    val invites: Boolean = true,
) {
    companion object {
        val DEFAULT = NotificationPrefs()
    }
}
