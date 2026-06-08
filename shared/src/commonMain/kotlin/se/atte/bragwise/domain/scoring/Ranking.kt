package se.atte.bragwise.domain.scoring

data class RankedEntry(val uid: String, val points: Int, val rank: Int)

fun competitionRanks(leaderboard: Map<String, Int>): List<RankedEntry> {
    val sorted = leaderboard.entries.sortedByDescending { it.value }
    val result = mutableListOf<RankedEntry>()
    var i = 0
    while (i < sorted.size) {
        val points = sorted[i].value
        var j = i
        while (j < sorted.size && sorted[j].value == points) j++
        for (k in i until j) {
            result += RankedEntry(uid = sorted[k].key, points = points, rank = i + 1)
        }
        i = j
    }
    return result
}
