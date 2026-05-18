package se.atte.bragwise.data

import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlin.time.Clock
import se.atte.bragwise.domain.LocalFriend
import se.atte.bragwise.util.randomUuid

/**
 * On-device store for [LocalFriend] rows. Guest-only — signed-in users
 * never write here, and reconciliation deletes mapped rows.
 *
 * Persistence is in-memory for now. Future SQLDelight schema:
 *
 *   CREATE TABLE LocalFriend (
 *     localId      TEXT PRIMARY KEY,
 *     displayName  TEXT NOT NULL,
 *     avatarSeed   TEXT NOT NULL,
 *     addedAt      INTEGER NOT NULL
 *   );
 *
 * `multiplatform-settings` JSON-blob persistence is the bridging stub
 * once SQLDelight + multiplatform-settings deps land — see decision.md
 * for why those deps are deferred. The Flow + mutator API stays stable.
 */
class LocalFriendStore {

    private val _friends = MutableStateFlow<List<LocalFriend>>(emptyList())
    val friends: StateFlow<List<LocalFriend>> = _friends.asStateFlow()

    fun add(displayName: String, avatarSeed: String): LocalFriend {
        val friend = LocalFriend(
            localId = randomUuid(),
            displayName = displayName,
            avatarSeed = avatarSeed,
            addedAt = Clock.System.now(),
        )
        _friends.update { it + friend }
        return friend
    }

    fun edit(localId: String, displayName: String, avatarSeed: String): Boolean {
        var found = false
        _friends.update { list ->
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
        _friends.update { list ->
            list.filterNot { row ->
                if (row.localId == localId) { found = true; true } else false
            }
        }
        return found
    }

    fun snapshot(): List<LocalFriend> = _friends.value
}
