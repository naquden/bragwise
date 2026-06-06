package se.atte.bragwise.data.mock

import se.atte.bragwise.domain.Bet
import se.atte.bragwise.domain.BetOption
import se.atte.bragwise.domain.Challenge
import se.atte.bragwise.domain.ChallengeStatus
import se.atte.bragwise.domain.CloudFriend
import se.atte.bragwise.domain.LeaderboardEntry
import se.atte.bragwise.domain.OptionType
import se.atte.bragwise.domain.ParticipantInfo
import se.atte.bragwise.domain.Player
import se.atte.bragwise.domain.PredictionPayload
import se.atte.bragwise.domain.PublicProfile
import se.atte.bragwise.domain.Visibility
import kotlin.time.Clock
import kotlin.time.Duration.Companion.days
import kotlin.time.Duration.Companion.hours
import kotlin.time.Duration.Companion.minutes

internal const val MOCK_UID = "mock-user-001"
internal const val MOCK_EMAIL = "demo@bragwise.dev"

internal val mockPlayer = Player(
    uid = MOCK_UID,
    username = "demoplayer",
    displayName = "Demo Player",
    avatarSeed = "demo",
    createdAt = Clock.System.now() - 30.days,
)

internal val mockPublicProfile = PublicProfile(
    uid = MOCK_UID,
    username = "demoplayer",
    displayName = "Demo Player",
    avatarSeed = "demo",
)

private val now = Clock.System.now()

internal val mockChallenges = listOf(
    Challenge(
        id = "mock-challenge-005",
        title = "Champions League Final 2026",
        description = "Real Madrid vs Man City — who called it?",
        category = "Football",
        visibility = Visibility.FRIENDS,
        createdBy = MOCK_UID,
        createdAt = now - 10.days,
        locksAt = now - 5.days,
        resultsPostedAt = now - 2.hours,
        status = ChallengeStatus.RESULTS_POSTED,
        joinedCount = 5,
        promoted = false,
        trusted = false,
        bets = listOf(
            Bet.SinglePick(
                id = "bet-005-a",
                title = "Who wins the final?",
                optionType = OptionType.NONE,
                options = listOf(
                    BetOption(id = "o1", label = "Real Madrid"),
                    BetOption(id = "o2", label = "Man City"),
                ),
            ),
            Bet.BooleanProp(
                id = "bet-005-b",
                title = "Will there be extra time?",
            ),
        ),
        results = mapOf(
            "bet-005-a" to PredictionPayload.SinglePick(optionId = "o1"),
            "bet-005-b" to PredictionPayload.BooleanProp(value = true),
        ),
        leaderboard = mapOf(
            MOCK_UID to 95,
            "uid-alice" to 80,
            "uid-bob" to 65,
            "uid-carol" to 50,
            "uid-dave" to 30,
        ),
        betsVisible = true,
        participants = listOf(
            ParticipantInfo(uid = MOCK_UID, displayName = "Demo Player", avatarSeed = "a1"),
            ParticipantInfo(uid = "uid-alice", displayName = "Alice", avatarSeed = "a3"),
            ParticipantInfo(uid = "uid-bob", displayName = "Bob", avatarSeed = "a5"),
            ParticipantInfo(uid = "uid-carol", displayName = "Carol", avatarSeed = "a7"),
            ParticipantInfo(uid = "uid-dave", displayName = "Dave", avatarSeed = "a2"),
        ),
    ),
    Challenge(
        id = "mock-challenge-006",
        title = "Friday Quiz Night 🧠",
        description = "5 friends, one chance at glory. Who paid attention in school?",
        category = "Quiz",
        visibility = Visibility.FRIENDS,
        createdBy = "uid-alice",
        createdAt = now - 1.days,
        locksAt = now + 2.days,
        resultsPostedAt = null,
        status = ChallengeStatus.OPEN,
        joinedCount = 5,
        promoted = false,
        trusted = false,
        bets = listOf(
            Bet.SinglePick(
                id = "bet-006-a",
                title = "Capital of Australia?",
                optionType = OptionType.NONE,
                options = listOf(
                    BetOption(id = "o1", label = "Sydney"),
                    BetOption(id = "o2", label = "Melbourne"),
                    BetOption(id = "o3", label = "Canberra"),
                    BetOption(id = "o4", label = "Brisbane"),
                ),
            ),
            Bet.SinglePick(
                id = "bet-006-b",
                title = "Largest planet in our solar system?",
                optionType = OptionType.NONE,
                options = listOf(
                    BetOption(id = "o1", label = "Saturn"),
                    BetOption(id = "o2", label = "Jupiter"),
                    BetOption(id = "o3", label = "Neptune"),
                    BetOption(id = "o4", label = "Uranus"),
                ),
            ),
            Bet.BooleanProp(
                id = "bet-006-c",
                title = "Is a tomato a fruit?",
            ),
            Bet.SinglePick(
                id = "bet-006-d",
                title = "Which year was the first iPhone released?",
                optionType = OptionType.NONE,
                options = listOf(
                    BetOption(id = "o1", label = "2005"),
                    BetOption(id = "o2", label = "2006"),
                    BetOption(id = "o3", label = "2007"),
                    BetOption(id = "o4", label = "2008"),
                ),
            ),
        ),
        results = null,
        leaderboard = null,
        betsVisible = true,
        participants = listOf(
            ParticipantInfo(uid = "uid-alice", displayName = "Alice", avatarSeed = "a3"),
            ParticipantInfo(uid = MOCK_UID, displayName = "Demo Player", avatarSeed = "a1"),
            ParticipantInfo(uid = "uid-bob", displayName = "Bob", avatarSeed = "a5"),
            ParticipantInfo(uid = "uid-carol", displayName = "Carol", avatarSeed = "a7"),
            ParticipantInfo(uid = "uid-dave", displayName = "Dave", avatarSeed = "a2"),
        ),
    ),
    Challenge(
        id = "mock-challenge-004",
        title = "FIFA World Cup 2026",
        description = "48 nations, one champion. Drag your picks into the podium slots.",
        category = "Football",
        visibility = Visibility.PROMOTED,
        createdBy = "uid-admin",
        createdAt = now - 3.days,
        locksAt = now + 30.days,
        resultsPostedAt = null,
        status = ChallengeStatus.OPEN,
        joinedCount = 512,
        promoted = true,
        trusted = true,
        bets = listOf(
            Bet.Ranking(
                id = "bet-004-a",
                title = "Group A final standings",
                optionType = OptionType.COUNTRY,
                topN = 4,
                options = listOf(
                    BetOption(id = "us", label = "USA", countryCode = "US"),
                    BetOption(id = "mx", label = "Mexico", countryCode = "MX"),
                    BetOption(id = "ca", label = "Canada", countryCode = "CA"),
                    BetOption(id = "ng", label = "Nigeria", countryCode = "NG"),
                ),
            ),
            Bet.SinglePick(
                id = "bet-004-c",
                title = "Who wins the tournament?",
                optionType = OptionType.COUNTRY,
                options = listOf(
                    BetOption(id = "ar", label = "Argentina", countryCode = "AR"),
                    BetOption(id = "br", label = "Brazil", countryCode = "BR"),
                    BetOption(id = "fr", label = "France", countryCode = "FR"),
                    BetOption(id = "de", label = "Germany", countryCode = "DE"),
                    BetOption(id = "en", label = "England", countryCode = "GB"),
                    BetOption(id = "es", label = "Spain", countryCode = "ES"),
                    BetOption(id = "pt", label = "Portugal", countryCode = "PT"),
                ),
            ),
            Bet.BooleanProp(
                id = "bet-004-b",
                title = "Will the host nation (USA/Canada/Mexico) reach the semi-finals?",
            ),
        ),
        results = null,
        leaderboard = null,
    ),
    Challenge(
        id = "mock-challenge-001",
        title = "2026 World Cup Top Scorer",
        description = "Who will score the most goals at the 2026 World Cup?",
        category = "Football",
        visibility = Visibility.FRIENDS,
        createdBy = MOCK_UID,
        createdAt = now - 5.days,
        locksAt = now + 10.days,
        resultsPostedAt = null,
        status = ChallengeStatus.OPEN,
        joinedCount = 8,
        promoted = false,
        trusted = false,
        bets = listOf(
            Bet.SinglePick(
                id = "bet-001-a",
                title = "Who will be the top scorer?",
                optionType = OptionType.NONE,
                options = listOf(
                    BetOption(id = "o1", label = "Erling Haaland"),
                    BetOption(id = "o2", label = "Kylian Mbappé"),
                    BetOption(id = "o3", label = "Vinicius Jr."),
                    BetOption(id = "o4", label = "Harry Kane"),
                ),
            ),
            Bet.BooleanProp(
                id = "bet-001-b",
                title = "Will the top scorer net more than 6 goals?",
            ),
        ),
        results = null,
        leaderboard = mapOf(MOCK_UID to 120, "uid-alice" to 80, "uid-bob" to 60),
        betsVisible = true,
        participants = listOf(
            ParticipantInfo(uid = MOCK_UID, displayName = "Demo Player", avatarSeed = "demo"),
            ParticipantInfo(uid = "uid-alice", displayName = "Alice", avatarSeed = "alice-seed"),
            ParticipantInfo(uid = "uid-bob", displayName = "Bob", avatarSeed = "bob-seed"),
        ),
    ),
    Challenge(
        id = "mock-challenge-002",
        title = "F1 2026 Constructors Podium",
        description = "Rank the top 3 constructors for the 2026 F1 season.",
        category = "Motorsport",
        visibility = Visibility.FRIENDS,
        createdBy = MOCK_UID,
        createdAt = now - 2.days,
        locksAt = now + 3.days,
        resultsPostedAt = null,
        status = ChallengeStatus.OPEN,
        joinedCount = 3,
        promoted = false,
        trusted = false,
        bets = listOf(
            Bet.Ranking(
                id = "bet-002-a",
                title = "Rank the top 3 constructors",
                optionType = OptionType.NONE,
                topN = 3,
                options = listOf(
                    BetOption(id = "o1", label = "Red Bull"),
                    BetOption(id = "o2", label = "Ferrari"),
                    BetOption(id = "o3", label = "McLaren"),
                    BetOption(id = "o4", label = "Mercedes"),
                    BetOption(id = "o5", label = "Aston Martin"),
                ),
            ),
        ),
        results = null,
        leaderboard = mapOf(MOCK_UID to 50, "uid-alice" to 50),
    ),
    Challenge(
        id = "mock-challenge-003",
        title = "Eurovision 2027 Pick",
        description = "A promoted showcase challenge — pick the winner!",
        category = "Entertainment",
        visibility = Visibility.PROMOTED,
        createdBy = "uid-admin",
        createdAt = now - 1.days,
        locksAt = now + 20.days,
        resultsPostedAt = null,
        status = ChallengeStatus.OPEN,
        joinedCount = 142,
        promoted = true,
        trusted = true,
        bets = listOf(
            Bet.SinglePick(
                id = "bet-003-a",
                title = "Which country will win?",
                optionType = OptionType.COUNTRY,
                options = listOf(
                    BetOption(id = "o1", label = "Sweden", countryCode = "SE"),
                    BetOption(id = "o2", label = "Ukraine", countryCode = "UA"),
                    BetOption(id = "o3", label = "Italy", countryCode = "IT"),
                    BetOption(id = "o4", label = "Norway", countryCode = "NO"),
                ),
            ),
            Bet.BooleanProp(
                id = "bet-003-b",
                title = "Will the host country make the top 5?",
            ),
        ),
        results = null,
        leaderboard = null,
    ),
)

internal val mockCloudFriends = listOf(
    CloudFriend(
        player = Player(
            uid = "uid-alice",
            username = "alice",
            displayName = "Alice",
            avatarSeed = "alice-seed",
            createdAt = now - 60.days,
        ),
        since = now - 20.days,
    ),
    CloudFriend(
        player = Player(
            uid = "uid-bob",
            username = "bobthebrave",
            displayName = "Bob",
            avatarSeed = "bob-seed",
            createdAt = now - 45.days,
        ),
        since = now - 15.days,
    ),
)

internal val mockLeaderboard: List<LeaderboardEntry> = listOf(
    LeaderboardEntry(uid = MOCK_UID, displayName = "Demo Player", points = 120, rank = 1),
    LeaderboardEntry(uid = "uid-alice", displayName = "Alice", points = 80, rank = 2),
    LeaderboardEntry(uid = "uid-bob", displayName = "Bob", points = 60, rank = 3),
)
