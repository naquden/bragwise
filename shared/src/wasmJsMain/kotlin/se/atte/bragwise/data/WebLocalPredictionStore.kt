package se.atte.bragwise.data

import kotlinx.serialization.Serializable
import kotlinx.serialization.builtins.ListSerializer
import kotlinx.serialization.builtins.MapSerializer
import kotlinx.serialization.builtins.serializer
import kotlinx.serialization.json.Json
import se.atte.bragwise.domain.PredictionPayload

/**
 * localStorage-backed [LocalPredictionStore] for the web guest flow.
 *
 * Storage layout (key "bragwise.guest.predictions"):
 *   JSON-encoded Map<challengeId, Map<betId, PredictionPayloadDto>>
 *
 * PredictionPayloadDto mirrors the column encoding used by
 * [SqlDelightLocalPredictionStore] so prediction data is semantically
 * identical across platforms.
 */
class WebLocalPredictionStore : LocalPredictionStore {

    private val json = Json { ignoreUnknownKeys = true }
    private val key = "bragwise.guest.predictions"

    // ── Public interface ──────────────────────────────────────────────────────

    override fun forChallenge(challengeId: String): Map<String, PredictionPayload> =
        load()[challengeId]?.mapNotNull { (betId, dto) ->
            dto.toDomain()?.let { betId to it }
        }?.toMap() ?: emptyMap()

    override fun put(challengeId: String, predictions: Map<String, PredictionPayload>) {
        val data = load().toMutableMap()
        data[challengeId] = predictions.mapValues { (_, payload) -> payload.toDto() }
        save(data)
    }

    override fun deleteForChallenge(challengeId: String) {
        val data = load().toMutableMap()
        data.remove(challengeId)
        save(data)
    }

    override fun snapshot(): List<LocalPrediction> =
        load().flatMap { (challengeId, bets) ->
            bets.mapNotNull { (betId, dto) ->
                dto.toDomain()?.let { LocalPrediction(challengeId, betId, it) }
            }
        }

    override fun clear() {
        lsRemove(key)
    }

    // ── Persistence ───────────────────────────────────────────────────────────

    private fun load(): Map<String, Map<String, PredictionPayloadDto>> {
        val raw = lsGet(key) ?: return emptyMap()
        return runCatching {
            json.decodeFromString(MapSerializer(String.serializer(), MapSerializer(String.serializer(), PredictionPayloadDto.serializer())), raw)
        }.getOrDefault(emptyMap())
    }

    private fun save(data: Map<String, Map<String, PredictionPayloadDto>>) {
        lsSet(key, json.encodeToString(MapSerializer(String.serializer(), MapSerializer(String.serializer(), PredictionPayloadDto.serializer())), data))
    }

    // ── DTO ───────────────────────────────────────────────────────────────────

    @Serializable
    private data class PredictionPayloadDto(
        val kind: String,
        /** SINGLE_PICK: optionId; GUESS: value.toString(); OVER_UNDER: actualValue?.toString() */
        val optionId: String? = null,
        /** RANKING/MULTI_SELECT: JSON-encoded list of strings */
        val orderedOptionIds: String = "",
        /** BOOLEAN_PROP: 1/0; OVER_UNDER: over (1=true,0=false) */
        val boolValue: Long? = null,
    )

    private fun PredictionPayload.toDto(): PredictionPayloadDto = when (this) {
        is PredictionPayload.SinglePick -> PredictionPayloadDto(
            kind = "SINGLE_PICK",
            optionId = optionId,
        )
        is PredictionPayload.Ranking -> PredictionPayloadDto(
            kind = "RANKING",
            orderedOptionIds = json.encodeToString(ListSerializer(String.serializer()), orderedOptionIds),
        )
        is PredictionPayload.BooleanProp -> PredictionPayloadDto(
            kind = "BOOLEAN_PROP",
            boolValue = if (value) 1L else 0L,
        )
        is PredictionPayload.Guess -> PredictionPayloadDto(
            kind = "GUESS",
            optionId = value.toString(),
        )
        is PredictionPayload.MultiSelect -> PredictionPayloadDto(
            kind = "MULTI_SELECT",
            orderedOptionIds = json.encodeToString(ListSerializer(String.serializer()), selectedOptionIds),
        )
        is PredictionPayload.OverUnder -> PredictionPayloadDto(
            kind = "OVER_UNDER",
            optionId = actualValue?.toString(),
            boolValue = over?.let { if (it) 1L else 0L },
        )
    }

    private fun PredictionPayloadDto.toDomain(): PredictionPayload? = when (kind) {
        "SINGLE_PICK" -> optionId?.let { PredictionPayload.SinglePick(optionId = it) }
        "RANKING" -> PredictionPayload.Ranking(
            orderedOptionIds = if (orderedOptionIds.isEmpty()) emptyList()
            else runCatching { json.decodeFromString(ListSerializer(String.serializer()), orderedOptionIds) }.getOrDefault(emptyList())
        )
        "BOOLEAN_PROP" -> boolValue?.let { PredictionPayload.BooleanProp(value = it != 0L) }
        "GUESS" -> optionId?.toLongOrNull()?.let { PredictionPayload.Guess(value = it) }
        "MULTI_SELECT" -> PredictionPayload.MultiSelect(
            selectedOptionIds = if (orderedOptionIds.isEmpty()) emptyList()
            else runCatching { json.decodeFromString(ListSerializer(String.serializer()), orderedOptionIds) }.getOrDefault(emptyList())
        )
        "OVER_UNDER" -> PredictionPayload.OverUnder(
            over = boolValue?.let { it != 0L },
            actualValue = optionId?.toLongOrNull(),
        )
        else -> null
    }
}
