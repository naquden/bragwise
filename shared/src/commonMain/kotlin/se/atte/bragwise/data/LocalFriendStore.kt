package se.atte.bragwise.data

import app.cash.sqldelight.coroutines.asFlow
import app.cash.sqldelight.coroutines.mapToList
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map
import kotlin.time.Clock
import kotlin.time.Instant
import se.atte.bragwise.db.BragwiseDatabase
import se.atte.bragwise.domain.LocalFriend
import se.atte.bragwise.util.randomUuid

class LocalFriendStore(db: BragwiseDatabase) {

    private val queries = db.bragwiseQueries

    val friends: Flow<List<LocalFriend>> =
        queries.friendsAll()
            .asFlow()
            .mapToList(Dispatchers.Default)
            .map { rows -> rows.map { it.toDomain() } }

    fun add(displayName: String): LocalFriend {
        val friend = LocalFriend(
            localId = randomUuid(),
            displayName = displayName,
            addedAt = Clock.System.now(),
        )
        queries.friendUpsert(
            localId = friend.localId,
            displayName = friend.displayName,
            addedAtEpochMillis = friend.addedAt.toEpochMilliseconds(),
        )
        return friend
    }

    fun edit(localId: String, displayName: String): Boolean {
        val existing = queries.friendsAll().executeAsList().firstOrNull { it.localId == localId }
            ?: return false
        queries.friendUpsert(
            localId = localId,
            displayName = displayName,
            addedAtEpochMillis = existing.addedAtEpochMillis,
        )
        return true
    }

    fun remove(localId: String): Boolean {
        val exists = queries.friendsAll().executeAsList().any { it.localId == localId }
        if (!exists) return false
        queries.friendDelete(localId)
        return true
    }

    fun snapshot(): List<LocalFriend> =
        queries.friendsAll().executeAsList().map { it.toDomain() }

    private fun se.atte.bragwise.db.LocalFriend.toDomain() = LocalFriend(
        localId = localId,
        displayName = displayName,
        addedAt = Instant.fromEpochMilliseconds(addedAtEpochMillis),
    )
}
