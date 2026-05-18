@file:OptIn(kotlinx.coroutines.ExperimentalCoroutinesApi::class)

package se.atte.bragwise.data

import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.flatMapLatest
import kotlinx.coroutines.flow.flowOf
import se.atte.bragwise.domain.CloudFriend
import se.atte.bragwise.domain.Friend
import se.atte.bragwise.domain.FriendRequests
import se.atte.bragwise.domain.HeadToHead
import se.atte.bragwise.domain.LocalFriend

data class ReconciliationSummary(
    val reconciled: Int,
    val skipped: Int,
    val failed: Int,
)

open class SocialRepository(
    val remote: SocialRemoteDataSource,
    private val local: SocialLocalDataSource,
    private val auth: AuthRepository,
    private val localFriends: LocalFriendStore = LocalFriendStore(),
) {
    private fun observeCloudFriends(): Flow<List<CloudFriend>> =
        auth.authState.flatMapLatest { state ->
            when (state) {
                is AuthState.SignedIn -> remote.observeCloudFriends(state.uid)
                else -> flowOf(emptyList())
            }
        }

    fun observeLocalFriends(): Flow<List<LocalFriend>> = localFriends.friends

    fun localFriendSnapshot(): List<LocalFriend> = localFriends.snapshot()

    fun observeFriends(): Flow<List<Friend>> =
        combine(observeCloudFriends(), observeLocalFriends()) { cloud, local ->
            (cloud as List<Friend>) + local
        }

    fun observeFriendRequests(): Flow<FriendRequests> =
        auth.authState.flatMapLatest { state ->
            when (state) {
                is AuthState.SignedIn -> remote.observeFriendRequests(state.uid)
                else -> flowOf(FriendRequests(emptyMap(), emptyMap()))
            }
        }

    fun observeHeadToHead(): Flow<HeadToHead> =
        auth.authState.flatMapLatest { state ->
            when (state) {
                is AuthState.SignedIn -> remote.observeHeadToHead(state.uid)
                else -> flowOf(HeadToHead(emptyMap()))
            }
        }

    // ── Local-friend mutations ───────────────────────────────────────────────

    fun addLocalFriend(displayName: String, avatarSeed: String): LocalFriend =
        localFriends.add(displayName = displayName, avatarSeed = avatarSeed)

    fun editLocalFriend(localId: String, displayName: String, avatarSeed: String): Boolean =
        localFriends.edit(localId, displayName, avatarSeed)

    fun removeLocalFriend(localId: String): Boolean = localFriends.remove(localId)

    // ── Cloud friend graph ───────────────────────────────────────────────────

    open suspend fun sendFriendRequest(handle: String): Result<Unit> = runCatching {
        remote.sendFriendRequest(handle)
    }

    suspend fun acceptFriendRequest(requesterUid: String): Result<Unit> = runCatching {
        remote.acceptFriendRequest(requesterUid)
    }

    suspend fun declineFriendRequest(requesterUid: String): Result<Unit> = runCatching {
        remote.declineFriendRequest(requesterUid)
    }

    suspend fun unfriend(otherUid: String): Result<Unit> = runCatching {
        remote.unfriend(otherUid)
    }

    // ── Reconciliation (OB-06) ───────────────────────────────────────────────

    suspend fun reconcileLocalFriendToHandle(localId: String, handle: String): Result<Unit> {
        val result = sendFriendRequest(handle)
        if (result.isSuccess) localFriends.remove(localId)
        return result
    }

    suspend fun reconcileLocalFriends(mappings: List<Pair<String, String?>>): ReconciliationSummary {
        var reconciled = 0; var skipped = 0; var failed = 0
        for ((localId, handle) in mappings) {
            if (handle.isNullOrBlank()) { skipped++; continue }
            reconcileLocalFriendToHandle(localId = localId, handle = handle)
                .onSuccess { reconciled++ }
                .onFailure { failed++ }
        }
        return ReconciliationSummary(reconciled, skipped, failed)
    }
}
