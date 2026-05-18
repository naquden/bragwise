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

    suspend fun claimHandle(handle: String) {
        functions.httpsCallable("claimHandle")(hashMapOf("handle" to handle))
    }

    suspend fun updateProfile(
        displayName: String?,
        handle: String?,
        avatarSeed: String?,
    ) {
        val data = hashMapOf<String, Any?>()
        displayName?.let { data["displayName"] = it }
        handle?.let { data["handle"] = it }
        avatarSeed?.let { data["avatarSeed"] = it }
        if (data.isNotEmpty()) {
            functions.httpsCallable("updateProfile")(data)
        }
    }
}
