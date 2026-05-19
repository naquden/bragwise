package se.atte.bragwise.data.mock

import se.atte.bragwise.domain.Bet
import se.atte.bragwise.domain.BetOption
import se.atte.bragwise.domain.Challenge
import se.atte.bragwise.domain.ChallengeStatus
import se.atte.bragwise.domain.CloudFriend
import se.atte.bragwise.domain.LeaderboardEntry
import se.atte.bragwise.domain.OptionType
import se.atte.bragwise.domain.Player
import se.atte.bragwise.domain.PublicProfile
import se.atte.bragwise.domain.Visibility
import kotlin.time.Clock
import kotlin.time.Duration.Companion.days
import kotlin.time.Duration.Companion.hours

internal const val MOCK_UID = "mock-user-001"
internal const val MOCK_EMAIL = "demo@bragwise.dev"

internal val mockPlayer = Player(
    uid = MOCK_UID,
    handle = "demoplayer",
    displayName = "Demo Player",
    avatarSeed = "demo",
    createdAt = Clock.System.now() - 30.days,
)

internal val mockPublicProfile = PublicProfile(
    uid = MOCK_UID,
    handle = "demoplayer",
    displayName = "Demo Player",
    avatarSeed = "demo",
)

private val now = Clock.System.now()

internal val mockChallenges = listOf(
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
                title = "Who wins the final?",
                optionType = OptionType.COUNTRY,
                topN = 1,
                options = listOf(
                    BetOption(id = "ar", label = "Argentina", countryCode = "AR"),
                    BetOption(id = "au", label = "Australia", countryCode = "AU"),
                    BetOption(id = "be", label = "Belgium", countryCode = "BE"),
                    BetOption(id = "br", label = "Brazil", countryCode = "BR"),
                    BetOption(id = "ca", label = "Canada", countryCode = "CA"),
                    BetOption(id = "hr", label = "Croatia", countryCode = "HR"),
                    BetOption(id = "dk", label = "Denmark", countryCode = "DK"),
                    BetOption(id = "eg", label = "Egypt", countryCode = "EG"),
                    BetOption(id = "en", label = "England", countryCode = "GB"),
                    BetOption(id = "fr", label = "France", countryCode = "FR"),
                    BetOption(id = "de", label = "Germany", countryCode = "DE"),
                    BetOption(id = "gh", label = "Ghana", countryCode = "GH"),
                    BetOption(id = "it", label = "Italy", countryCode = "IT"),
                    BetOption(id = "jp", label = "Japan", countryCode = "JP"),
                    BetOption(id = "mx", label = "Mexico", countryCode = "MX"),
                    BetOption(id = "ma", label = "Morocco", countryCode = "MA"),
                    BetOption(id = "nl", label = "Netherlands", countryCode = "NL"),
                    BetOption(id = "ng", label = "Nigeria", countryCode = "NG"),
                    BetOption(id = "pt", label = "Portugal", countryCode = "PT"),
                    BetOption(id = "sn", label = "Senegal", countryCode = "SN"),
                    BetOption(id = "es", label = "Spain", countryCode = "ES"),
                    BetOption(id = "tr", label = "Türkiye", countryCode = "TR"),
                    BetOption(id = "us", label = "USA", countryCode = "US"),
                    BetOption(id = "uy", label = "Uruguay", countryCode = "UY"),
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
            handle = "alice",
            displayName = "Alice",
            avatarSeed = "alice-seed",
            createdAt = now - 60.days,
        ),
        since = now - 20.days,
    ),
    CloudFriend(
        player = Player(
            uid = "uid-bob",
            handle = "bobthebrave",
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
