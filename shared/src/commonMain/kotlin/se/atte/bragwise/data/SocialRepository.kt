package se.atte.bragwise.data

import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.flowOf
import se.atte.bragwise.domain.CloudFriend
import se.atte.bragwise.domain.Friend
import se.atte.bragwise.domain.FriendRequests
import se.atte.bragwise.domain.HeadToHead
import se.atte.bragwise.domain.LocalFriend

/** Outcome of a per-batch reconciliation pass on OB-06. */
data class ReconciliationSummary(
    val reconciled: Int,
    val skipped: Int,
    val failed: Int,
)

open class SocialRepository(
    private val remote: SocialRemoteDataSource,
    private val local: SocialLocalDataSource,
    private val localFriends: LocalFriendStore = LocalFriendStore(),
) {

    /** Cloud-side friends; empty for guests. Stub returns empty until GitLive lands. */
    private fun observeCloudFriends(): Flow<List<CloudFriend>> = flowOf(emptyList())

    /** Local-only friends; non-empty only for guests (and post-sign-up until reconciled). */
    fun observeLocalFriends(): Flow<List<LocalFriend>> = localFriends.friends

    /** Synchronous snapshot — use only for one-shot reads (e.g. editor pre-fill). */
    fun localFriendSnapshot(): List<LocalFriend> = localFriends.snapshot()

    /**
     * Unified stream over both kinds. Cloud rows first (signed-in user's
     * primary), local rows after.
     */
    fun observeFriends(): Flow<List<Friend>> =
        combine(observeCloudFriends(), observeLocalFriends()) { cloud, local ->
            (cloud as List<Friend>) + local
        }

    fun observeFriendRequests(): Flow<FriendRequests> =
        flowOf(FriendRequests(incoming = emptyMap(), outgoing = emptyMap()))

    fun observeHeadToHead(): Flow<HeadToHead> = flowOf(HeadToHead(vs = emptyMap()))

    // ── Local-friend mutations ───────────────────────────────────────────

    fun addLocalFriend(displayName: String, avatarSeed: String): LocalFriend =
        localFriends.add(displayName = displayName, avatarSeed = avatarSeed)

    fun editLocalFriend(localId: String, displayName: String, avatarSeed: String): Boolean =
        localFriends.edit(localId, displayName, avatarSeed)

    fun removeLocalFriend(localId: String): Boolean = localFriends.remove(localId)

    // ── Cloud friend graph (callable-backed; stubs) ──────────────────────

    open suspend fun sendFriendRequest(handle: String): Result<Unit> =
        Result.failure(NotImplementedError("sendFriendRequest not wired"))

    suspend fun acceptFriendRequest(requestId: String): Result<Unit> =
        Result.failure(NotImplementedError("acceptFriendRequest not wired"))

    suspend fun declineFriendRequest(requestId: String): Result<Unit> =
        Result.failure(NotImplementedError("declineFriendRequest not wired"))

    // ── Reconciliation (OB-06) ───────────────────────────────────────────

    /**
     * Reconcile a single local friend onto a cloud handle: fire a real
     * `sendFriendRequest`; on success delete the local row. On failure
     * the local row stays so the user can retry.
     */
    suspend fun reconcileLocalFriendToHandle(
        localId: String,
        handle: String,
    ): Result<Unit> {
        val result = sendFriendRequest(handle)
        if (result.isSuccess) localFriends.remove(localId)
        return result
    }

    /**
     * Batch entry point used by OB-06. `mappings` pairs a `localId` with
     * a target handle, or `null` to indicate the user explicitly skipped
     * that row (it remains local). Returns counts.
     */
    suspend fun reconcileLocalFriends(
        mappings: List<Pair<String, String?>>,
    ): ReconciliationSummary {
        var reconciled = 0
        var skipped = 0
        var failed = 0
        for ((localId, handle) in mappings) {
            if (handle.isNullOrBlank()) {
                skipped++
                continue
            }
            reconcileLocalFriendToHandle(localId = localId, handle = handle)
                .onSuccess { reconciled++ }
                .onFailure { failed++ }
        }
        return ReconciliationSummary(reconciled = reconciled, skipped = skipped, failed = failed)
    }
}
