package se.atte.bragwise.data

import app.cash.sqldelight.coroutines.asFlow
import app.cash.sqldelight.coroutines.mapToList
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map
import se.atte.bragwise.db.BragwiseDatabase

class SqlDelightResultsSeenStore(
    db: BragwiseDatabase,
    private val dispatcher: CoroutineDispatcher,
) : ResultsSeenStore {
    private val queries = db.bragwiseQueries

    override val seenIds: Flow<Set<String>> =
        queries.seenResultAll().asFlow().mapToList(dispatcher).map { it.toSet() }

    override val archivedIds: Flow<Set<String>> =
        queries.archivedResultAll().asFlow().mapToList(dispatcher).map { it.toSet() }

    override fun markSeen(challengeId: String) {
        queries.seenResultInsert(challengeId)
    }

    override fun markUnseen(challengeId: String) {
        queries.seenResultDelete(challengeId)
    }

    override fun archive(challengeId: String) {
        queries.archivedResultInsert(challengeId)
    }
}
