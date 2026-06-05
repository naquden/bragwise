package se.atte.bragwise.data.mock

import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.flowOf
import se.atte.bragwise.data.SocialRepository
import se.atte.bragwise.domain.CloudFriend
import se.atte.bragwise.domain.Friend
import se.atte.bragwise.domain.FriendRequests
import se.atte.bragwise.domain.HeadToHead

class MockSocialRepository : SocialRepository {
    private val _cloudFriends = MutableStateFlow<List<CloudFriend>>(mockCloudFriends)

    override fun observeFriends(): Flow<List<Friend>> = _cloudFriends

    override fun observeFriendRequests(): Flow<FriendRequests> =
        flowOf(FriendRequests(emptyMap(), emptyMap()))

    override fun observeHeadToHead(): Flow<HeadToHead> = flowOf(HeadToHead(emptyMap()))

    override suspend fun sendFriendRequest(username: String): Result<Unit> = Result.success(Unit)

    override suspend fun acceptFriendRequest(requesterUid: String): Result<Unit> = Result.success(Unit)

    override suspend fun declineFriendRequest(requesterUid: String): Result<Unit> = Result.success(Unit)

    override suspend fun unfriend(otherUid: String): Result<Unit> {
        _cloudFriends.value = _cloudFriends.value.filterNot { it.id == otherUid }
        return Result.success(Unit)
    }
}
