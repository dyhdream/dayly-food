package com.daylyfood.app.domain

/**
 * Turns "早上吃了两个包子一碗豆浆" into the foods it mentions.
 *
 * Kept deliberately dumb: it only finds food names and aliases that literally appear in the
 * sentence. Quantities are taken from the food's default serving — guessing grams from spoken
 * words is where wrong numbers come from, and the user can adjust afterwards.
 */
object VoiceMealParser {

    data class Candidate(val foodId: String, val name: String, val aliases: List<String>)

    data class Match(val foodId: String, val name: String, val offset: Int)

    /** One match per food, in the order the user said them. Longer names beat shorter overlaps. */
    fun parse(text: String, candidates: List<Candidate>): List<Match> {
        if (text.isBlank()) return emptyList()

        // Longest first so "辣椒炒肉" wins over "辣椒" instead of matching both.
        val probes = candidates
            .flatMap { candidate -> candidate.aliases.plus(candidate.name).map { candidate to it } }
            .filter { (_, probe) -> probe.isNotBlank() }
            .sortedByDescending { (_, probe) -> probe.length }

        val taken = mutableListOf<IntRange>()
        val matches = mutableListOf<Match>()

        for ((candidate, probe) in probes) {
            if (matches.any { it.foodId == candidate.foodId }) continue
            var index = text.indexOf(probe)
            while (index >= 0) {
                val range = index until index + probe.length
                if (taken.none { it.first <= range.last && range.first <= it.last }) {
                    taken += range
                    matches += Match(candidate.foodId, candidate.name, index)
                    break
                }
                index = text.indexOf(probe, index + 1)
            }
        }
        return matches.sortedBy { it.offset }
    }
}
