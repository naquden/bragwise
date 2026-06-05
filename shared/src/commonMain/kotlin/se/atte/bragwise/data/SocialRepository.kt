@file:OptIn(kotlinx.coroutines.ExperimentalCoroutinesApi::class)

package se.atte.bragwise.data

import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.flatMapLatest
import kotlinx.coroutines.flow.flowOf
import se.atte.bragwise.domain.CloudFriend
import se.atte.bragwise.domain.Friend
import se.atte.bragwise.domain.FriendRequests
import se.atte.bragwise.domain.HeadToHead

interface SocialRepository {
    fun observeFriends(): Flow<List<Friend>>
    fun observeFriendRequests(): Flow<FriendRequests>
    fun observeHeadToHead(): Flow<HeadToHead>

    suspend fun sendFriendRequest(username: String): Result<Unit>
    suspend fun acceptFriendRequest(requesterUid: String): Result<Unit>
    suspend fun declineFriendRequest(requesterUid: String): Result<Unit>
    suspend fun unfriend(otherUid: String): Result<Unit>
}

class FirebaseSocialRepository(
    val remote: SocialRemoteDataSource,
    private val local: SocialLocalDataSource,
    private val auth: AuthRepository,
) : SocialRepository {
    override fun observeFriends(): Flow<List<Friend>> =
        auth.authState.flatMapLatest { state ->
            when (state) {
                is AuthState.SignedIn -> remote.observeCloudFriends(state.uid)
                else -> flowOf(emptyList<CloudFriend>())
            }
        }

    override fun observeFriendRequests(): Flow<FriendRequests> =
        auth.authState.flatMapLatest { state ->
            when (state) {
                is AuthState.SignedIn -> remote.observeFriendRequests(state.uid)
                else -> flowOf(FriendRequests(emptyMap(), emptyMap()))
            }
        }

    override fun observeHeadToHead(): Flow<HeadToHead> =
        auth.authState.flatMapLatest { state ->
            when (state) {
                is AuthState.SignedIn -> remote.observeHeadToHead(state.uid)
                else -> flowOf(HeadToHead(emptyMap()))
            }
        }

    override suspend fun sendFriendRequest(username: String): Result<Unit> = runCatching {
        remote.sendFriendRequest(username)
    }

    override suspend fun acceptFriendRequest(requesterUid: String): Result<Unit> = runCatching {
        remote.acceptFriendRequest(requesterUid)
    }

    override suspend fun declineFriendRequest(requesterUid: String): Result<Unit> = runCatching {
        remote.declineFriendRequest(requesterUid)
    }

    override suspend fun unfriend(otherUid: String): Result<Unit> = runCatching {
        remote.unfriend(otherUid)
    }
}
