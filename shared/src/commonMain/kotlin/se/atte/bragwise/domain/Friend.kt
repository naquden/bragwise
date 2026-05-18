package se.atte.bragwise.domain

import kotlin.time.Instant

/**
 * Bragwise has two friend kinds.
 *
 *  - [CloudFriend] — uid-keyed, dual-write mirrored under
 *    `players/{uid}/private/social.friends`. Server source of truth.
 *  - [LocalFriend] — guest-only, on-device label with a stable
 *    `localId` UUID. Never leaves the device. On sign-up the user can
 *    map each local friend to a cloud handle via OB-06; the local row
 *    is then deleted and replaced by a real `sendFriendRequest`.
 *
 * `displayName` is mutable for both kinds (renames don't break keying);
 * `id` is the persistent identity (`uid` for cloud, `localId` for local).
 */
sealed interface Friend {
    val id: String
    val displayName: String
    val avatarSeed: String
}

data class CloudFriend(
    val player: Player,
    val since: Instant,
) : Friend {
    override val id: String get() = player.uid
    override val displayName: String get() = player.displayName
    override val avatarSeed: String get() = player.avatarSeed
}

data class LocalFriend(
    val localId: String,
    override val displayName: String,
    override val avatarSeed: String,
    val addedAt: Instant,
) : Friend {
    override val id: String get() = localId
}
