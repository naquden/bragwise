package se.atte.bragwise.data

import dev.gitlive.firebase.firestore.DocumentSnapshot
import dev.gitlive.firebase.firestore.Timestamp
import kotlin.time.Instant
import se.atte.bragwise.domain.Bet
import se.atte.bragwise.domain.BetOption
import se.atte.bragwise.domain.Challenge
import se.atte.bragwise.domain.ChallengeStatus
import se.atte.bragwise.domain.CloudFriend
import se.atte.bragwise.domain.FriendRequests
import se.atte.bragwise.domain.HeadToHead
import se.atte.bragwise.domain.Invitation
import se.atte.bragwise.domain.OptionType
import se.atte.bragwise.domain.Player
import se.atte.bragwise.domain.PredictionPayload
import se.atte.bragwise.domain.PublicProfile
import se.atte.bragwise.domain.Visibility

internal fun Timestamp.toInstant(): Instant =
    Instant.fromEpochSeconds(seconds, nanoseconds.toLong())

/** Safe typed field read — returns null on absent/null/type-mismatch. */
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

/**
 * Returns the raw platform value for a field, suitable for casting to
 * List<*> or Map<*,*>. Uses Any? to bypass GitLive's typed decoder so
 * complex nested structures (bets, results, leaderboard) pass through as
 * the platform's native Java/ObjC types.
 */
@Suppress("UNCHECKED_CAST")
internal fun DocumentSnapshot.rawOrNull(field: String): Any? = runCatching {
    get<Any?>(field)
}.getOrNull()

@Suppress("UNCHECKED_CAST")
internal fun DocumentSnapshot.toChallenge(): Challenge {
    val rawBets = (rawOrNull("bets") as? List<*>)
        ?.filterIsInstance<Map<String, Any?>>()
        ?: emptyList()
    val rawResults = rawOrNull("results") as? Map<String, Any?>
    val rawLeaderboard = rawOrNull("leaderboard") as? Map<String, Any?>

    return Challenge(
        id = strOrNull("id") ?: id,
        title = strOrNull("title") ?: "",
        description = strOrNull("description") ?: "",
        category = strOrNull("category") ?: "",
        visibility = runCatching {
            Visibility.valueOf(strOrNull("visibility") ?: "FRIENDS")
        }.getOrDefault(Visibility.FRIENDS),
        createdBy = strOrNull("createdBy") ?: "",
        createdAt = timestampOrNull("createdAt") ?: Instant.DISTANT_PAST,
        locksAt = timestampOrNull("locksAt") ?: Instant.DISTANT_FUTURE,
        resultsPostedAt = timestampOrNull("resultsPostedAt"),
        status = runCatching {
            ChallengeStatus.valueOf(strOrNull("status") ?: "DRAFT")
        }.getOrDefault(ChallengeStatus.DRAFT),
        joinedCount = longOrNull("joinedCount")?.toInt() ?: 0,
        promoted = boolOrNull("promoted") ?: false,
        trusted = boolOrNull("trusted") ?: false,
        bets = rawBets.mapNotNull { it.toBet() },
        results = rawResults?.entries
            ?.mapNotNull { (betId, v) ->
                (v as? Map<String, Any?>)?.toPredictionPayload()?.let { betId to it }
            }?.toMap(),
        leaderboard = rawLeaderboard?.entries
            ?.mapNotNull { (uid, pts) ->
                val p = (pts as? Long)?.toInt() ?: (pts as? Int) ?: return@mapNotNull null
                uid to p
            }?.toMap(),
    )
}

@Suppress("UNCHECKED_CAST")
private fun Map<String, Any?>.toBet(): Bet? {
    val id = this["id"] as? String ?: return null
    val title = this["title"] as? String ?: ""
    val optionType = runCatching {
        OptionType.valueOf(this["optionType"] as? String ?: "NONE")
    }.getOrDefault(OptionType.NONE)
    return when (this["kind"] as? String) {
        "SINGLE_PICK" -> Bet.SinglePick(
            id = id, title = title, optionType = optionType,
            options = decodeOptions(),
        )
        "RANKING" -> Bet.Ranking(
            id = id, title = title, optionType = optionType,
            topN = (this["topN"] as? Long)?.toInt() ?: (this["topN"] as? Int) ?: 1,
            options = decodeOptions(),
        )
        "BOOLEAN_PROP" -> Bet.BooleanProp(id = id, title = title)
        else -> null
    }
}

@Suppress("UNCHECKED_CAST")
private fun Map<String, Any?>.decodeOptions(): List<BetOption> =
    (this["options"] as? List<*>)
        ?.filterIsInstance<Map<String, Any?>>()
        ?.mapNotNull { opt ->
            val optId = opt["id"] as? String ?: return@mapNotNull null
            BetOption(
                id = optId,
                label = opt["label"] as? String ?: "",
                countryCode = opt["countryCode"] as? String,
            )
        } ?: emptyList()

@Suppress("UNCHECKED_CAST")
internal fun Map<String, Any?>.toPredictionPayload(): PredictionPayload? =
    when (this["kind"] as? String) {
        "SINGLE_PICK" -> PredictionPayload.SinglePick(
            optionId = this["optionId"] as? String ?: return null,
        )
        "RANKING" -> PredictionPayload.Ranking(
            orderedOptionIds = (this["orderedOptionIds"] as? List<*>)
                ?.filterIsInstance<String>() ?: emptyList(),
        )
        "BOOLEAN_PROP" -> PredictionPayload.BooleanProp(
            value = this["value"] as? Boolean ?: return null,
        )
        else -> null
    }

internal fun PredictionPayload.toMap(): Map<String, Any?> = when (this) {
    is PredictionPayload.SinglePick -> mapOf("kind" to "SINGLE_PICK", "optionId" to optionId)
    is PredictionPayload.Ranking -> mapOf("kind" to "RANKING", "orderedOptionIds" to orderedOptionIds)
    is PredictionPayload.BooleanProp -> mapOf("kind" to "BOOLEAN_PROP", "value" to value)
}

internal fun DocumentSnapshot.toPublicProfile(): PublicProfile = PublicProfile(
    uid = id,
    handle = strOrNull("handle") ?: "",
    displayName = strOrNull("displayName") ?: "",
    avatarSeed = strOrNull("avatarSeed") ?: "",
)

internal fun DocumentSnapshot.toPlayer(): Player = Player(
    uid = id,
    handle = strOrNull("handle") ?: "",
    displayName = strOrNull("displayName") ?: "",
    avatarSeed = strOrNull("avatarSeed") ?: "",
    createdAt = timestampOrNull("createdAt") ?: Instant.DISTANT_PAST,
)

@Suppress("UNCHECKED_CAST")
internal fun DocumentSnapshot.toCloudFriends(): List<CloudFriend> {
    val friendsMap = rawOrNull("friends") as? Map<String, Any?> ?: return emptyList()
    return friendsMap.mapNotNull { (uid, since) ->
        // `since` is a platform Timestamp; convert via runCatching
        val sinceInstant = runCatching { (since as Timestamp).toInstant() }.getOrNull()
            ?: Instant.DISTANT_PAST
        CloudFriend(
            player = Player(
                uid = uid,
                handle = "",
                displayName = uid,
                avatarSeed = uid,
                createdAt = sinceInstant,
            ),
            since = sinceInstant,
        )
    }
}

@Suppress("UNCHECKED_CAST")
internal fun DocumentSnapshot.toFriendRequests(): FriendRequests {
    val incoming = (rawOrNull("requestsIn") as? Map<String, Any?>)
        ?.mapNotNull { (uid, ts) ->
            runCatching { (ts as Timestamp).toInstant() }.getOrNull()?.let { uid to it }
        }?.toMap() ?: emptyMap()
    val outgoing = (rawOrNull("requestsOut") as? Map<String, Any?>)
        ?.mapNotNull { (uid, ts) ->
            runCatching { (ts as Timestamp).toInstant() }.getOrNull()?.let { uid to it }
        }?.toMap() ?: emptyMap()
    return FriendRequests(incoming = incoming, outgoing = outgoing)
}

@Suppress("UNCHECKED_CAST")
internal fun DocumentSnapshot.toHeadToHead(): HeadToHead {
    val vsMap = rawOrNull("vs") as? Map<String, Any?> ?: return HeadToHead(emptyMap())
    val records = vsMap.mapNotNull { (uid, raw) ->
        val m = raw as? Map<String, Any?> ?: return@mapNotNull null
        uid to HeadToHead.Record(
            wins = (m["wins"] as? Long)?.toInt() ?: 0,
            losses = (m["losses"] as? Long)?.toInt() ?: 0,
            ties = (m["ties"] as? Long)?.toInt() ?: 0,
        )
    }.toMap()
    return HeadToHead(vs = records)
}

@Suppress("UNCHECKED_CAST")
internal fun DocumentSnapshot.toInvitation(challengeId: String): Invitation = Invitation(
    challengeId = challengeId,
    invitedUid = strOrNull("invitedUid") ?: id,
    invitedBy = strOrNull("invitedBy") ?: "SYSTEM",
    invitedAt = timestampOrNull("invitedAt") ?: Instant.DISTANT_PAST,
)
