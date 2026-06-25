package se.atte.bragwise.domain

/** A pending invitation paired with its resolved challenge, for rendering as a card. */
data class InviteCard(
    val challenge: Challenge,
    val invitedByUid: String,
)
