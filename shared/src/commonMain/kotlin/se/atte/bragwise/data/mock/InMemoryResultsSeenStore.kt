package se.atte.bragwise.data.mock

import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow
import se.atte.bragwise.data.ResultsSeenStore

class InMemoryResultsSeenStore : ResultsSeenStore {
    private val _seenIds = MutableStateFlow<Set<String>>(emptySet())
    private val _archivedIds = MutableStateFlow<Set<String>>(emptySet())

    override val seenIds: Flow<Set<String>> = _seenIds.asStateFlow()
    override val archivedIds: Flow<Set<String>> = _archivedIds.asStateFlow()

    override fun markSeen(challengeId: String) {
        _seenIds.value = _seenIds.value + challengeId
    }

    override fun markUnseen(challengeId: String) {
        _seenIds.value = _seenIds.value - challengeId
    }

    override fun archive(challengeId: String) {
        _archivedIds.value = _archivedIds.value + challengeId
    }
}
