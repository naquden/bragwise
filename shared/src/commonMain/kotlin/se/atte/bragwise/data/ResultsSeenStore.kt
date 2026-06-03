package se.atte.bragwise.data

import se.atte.bragwise.db.BragwiseDatabase

/**
 * Tracks which challenge result reveals the current user has already opened.
 * Drives the unseen badge on the Results tab and the confetti one-shot guard.
 */
class ResultsSeenStore(db: BragwiseDatabase) {
    private val queries = db.bragwiseQueries

    fun markSeen(challengeId: String) {
        queries.seenResultInsert(challengeId)
    }

    fun isSeen(challengeId: String): Boolean =
        queries.seenResultContains(challengeId).executeAsOne() > 0L

    fun seenIds(): Set<String> =
        queries.seenResultAll().executeAsList().toSet()
}
