package se.atte.bragwise.domain

import kotlin.time.Instant

/**
 * Cloud-only friend: uid-keyed, dual-write mirrored under
 * `players/{uid}/private/social.friends`. Server source of truth.
 *
 * `displayName` is mutable (renames don't break keying);
 * `id` is the persistent identity (`uid`).
 */
sealed interface Friend {
    val id: String
    val displayName: String
}

data class CloudFriend(
    val player: Player,
    val since: Instant,
) : Friend {
    override val id: String get() = player.uid
    override val displayName: String get() = player.displayName
}
