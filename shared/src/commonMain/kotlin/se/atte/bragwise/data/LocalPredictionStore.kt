package se.atte.bragwise.data

import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.serialization.Serializable
import kotlinx.serialization.builtins.ListSerializer
import kotlinx.serialization.json.Json
import kotlinx.serialization.serializer
import se.atte.bragwise.domain.PredictionPayload

/**
 * On-device store for guest predictions. Guest-only — signed-in users
 * submit straight to the cloud via `submitPredictions`. On authenticate,
 * `AuthRepository.migrateLocalToCloud` (Sync mode) replays these through the
 * `migrateGuestData` callable, after which [clear] is called.
 *
 * Keyed by `(challengeId, betId)` so a guest can predict on many challenges
 * and re-tap a bet to overwrite their pick. Persistence is a JSON blob
 * through [LocalPredictionPersistence] (SharedPrefs on Android,
 * NSUserDefaults on iOS) — same dumb-string contract as [LocalFriendStore].
 */
class LocalPredictionStore(
    private val persistence: LocalPredictionPersistence,
) {

    private val json = Json { ignoreUnknownKeys = true }

    private val _predictions = MutableStateFlow(loadInitial())

    /** All stored guest predictions, keyed by challengeId then betId. */
    val predictions: StateFlow<Map<String, Map<String, PredictionPayload>>> =
        _predictions.asStateFlow()

    /** Drafts for a single challenge, for pre-filling the Predict screen. */
    fun forChallenge(challengeId: String): Map<String, PredictionPayload> =
        _predictions.value[challengeId].orEmpty()

    /** Overwrite the full prediction set for one challenge. */
    fun put(challengeId: String, predictions: Map<String, PredictionPayload>) {
        mutate { it + (challengeId to predictions) }
    }

    /** Flatten into the wire shape `migrateGuestData` expects. */
    fun snapshot(): List<LocalPrediction> =
        _predictions.value.flatMap { (challengeId, byBet) ->
            byBet.map { (betId, payload) -> LocalPrediction(challengeId, betId, payload) }
        }

    /** Wipe everything — called after a successful migration. */
    fun clear() {
        _predictions.value = emptyMap()
        persistence.save(null)
    }

    private fun mutate(
        transform: (Map<String, Map<String, PredictionPayload>>) -> Map<String, Map<String, PredictionPayload>>,
    ) {
        _predictions.update(transform)
        persistence.save(encode(_predictions.value))
    }

    private fun loadInitial(): Map<String, Map<String, PredictionPayload>> {
        val raw = persistence.load() ?: return emptyMap()
        return runCatching { decode(raw) }.getOrElse { emptyMap() }
    }

    private fun encode(map: Map<String, Map<String, PredictionPayload>>): String {
        val rows = map.flatMap { (challengeId, byBet) ->
            byBet.map { (betId, payload) -> payload.toRow(challengeId, betId) }
        }
        return json.encodeToString(ListSerializer(serializer<LocalPredictionRow>()), rows)
    }

    private fun decode(raw: String): Map<String, Map<String, PredictionPayload>> {
        val rows = json.decodeFromString<List<LocalPredictionRow>>(raw)
        return rows
            .mapNotNull { row -> row.toDomain()?.let { Triple(row.challengeId, row.betId, it) } }
            .groupBy { it.first }
            .mapValues { (_, triples) -> triples.associate { it.second to it.third } }
    }

    @Serializable
    private data class LocalPredictionRow(
        val challengeId: String,
        val betId: String,
        val kind: String,
        val optionId: String? = null,
        val orderedOptionIds: List<String> = emptyList(),
        val value: Boolean? = null,
    )

    private fun PredictionPayload.toRow(challengeId: String, betId: String) = when (this) {
        is PredictionPayload.SinglePick -> LocalPredictionRow(
            challengeId = challengeId, betId = betId, kind = "SINGLE_PICK", optionId = optionId,
        )
        is PredictionPayload.Ranking -> LocalPredictionRow(
            challengeId = challengeId, betId = betId, kind = "RANKING", orderedOptionIds = orderedOptionIds,
        )
        is PredictionPayload.BooleanProp -> LocalPredictionRow(
            challengeId = challengeId, betId = betId, kind = "BOOLEAN_PROP", value = value,
        )
    }

    private fun LocalPredictionRow.toDomain(): PredictionPayload? = when (kind) {
        "SINGLE_PICK" -> optionId?.let { PredictionPayload.SinglePick(optionId = it) }
        "RANKING" -> PredictionPayload.Ranking(orderedOptionIds = orderedOptionIds)
        "BOOLEAN_PROP" -> value?.let { PredictionPayload.BooleanProp(value = it) }
        else -> null
    }
}

/** Flattened guest prediction — the unit `migrateGuestData` consumes. */
data class LocalPrediction(
    val challengeId: String,
    val betId: String,
    val payload: PredictionPayload,
)
