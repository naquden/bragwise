package se.atte.bragwise.ui.nav

sealed interface DeepLink {
    data class Challenge(val id: String) : DeepLink
    data class Result(val challengeId: String) : DeepLink
    data object FriendRequests : DeepLink
    data object Friends : DeepLink
}

fun parseDeepLink(url: String): DeepLink? {
    val slashIdx = url.indexOf('/', url.indexOf("://") + 3)
    if (slashIdx < 0) return null
    val path = url.substring(slashIdx)
    Regex("^/c/([a-zA-Z0-9_-]+)/results$").find(path)?.let {
        return DeepLink.Result(it.groupValues[1])
    }
    Regex("^/c/([a-zA-Z0-9_-]+)$").find(path)?.let {
        return DeepLink.Challenge(it.groupValues[1])
    }
    if (path == "/requests") return DeepLink.FriendRequests
    if (path == "/friends") return DeepLink.Friends
    return null
}
