package ani.saikou.others

import ani.saikou.parsers.ShowResponse
import kotlin.math.max


data class AnilistTitles(
    val titles: List<String>
) {
    companion object {
        fun of(vararg titles: String?): AnilistTitles =
            AnilistTitles(titles.filterNotNull())
    }
}

/**
 * Finds the ShowResponse whose name/otherNames best matches any of the target's known titles.
 *  Uses fuzzball/fuzzywuzzy-style token_sort_ratio
 **/
fun findBestMatch(
    target: AnilistTitles,
    candidates: List<ShowResponse>?
): ShowResponse? {
    if (candidates.isNullOrEmpty()) return null

    val targetTitles = target.titles
        .filter { it.isNotBlank() }
        .map { it.lowercase() }
        .distinct()

    if (targetTitles.isEmpty()) return null

    var bestMatch: ShowResponse? = null
    var bestScore = -1

    for (candidate in candidates) {
        val candidateTitles = (listOf(candidate.name) + candidate.otherNames)
            .filter { it.isNotBlank() }
            .map { it.lowercase() }
            .distinct()

        var candidateBestScore = 0

        for (tTitle in targetTitles) {
            for (cTitle in candidateTitles) {
                val score = tokenSortRatio(tTitle, cTitle)
                candidateBestScore = max(candidateBestScore, score)
            }
        }

        if (candidateBestScore > bestScore) {
            bestScore = candidateBestScore
            bestMatch = candidate
        }
    }

    return bestMatch
}

fun tokenSortRatio(a: String, b: String): Int {
    val sortedA = a.split(Regex("\\s+")).filter { it.isNotEmpty() }.sorted().joinToString(" ")
    val sortedB = b.split(Regex("\\s+")).filter { it.isNotEmpty() }.sorted().joinToString(" ")
    return ratio(sortedA, sortedB)
}

fun ratio(a: String, b: String): Int {
    if (a.isEmpty() && b.isEmpty()) return 100
    val distance = levenshtein(a, b)
    val maxLen = max(a.length, b.length)
    if (maxLen == 0) return 100
    return (((maxLen - distance).toDouble() / maxLen) * 100).toInt()
}

fun levenshtein(a: String, b: String): Int {
    val dp = Array(a.length + 1) { IntArray(b.length + 1) }
    for (i in 0..a.length) dp[i][0] = i
    for (j in 0..b.length) dp[0][j] = j

    for (i in 1..a.length) {
        for (j in 1..b.length) {
            val cost = if (a[i - 1] == b[j - 1]) 0 else 1
            dp[i][j] = minOf(
                dp[i - 1][j] + 1,
                dp[i][j - 1] + 1,
                dp[i - 1][j - 1] + cost
            )
        }
    }
    return dp[a.length][b.length]
}