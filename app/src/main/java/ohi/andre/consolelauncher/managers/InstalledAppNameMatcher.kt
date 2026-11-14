package ohi.andre.consolelauncher.managers

import java.util.Locale

/** Resolves typed app names against the labels and package identifiers Android exposes. */
internal object InstalledAppNameMatcher {
    fun find(query: String?, apps: List<AppsManager.LaunchInfo>): AppsManager.LaunchInfo? {
        val queryWords = words(query.orEmpty())
        if (queryWords.isEmpty()) return null

        return apps.mapNotNull { app ->
            val label = app.publicLabel.orEmpty()
            val labelWords = words(label, splitCamelCase = true).distinct()
            val packageWords = words(app.componentName?.packageName.orEmpty())
            val activityWords = words(app.componentName?.className.orEmpty())

            var score = 0
            for (queryWord in queryWords) {
                val wordScore = bestWordScore(queryWord, labelWords, packageWords + activityWords)
                    ?: return@mapNotNull null
                score += wordScore
            }

            Match(app, score)
        }.minWithOrNull(
            compareBy<Match> { it.score }
                .thenBy { if (it.app.currentProfile) 0 else 1 }
                .thenByDescending { it.app.launchedTimes }
                .thenBy { it.app.publicLabel.orEmpty().lowercase(Locale.ROOT) },
        )?.app
    }

    private fun bestWordScore(query: String, labelWords: List<String>, identifiers: List<String>): Int? {
        val labelScore = labelWords.mapNotNull { matchWordScore(query, it) }.minOrNull()
        if (labelScore != null) return labelScore

        val identifierScore = identifiers.mapNotNull { matchWordScore(query, it) }.minOrNull()
        return identifierScore?.plus(8)
    }

    private fun matchWordScore(query: String, candidate: String): Int? = when {
        candidate == query -> 0
        candidate.startsWith(query) -> 4
        candidate.contains(query) -> 12
        query.length >= 5 && candidate.length >= 5 && differsByOneCharacter(query, candidate) -> 24
        else -> null
    }

    private fun differsByOneCharacter(left: String, right: String): Boolean {
        if (kotlin.math.abs(left.length - right.length) > 1) return false

        var leftIndex = 0
        var rightIndex = 0
        var differences = 0
        while (leftIndex < left.length && rightIndex < right.length) {
            if (left[leftIndex] == right[rightIndex]) {
                leftIndex++
                rightIndex++
            } else {
                differences++
                if (differences > 1) return false
                if (left.length > right.length) leftIndex++
                else if (right.length > left.length) rightIndex++
                else {
                    leftIndex++
                    rightIndex++
                }
            }
        }
        if (leftIndex < left.length || rightIndex < right.length) differences++
        return differences == 1
    }

    private fun words(value: String, splitCamelCase: Boolean = false): List<String> {
        val expanded = if (splitCamelCase) {
            value.replace(Regex("(?<=[\\p{Ll}])(?=[\\p{Lu}])"), " ")
        } else {
            value
        }
        return Regex("[\\p{L}\\p{N}]+").findAll(expanded)
            .map { it.value.lowercase(Locale.ROOT) }
            .toList()
    }

    private data class Match(val app: AppsManager.LaunchInfo, val score: Int)
}
