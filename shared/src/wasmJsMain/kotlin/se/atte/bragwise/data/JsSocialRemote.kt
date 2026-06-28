package se.atte.bragwise.data

import kotlin.js.JsAny
import kotlinx.coroutines.await
import kotlinx.coroutines.channels.awaitClose
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.callbackFlow
import kotlinx.coroutines.flow.map
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.put
import se.atte.bragwise.domain.CloudFriend
import se.atte.bragwise.domain.FriendRequests
import se.atte.bragwise.domain.HeadToHead
import se.atte.bragwise.firebase.JsFirestore
import se.atte.bragwise.firebase.JsFunctions
import se.atte.bragwise.firebase.callFunctionJson
import se.atte.bragwise.firebase.doc
import se.atte.bragwise.firebase.firestoreErrorCode
import se.atte.bragwise.firebase.getFirebaseApp
import se.atte.bragwise.firebase.getFunctions
import se.atte.bragwise.firebase.getFirestore
import se.atte.bragwise.firebase.onDocSnapshot
import se.atte.bragwise.firebase.snapshotDataJson

class JsSocialRemote : SocialRemote {

    private val db: JsFirestore by lazy { getFirestore() }
    private val functions: JsFunctions by lazy { getFunctions(getFirebaseApp(), FUNCTIONS_REGION) }

    private fun docSnapshots(path: String): Flow<JsAny> = callbackFlow {
        val unsub = onDocSnapshot(
            ref = doc(db, path),
            onNext = { trySend(it) },
            onError = { err -> close(Exception(firestoreErrorCode(err))) },
        )
        awaitClose { unsub() }
    }

    override fun observeCloudFriends(uid: String): Flow<List<CloudFriend>> =
        docSnapshots("players/$uid/private/social").map { snap ->
            val dataJson = snapshotDataJson(snap) ?: return@map emptyList()
            parseCloudFriends(dataJson)
        }

    override fun observeFriendRequests(uid: String): Flow<FriendRequests> =
        docSnapshots("players/$uid/private/social").map { snap ->
            val dataJson = snapshotDataJson(snap) ?: return@map FriendRequests(emptyMap(), emptyMap())
            parseFriendRequests(dataJson)
        }

    override fun observeHeadToHead(uid: String): Flow<HeadToHead> =
        docSnapshots("players/$uid/private/headToHead").map { snap ->
            val dataJson = snapshotDataJson(snap) ?: return@map HeadToHead(emptyMap())
            parseHeadToHead(dataJson)
        }

    override suspend fun sendFriendRequest(username: String) = mapErrors {
        val payload = buildJsonObject { put("handle", username) }
        callFunctionJson(functions, "sendFriendRequest", payload.toString()).await<JsAny?>()
        Unit
    }

    override suspend fun acceptFriendRequest(requesterUid: String) = mapErrors {
        val payload = buildJsonObject { put("requesterUid", requesterUid) }
        callFunctionJson(functions, "acceptFriendRequest", payload.toString()).await<JsAny?>()
        Unit
    }

    override suspend fun declineFriendRequest(requesterUid: String) = mapErrors {
        val payload = buildJsonObject { put("requesterUid", requesterUid) }
        callFunctionJson(functions, "declineFriendRequest", payload.toString()).await<JsAny?>()
        Unit
    }

    override suspend fun withdrawFriendRequest(otherUid: String) = mapErrors {
        val payload = buildJsonObject { put("otherUid", otherUid) }
        callFunctionJson(functions, "withdrawFriendRequest", payload.toString()).await<JsAny?>()
        Unit
    }

    override suspend fun unfriend(otherUid: String) = mapErrors {
        val payload = buildJsonObject { put("otherUid", otherUid) }
        callFunctionJson(functions, "unfriend", payload.toString()).await<JsAny?>()
        Unit
    }
}
