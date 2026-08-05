package se.atte.bragwise.data

import dev.gitlive.firebase.Firebase
import dev.gitlive.firebase.firestore.firestore
import dev.gitlive.firebase.functions.functions
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.emitAll
import kotlinx.coroutines.flow.flow
import kotlinx.coroutines.flow.map
import kotlinx.serialization.EncodeDefault
import kotlinx.serialization.ExperimentalSerializationApi
import kotlinx.serialization.Serializable
import se.atte.bragwise.domain.Player
import se.atte.bragwise.domain.PublicProfile

class ProfileRemoteDataSource(
    private val db: dev.gitlive.firebase.firestore.FirebaseFirestore = Firebase.firestore,
    private val functions: dev.gitlive.firebase.functions.FirebaseFunctions = Firebase.functions(FUNCTIONS_REGION),
) : ProfileRemote {
    override fun observePlayer(uid: String): Flow<Player?> = flow {
        emitAll(
            db.document("players/$uid").snapshots.map { snap ->
                if (snap.exists) snap.toPlayer() else null
            },
        )
    }

    override fun observePublicProfile(uid: String): Flow<PublicProfile?> = flow {
        emitAll(
            db.document("publicProfiles/$uid").snapshots.map { snap ->
                if (snap.exists) snap.toPublicProfile() else null
            },
        )
    }

    override fun observeNotificationPrefs(uid: String): Flow<NotificationPrefs> = flow {
        emitAll(
            db.document("players/$uid/private/preferences").snapshots.map { snap ->
                if (!snap.exists) return@map NotificationPrefs.DEFAULT
                val cats = runCatching { snap.get<Map<String, Boolean>>("categories") }.getOrNull() ?: emptyMap()
                NotificationPrefs(
                    master = snap.boolOrNull("notifications") ?: true,
                    social = cats["social"] ?: true,
                    results = cats["results"] ?: true,
                    participations = cats["participations"] ?: true,
                    invites = cats["invites"] ?: true,
                )
            },
        )
    }

    override suspend fun setMasterNotification(enabled: Boolean) = mapErrors {
        functions.httpsCallable("setNotificationPref").invoke(SetNotificationPrefPayload(enabled = enabled))
        Unit
    }

    override suspend fun setCategoryNotification(key: String, enabled: Boolean) = mapErrors {
        functions.httpsCallable("setNotificationPref").invoke(SetNotificationPrefPayload(categories = mapOf(key to enabled)))
        Unit
    }

    override suspend fun recordActivity() = mapErrors {
        functions.httpsCallable("recordActivity")(emptyMap<String, Any?>())
        Unit
    }

    override suspend fun claimUsername(username: String) = mapErrors {
        functions.httpsCallable("claimHandle")(hashMapOf("handle" to username))
        Unit
    }

    override suspend fun updateProfile(
        displayName: String?,
        username: String?,
        avatarSeed: String?,
    ) = mapErrors {
        val data = hashMapOf<String, Any?>()
        displayName?.let { data["displayName"] = it }
        username?.let { data["handle"] = it }
        avatarSeed?.let { data["avatarSeed"] = it }
        if (data.isNotEmpty()) {
            functions.httpsCallable("updateProfile")(data)
        }
        Unit
    }
}

@OptIn(ExperimentalSerializationApi::class)
@Serializable
private data class SetNotificationPrefPayload(
    @EncodeDefault(EncodeDefault.Mode.NEVER) val enabled: Boolean? = null,
    @EncodeDefault(EncodeDefault.Mode.NEVER) val categories: Map<String, Boolean>? = null,
)
