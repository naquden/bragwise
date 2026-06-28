@file:OptIn(kotlinx.coroutines.ExperimentalCoroutinesApi::class)

package se.atte.bragwise.data

import kotlin.js.JsAny
import kotlinx.coroutines.await
import kotlinx.coroutines.channels.awaitClose
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.callbackFlow
import kotlinx.coroutines.flow.catch
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.emitAll
import kotlinx.coroutines.flow.flatMapLatest
import kotlinx.coroutines.flow.flow
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.flow.map
import kotlinx.serialization.Serializable
import kotlinx.serialization.builtins.ListSerializer
import kotlinx.serialization.builtins.serializer
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonArray
import kotlinx.serialization.json.JsonElement
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.JsonNull
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.decodeFromJsonElement
import kotlinx.serialization.json.put
import kotlinx.serialization.json.putJsonArray
import se.atte.bragwise.domain.Challenge
import se.atte.bragwise.domain.ChallengeDetail
import se.atte.bragwise.domain.GENERIC_DISPLAY_NAME
import se.atte.bragwise.domain.Invitation
import se.atte.bragwise.domain.LeaderboardEntry
import se.atte.bragwise.domain.Prediction
import se.atte.bragwise.domain.PredictionPayload
import se.atte.bragwise.domain.PublicProfile
import se.atte.bragwise.domain.Reaction
import se.atte.bragwise.domain.scoring.competitionRanks
import se.atte.bragwise.firebase.JsFirestore
import se.atte.bragwise.firebase.JsFunctions
import se.atte.bragwise.firebase.JsQuery
import se.atte.bragwise.firebase.callFunctionJson
import se.atte.bragwise.firebase.doc
import se.atte.bragwise.firebase.firestoreErrorCode
import se.atte.bragwise.firebase.getFirebaseApp
import se.atte.bragwise.firebase.getFunctions
import se.atte.bragwise.firebase.getFirestore
import se.atte.bragwise.firebase.onDocSnapshot
import se.atte.bragwise.firebase.onQuerySnapshot
import se.atte.bragwise.firebase.queryCollection
import se.atte.bragwise.firebase.queryCreatedBy
import se.atte.bragwise.firebase.queryFromFriendsChunk
import se.atte.bragwise.firebase.queryInvitationsByUid
import se.atte.bragwise.firebase.queryPlayersByUid
import se.atte.bragwise.firebase.queryPromoted
import se.atte.bragwise.firebase.querySnapshotJson
import se.atte.bragwise.firebase.snapshotDataJson
import se.atte.bragwise.firebase.snapshotId
import kotlin.time.Instant

class JsChallengeRemote : ChallengeRemote {

    private val db: JsFirestore by lazy { getFirestore() }
    private val functions: JsFunctions by lazy { getFunctions(getFirebaseApp(), FUNCTIONS_REGION) }

    // ── Flow helpers ──────────────────────────────────────────────────────────

    private fun docSnapshots(path: String): Flow<JsAny> = callbackFlow {
        val unsub = onDocSnapshot(
            ref = doc(db, path),
            onNext = { trySend(it) },
            onError = { err -> close(Exception(firestoreErrorCode(err))) },
        )
        awaitClose { unsub() }
    }

    private fun querySnapshots(query: JsQuery): Flow<JsAny> = callbackFlow {
        val unsub = onQuerySnapshot(
            query = query,
            onNext = { trySend(it) },
            onError = { err -> close(Exception(firestoreErrorCode(err))) },
        )
        awaitClose { unsub() }
    }

    /** Emits null on permission-denied; re-throws everything else. */
    private fun docSnapshotsPermissive(path: String): Flow<JsAny?> =
        docSnapshots(path)
            .map<JsAny, JsAny?> { it }
            .catch { e ->
                if ("permission-denied" in (e.message ?: "")) emit(null)
                else throw e
            }

    // ── Reads ─────────────────────────────────────────────────────────────────

    override fun observePromoted(): Flow<List<Challenge>> =
        querySnapshots(queryPromoted(db)).map { snap ->
            parseQuerySnapshot(querySnapshotJson(snap)).mapNotNull { it.toChallengeOrNull() }
        }

    override fun observeCreatedBy(uid: String): Flow<List<Challenge>> =
        querySnapshots(queryCreatedBy(db, uid)).map { snap ->
            parseQuerySnapshot(querySnapshotJson(snap)).mapNotNull { it.toChallengeOrNull() }
        }

    override fun observeJoined(uid: String): Flow<List<Challenge>> =
        querySnapshots(queryPlayersByUid(db, uid))
            .flatMapLatest { playerSnap ->
                val ids = extractGrandparentIds(playerSnap)
                if (ids.isEmpty()) return@flatMapLatest flowOf(emptyList())
                combine(
                    ids.map { challengeId ->
                        docSnapshotsPermissive("challenges/$challengeId").map { snap ->
                            if (snap == null) return@map null
                            val dataJson = snapshotDataJson(snap) ?: return@map null
                            parseChallenge(snapshotId(snap), dataJson)
                        }
                    },
                ) { challenges -> challenges.filterNotNull() }
            }

    override fun observePendingInvites(uid: String): Flow<List<Invitation>> =
        querySnapshots(queryInvitationsByUid(db, uid)).map { snap ->
            parseInvitationsWithGrandparentIds(snap)
        }

    override fun observeChallengesByIds(ids: List<String>): Flow<List<Challenge>> {
        if (ids.isEmpty()) return flowOf(emptyList())
        return combine(
            ids.map { challengeId ->
                docSnapshotsPermissive("challenges/$challengeId").map { snap ->
                    if (snap == null) return@map null
                    val dataJson = snapshotDataJson(snap) ?: return@map null
                    parseChallenge(snapshotId(snap), dataJson)
                }
            },
        ) { challenges -> challenges.filterNotNull() }
    }

    override fun observeChallengeDetail(challengeId: String, myUid: String): Flow<ChallengeDetail> = flow {
        val challengeFlow = docSnapshots("challenges/$challengeId")
            .map { snap ->
                val dataJson = snapshotDataJson(snap)
                    ?: throw ChallengeGoneException()
                parseChallenge(snapshotId(snap), dataJson)
                    ?: throw ChallengeGoneException()
            }
            .catch { e ->
                if ("permission-denied" in (e.message ?: "") || e is ChallengeGoneException) {
                    throw ChallengeGoneException()
                } else throw e
            }

        val playerFlow: Flow<Map<String, PredictionPayload>> = if (myUid.isEmpty()) {
            flowOf(emptyMap())
        } else {
            docSnapshots("challenges/$challengeId/players/$myUid").map { snap ->
                val dataJson = snapshotDataJson(snap) ?: return@map emptyMap<String, PredictionPayload>()
                parsePredictionsMap(dataJson)
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

    override fun observeLeaderboard(challengeId: String): Flow<List<LeaderboardEntry>> = flow {
        emitAll(
            docSnapshots("challenges/$challengeId")
                .flatMapLatest { snap ->
                    val dataJson = snapshotDataJson(snap) ?: return@flatMapLatest flowOf(emptyList())
                    val challenge = parseChallenge(snapshotId(snap), dataJson)
                    val board = challenge?.leaderboard
                    if (board.isNullOrEmpty()) return@flatMapLatest flowOf(emptyList())
                    val sortedEntries = board.entries
                        .sortedWith(compareByDescending<Map.Entry<String, Int>> { it.value }.thenBy { it.key })
                    val profileFlows = sortedEntries.map { (uid, _) ->
                        docSnapshotsPermissive("publicProfiles/$uid").map { profSnap ->
                            if (profSnap == null) return@map null
                            val profJson = snapshotDataJson(profSnap) ?: return@map null
                            parsePublicProfile(snapshotId(profSnap), profJson)
                        }
                    }
                    combine(profileFlows) { profileArray ->
                        buildLeaderboardEntries(sortedEntries, profileArray.toList())
                    }
                },
        )
    }

    override fun observeFromFriends(friendUids: List<String>): Flow<List<Challenge>> {
        if (friendUids.isEmpty()) return flowOf(emptyList())
        val chunks = friendUids.chunked(30)
        val chunkFlows = chunks.map { chunk ->
            val chunkJson = webJson.encodeToString(ListSerializer(String.serializer()), chunk)
            querySnapshots(queryFromFriendsChunk(db, chunkJson)).map { snap ->
                parseQuerySnapshot(querySnapshotJson(snap)).mapNotNull { it.toChallengeOrNull() }
            }
        }
        return combine(chunkFlows) { arrays -> arrays.flatMap { it }.distinctBy { it.id } }
    }

    override fun observeParticipantPredictions(challengeId: String, uid: String): Flow<Map<String, PredictionPayload>> =
        docSnapshots("challenges/$challengeId/players/$uid").map { snap ->
            val dataJson = snapshotDataJson(snap) ?: return@map emptyMap<String, PredictionPayload>()
            parsePredictionsMap(dataJson)
        }

    override fun observeReactions(challengeId: String): Flow<List<Reaction>> =
        querySnapshots(queryCollection(db, "challenges/$challengeId/reactions"))
            .map { snap ->
                parseQuerySnapshot(querySnapshotJson(snap))
                    .mapNotNull { it.toReactionOrNull() }
                    .filter { it.emoji.isNotEmpty() }
            }
            .catch { e ->
                if ("permission-denied" in (e.message ?: "")) emit(emptyList())
                else throw e
            }

    // ── Writes (callables) ────────────────────────────────────────────────────

    override suspend fun createChallenge(challenge: Challenge): String = mapErrors {
        val payload = buildJsonObject {
            put("title", challenge.title)
            put("description", challenge.description)
            put("category", challenge.category)
            put("visibility", challenge.visibility.name)
            put("locksAt", checkNotNull(challenge.locksAt) { "locksAt required" }.toString())
            putJsonArray("bets") {
                challenge.bets.forEach { bet -> add(bet.toJsonObject()) }
            }
            put("betsVisible", challenge.betsVisible)
            putJsonArray("invitedUids") {
                challenge.invitedUids.forEach { uid -> add(JsonPrimitive(uid)) }
            }
        }
        val resultJson = callFunctionJson(functions, "createChallenge", payload.toString())
            .await<JsAny?>()
            ?.toString()
            ?: error("createChallenge returned null")
        val resultObj = webJson.parseToJsonElement(resultJson) as? JsonObject
            ?: error("createChallenge result is not a JSON object")
        val idEl = resultObj["challengeId"] as? JsonPrimitive
        idEl?.content ?: error("createChallenge returned no challengeId")
    }

    override suspend fun submitPredictions(challengeId: String, predictions: List<Prediction>) = mapErrors {
        val payload = buildJsonObject {
            put("challengeId", challengeId)
            put("predictions", JsonArray(
                predictions.map { p ->
                    buildJsonObject {
                        put("betId", p.betId)
                        put("payload", p.payload.toJsonObject())
                    }
                },
            ))
        }
        callFunctionJson(functions, "submitPredictions", payload.toString()).await<JsAny?>()
        Unit
    }

    override suspend fun postResults(challengeId: String, results: Map<String, PredictionPayload>) = mapErrors {
        val payload = buildJsonObject {
            put("challengeId", challengeId)
            put("results", JsonObject(results.mapValues { (_, v) -> v.toJsonObject() as JsonElement }))
        }
        callFunctionJson(functions, "postResults", payload.toString()).await<JsAny?>()
        Unit
    }

    override suspend fun inviteFriends(challengeId: String, uids: List<String>) = mapErrors {
        val payload = buildJsonObject {
            put("challengeId", challengeId)
            putJsonArray("uids") { uids.forEach { uid -> add(JsonPrimitive(uid)) } }
        }
        callFunctionJson(functions, "inviteFriends", payload.toString()).await<JsAny?>()
        Unit
    }

    override suspend fun deleteChallenge(challengeId: String) = mapErrors {
        val payload = buildJsonObject { put("challengeId", challengeId) }
        callFunctionJson(functions, "deleteChallenge", payload.toString()).await<JsAny?>()
        Unit
    }

    override suspend fun setReaction(challengeId: String, emoji: String?) = mapErrors {
        val payload = buildJsonObject {
            put("challengeId", challengeId)
            // emoji can be null (to clear); serialize as JSON null
            put("emoji", if (emoji != null) JsonPrimitive(emoji) else JsonNull)
        }
        callFunctionJson(functions, "setReaction", payload.toString()).await<JsAny?>()
        Unit
    }

    override suspend fun migrateGuestData(predictions: List<LocalPrediction>): MigrationSummary = mapErrors {
        val payload = buildJsonObject {
            put("predictions", JsonArray(
                predictions.map { p ->
                    buildJsonObject {
                        put("challengeId", p.challengeId)
                        put("betId", p.betId)
                        put("payload", p.payload.toJsonObject())
                    }
                },
            ))
        }
        val resultJson = callFunctionJson(functions, "migrateGuestData", payload.toString())
            .await<JsAny?>()
            ?.toString()
            ?: return@mapErrors MigrationSummary(migrated = 0, skipped = 0, failed = 0)
        val obj = runCatching { webJson.parseToJsonElement(resultJson) as? JsonObject }.getOrNull()
            ?: return@mapErrors MigrationSummary(migrated = 0, skipped = 0, failed = 0)
        val migrated = (obj["migrated"] as? JsonArray)?.size ?: 0
        val skipped = (obj["skipped"] as? JsonArray)?.size ?: 0
        val failed = (obj["failed"] as? JsonArray)?.size ?: 0
        MigrationSummary(migrated = migrated, skipped = skipped, failed = failed)
    }
}

// ── Private file-level helpers ────────────────────────────────────────────────

/**
 * Extracts grandparent ids (challenge ids) from a players collectionGroup snapshot.
 * Each player doc path is: challenges/{challengeId}/players/{uid}
 */
@JsFun("""(qsnap) => JSON.stringify(
  qsnap.docs
    .map(d => d.ref && d.ref.parent && d.ref.parent.parent ? d.ref.parent.parent.id : null)
    .filter(x => x !== null)
)""")
private external fun extractGrandparentIdsJs(qsnap: JsAny): String

private fun extractGrandparentIds(qsnap: JsAny): List<String> = runCatching {
    webJson.decodeFromString(ListSerializer(String.serializer()), extractGrandparentIdsJs(qsnap)).distinct()
}.getOrElse { emptyList() }

/**
 * Extracts invitations with their parent challenge id from a collectionGroup snapshot.
 */
@JsFun("""(qsnap) => JSON.stringify(
  qsnap.docs
    .map(d => ({
      id: d.id,
      challengeId: d.ref && d.ref.parent && d.ref.parent.parent ? d.ref.parent.parent.id : null,
      data: d.data()
    }))
    .filter(x => x.challengeId !== null)
)""")
private external fun extractInvitationsWithChallengeIdJs(qsnap: JsAny): String

@Serializable
private data class InvitationEntry(
    val id: String,
    val challengeId: String,
    val data: JsonElement,
)

private fun parseInvitationsWithGrandparentIds(qsnap: JsAny): List<Invitation> = runCatching {
    val entries = webJson.decodeFromString(
        ListSerializer(InvitationEntry.serializer()),
        extractInvitationsWithChallengeIdJs(qsnap),
    )
    entries.mapNotNull { entry ->
        runCatching {
            val obj = entry.data as? JsonObject ?: return@runCatching null
            val invitedUid = (obj["invitedUid"] as? JsonPrimitive)?.content ?: entry.id
            val invitedBy = (obj["invitedBy"] as? JsonPrimitive)?.content ?: "SYSTEM"
            val invitedAt: Instant = obj["invitedAt"]?.let {
                runCatching {
                    webJson.decodeFromJsonElement(TimestampDto.serializer(), it).toInstant()
                }.getOrNull()
            } ?: Instant.DISTANT_PAST
            Invitation(
                challengeId = entry.challengeId,
                invitedUid = invitedUid,
                invitedBy = invitedBy,
                invitedAt = invitedAt,
            )
        }.getOrNull()
    }
}.getOrElse { emptyList() }

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
