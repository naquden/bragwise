package se.atte.bragwise.data

import app.cash.sqldelight.coroutines.asFlow
import app.cash.sqldelight.coroutines.mapToList
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map
import se.atte.bragwise.db.BragwiseDatabase

class ResultsSeenStore(
    db: BragwiseDatabase,
    private val dispatcher: CoroutineDispatcher,
) {
    private val queries = db.bragwiseQueries

    val seenIds: Flow<Set<String>> =
        queries.seenResultAll().asFlow().mapToList(dispatcher).map { it.toSet() }

    fun markSeen(challengeId: String) {
        queries.seenResultInsert(challengeId)
    }
}
