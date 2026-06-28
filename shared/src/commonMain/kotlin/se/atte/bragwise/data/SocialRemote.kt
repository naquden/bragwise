package se.atte.bragwise.data

import kotlinx.coroutines.flow.Flow
import se.atte.bragwise.domain.CloudFriend
import se.atte.bragwise.domain.FriendRequests
import se.atte.bragwise.domain.HeadToHead

interface SocialRemote {
    fun observeCloudFriends(uid: String): Flow<List<CloudFriend>>
    fun observeFriendRequests(uid: String): Flow<FriendRequests>
    fun observeHeadToHead(uid: String): Flow<HeadToHead>
    suspend fun sendFriendRequest(username: String)
    suspend fun acceptFriendRequest(requesterUid: String)
    suspend fun declineFriendRequest(requesterUid: String)
    suspend fun withdrawFriendRequest(otherUid: String)
    suspend fun unfriend(otherUid: String)
}
