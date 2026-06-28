package se.atte.bragwise.data

import kotlinx.coroutines.flow.Flow

interface ResultsSeenStore {
    val seenIds: Flow<Set<String>>
    val archivedIds: Flow<Set<String>>
    fun markSeen(challengeId: String)
    fun markUnseen(challengeId: String)
    fun archive(challengeId: String)
}
