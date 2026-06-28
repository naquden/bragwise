@file:OptIn(kotlinx.coroutines.ExperimentalCoroutinesApi::class)

package se.atte.bragwise.data

import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.catch
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.flatMapLatest
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.onStart
import se.atte.bragwise.domain.Player
import se.atte.bragwise.domain.PublicProfile

interface ProfileRepository {
    /**
     * Real-time stream of the signed-in user's full player doc. Emits null
     * when signed out or the doc doesn't exist yet (new account).
     */
    fun observeMe(): Flow<Player?>
    fun observePublicProfile(uid: String): Flow<PublicProfile?>
    /** Notification prefs for the signed-in user. Emits defaults when signed out. */
    fun observeNotificationPrefs(): Flow<NotificationPrefs>
    suspend fun claimUsername(username: String): Result<Unit>
    suspend fun updateProfile(
        displayName: String? = null,
        username: String? = null,
        avatarSeed: String? = null,
    ): Result<Unit>
    suspend fun setMasterNotification(enabled: Boolean): Result<Unit>
    suspend fun setCategoryNotification(key: String, enabled: Boolean): Result<Unit>

    /**
     * Heartbeat: stamps `lastSeen` (and the anonymous flag) on the player doc
     * so stale guest accounts can be reaped after 90 days. Called on launch
     * for any signed-in session, anonymous guests included.
     */
    suspend fun recordActivity(): Result<Unit>
}

class FirebaseProfileRepository(
    val remote: ProfileRemote,
    private val local: ProfileLocalDataSource,
    private val auth: AuthRepository,
) : ProfileRepository {
    override fun observeMe(): Flow<Player?> =
        auth.authState.flatMapLatest { state ->
            when (state) {
                is AuthState.SignedIn -> remote.observePlayer(state.uid).catch { emit(null) }
                else -> flowOf(null)
            }
        }

    override fun observePublicProfile(uid: String): Flow<PublicProfile?> =
        remote.observePublicProfile(uid)

    override fun observeNotificationPrefs(): Flow<NotificationPrefs> =
        auth.authState.flatMapLatest { state ->
            when (state) {
                is AuthState.SignedIn -> remote.observeNotificationPrefs(state.uid).catch { emit(NotificationPrefs.DEFAULT) }
                else -> flowOf(NotificationPrefs.DEFAULT)
            }
        }

    override suspend fun claimUsername(username: String): Result<Unit> = runCatching {
        remote.claimUsername(username)
    }

    override suspend fun setMasterNotification(enabled: Boolean): Result<Unit> = runCatching {
        remote.setMasterNotification(enabled)
    }

    override suspend fun setCategoryNotification(key: String, enabled: Boolean): Result<Unit> = runCatching {
        remote.setCategoryNotification(key, enabled)
    }

    override suspend fun updateProfile(
        displayName: String?,
        username: String?,
        avatarSeed: String?,
    ): Result<Unit> = runCatching {
        remote.updateProfile(displayName = displayName, username = username, avatarSeed = avatarSeed)
    }

    override suspend fun recordActivity(): Result<Unit> = runCatching {
        remote.recordActivity()
    }
}

fun ProfileRepository.observeProfiles(uids: List<String>): Flow<Map<String, PublicProfile?>> =
    if (uids.isEmpty()) {
        flowOf(emptyMap())
    } else {
        combine(
            uids.map { uid ->
                observePublicProfile(uid)
                    .onStart { emit(null) }
                    .catch { emit(null) }
                    .map { uid to it }
            },
        ) { it.toMap() }
    }
