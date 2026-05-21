package se.atte.bragwise.data.mock

import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.flow.map
import se.atte.bragwise.data.LocalFriendPersistence
import se.atte.bragwise.data.LocalFriendStore
import se.atte.bragwise.data.ReconciliationSummary
import se.atte.bragwise.data.SocialRepository
import se.atte.bragwise.domain.CloudFriend
import se.atte.bragwise.domain.Friend
import se.atte.bragwise.domain.FriendRequests
import se.atte.bragwise.domain.HeadToHead
import se.atte.bragwise.domain.LocalFriend

class MockSocialRepository : SocialRepository {
    private val _cloudFriends = MutableStateFlow<List<CloudFriend>>(mockCloudFriends)
    private val localFriends = LocalFriendStore(InMemoryPersistence())

    override fun observeLocalFriends(): Flow<List<LocalFriend>> = localFriends.friends

    override fun localFriendSnapshot(): List<LocalFriend> = localFriends.snapshot()

    override fun observeFriends(): Flow<List<Friend>> =
        combine(_cloudFriends, localFriends.friends) { cloud, local ->
            (cloud as List<Friend>) + local
        }

    override fun observeFriendRequests(): Flow<FriendRequests> =
        flowOf(FriendRequests(emptyMap(), emptyMap()))

    override fun observeHeadToHead(): Flow<HeadToHead> = flowOf(HeadToHead(emptyMap()))

    override fun addLocalFriend(displayName: String, avatarSeed: String): LocalFriend =
        localFriends.add(displayName = displayName, avatarSeed = avatarSeed)

    override fun editLocalFriend(localId: String, displayName: String, avatarSeed: String): Boolean =
        localFriends.edit(localId, displayName, avatarSeed)

    override fun removeLocalFriend(localId: String): Boolean = localFriends.remove(localId)

    override suspend fun sendFriendRequest(handle: String): Result<Unit> = Result.success(Unit)

    override suspend fun acceptFriendRequest(requesterUid: String): Result<Unit> = Result.success(Unit)

    override suspend fun declineFriendRequest(requesterUid: String): Result<Unit> = Result.success(Unit)

    override suspend fun unfriend(otherUid: String): Result<Unit> {
        _cloudFriends.value = _cloudFriends.value.filterNot { it.id == otherUid }
        return Result.success(Unit)
    }

    override suspend fun reconcileLocalFriendToHandle(localId: String, handle: String): Result<Unit> {
        localFriends.remove(localId)
        return Result.success(Unit)
    }

    override suspend fun reconcileLocalFriends(mappings: List<Pair<String, String?>>): ReconciliationSummary {
        var reconciled = 0; var skipped = 0
        for ((localId, handle) in mappings) {
            if (handle.isNullOrBlank()) { skipped++; continue }
            reconcileLocalFriendToHandle(localId = localId, handle = handle)
            reconciled++
        }
        return ReconciliationSummary(reconciled = reconciled, skipped = skipped, failed = 0)
    }

    private class InMemoryPersistence : LocalFriendPersistence {
        private var blob: String? = null
        override fun load(): String? = blob
        override fun save(json: String?) { blob = json }
    }
}
