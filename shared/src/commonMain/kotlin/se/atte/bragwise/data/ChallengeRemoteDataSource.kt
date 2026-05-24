@file:OptIn(kotlinx.coroutines.ExperimentalCoroutinesApi::class)

package se.atte.bragwise.data

import dev.gitlive.firebase.Firebase
import dev.gitlive.firebase.firestore.Direction
import dev.gitlive.firebase.firestore.FirebaseFirestore
import dev.gitlive.firebase.firestore.firestore
import dev.gitlive.firebase.functions.FirebaseFunctions
import dev.gitlive.firebase.functions.functions
import kotlinx.coroutines.flow.Flow
import kotlinx.serialization.builtins.MapSerializer
import kotlinx.serialization.builtins.nullable
import kotlinx.serialization.builtins.serializer
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.emitAll
import kotlinx.coroutines.flow.flatMapLatest
import kotlinx.coroutines.flow.flow
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.flow.map
import se.atte.bragwise.domain.Challenge
import se.atte.bragwise.domain.ChallengeDetail
import se.atte.bragwise.domain.Invitation
import se.atte.bragwise.domain.LeaderboardEntry
import se.atte.bragwise.domain.Prediction

class ChallengeRemoteDataSource(
    private val db: FirebaseFirestore = Firebase.firestore,
    private val functions: FirebaseFunctions = Firebase.functions(FUNCTIONS_REGION),
) {
    // ── Reads ────────────────────────────────────────────────────────────────

    fun observePromoted(): Flow<List<Challenge>> = flow {
        emitAll(
            db.collection("challenges")
                .where { "promoted" equalTo true }
                .where { "status" equalTo "OPEN" }
                .orderBy("locksAt", Direction.ASCENDING)
                .snapshots
                .map { snap ->
                    snap.documents.mapNotNull { doc ->
                        runCatching { doc.toChallenge() }.getOrNull()
                    }
                },
        )
    }

    fun observeCreatedBy(uid: String): Flow<List<Challenge>> = flow {
        emitAll(
            db.collection("challenges")
                .where { "createdBy" equalTo uid }
                .orderBy("createdAt", Direction.DESCENDING)
                .snapshots
                .map { snap ->
                    snap.documents.mapNotNull { doc ->
                        runCatching { doc.toChallenge() }.getOrNull()
                    }
                },
        )
    }

    /**
     * Challenges the user has joined but didn't create. Uses a collection-group
     * query on `players` to find their membership docs, then fetches each parent
     * challenge doc. Re-subscribes whenever the membership set changes.
     */
    fun observeJoined(uid: String): Flow<List<Challenge>> = flow {
        emitAll(
            db.collectionGroup("players")
                .where { "uid" equalTo uid }
                .snapshots
                .flatMapLatest { playerSnap ->
                    val ids = playerSnap.documents
                        .mapNotNull { it.reference.parent.parent?.id }
                        .distinct()
                    if (ids.isEmpty()) return@flatMapLatest flowOf(emptyList())
                    combine(
                        ids.map { challengeId ->
                            db.document("challenges/$challengeId").snapshots.map { snap ->
                                runCatching { if (snap.exists) snap.toChallenge() else null }.getOrNull()
                            }
                        },
                    ) { challenges -> challenges.filterNotNull() }
                },
        )
    }

    fun observePendingInvites(uid: String): Flow<List<Invitation>> = flow {
        emitAll(
            db.collectionGroup("invitations")
                .where { "invitedUid" equalTo uid }
                .snapshots
                .map { snap ->
                    snap.documents.mapNotNull { doc ->
                        val challengeId = doc.reference.parent.parent?.id ?: return@mapNotNull null
                        runCatching { doc.toInvitation(challengeId) }.getOrNull()
                    }
                },
        )
    }

    fun observeChallengeDetail(challengeId: String, myUid: String): Flow<ChallengeDetail> = flow {
        val challengeFlow = db.document("challenges/$challengeId").snapshots
            .map { snap -> snap.toChallenge() }
        val playerFlow = db.document("challenges/$challengeId/players/$myUid").snapshots
            .map { snap ->
                if (!snap.exists) return@map emptyMap<String, se.atte.bragwise.domain.PredictionPayload>()
                snap.toPredictionsMap()
            }

        emitAll(
            combine(challengeFlow, playerFlow) { challenge, myPredictions ->
                val myPoints = challenge.leaderboard?.get(myUid)
                val rank = challenge.leaderboard
                    ?.entries
                    ?.sortedByDescending { it.value }
                    ?.indexOfFirst { it.key == myUid }
                    ?.takeIf { it >= 0 }
                    ?.let { it + 1 }
                ChallengeDetail(
                    challenge = challenge,
                    myPredictions = myPredictions,
                    myRank = rank,
                )
            },
        )
    }

    /**
     * Leaderboard sorted by points desc. Resolves display names from
     * `publicProfiles` once per call (snapshot-driven reload). For large
     * challenges Phase 2+ should paginate.
     */
    fun observeLeaderboard(challengeId: String): Flow<List<LeaderboardEntry>> = flow {
        emitAll(
            db.document("challenges/$challengeId").snapshots
                .map { snap ->
                    val board = snap.toLeaderboardMap() ?: return@map emptyList()
                    board.entries
                        .sortedByDescending { it.value }
                        .mapIndexed { idx, (uid, pts) ->
                            LeaderboardEntry(
                                uid = uid,
                                displayName = uid,
                                points = pts,
                                rank = idx + 1,
                            )
                        }
                },
        )
    }

    // ── Writes (callables) ───────────────────────────────────────────────────

    suspend fun createChallenge(challenge: Challenge): String {
        val data = hashMapOf<String, Any?>(
            "title" to challenge.title,
            "description" to challenge.description,
            "category" to challenge.category,
            "visibility" to challenge.visibility.name,
            "locksAt" to checkNotNull(challenge.locksAt) { "locksAt required to create a challenge" }.toString(),
            "bets" to challenge.bets.map { it.toMap() },
        )
        val result = functions.httpsCallable("createChallenge")(data)
        val resultData = result.data(MapSerializer(String.serializer(), String.serializer().nullable))
        return resultData["challengeId"] ?: error("createChallenge returned no challengeId")
    }

    suspend fun updateDraft(challenge: Challenge) {
        val data = hashMapOf<String, Any?>(
            "challengeId" to challenge.id,
            "title" to challenge.title,
            "description" to challenge.description,
            "category" to challenge.category,
            "visibility" to challenge.visibility.name,
            "locksAt" to checkNotNull(challenge.locksAt) { "locksAt required to update a draft" }.toString(),
            "bets" to challenge.bets.map { it.toMap() },
        )
        functions.httpsCallable("updateDraft")(data)
    }

    suspend fun publishChallenge(challengeId: String) {
        functions.httpsCallable("publishChallenge")(hashMapOf("challengeId" to challengeId))
    }

    suspend fun submitPredictions(challengeId: String, predictions: List<Prediction>) {
        val data = hashMapOf(
            "challengeId" to challengeId,
            "predictions" to predictions.map { p ->
                hashMapOf("betId" to p.betId, "payload" to p.payload.toMap())
            },
        )
        println("$REMOTE_DBG submitPredictions.call challengeId=$challengeId count=${predictions.size}")
        try {
            functions.httpsCallable("submitPredictions")(data)
            println("$REMOTE_DBG submitPredictions.ok challengeId=$challengeId")
        } catch (e: Throwable) {
            println("$REMOTE_DBG submitPredictions.err class=${e::class.simpleName} message=${e.message}")
            println("$REMOTE_DBG submitPredictions.err.stack ${e.stackTraceToString()}")
            throw e
        }
    }

    suspend fun postResults(challengeId: String, results: Map<String, se.atte.bragwise.domain.PredictionPayload>) {
        val data = hashMapOf(
            "challengeId" to challengeId,
            "results" to results.mapValues { (_, v) -> v.toMap() },
        )
        functions.httpsCallable("postResults")(data)
    }

    suspend fun inviteFriends(challengeId: String, uids: List<String>) {
        functions.httpsCallable("inviteFriends")(hashMapOf("challengeId" to challengeId, "uids" to uids))
    }
}

private fun se.atte.bragwise.domain.Bet.toMap(): Map<String, Any?> = when (this) {
    is se.atte.bragwise.domain.Bet.SinglePick -> mapOf(
        "kind" to "SINGLE_PICK", "id" to id, "title" to title,
        "optionType" to optionType.name,
        "options" to options.map { mapOf("id" to it.id, "label" to it.label, "countryCode" to it.countryCode) },
    )
    is se.atte.bragwise.domain.Bet.Ranking -> mapOf(
        "kind" to "RANKING", "id" to id, "title" to title,
        "optionType" to optionType.name, "topN" to topN,
        "options" to options.map { mapOf("id" to it.id, "label" to it.label, "countryCode" to it.countryCode) },
    )
    is se.atte.bragwise.domain.Bet.BooleanProp -> mapOf(
        "kind" to "BOOLEAN_PROP", "id" to id, "title" to title,
    )
}

private const val REMOTE_DBG = "BRAGWISE_REMOTE_9c95cf"
