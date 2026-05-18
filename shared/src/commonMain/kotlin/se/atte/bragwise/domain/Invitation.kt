package se.atte.bragwise.domain

import kotlin.time.Instant

data class Invitation(
    val challengeId: String,
    val invitedUid: String,
    /** Creator uid, or "SYSTEM" for friend-accept auto-invites. */
    val invitedBy: String,
    val invitedAt: Instant,
)
