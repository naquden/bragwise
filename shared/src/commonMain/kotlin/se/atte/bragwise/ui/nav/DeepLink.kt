package se.atte.bragwise.ui.nav

sealed interface DeepLink {
    data class Challenge(val id: String) : DeepLink
}

fun parseDeepLink(url: String): DeepLink? {
    val slashIdx = url.indexOf('/', url.indexOf("://") + 3)
    if (slashIdx < 0) return null
    val path = url.substring(slashIdx)
    val challengeMatch = Regex("^/c/([a-zA-Z0-9_-]+)$").find(path)
    if (challengeMatch != null) return DeepLink.Challenge(challengeMatch.groupValues[1])
    return null
}
