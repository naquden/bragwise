@file:OptIn(kotlinx.coroutines.ExperimentalCoroutinesApi::class)

package se.atte.bragwise.data

import dev.gitlive.firebase.Firebase
import dev.gitlive.firebase.firestore.Direction
import dev.gitlive.firebase.firestore.FirebaseFirestore
import dev.gitlive.firebase.firestore.firestore
import dev.gitlive.firebase.functions.FirebaseFunctions
import dev.gitlive.firebase.functions.functions
import dev.gitlive.firebase.firestore.FirebaseFirestoreException
import dev.gitlive.firebase.firestore.FirestoreExceptionCode
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.catch
import kotlinx.serialization.builtins.ListSerializer
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
import se.atte.bragwise.domain.GENERIC_DISPLAY_NAME
import se.atte.bragwise.domain.Invitation
import se.atte.bragwise.domain.LeaderboardEntry
import se.atte.bragwise.domain.Prediction
import se.atte.bragwise.domain.PredictionPayload
import se.atte.bragwise.domain.PublicProfile
import se.atte.bragwise.domain.scoring.competitionRanks

/** Thrown when a Firestore PERMISSION_DENIED indicates the challenge no longer exists for this user. */
class ChallengeGoneException : Exception("challenge-gone")

class ChallengeRemoteDataSource(
    private val db: FirebaseFirestore = Firebase.firestore,
    private val functions: FirebaseFunctions = Firebase.functions(FUNCTIONS_REGION),
) {
    // ── Reads ────────────────────────────────────────────────────────────────

    fun observePromoted(): Flow<List<Challenge>> = flow {
        emitAll(
            db.collection("challenges")
                .where { "visibility" equalTo "PROMOTED" }
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
                            db.document("challenges/$challengeId").snapshots
                                .map { snap -> runCatching { if (snap.exists) snap.toChallenge() else null }.getOrNull() }
                                .catch { e ->
                                    if (e is FirebaseFirestoreException && e.code == FirestoreExceptionCode.PERMISSION_DENIED) emit(null)
                                    else throw e
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
            .catch { e ->
                if (e is FirebaseFirestoreException && e.code == FirestoreExceptionCode.PERMISSION_DENIED) {
                    // Challenge was deleted — rethrow as a recognisable sentinel so the
                    // detail screen navigates away instead of showing an error.
                    throw ChallengeGoneException()
                } else throw e
            }
        val playerFlow = if (myUid.isEmpty()) {
            flowOf(emptyMap<String, se.atte.bragwise.domain.PredictionPayload>())
        } else {
            db.document("challenges/$challengeId/players/$myUid").snapshots
                .map { snap ->
                    if (!snap.exists) return@map emptyMap<String, se.atte.bragwise.domain.PredictionPayload>()
                    snap.toPredictionsMap()
                }
        }

        emitAll(
            combine(challengeFlow, playerFlow) { challenge, myPredictions ->
                val rank = challenge.leaderboard
                    ?.let { competitionRanks(it).firstOrNull { e -> e.uid == myUid }?.rank }
                ChallengeDetail(
                    challenge = challenge,
                    myPredictions = myPredictions,
                    myRank = rank,
                )
            },
        )
    }

    /**
     * Leaderboard sorted by points desc, names and avatars resolved from publicProfiles.
     * Co-winners (equal points) share a rank number and have isTied = true.
     * Secondary sort is by uid for deterministic stable ordering.
     */
    fun observeLeaderboard(challengeId: String): Flow<List<LeaderboardEntry>> = flow {
        emitAll(
            db.document("challenges/$challengeId").snapshots
                .flatMapLatest { snap ->
                    val board = snap.toLeaderboardMap()
                    if (board.isNullOrEmpty()) return@flatMapLatest flowOf(emptyList())
                    val sortedEntries = board.entries
                        .sortedWith(compareByDescending<Map.Entry<String, Int>> { it.value }.thenBy { it.key })
                    val profileFlows = sortedEntries.map { (uid, _) ->
                        db.document("publicProfiles/$uid").snapshots.map { profileSnap ->
                            if (profileSnap.exists) profileSnap.toPublicProfile() else null
                        }
                    }
                    combine(profileFlows) { profileArray ->
                        buildLeaderboardEntries(sortedEntries = sortedEntries, profiles = profileArray.toList())
                    }
                }
        )
    }

    /**
     * Open FRIENDS-visibility challenges created by any of the given uids.
     * Chunked into groups of 30 (Firestore `inArray` limit); chunks are
     * combined into a single deduplicated list.
     */
    fun observeFromFriends(friendUids: List<String>): Flow<List<Challenge>> {
        if (friendUids.isEmpty()) return flowOf(emptyList())
        val chunks = friendUids.chunked(30)
        val chunkFlows = chunks.map { chunk ->
            flow {
                emitAll(
                    db.collection("challenges")
                        .where { "createdBy" inArray chunk }
                        .where { "status" equalTo "OPEN" }
                        .where { "visibility" equalTo "FRIENDS" }
                        .snapshots
                        .map { snap ->
                            snap.documents.mapNotNull { doc ->
                                runCatching { doc.toChallenge() }.getOrNull()
                            }
                        },
                )
            }
        }
        return combine(chunkFlows) { arrays -> arrays.flatMap { it }.distinctBy { it.id } }
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
            "betsVisible" to challenge.betsVisible,
            "invitedUids" to challenge.invitedUids.toList(),
        )
        val result = functions.httpsCallable("createChallenge")(data)
        val resultData = result.data(MapSerializer(String.serializer(), String.serializer().nullable))
        return resultData["challengeId"] ?: error("createChallenge returned no challengeId")
    }

    fun observeParticipantPredictions(challengeId: String, uid: String): Flow<Map<String, PredictionPayload>> = flow {
        emitAll(
            db.document("challenges/$challengeId/players/$uid").snapshots
                .map { snap ->
                    if (!snap.exists) emptyMap()
                    else snap.toPredictionsMap()
                },
        )
    }

    suspend fun submitPredictions(challengeId: String, predictions: List<Prediction>) {
        val data = hashMapOf(
            "challengeId" to challengeId,
            "predictions" to predictions.map { p ->
                hashMapOf("betId" to p.betId, "payload" to p.payload.toMap())
            },
        )
        functions.httpsCallable("submitPredictions")(data)
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

    suspend fun deleteChallenge(challengeId: String) {
        functions.httpsCallable("deleteChallenge")(hashMapOf("challengeId" to challengeId))
    }

    /**
     * Replay guest predictions into the cloud on authenticate.
     * Server skips challenges the account already predicted (never clobbers).
     * Returns per-challenge id arrays: migrated (new), skipped (already existed),
     * failed (locked / ineligible / invalid).
     */
    suspend fun migrateGuestData(predictions: List<LocalPrediction>): MigrationSummary {
        val data = hashMapOf(
            "predictions" to predictions.map { p ->
                hashMapOf("challengeId" to p.challengeId, "betId" to p.betId, "payload" to p.payload.toMap())
            },
        )
        val result = functions.httpsCallable("migrateGuestData")(data)
        val lists = result.data(MapSerializer(String.serializer(), ListSerializer(String.serializer())))
        val migrated = lists["migrated"]?.size ?: 0
        val skipped = lists["skipped"]?.size ?: 0
        val failed = lists["failed"]?.size ?: 0
        return MigrationSummary(migrated = migrated, skipped = skipped, failed = failed)
    }
}

private fun buildLeaderboardEntries(
    sortedEntries: List<Map.Entry<String, Int>>,
    profiles: List<PublicProfile?>,
): List<LeaderboardEntry> {
    val profileByUid: Map<String, PublicProfile?> = sortedEntries
        .mapIndexed { idx, entry -> entry.key to profiles.getOrNull(idx) }
        .toMap()
    val board = sortedEntries.associate { it.key to it.value }
    val ranks = competitionRanks(board)
    return ranks.map { entry ->
        val profile = profileByUid[entry.uid]
        val isTied = ranks.count { it.rank == entry.rank } > 1
        LeaderboardEntry(
            uid = entry.uid,
            displayName = profile?.displayName?.takeIf { it.isNotBlank() } ?: GENERIC_DISPLAY_NAME,
            avatarSeed = profile?.avatarSeed?.takeIf { it.isNotBlank() } ?: "",
            points = entry.points,
            rank = entry.rank,
            isTied = isTied,
        )
    }
}

private fun se.atte.bragwise.domain.Bet.toMap(): Map<String, Any?> = when (this) {
    is se.atte.bragwise.domain.Bet.SinglePick -> mapOf(
        "kind" to "SINGLE_PICK", "id" to id, "title" to title,
        "optionType" to optionType.name,
        "options" to options.map { it.toMap() },
    )
    is se.atte.bragwise.domain.Bet.Ranking -> mapOf(
        "kind" to "RANKING", "id" to id, "title" to title,
        "optionType" to optionType.name, "topN" to topN,
        "options" to options.map { it.toMap() },
    )
    is se.atte.bragwise.domain.Bet.BooleanProp -> mapOf(
        "kind" to "BOOLEAN_PROP", "id" to id, "title" to title,
    )
}

private fun se.atte.bragwise.domain.BetOption.toMap(): Map<String, Any> = buildMap {
    put("id", id)
    put("label", label)
    countryCode?.let { put("countryCode", it) }
}

