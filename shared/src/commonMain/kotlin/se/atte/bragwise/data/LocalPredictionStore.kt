package se.atte.bragwise.data

import kotlinx.serialization.builtins.ListSerializer
import kotlinx.serialization.json.Json
import kotlinx.serialization.serializer
import se.atte.bragwise.db.BragwiseDatabase
import se.atte.bragwise.domain.PredictionPayload

class LocalPredictionStore(db: BragwiseDatabase) {

    private val queries = db.bragwiseQueries
    private val json = Json { ignoreUnknownKeys = true }

    fun forChallenge(challengeId: String): Map<String, PredictionPayload> =
        queries.predictionsForChallenge(challengeId).executeAsList()
            .mapNotNull { row -> row.toDomain()?.let { row.betId to it } }
            .toMap()

    fun put(challengeId: String, predictions: Map<String, PredictionPayload>) {
        queries.transaction {
            predictions.forEach { (betId, payload) ->
                val (kind, optionId, orderedOptionIds, boolValue) = payload.toColumns()
                queries.predictionUpsert(
                    challengeId = challengeId,
                    betId = betId,
                    kind = kind,
                    optionId = optionId,
                    orderedOptionIds = orderedOptionIds,
                    boolValue = boolValue,
                )
            }
        }
    }

    fun deleteForChallenge(challengeId: String) {
        queries.predictionDeleteForChallenge(challengeId)
    }

    fun snapshot(): List<LocalPrediction> =
        queries.predictionsAll().executeAsList().mapNotNull { row ->
            row.toDomain()?.let { LocalPrediction(row.challengeId, row.betId, it) }
        }

    fun clear() {
        queries.predictionClear()
    }

    private fun se.atte.bragwise.db.LocalPrediction.toDomain(): PredictionPayload? = when (kind) {
        "SINGLE_PICK" -> optionId?.let { PredictionPayload.SinglePick(optionId = it) }
        "RANKING" -> PredictionPayload.Ranking(
            orderedOptionIds = if (orderedOptionIds.isEmpty()) emptyList()
            else json.decodeFromString(ListSerializer(serializer()), orderedOptionIds)
        )
        "BOOLEAN_PROP" -> boolValue?.let { PredictionPayload.BooleanProp(value = it != 0L) }
        // Guess value stored as stringified Long in the optionId TEXT column (no schema migration needed).
        "GUESS" -> optionId?.toLongOrNull()?.let { PredictionPayload.Guess(value = it) }
        // MultiSelect: selectedOptionIds stored as JSON in orderedOptionIds column.
        "MULTI_SELECT" -> PredictionPayload.MultiSelect(
            selectedOptionIds = if (orderedOptionIds.isEmpty()) emptyList()
            else json.decodeFromString(ListSerializer(serializer()), orderedOptionIds)
        )
        // OverUnder prediction: over (Boolean) in boolValue; actualValue in optionId as stringified Long.
        "OVER_UNDER" -> PredictionPayload.OverUnder(
            over = boolValue?.let { it != 0L },
            actualValue = optionId?.toLongOrNull(),
        )
        else -> null
    }

    private data class Columns(
        val kind: String,
        val optionId: String?,
        val orderedOptionIds: String,
        val boolValue: Long?,
    )

    private fun PredictionPayload.toColumns(): Columns = when (this) {
        is PredictionPayload.SinglePick -> Columns(
            kind = "SINGLE_PICK",
            optionId = optionId,
            orderedOptionIds = "",
            boolValue = null,
        )
        is PredictionPayload.Ranking -> Columns(
            kind = "RANKING",
            optionId = null,
            orderedOptionIds = json.encodeToString(
                ListSerializer(serializer()),
                orderedOptionIds,
            ),
            boolValue = null,
        )
        is PredictionPayload.BooleanProp -> Columns(
            kind = "BOOLEAN_PROP",
            optionId = null,
            orderedOptionIds = "",
            boolValue = if (value) 1L else 0L,
        )
        is PredictionPayload.Guess -> Columns(
            kind = "GUESS",
            optionId = value.toString(),
            orderedOptionIds = "",
            boolValue = null,
        )
        is PredictionPayload.MultiSelect -> Columns(
            kind = "MULTI_SELECT",
            optionId = null,
            orderedOptionIds = json.encodeToString(ListSerializer(serializer()), selectedOptionIds),
            boolValue = null,
        )
        is PredictionPayload.OverUnder -> Columns(
            kind = "OVER_UNDER",
            optionId = actualValue?.toString(),
            orderedOptionIds = "",
            boolValue = over?.let { if (it) 1L else 0L },
        )
    }
}

data class LocalPrediction(
    val challengeId: String,
    val betId: String,
    val payload: PredictionPayload,
)
