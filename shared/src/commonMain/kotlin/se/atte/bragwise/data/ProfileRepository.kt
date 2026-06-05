@file:OptIn(kotlinx.coroutines.ExperimentalCoroutinesApi::class)

package se.atte.bragwise.data

import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.catch
import kotlinx.coroutines.flow.flatMapLatest
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.flow.map
import se.atte.bragwise.domain.Player
import se.atte.bragwise.domain.PublicProfile

interface ProfileRepository {
    /**
     * Real-time stream of the signed-in user's full player doc. Emits null
     * when signed out or the doc doesn't exist yet (new account).
     */
    fun observeMe(): Flow<Player?>
    fun observePublicProfile(uid: String): Flow<PublicProfile?>
    /** Notifications-enabled pref for the signed-in user. Emits true when signed out. */
    fun observeNotificationsEnabled(): Flow<Boolean>
    suspend fun claimUsername(username: String): Result<Unit>
    suspend fun updateProfile(
        displayName: String? = null,
        username: String? = null,
        avatarSeed: String? = null,
    ): Result<Unit>
    suspend fun setNotificationsEnabled(enabled: Boolean): Result<Unit>

    /**
     * Heartbeat: stamps `lastSeen` (and the anonymous flag) on the player doc
     * so stale guest accounts can be reaped after 90 days. Called on launch
     * for any signed-in session, anonymous guests included.
     */
    suspend fun recordActivity(): Result<Unit>
}

class FirebaseProfileRepository(
    val remote: ProfileRemoteDataSource,
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

    override fun observeNotificationsEnabled(): Flow<Boolean> =
        auth.authState.flatMapLatest { state ->
            when (state) {
                is AuthState.SignedIn -> remote.observeNotificationsEnabled(state.uid).catch { emit(true) }
                else -> flowOf(true)
            }
        }

    override suspend fun claimUsername(username: String): Result<Unit> = runCatching {
        remote.claimUsername(username)
    }

    override suspend fun setNotificationsEnabled(enabled: Boolean): Result<Unit> = runCatching {
        remote.setNotificationsEnabled(enabled)
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
