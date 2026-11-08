package io.issac.pleosandroid.core.domain.cards

import kotlin.random.Random

data class ForgeOdds(val success: Int, val great: Int, val hold: Int, val drop: Int, val destroy: Int) {
    fun result(roll: Int): ForgeOutcome {
        require(roll in 0..99)
        return when {
            roll < success -> ForgeOutcome.Success
            roll < success + great -> ForgeOutcome.GreatSuccess
            roll < success + great + hold -> ForgeOutcome.Hold
            roll < 100 - destroy -> ForgeOutcome.Drop
            else -> ForgeOutcome.Destroyed
        }
    }
}

data class PackOdds(val weights: Map<PrintGroup, Int>, val variantPercent: Int) {
    init {
        require(weights.values.sum() == 100 && weights.values.all { it > 0 } && variantPercent in 0..100)
    }

    fun group(roll: Int): PrintGroup {
        require(roll in 0..99)
        var cumulative = 0
        return weights.entries.first { (_, weight) ->
            cumulative += weight
            roll < cumulative
        }.key
    }

    /** Final grade probability in hundredths of a percent. */
    fun gradeChance(grade: CardGrade): Int = weights.entries.sumOf { (group, weight) ->
        val base = if (group.grade == grade) 100 - variantPercent else 0
        val variant = if (group.grade.variantGrade() == grade) variantPercent else 0
        weight * (base + variant)
    }
}

object CardRules {
    const val VARIANT_PERCENT = 5
    private val inland = PackOdds(mapOf(PrintGroup.Landmark to 96, PrintGroup.Specialty to 4), VARIANT_PERCENT)
    private val coastal = PackOdds(
        mapOf(PrintGroup.Beach to 36, PrintGroup.Landmark to 60, PrintGroup.Specialty to 4),
        VARIANT_PERCENT,
    )
    private val hyundai = PackOdds(mapOf(PrintGroup.Studio to 50, PrintGroup.Epit to 50), 0)

    fun packOdds(type: PackType): PackOdds = when (type) {
        PackType.Seoul -> inland
        PackType.Busan, PackType.Jeju -> coastal
        PackType.Hyundai -> hyundai
    }

    private val odds = listOf(
        ForgeOdds(85, 10, 5, 0, 0), ForgeOdds(80, 10, 10, 0, 0), ForgeOdds(75, 10, 15, 0, 0),
        ForgeOdds(70, 10, 20, 0, 0), ForgeOdds(65, 10, 25, 0, 0), ForgeOdds(60, 10, 25, 5, 0),
        ForgeOdds(55, 10, 25, 10, 0), ForgeOdds(50, 10, 25, 15, 0), ForgeOdds(45, 10, 25, 20, 0),
        ForgeOdds(40, 10, 25, 25, 0), ForgeOdds(38, 10, 26, 25, 1), ForgeOdds(36, 10, 28, 25, 1),
        ForgeOdds(34, 9, 31, 25, 1), ForgeOdds(32, 9, 32, 25, 2), ForgeOdds(30, 8, 35, 25, 2),
        ForgeOdds(28, 7, 38, 25, 2), ForgeOdds(26, 7, 40, 25, 2), ForgeOdds(24, 6, 42, 25, 3),
        ForgeOdds(22, 6, 44, 25, 3), ForgeOdds(20, 5, 47, 25, 3), ForgeOdds(35, 0, 48, 15, 2),
    )

    fun odds(level: Int): ForgeOdds {
        require(level >= 0)
        return odds[level.coerceAtMost(odds.lastIndex)]
    }

    /** Integer half-points preserve the exact Epic price, including 247.5P at +1. */
    fun cost(grade: CardGrade, level: Int): Long = (10L + level) * when (grade) {
        CardGrade.Common -> 20
        CardGrade.Rare, CardGrade.H -> 30
        CardGrade.Epic, CardGrade.HH -> 45
    }

    fun nextLevel(level: Int, outcome: ForgeOutcome): Int = when (outcome) {
        ForgeOutcome.Success -> level + 1
        ForgeOutcome.GreatSuccess -> level + 2
        ForgeOutcome.Hold, ForgeOutcome.Restored -> level
        ForgeOutcome.Drop -> level - 1
        ForgeOutcome.Destroyed -> level - 5
    }

    fun draw(packType: PackType, catalog: List<CardPrint>, random: Random): Pair<CardPrint, Boolean> {
        val odds = packOdds(packType)
        val group = odds.group(random.nextInt(100))
        val print = catalog.filter { it.packType == packType && it.group == group }.random(random)
        return print to (odds.variantPercent > 0 && random.nextInt(100) < odds.variantPercent)
    }
}
