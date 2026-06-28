package se.atte.bragwise.data

import kotlin.js.JsAny
import kotlinx.coroutines.await
import kotlinx.coroutines.channels.awaitClose
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.callbackFlow
import kotlinx.coroutines.flow.map
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.put
import se.atte.bragwise.domain.Player
import se.atte.bragwise.domain.PublicProfile
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
import se.atte.bragwise.firebase.snapshotId

class JsProfileRemote : ProfileRemote {

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

    override fun observePlayer(uid: String): Flow<Player?> =
        docSnapshots("players/$uid").map { snap ->
            val dataJson = snapshotDataJson(snap) ?: return@map null
            parsePlayer(snapshotId(snap), dataJson)
        }

    override fun observePublicProfile(uid: String): Flow<PublicProfile?> =
        docSnapshots("publicProfiles/$uid").map { snap ->
            val dataJson = snapshotDataJson(snap) ?: return@map null
            parsePublicProfile(snapshotId(snap), dataJson)
        }

    override fun observeNotificationPrefs(uid: String): Flow<NotificationPrefs> =
        docSnapshots("players/$uid/private/preferences").map { snap ->
            val dataJson = snapshotDataJson(snap) ?: return@map NotificationPrefs.DEFAULT
            parseNotificationPrefs(dataJson)
        }

    override suspend fun setMasterNotification(enabled: Boolean) = mapErrors {
        val payload = buildJsonObject { put("enabled", enabled) }
        callFunctionJson(functions, "setNotificationPref", payload.toString()).await<JsAny?>()
        Unit
    }

    override suspend fun setCategoryNotification(key: String, enabled: Boolean) = mapErrors {
        val payload = buildJsonObject {
            put("categories", buildJsonObject { put(key, enabled) })
        }
        callFunctionJson(functions, "setNotificationPref", payload.toString()).await<JsAny?>()
        Unit
    }

    override suspend fun recordActivity() = mapErrors {
        callFunctionJson(functions, "recordActivity", "{}").await<JsAny?>()
        Unit
    }

    override suspend fun claimUsername(username: String) = mapErrors {
        val payload = buildJsonObject { put("handle", username) }
        callFunctionJson(functions, "claimHandle", payload.toString()).await<JsAny?>()
        Unit
    }

    override suspend fun updateProfile(displayName: String?, username: String?, avatarSeed: String?) = mapErrors {
        val updates = buildMap<String, String> {
            displayName?.let { put("displayName", it) }
            username?.let { put("handle", it) }
            avatarSeed?.let { put("avatarSeed", it) }
        }
        if (updates.isNotEmpty()) {
            val payload = buildJsonObject {
                updates.forEach { (k, v) -> put(k, v) }
            }
            callFunctionJson(functions, "updateProfile", payload.toString()).await<JsAny?>()
        }
        Unit
    }
}
