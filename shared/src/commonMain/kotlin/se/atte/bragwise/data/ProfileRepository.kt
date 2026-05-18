@file:OptIn(kotlinx.coroutines.ExperimentalCoroutinesApi::class)

package se.atte.bragwise.data

import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.catch
import kotlinx.coroutines.flow.flatMapLatest
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.flow.map
import se.atte.bragwise.domain.Player
import se.atte.bragwise.domain.PublicProfile

open class ProfileRepository(
    val remote: ProfileRemoteDataSource,
    private val local: ProfileLocalDataSource,
    private val auth: AuthRepository,
) {
    /**
     * Real-time stream of the signed-in user's full player doc. Emits null
     * when signed out or the doc doesn't exist yet (new account).
     */
    fun observeMe(): Flow<Player?> =
        auth.authState.flatMapLatest { state ->
            when (state) {
                is AuthState.SignedIn -> remote.observePlayer(state.uid).catch { emit(null) }
                else -> flowOf(null)
            }
        }

    fun observePublicProfile(uid: String): Flow<PublicProfile?> =
        remote.observePublicProfile(uid)

    /** @deprecated signature kept for compatibility; prefer observePublicProfile(uid). */
    fun observePlayer(handle: String): Flow<PublicProfile?> = flowOf(null)

    suspend fun claimHandle(handle: String): Result<Unit> = runCatching {
        remote.claimHandle(handle)
    }

    suspend fun updateProfile(
        displayName: String? = null,
        handle: String? = null,
        avatarSeed: String? = null,
    ): Result<Unit> = runCatching {
        remote.updateProfile(displayName = displayName, handle = handle, avatarSeed = avatarSeed)
    }
}
