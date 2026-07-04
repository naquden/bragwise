package se.atte.bragwise.data

import kotlinx.serialization.Serializable
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonArray
import kotlinx.serialization.json.JsonElement
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.contentOrNull
import kotlinx.serialization.json.decodeFromJsonElement
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive
import kotlin.time.Clock
import kotlin.time.Instant
import se.atte.bragwise.domain.Bet
import se.atte.bragwise.domain.BetOption
import se.atte.bragwise.domain.Challenge
import se.atte.bragwise.domain.ChallengeStatus
import se.atte.bragwise.domain.CloudFriend
import se.atte.bragwise.domain.FriendRequests
import se.atte.bragwise.domain.GENERIC_DISPLAY_NAME
import se.atte.bragwise.domain.GuessGranularity
import se.atte.bragwise.domain.HeadToHead
import se.atte.bragwise.domain.Invitation
import se.atte.bragwise.domain.OptionType
import se.atte.bragwise.domain.ParticipantInfo
import se.atte.bragwise.domain.Player
import se.atte.bragwise.domain.PredictionPayload
import se.atte.bragwise.domain.PublicProfile
import se.atte.bragwise.domain.Reaction
import se.atte.bragwise.domain.ScoringMode
import se.atte.bragwise.domain.Visibility

// ── JSON config ───────────────────────────────────────────────────────────────

internal val webJson = Json { ignoreUnknownKeys = true }

// ── Timestamp DTO ─────────────────────────────────────────────────────────────
// Firestore Timestamps serialize to JSON as { seconds: N, nanoseconds: N }.

@Serializable
internal data class TimestampDto(
    val seconds: Long = 0,
    val nanoseconds: Long = 0,
) {
    fun toInstant(): Instant = Instant.fromEpochSeconds(seconds, nanoseconds)
}

// ── Bet DTOs ──────────────────────────────────────────────────────────────────

@Serializable
internal data class BetOptionDto(
    val id: String,
    val label: String = "",
    val countryCode: String? = null,
)

@Serializable
internal data class BetDto(
    val kind: String,
    val id: String,
    val title: String = "",
    val optionType: String = "NONE",
    val options: List<BetOptionDto> = emptyList(),
    val topN: Int = 1,
    val granularity: String? = null,
    val closest: Boolean = true,
    val placement: Boolean = false,
    val line: Long? = null,
)

// ── Prediction payload DTO ────────────────────────────────────────────────────

@Serializable
internal data class PredictionPayloadDto(
    val kind: String,
    val optionId: String? = null,
    val orderedOptionIds: List<String> = emptyList(),
    val value: Boolean? = null,
    val guessValue: Long? = null,
    val selectedOptionIds: List<String> = emptyList(),
    val over: Boolean? = null,
    val actualValue: Long? = null,
)

// ── Participant DTO ───────────────────────────────────────────────────────────

@Serializable
internal data class ParticipantDto(
    val displayName: String = "",
    val avatarSeed: String = "",
)

// ── Challenge doc DTO ─────────────────────────────────────────────────────────

@Serializable
internal data class ChallengeDocDto(
    val title: String = "",
    val description: String = "",
    val category: String = "",
    val visibility: String = "FRIENDS",
    val createdBy: String = "",
    val createdAt: TimestampDto? = null,
    val locksAt: TimestampDto? = null,
    val resultsPostedAt: TimestampDto? = null,
    val status: String = "DRAFT",
    val joinedCount: Long = 0,
    val promoted: Boolean = false,
    val bets: List<BetDto> = emptyList(),
    val results: Map<String, PredictionPayloadDto>? = null,
    val leaderboard: Map<String, Int>? = null,
    val betsVisible: Boolean = false,
    val scoringMode: String? = null,
    val participants: Map<String, ParticipantDto> = emptyMap(),
)

// ── Social DTOs ───────────────────────────────────────────────────────────────

@Serializable
internal data class SocialDocDto(
    val friends: Map<String, TimestampDto> = emptyMap(),
    val requestsIn: Map<String, TimestampDto> = emptyMap(),
    val requestsOut: Map<String, TimestampDto> = emptyMap(),
)

@Serializable
internal data class HeadToHeadDto(
    val vs: Map<String, RecordDto> = emptyMap(),
) {
    @Serializable
    data class RecordDto(val wins: Int = 0, val losses: Int = 0, val ties: Int = 0)
}

// ── Preferences doc DTO ───────────────────────────────────────────────────────

@Serializable
internal data class PreferencesDocDto(
    val notifications: Boolean? = null,
    val categories: Map<String, Boolean> = emptyMap(),
)

// ── Converter helpers ─────────────────────────────────────────────────────────

internal fun BetDto.toDomain(): Bet {
    val opts = options.map { BetOption(id = it.id, label = it.label, countryCode = it.countryCode) }
    val ot = runCatching { OptionType.valueOf(optionType) }.getOrDefault(OptionType.NONE)
    return when (kind) {
        "SINGLE_PICK" -> Bet.SinglePick(id = id, title = title, optionType = ot, options = opts)
        "RANKING" -> Bet.Ranking(id = id, title = title, optionType = ot, topN = topN, options = opts)
        "GUESS" -> Bet.Guess(
            id = id,
            title = title,
            granularity = runCatching { GuessGranularity.valueOf(granularity ?: "TIME") }.getOrDefault(GuessGranularity.TIME),
            closest = closest,
            placement = placement,
        )
        "MULTI_SELECT" -> Bet.MultiSelect(id = id, title = title, optionType = ot, options = opts)
        "OVER_UNDER" -> Bet.OverUnder(id = id, title = title, line = line ?: 0L)
        else -> Bet.BooleanProp(id = id, title = title)
    }
}

internal fun PredictionPayloadDto.toDomain(): PredictionPayload? = when (kind) {
    "SINGLE_PICK" -> optionId?.let { PredictionPayload.SinglePick(optionId = it) }
    "RANKING" -> PredictionPayload.Ranking(orderedOptionIds = orderedOptionIds)
    "BOOLEAN_PROP" -> value?.let { PredictionPayload.BooleanProp(value = it) }
    "GUESS" -> guessValue?.let { PredictionPayload.Guess(value = it) }
    "MULTI_SELECT" -> PredictionPayload.MultiSelect(selectedOptionIds = selectedOptionIds)
    "OVER_UNDER" -> PredictionPayload.OverUnder(over = over, actualValue = actualValue)
    else -> null
}

internal fun ChallengeDocDto.toChallenge(id: String): Challenge {
    val bets = bets.map { it.toDomain() }
    val results: Map<String, PredictionPayload>? = this.results
        ?.mapNotNull { (betId, dto) -> dto.toDomain()?.let { betId to it } }
        ?.toMap()
    val participants: List<ParticipantInfo> = this.participants
        .map { (uid, dto) -> ParticipantInfo(uid = uid, displayName = dto.displayName, avatarSeed = dto.avatarSeed) }

    val locksAtInstant = locksAt?.toInstant()
    val resultsPostedAtInstant = resultsPostedAt?.toInstant()
    val storedStatus = runCatching { ChallengeStatus.valueOf(status) }.getOrDefault(ChallengeStatus.DRAFT)
    val effectiveStatus = if (
        storedStatus == ChallengeStatus.OPEN &&
        locksAtInstant != null &&
        resultsPostedAtInstant == null &&
        Clock.System.now() >= locksAtInstant
    ) ChallengeStatus.LOCKED else storedStatus

    return Challenge(
        id = id,
        title = title,
        description = description,
        category = category,
        visibility = runCatching { Visibility.valueOf(visibility) }.getOrDefault(Visibility.FRIENDS),
        createdBy = createdBy,
        createdAt = createdAt?.toInstant() ?: Instant.DISTANT_PAST,
        locksAt = locksAtInstant,
        resultsPostedAt = resultsPostedAtInstant,
        status = effectiveStatus,
        joinedCount = joinedCount.toInt(),
        promoted = promoted,
        bets = bets,
        results = results,
        leaderboard = leaderboard,
        betsVisible = betsVisible,
        participants = participants,
        scoringMode = runCatching {
            ScoringMode.valueOf(this.scoringMode ?: "STANDARD")
        }.getOrDefault(ScoringMode.STANDARD),
    )
}

/** Parses the JSON string from querySnapshotJson() into a list of (id, dataJson) pairs. */
@Serializable
internal data class QueryDocEntry(val id: String, val data: JsonElement)

internal fun parseQuerySnapshot(json: String): List<QueryDocEntry> =
    webJson.decodeFromString(kotlinx.serialization.builtins.ListSerializer(QueryDocEntry.serializer()), json)

internal fun QueryDocEntry.toChallengeOrNull(): Challenge? = runCatching {
    val dto = webJson.decodeFromJsonElement(ChallengeDocDto.serializer(), data)
    dto.toChallenge(id)
}.getOrNull()

internal fun QueryDocEntry.toInvitationOrNull(challengeId: String): Invitation? = runCatching {
    val obj = data.jsonObject
    val invitedUid = obj["invitedUid"]?.jsonPrimitive?.contentOrNull ?: id
    val invitedBy = obj["invitedBy"]?.jsonPrimitive?.contentOrNull ?: "SYSTEM"
    val invitedAt = obj["invitedAt"]?.let {
        runCatching { webJson.decodeFromJsonElement(TimestampDto.serializer(), it).toInstant() }.getOrNull()
    } ?: Instant.DISTANT_PAST
    Invitation(challengeId = challengeId, invitedUid = invitedUid, invitedBy = invitedBy, invitedAt = invitedAt)
}.getOrNull()

internal fun QueryDocEntry.toReactionOrNull(): Reaction? = runCatching {
    val obj = data.jsonObject
    val uid = obj["uid"]?.jsonPrimitive?.contentOrNull ?: id
    val emoji = obj["emoji"]?.jsonPrimitive?.contentOrNull ?: ""
    val updatedAt = obj["updatedAt"]?.let {
        runCatching { webJson.decodeFromJsonElement(TimestampDto.serializer(), it).toInstant() }.getOrNull()
    } ?: Instant.DISTANT_PAST
    Reaction(uid = uid, emoji = emoji, updatedAt = updatedAt)
}.getOrNull()

// ── Doc snapshot direct parsers ───────────────────────────────────────────────

internal fun parseChallenge(id: String, dataJson: String): Challenge? = runCatching {
    val dto = webJson.decodeFromString(ChallengeDocDto.serializer(), dataJson)
    dto.toChallenge(id)
}.getOrNull()

internal fun parsePlayer(id: String, dataJson: String): Player? = runCatching {
    val obj = webJson.parseToJsonElement(dataJson) as JsonObject
    Player(
        uid = id,
        username = obj["handle"]?.jsonPrimitive?.contentOrNull ?: "",
        displayName = obj["displayName"]?.jsonPrimitive?.contentOrNull ?: "",
        avatarSeed = obj["avatarSeed"]?.jsonPrimitive?.contentOrNull ?: "",
        createdAt = obj["createdAt"]?.let {
            runCatching { webJson.decodeFromJsonElement(TimestampDto.serializer(), it).toInstant() }.getOrNull()
        } ?: Instant.DISTANT_PAST,
    )
}.getOrNull()

internal fun parsePublicProfile(id: String, dataJson: String): PublicProfile? = runCatching {
    val obj = webJson.parseToJsonElement(dataJson) as JsonObject
    PublicProfile(
        uid = id,
        username = obj["handle"]?.jsonPrimitive?.contentOrNull ?: "",
        displayName = obj["displayName"]?.jsonPrimitive?.contentOrNull ?: "",
        avatarSeed = obj["avatarSeed"]?.jsonPrimitive?.contentOrNull ?: "",
    )
}.getOrNull()

internal fun parsePredictionsMap(dataJson: String): Map<String, PredictionPayload> = runCatching {
    val obj = webJson.parseToJsonElement(dataJson) as JsonObject
    val predsEl = obj["predictions"]?.jsonObject ?: return@runCatching emptyMap()
    predsEl.entries.mapNotNull { (betId, el) ->
        runCatching {
            val dto = webJson.decodeFromJsonElement(PredictionPayloadDto.serializer(), el)
            dto.toDomain()?.let { betId to it }
        }.getOrNull()
    }.toMap()
}.getOrElse { emptyMap() }

internal fun parseCloudFriends(dataJson: String): List<CloudFriend> = runCatching {
    val dto = webJson.decodeFromString(SocialDocDto.serializer(), dataJson)
    dto.friends.map { (uid, ts) ->
        val since = ts.toInstant()
        CloudFriend(
            player = Player(uid = uid, username = "", displayName = GENERIC_DISPLAY_NAME, avatarSeed = "", createdAt = since),
            since = since,
        )
    }
}.getOrElse { emptyList() }

internal fun parseFriendRequests(dataJson: String): FriendRequests = runCatching {
    val dto = webJson.decodeFromString(SocialDocDto.serializer(), dataJson)
    FriendRequests(
        incoming = dto.requestsIn.mapValues { it.value.toInstant() },
        outgoing = dto.requestsOut.mapValues { it.value.toInstant() },
    )
}.getOrElse { FriendRequests(emptyMap(), emptyMap()) }

internal fun parseHeadToHead(dataJson: String): HeadToHead = runCatching {
    val dto = webJson.decodeFromString(HeadToHeadDto.serializer(), dataJson)
    HeadToHead(vs = dto.vs.mapValues { (_, r) -> HeadToHead.Record(wins = r.wins, losses = r.losses, ties = r.ties) })
}.getOrElse { HeadToHead(emptyMap()) }

internal fun parseNotificationPrefs(dataJson: String): NotificationPrefs = runCatching {
    val dto = webJson.decodeFromString(PreferencesDocDto.serializer(), dataJson)
    NotificationPrefs(
        master = dto.notifications ?: true,
        social = dto.categories["social"] ?: true,
        results = dto.categories["results"] ?: true,
        participations = dto.categories["participations"] ?: true,
        invites = dto.categories["invites"] ?: true,
    )
}.getOrElse { NotificationPrefs.DEFAULT }

// ── PredictionPayload → JSON (write path) ─────────────────────────────────────
// Mirrors PredictionPayload.toMap() in mobile FirestoreMappers.kt.

internal fun PredictionPayload.toJsonObject(): JsonObject = when (this) {
    is PredictionPayload.SinglePick -> buildJsonObj {
        put("kind", "SINGLE_PICK")
        put("optionId", optionId)
    }
    is PredictionPayload.Ranking -> buildJsonObj {
        put("kind", "RANKING")
        putArray("orderedOptionIds", orderedOptionIds)
    }
    is PredictionPayload.BooleanProp -> buildJsonObj {
        put("kind", "BOOLEAN_PROP")
        put("value", value)
    }
    is PredictionPayload.Guess -> buildJsonObj {
        put("kind", "GUESS")
        put("guessValue", value)
    }
    is PredictionPayload.MultiSelect -> buildJsonObj {
        put("kind", "MULTI_SELECT")
        putArray("selectedOptionIds", selectedOptionIds)
    }
    is PredictionPayload.OverUnder -> buildJsonObj {
        put("kind", "OVER_UNDER")
        over?.let { put("over", it) }
        actualValue?.let { put("actualValue", it) }
    }
}

// ── Bet → JSON (write path) ───────────────────────────────────────────────────
// Mirrors Bet.toMap() in mobile ChallengeRemoteDataSource.kt.

internal fun Bet.toJsonObject(): JsonObject = when (this) {
    is Bet.SinglePick -> buildJsonObj {
        put("kind", "SINGLE_PICK"); put("id", id); put("title", title)
        put("optionType", optionType.name)
        putArray("options", options.map { it.toJsonObject() })
    }
    is Bet.Ranking -> buildJsonObj {
        put("kind", "RANKING"); put("id", id); put("title", title)
        put("optionType", optionType.name); put("topN", topN)
        putArray("options", options.map { it.toJsonObject() })
    }
    is Bet.BooleanProp -> buildJsonObj {
        put("kind", "BOOLEAN_PROP"); put("id", id); put("title", title)
    }
    is Bet.Guess -> buildJsonObj {
        put("kind", "GUESS"); put("id", id); put("title", title)
        put("granularity", granularity.name); put("closest", closest); put("placement", placement)
    }
    is Bet.MultiSelect -> buildJsonObj {
        put("kind", "MULTI_SELECT"); put("id", id); put("title", title)
        put("optionType", optionType.name)
        putArray("options", options.map { it.toJsonObject() })
    }
    is Bet.OverUnder -> buildJsonObj {
        put("kind", "OVER_UNDER"); put("id", id); put("title", title)
        put("line", line)
    }
}

internal fun BetOption.toJsonObject(): JsonObject = buildJsonObj {
    put("id", id); put("label", label)
    countryCode?.let { put("countryCode", it) }
}

// ── Simple JSON builder DSL ───────────────────────────────────────────────────

private class JsonObjBuilder {
    private val map = mutableMapOf<String, JsonElement>()

    fun put(key: String, value: String) {
        map[key] = kotlinx.serialization.json.JsonPrimitive(value)
    }
    fun put(key: String, value: Boolean) {
        map[key] = kotlinx.serialization.json.JsonPrimitive(value)
    }
    fun put(key: String, value: Long) {
        map[key] = kotlinx.serialization.json.JsonPrimitive(value)
    }
    fun put(key: String, value: Int) {
        map[key] = kotlinx.serialization.json.JsonPrimitive(value)
    }
    fun putArray(key: String, strings: List<String>) {
        map[key] = JsonArray(strings.map { kotlinx.serialization.json.JsonPrimitive(it) })
    }
    fun putArray(key: String, objects: List<JsonObject>) {
        map[key] = JsonArray(objects)
    }
    fun putJsonObject(key: String, obj: JsonObject) {
        map[key] = obj
    }
    fun build() = JsonObject(map)
}

private fun buildJsonObj(block: JsonObjBuilder.() -> Unit): JsonObject =
    JsonObjBuilder().apply(block).build()
