package se.atte.bragwise.data

import kotlinx.coroutines.flow.StateFlow
import se.atte.bragwise.domain.Challenge

interface LocalDraftStore {
    fun observeDrafts(): StateFlow<List<Challenge>>
    fun save(challenge: Challenge): Challenge
    fun get(id: String): Challenge?
    fun delete(id: String)
    fun clear()
}
