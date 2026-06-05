package se.atte.bragwise.domain

import kotlin.time.Instant

data class Player(
    val uid: String,
    val username: String,
    val displayName: String,
    val avatarSeed: String,
    val createdAt: Instant,
)

data class PublicProfile(
    val uid: String,
    val username: String,
    val displayName: String,
    val avatarSeed: String,
)

data class HeadToHead(
    val vs: Map<String, Record>,
) {
    data class Record(val wins: Int, val losses: Int, val ties: Int)
}

data class FriendRequests(
    val incoming: Map<String, Instant>,
    val outgoing: Map<String, Instant>,
)
