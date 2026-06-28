package se.atte.bragwise.data

import se.atte.bragwise.domain.PredictionPayload

interface LocalPredictionStore {
    fun forChallenge(challengeId: String): Map<String, PredictionPayload>
    fun put(challengeId: String, predictions: Map<String, PredictionPayload>)
    fun deleteForChallenge(challengeId: String)
    fun snapshot(): List<LocalPrediction>
    fun clear()
}

data class LocalPrediction(
    val challengeId: String,
    val betId: String,
    val payload: PredictionPayload,
)
