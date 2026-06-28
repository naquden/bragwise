package se.atte.bragwise.data

import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.serialization.Serializable
import kotlinx.serialization.builtins.ListSerializer
import kotlinx.serialization.json.Json
import se.atte.bragwise.domain.Bet
import se.atte.bragwise.domain.BetOption
import se.atte.bragwise.domain.Challenge
import se.atte.bragwise.domain.ChallengeStatus
import se.atte.bragwise.domain.GuessGranularity
import se.atte.bragwise.domain.OptionType
import se.atte.bragwise.domain.Visibility
import se.atte.bragwise.util.randomUuid
import kotlin.time.Clock
import kotlin.time.Instant

/**
 * localStorage-backed [LocalDraftStore] for the web guest flow.
 *
 * Storage key: "bragwise.guest.drafts" → JSON-encoded List<DraftDto>.
 * DTOs and conversion helpers are copied verbatim from
 * [SqlDelightLocalDraftStore] so the format is semantically identical.
 */
class WebLocalDraftStore : LocalDraftStore {

    private val json = Json { ignoreUnknownKeys = true }
    private val key = "bragwise.guest.drafts"

    private val _drafts = MutableStateFlow(loadAll())

    override fun observeDrafts(): StateFlow<List<Challenge>> = _drafts.asStateFlow()

    override fun save(challenge: Challenge): Challenge {
        val id = challenge.id.ifBlank { randomUuid() }
        val now = Clock.System.now()
        val saved = challenge.copy(
            id = id,
            createdAt = if (challenge.id.isBlank()) now else challenge.createdAt,
        )
        val dtos = load().toMutableList()
        dtos.removeAll { it.id == id }
        dtos.add(saved.toDto())
        persist(dtos)
        _drafts.value = dtos.map { it.toDomain() }
        return saved
    }

    override fun get(id: String): Challenge? =
        load().firstOrNull { it.id == id }?.toDomain()

    override fun delete(id: String) {
        val dtos = load().toMutableList()
        dtos.removeAll { it.id == id }
        persist(dtos)
        _drafts.value = dtos.map { it.toDomain() }
    }

    override fun clear() {
        lsRemove(key)
        _drafts.value = emptyList()
    }

    // ── Persistence ───────────────────────────────────────────────────────────

    private fun load(): List<DraftDto> {
        val raw = lsGet(key) ?: return emptyList()
        return runCatching { json.decodeFromString(ListSerializer(DraftDto.serializer()), raw) }.getOrDefault(emptyList())
    }

    private fun loadAll(): List<Challenge> = load().map { it.toDomain() }

    private fun persist(dtos: List<DraftDto>) {
        lsSet(key, json.encodeToString(ListSerializer(DraftDto.serializer()), dtos))
    }

    // ── Serializable DTOs (identical to SqlDelightLocalDraftStore) ────────────

    @Serializable
    private data class DraftDto(
        val id: String,
        val title: String,
        val category: String,
        val visibility: String,
        val createdAtEpochSeconds: Long,
        val locksAtEpochSeconds: Long?,
        val betsVisible: Boolean = false,
        val invitedUids: List<String> = emptyList(),
        val bets: List<BetDtoLocal> = emptyList(),
    )

    @Serializable
    private data class BetDtoLocal(
        val kind: String,
        val id: String,
        val title: String,
        val optionType: String = "NONE",
        val options: List<BetOptionDtoLocal> = emptyList(),
        val topN: Int = 1,
        val granularity: String? = null,
        val closest: Boolean = true,
        val line: Long? = null,
    )

    @Serializable
    private data class BetOptionDtoLocal(
        val id: String,
        val label: String,
        val countryCode: String? = null,
    )

    // ── toDto / toDomain ──────────────────────────────────────────────────────

    private fun Challenge.toDto() = DraftDto(
        id = id,
        title = title,
        category = category,
        visibility = visibility.name,
        createdAtEpochSeconds = createdAt.epochSeconds,
        locksAtEpochSeconds = locksAt?.epochSeconds,
        betsVisible = betsVisible,
        invitedUids = invitedUids.toList(),
        bets = bets.map { it.toDtoLocal() },
    )

    private fun Bet.toDtoLocal(): BetDtoLocal = when (this) {
        is Bet.SinglePick -> BetDtoLocal(
            kind = "SINGLE_PICK",
            id = id,
            title = title,
            optionType = optionType.name,
            options = options.map { BetOptionDtoLocal(id = it.id, label = it.label, countryCode = it.countryCode) },
        )
        is Bet.Ranking -> BetDtoLocal(
            kind = "RANKING",
            id = id,
            title = title,
            optionType = optionType.name,
            options = options.map { BetOptionDtoLocal(id = it.id, label = it.label, countryCode = it.countryCode) },
            topN = topN,
        )
        is Bet.BooleanProp -> BetDtoLocal(kind = "BOOLEAN_PROP", id = id, title = title)
        is Bet.Guess -> BetDtoLocal(
            kind = "GUESS",
            id = id,
            title = title,
            granularity = granularity.name,
            closest = closest,
        )
        is Bet.MultiSelect -> BetDtoLocal(
            kind = "MULTI_SELECT",
            id = id,
            title = title,
            optionType = optionType.name,
            options = options.map { BetOptionDtoLocal(id = it.id, label = it.label, countryCode = it.countryCode) },
        )
        is Bet.OverUnder -> BetDtoLocal(kind = "OVER_UNDER", id = id, title = title, line = line)
    }

    private fun DraftDto.toDomain(): Challenge = Challenge(
        id = id,
        title = title,
        description = "",
        category = category,
        visibility = runCatching { Visibility.valueOf(visibility) }.getOrDefault(Visibility.FRIENDS),
        createdBy = "",
        createdAt = Instant.fromEpochSeconds(createdAtEpochSeconds),
        locksAt = locksAtEpochSeconds?.let { Instant.fromEpochSeconds(it) },
        resultsPostedAt = null,
        status = ChallengeStatus.DRAFT,
        joinedCount = 0,
        promoted = false,
        bets = bets.map { it.toDomain() },
        results = null,
        leaderboard = null,
        betsVisible = betsVisible,
        invitedUids = invitedUids.toSet(),
    )

    private fun BetDtoLocal.toDomain(): Bet {
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
            )
            "MULTI_SELECT" -> Bet.MultiSelect(id = id, title = title, optionType = ot, options = opts)
            "OVER_UNDER" -> Bet.OverUnder(id = id, title = title, line = line ?: 0L)
            else -> Bet.BooleanProp(id = id, title = title)
        }
    }
}
