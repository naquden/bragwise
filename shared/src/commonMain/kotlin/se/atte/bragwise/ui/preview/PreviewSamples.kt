package se.atte.bragwise.ui.preview

import se.atte.bragwise.domain.Bet
import se.atte.bragwise.domain.BetOption
import se.atte.bragwise.domain.Challenge
import se.atte.bragwise.domain.ChallengeDetail
import se.atte.bragwise.domain.ChallengeStatus
import se.atte.bragwise.domain.CloudFriend
import se.atte.bragwise.domain.ParticipantInfo
import se.atte.bragwise.domain.Player
import se.atte.bragwise.domain.PredictionPayload
import se.atte.bragwise.domain.Visibility
import kotlin.time.Instant

/**
 * Shared sample data for `@Preview` composables only. Keeps screen previews
 * consistent and avoids re-declaring fixtures in every screen file.
 */

internal val sampleBets: List<Bet> = listOf(
    Bet.BooleanProp(id = "b1", title = "Will the home side win the final?"),
    Bet.SinglePick(
        id = "b2",
        title = "Top scorer",
        options = listOf(BetOption("o1", "Player A"), BetOption("o2", "Player B"), BetOption("o3", "Player C")),
    ),
    Bet.Ranking(
        id = "b3",
        title = "Group stage - top 2",
        topN = 2,
        options = listOf(
            BetOption("g1", "France"),
            BetOption("g2", "Belgium"),
            BetOption("g3", "Croatia"),
            BetOption("g4", "Senegal"),
        ),
    ),
)

internal val sampleParticipants: List<ParticipantInfo> = listOf(
    ParticipantInfo(uid = "u1", displayName = "Atte Lindqvist", avatarSeed = "atte"),
    ParticipantInfo(uid = "u2", displayName = "Alice", avatarSeed = "alice"),
    ParticipantInfo(uid = "u3", displayName = "Bob", avatarSeed = "bob"),
)

internal fun sampleChallenge(
    status: ChallengeStatus = ChallengeStatus.OPEN,
    bets: List<Bet> = sampleBets,
    results: Map<String, PredictionPayload>? = null,
    betsVisible: Boolean = false,
    participants: List<ParticipantInfo> = sampleParticipants,
): Challenge = Challenge(
    id = "c1",
    title = "World Football Cup 2026 Predictions",
    description = "Predict the outcomes",
    category = "sport",
    visibility = Visibility.FRIENDS,
    createdBy = "u1",
    createdAt = Instant.fromEpochSeconds(0),
    locksAt = Instant.fromEpochMilliseconds(1_750_096_200_000L),
    resultsPostedAt = if (status == ChallengeStatus.RESULTS_POSTED) Instant.fromEpochSeconds(0) else null,
    status = status,
    joinedCount = 12,
    promoted = false,
    bets = bets,
    results = results,
    leaderboard = null,
    betsVisible = betsVisible,
    participants = participants,
)

internal fun sampleDetail(
    status: ChallengeStatus = ChallengeStatus.OPEN,
    myPredictions: Map<String, PredictionPayload> = mapOf(
        "b1" to PredictionPayload.BooleanProp(true),
        "b3" to PredictionPayload.Ranking(listOf("g1", "g2")),
    ),
    myRank: Int? = 3,
    results: Map<String, PredictionPayload>? = null,
    betsVisible: Boolean = false,
): ChallengeDetail = ChallengeDetail(
    challenge = sampleChallenge(status = status, results = results, betsVisible = betsVisible),
    myPredictions = myPredictions,
    myRank = myRank,
)

internal fun samplePlayer(
    uid: String = "u1",
    username: String = "atte",
    displayName: String = "Atte Lindqvist",
): Player = Player(
    uid = uid,
    username = username,
    displayName = displayName,
    avatarSeed = username,
    createdAt = Instant.fromEpochSeconds(0),
)

internal val sampleCloudFriends: List<CloudFriend> = listOf(
    CloudFriend(player = samplePlayer(uid = "u2", username = "alice", displayName = "Alice"), since = Instant.fromEpochSeconds(0)),
    CloudFriend(player = samplePlayer(uid = "u3", username = "bob", displayName = "Bob"), since = Instant.fromEpochSeconds(0)),
    CloudFriend(player = samplePlayer(uid = "u4", username = "carol", displayName = "Carol"), since = Instant.fromEpochSeconds(0)),
)
