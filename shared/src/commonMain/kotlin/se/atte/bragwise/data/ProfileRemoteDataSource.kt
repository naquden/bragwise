package se.atte.bragwise.data

import dev.gitlive.firebase.Firebase
import dev.gitlive.firebase.firestore.firestore
import dev.gitlive.firebase.functions.functions
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.emitAll
import kotlinx.coroutines.flow.flow
import kotlinx.coroutines.flow.map
import se.atte.bragwise.domain.Player
import se.atte.bragwise.domain.PublicProfile

class ProfileRemoteDataSource(
    private val db: dev.gitlive.firebase.firestore.FirebaseFirestore = Firebase.firestore,
    private val functions: dev.gitlive.firebase.functions.FirebaseFunctions = Firebase.functions(FUNCTIONS_REGION),
) {
    fun observePlayer(uid: String): Flow<Player?> = flow {
        emitAll(
            db.document("players/$uid").snapshots.map { snap ->
                if (snap.exists) snap.toPlayer() else null
            },
        )
    }

    fun observePublicProfile(uid: String): Flow<PublicProfile?> = flow {
        emitAll(
            db.document("publicProfiles/$uid").snapshots.map { snap ->
                if (snap.exists) snap.toPublicProfile() else null
            },
        )
    }

    /** Notifications-enabled pref. Defaults to true when the doc/field is absent. */
    fun observeNotificationsEnabled(uid: String): Flow<Boolean> = flow {
        emitAll(
            db.document("players/$uid/private/preferences").snapshots.map { snap ->
                if (!snap.exists) return@map true
                snap.boolOrNull("notifications") ?: true
            },
        )
    }

    suspend fun setNotificationsEnabled(enabled: Boolean) {
        functions.httpsCallable("setNotificationPref")(hashMapOf("enabled" to enabled))
    }

    suspend fun recordActivity() {
        functions.httpsCallable("recordActivity")(emptyMap<String, Any?>())
    }

    suspend fun claimUsername(username: String) {
        functions.httpsCallable("claimHandle")(hashMapOf("handle" to username))
    }

    suspend fun updateProfile(
        displayName: String?,
        username: String?,
        avatarSeed: String?,
    ) {
        val data = hashMapOf<String, Any?>()
        displayName?.let { data["displayName"] = it }
        username?.let { data["handle"] = it }
        avatarSeed?.let { data["avatarSeed"] = it }
        if (data.isNotEmpty()) {
            functions.httpsCallable("updateProfile")(data)
        }
    }
}
