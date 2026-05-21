package se.atte.bragwise.data

import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlin.time.Clock
import kotlin.time.Instant
import kotlinx.serialization.Serializable
import kotlinx.serialization.builtins.ListSerializer
import kotlinx.serialization.json.Json
import kotlinx.serialization.serializer
import se.atte.bragwise.domain.LocalFriend
import se.atte.bragwise.util.randomUuid

/**
 * On-device store for [LocalFriend] rows. Guest-only — signed-in users
 * never write here, and reconciliation deletes mapped rows.
 *
 * Persistence is a JSON blob through [LocalFriendPersistence] (SharedPrefs
 * on Android, NSUserDefaults on iOS). One table, no relational queries —
 * SQLDelight would be overkill until a second table joins this one.
 */
class LocalFriendStore(
    private val persistence: LocalFriendPersistence,
) {

    private val json = Json { ignoreUnknownKeys = true }

    private val _friends = MutableStateFlow(loadInitial())
    val friends: StateFlow<List<LocalFriend>> = _friends.asStateFlow()

    fun add(displayName: String, avatarSeed: String): LocalFriend {
        val friend = LocalFriend(
            localId = randomUuid(),
            displayName = displayName,
            avatarSeed = avatarSeed,
            addedAt = Clock.System.now(),
        )
        mutate { it + friend }
        return friend
    }

    fun edit(localId: String, displayName: String, avatarSeed: String): Boolean {
        var found = false
        mutate { list ->
            list.map { row ->
                if (row.localId == localId) {
                    found = true
                    row.copy(displayName = displayName, avatarSeed = avatarSeed)
                } else row
            }
        }
        return found
    }

    fun remove(localId: String): Boolean {
        var found = false
        mutate { list ->
            list.filterNot { row ->
                if (row.localId == localId) { found = true; true } else false
            }
        }
        return found
    }

    fun snapshot(): List<LocalFriend> = _friends.value

    private fun mutate(transform: (List<LocalFriend>) -> List<LocalFriend>) {
        _friends.update(transform)
        persistence.save(encode(_friends.value))
    }

    private fun loadInitial(): List<LocalFriend> {
        val raw = persistence.load() ?: return emptyList()
        return runCatching { decode(raw) }.getOrElse { emptyList() }
    }

    private fun encode(list: List<LocalFriend>): String =
        json.encodeToString(ListSerializer(serializer<LocalFriendRow>()), list.map { it.toRow() })

    private fun decode(raw: String): List<LocalFriend> =
        json.decodeFromString<List<LocalFriendRow>>(raw).map { it.toDomain() }

    @Serializable
    private data class LocalFriendRow(
        val localId: String,
        val displayName: String,
        val avatarSeed: String,
        val addedAtEpochMillis: Long,
    )

    private fun LocalFriend.toRow() = LocalFriendRow(
        localId = localId,
        displayName = displayName,
        avatarSeed = avatarSeed,
        addedAtEpochMillis = addedAt.toEpochMilliseconds(),
    )

    private fun LocalFriendRow.toDomain() = LocalFriend(
        localId = localId,
        displayName = displayName,
        avatarSeed = avatarSeed,
        addedAt = Instant.fromEpochMilliseconds(addedAtEpochMillis),
    )
}
