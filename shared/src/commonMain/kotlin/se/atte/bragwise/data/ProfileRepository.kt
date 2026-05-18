package se.atte.bragwise.data

import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.flowOf
import se.atte.bragwise.domain.Player
import se.atte.bragwise.domain.PublicProfile

open class ProfileRepository(
    private val remote: ProfileRemoteDataSource,
    private val local: ProfileLocalDataSource,
) {
    fun observeMe(): Flow<Player?> = flowOf(null)
    fun observePlayer(handle: String): Flow<PublicProfile?> = flowOf(null)
    suspend fun updateProfile(displayName: String, handle: String): Result<Unit> =
        Result.failure(NotImplementedError("updateProfile not wired"))
}
