package se.atte.bragwise.data

import dev.gitlive.firebase.Firebase
import dev.gitlive.firebase.firestore.FirebaseFirestore
import dev.gitlive.firebase.firestore.firestore
import dev.gitlive.firebase.functions.FirebaseFunctions
import dev.gitlive.firebase.functions.functions
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.emitAll
import kotlinx.coroutines.flow.flow
import kotlinx.coroutines.flow.map
import se.atte.bragwise.domain.CloudFriend
import se.atte.bragwise.domain.FriendRequests
import se.atte.bragwise.domain.HeadToHead

class SocialRemoteDataSource(
    private val db: FirebaseFirestore = Firebase.firestore,
    private val functions: FirebaseFunctions = Firebase.functions(FUNCTIONS_REGION),
) {
    fun observeCloudFriends(uid: String): Flow<List<CloudFriend>> = flow {
        emitAll(
            db.document("players/$uid/private/social").snapshots.map { snap ->
                if (snap.exists) snap.toCloudFriends() else emptyList()
            },
        )
    }

    fun observeFriendRequests(uid: String): Flow<FriendRequests> = flow {
        emitAll(
            db.document("players/$uid/private/social").snapshots.map { snap ->
                if (snap.exists) snap.toFriendRequests() else FriendRequests(emptyMap(), emptyMap())
            },
        )
    }

    fun observeHeadToHead(uid: String): Flow<HeadToHead> = flow {
        emitAll(
            db.document("players/$uid/private/headToHead").snapshots.map { snap ->
                if (snap.exists) snap.toHeadToHead() else HeadToHead(emptyMap())
            },
        )
    }

    suspend fun sendFriendRequest(username: String) {
        functions.httpsCallable("sendFriendRequest")(hashMapOf("handle" to username))
    }

    suspend fun acceptFriendRequest(requesterUid: String) {
        functions.httpsCallable("acceptFriendRequest")(hashMapOf("requesterUid" to requesterUid))
    }

    suspend fun declineFriendRequest(requesterUid: String) {
        functions.httpsCallable("declineFriendRequest")(hashMapOf("requesterUid" to requesterUid))
    }

    suspend fun unfriend(otherUid: String) {
        functions.httpsCallable("unfriend")(hashMapOf("otherUid" to otherUid))
    }
}
