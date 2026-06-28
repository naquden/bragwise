package se.atte.bragwise.data.mock

import se.atte.bragwise.data.LocalPrediction
import se.atte.bragwise.data.LocalPredictionStore
import se.atte.bragwise.domain.PredictionPayload

class InMemoryLocalPredictionStore : LocalPredictionStore {
    private val store = mutableMapOf<String, MutableMap<String, PredictionPayload>>()

    override fun forChallenge(challengeId: String): Map<String, PredictionPayload> =
        store[challengeId]?.toMap() ?: emptyMap()

    override fun put(challengeId: String, predictions: Map<String, PredictionPayload>) {
        store.getOrPut(challengeId) { mutableMapOf() }.putAll(predictions)
    }

    override fun deleteForChallenge(challengeId: String) {
        store.remove(challengeId)
    }

    override fun snapshot(): List<LocalPrediction> =
        store.flatMap { (challengeId, bets) ->
            bets.map { (betId, payload) -> LocalPrediction(challengeId, betId, payload) }
        }

    override fun clear() {
        store.clear()
    }
}
