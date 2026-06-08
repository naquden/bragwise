package se.atte.bragwise.data

import dev.gitlive.firebase.firestore.DocumentSnapshot
import dev.gitlive.firebase.firestore.Timestamp
import kotlinx.serialization.Serializable
import kotlin.time.Clock
import kotlin.time.Instant
import se.atte.bragwise.domain.Bet
import se.atte.bragwise.domain.BetOption
import se.atte.bragwise.domain.Challenge
import se.atte.bragwise.domain.ChallengeStatus
import se.atte.bragwise.domain.CloudFriend
import se.atte.bragwise.domain.FriendRequests
import se.atte.bragwise.domain.GENERIC_DISPLAY_NAME
import se.atte.bragwise.domain.HeadToHead
import se.atte.bragwise.domain.Invitation
import se.atte.bragwise.domain.OptionType
import se.atte.bragwise.domain.ParticipantInfo
import se.atte.bragwise.domain.Player
import se.atte.bragwise.domain.PredictionPayload
import se.atte.bragwise.domain.PublicProfile
import se.atte.bragwise.domain.Visibility

// ── Serializable DTOs ────────────────────────────────────────────────────────
// Used so gitlive's typed decoder can deserialize nested Firestore structures.

@Serializable
private data class BetOptionDto(
    val id: String,
    val label: String = "",
    val countryCode: String? = null,
)

@Serializable
private data class BetDto(
    val kind: String,
    val id: String,
    val title: String = "",
    val optionType: String = "NONE",
    val options: List<BetOptionDto> = emptyList(),
    val topN: Int = 1,
)

@Serializable
private data class PredictionPayloadDto(
    val kind: String,
    val optionId: String? = null,
    val orderedOptionIds: List<String> = emptyList(),
    val value: Boolean? = null,
)

@Serializable
private data class LeaderboardDto(
    val entries: Map<String, Int> = emptyMap(),
)

@Serializable
private data class ParticipantDto(
    val displayName: String = "",
    val avatarSeed: String = "",
)

// ── Primitive helpers ────────────────────────────────────────────────────────

internal fun Timestamp.toInstant(): Instant =
    Instant.fromEpochSeconds(seconds, nanoseconds.toLong())

internal fun DocumentSnapshot.timestampOrNull(field: String): Instant? = runCatching {
    get<Timestamp>(field).toInstant()
}.getOrNull()

internal fun DocumentSnapshot.strOrNull(field: String): String? = runCatching {
    get<String>(field)
}.getOrNull()

internal fun DocumentSnapshot.longOrNull(field: String): Long? = runCatching {
    get<Long>(field)
}.getOrNull()

internal fun DocumentSnapshot.boolOrNull(field: String): Boolean? = runCatching {
    get<Boolean>(field)
}.getOrNull()

// ── Challenge ────────────────────────────────────────────────────────────────

internal fun DocumentSnapshot.toChallenge(): Challenge {
    val bets = runCatching {
        get<List<BetDto>>(field = "bets").map { it.toDomain() }
    }.onFailure { println("BRAGWISE_BETS_ERR ${it.message}") }.getOrElse { emptyList() }

    val results: Map<String, PredictionPayload>? = runCatching {
        get<Map<String, PredictionPayloadDto>>(field = "results")
            .mapNotNull { (betId, dto) -> dto.toDomain()?.let { betId to it } }
            .toMap()
    }.getOrNull()

    val leaderboard: Map<String, Int>? = runCatching {
        get<Map<String, Int>>(field = "leaderboard")
    }.getOrNull()

    val participants: List<ParticipantInfo> = runCatching {
        get<Map<String, ParticipantDto>>(field = "participants")
            .map { (uid, dto) -> ParticipantInfo(uid = uid, displayName = dto.displayName, avatarSeed = dto.avatarSeed) }
    }.getOrElse { emptyList() }

    return Challenge(
        id = id,
        title = strOrNull("title") ?: "",
        description = strOrNull("description") ?: "",
        category = strOrNull("category") ?: "",
        visibility = runCatching {
            Visibility.valueOf(strOrNull("visibility") ?: "FRIENDS")
        }.getOrDefault(Visibility.FRIENDS),
        createdBy = strOrNull("createdBy") ?: "",
        createdAt = timestampOrNull("createdAt") ?: Instant.DISTANT_PAST,
        locksAt = timestampOrNull("locksAt"),
        resultsPostedAt = timestampOrNull("resultsPostedAt"),
        status = run {
            val storedStatus = runCatching {
                ChallengeStatus.valueOf(strOrNull("status") ?: "DRAFT")
            }.getOrDefault(ChallengeStatus.DRAFT)
            val locksAtInstant = timestampOrNull("locksAt")
            val resultsPostedAtInstant = timestampOrNull("resultsPostedAt")
            if (storedStatus == ChallengeStatus.OPEN &&
                locksAtInstant != null &&
                resultsPostedAtInstant == null &&
                Clock.System.now() >= locksAtInstant
            ) {
                ChallengeStatus.LOCKED
            } else {
                storedStatus
            }
        },
        joinedCount = longOrNull("joinedCount")?.toInt() ?: 0,
        promoted = boolOrNull("promoted") ?: false,
        trusted = boolOrNull("trusted") ?: false,
        bets = bets,
        results = results,
        leaderboard = leaderboard,
        betsVisible = boolOrNull("betsVisible") ?: false,
        participants = participants,
    )
}

private fun BetDto.toDomain(): Bet {
    val opts = options.map { BetOption(id = it.id, label = it.label, countryCode = it.countryCode) }
    val ot = runCatching { OptionType.valueOf(optionType) }.getOrDefault(OptionType.NONE)
    return when (kind) {
        "SINGLE_PICK" -> Bet.SinglePick(id = id, title = title, optionType = ot, options = opts)
        "RANKING" -> Bet.Ranking(id = id, title = title, optionType = ot, topN = topN, options = opts)
        else -> Bet.BooleanProp(id = id, title = title)
    }
}

private fun PredictionPayloadDto.toDomain(): PredictionPayload? = when (kind) {
    "SINGLE_PICK" -> optionId?.let { PredictionPayload.SinglePick(optionId = it) }
    "RANKING" -> PredictionPayload.Ranking(orderedOptionIds = orderedOptionIds)
    "BOOLEAN_PROP" -> value?.let { PredictionPayload.BooleanProp(value = it) }
    else -> null
}

// ── Predictions (player sub-doc) ─────────────────────────────────────────────

internal fun DocumentSnapshot.toPredictionsMap(): Map<String, PredictionPayload> = runCatching {
    get<Map<String, PredictionPayloadDto>>(field = "predictions")
        .mapNotNull { (betId, dto) -> dto.toDomain()?.let { betId to it } }
        .toMap()
}.getOrElse { emptyMap() }

// ── Leaderboard ──────────────────────────────────────────────────────────────

internal fun DocumentSnapshot.toLeaderboardMap(): Map<String, Int>? = runCatching {
    get<Map<String, Int>>(field = "leaderboard")
}.getOrNull()

// ── PredictionPayload serialization (write path) ─────────────────────────────

internal fun PredictionPayload.toMap(): Map<String, Any?> = when (this) {
    is PredictionPayload.SinglePick -> mapOf("kind" to "SINGLE_PICK", "optionId" to optionId)
    is PredictionPayload.Ranking -> mapOf("kind" to "RANKING", "orderedOptionIds" to orderedOptionIds)
    is PredictionPayload.BooleanProp -> mapOf("kind" to "BOOLEAN_PROP", "value" to value)
}

// ── Other document types ─────────────────────────────────────────────────────

internal fun DocumentSnapshot.toPublicProfile(): PublicProfile = PublicProfile(
    uid = id,
    username = strOrNull("handle") ?: "",
    displayName = strOrNull("displayName") ?: "",
    avatarSeed = strOrNull("avatarSeed") ?: "",
)

internal fun DocumentSnapshot.toPlayer(): Player = Player(
    uid = id,
    username = strOrNull("handle") ?: "",
    displayName = strOrNull("displayName") ?: "",
    avatarSeed = strOrNull("avatarSeed") ?: "",
    createdAt = timestampOrNull("createdAt") ?: Instant.DISTANT_PAST,
)

@Serializable
private data class SocialDocDto(
    val friends: Map<String, @Serializable(with = dev.gitlive.firebase.firestore.TimestampSerializer::class) Timestamp> = emptyMap(),
)

internal fun DocumentSnapshot.toCloudFriends(): List<CloudFriend> = runCatching {
    data<SocialDocDto>().friends.map { (uid, ts) ->
        val since = ts.toInstant()
        CloudFriend(
            player = Player(uid = uid, username = "", displayName = GENERIC_DISPLAY_NAME, avatarSeed = "", createdAt = since),
            since = since,
        )
    }
}.getOrElse { emptyList() }

@Serializable
private data class FriendRequestsDto(
    val requestsIn: Map<String, @Serializable(with = dev.gitlive.firebase.firestore.TimestampSerializer::class) Timestamp> = emptyMap(),
    val requestsOut: Map<String, @Serializable(with = dev.gitlive.firebase.firestore.TimestampSerializer::class) Timestamp> = emptyMap(),
)

internal fun DocumentSnapshot.toFriendRequests(): FriendRequests = runCatching {
    val dto = data<FriendRequestsDto>()
    FriendRequests(
        incoming = dto.requestsIn.mapValues { it.value.toInstant() },
        outgoing = dto.requestsOut.mapValues { it.value.toInstant() },
    )
}.getOrElse { FriendRequests(incoming = emptyMap(), outgoing = emptyMap()) }

@Serializable
private data class HeadToHeadDto(
    val vs: Map<String, RecordDto> = emptyMap(),
) {
    @Serializable
    data class RecordDto(val wins: Int = 0, val losses: Int = 0, val ties: Int = 0)
}

internal fun DocumentSnapshot.toHeadToHead(): HeadToHead = runCatching {
    val dto = data<HeadToHeadDto>()
    HeadToHead(vs = dto.vs.mapValues { (_, r) -> HeadToHead.Record(wins = r.wins, losses = r.losses, ties = r.ties) })
}.getOrElse { HeadToHead(emptyMap()) }

internal fun DocumentSnapshot.toInvitation(challengeId: String): Invitation = Invitation(
    challengeId = challengeId,
    invitedUid = strOrNull("invitedUid") ?: id,
    invitedBy = strOrNull("invitedBy") ?: "SYSTEM",
    invitedAt = timestampOrNull("invitedAt") ?: Instant.DISTANT_PAST,
)
