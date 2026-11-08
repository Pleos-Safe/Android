package io.issac.pleosandroid.core.domain.cards

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class CardRulesTest {
    @Test fun everyRollHasExactlyOneOutcomeAndRiskStartsAtTheAgreedLevels() {
        for (level in 0..30) {
            val odds = CardRules.odds(level)
            val results = (0..99).map(odds::result).groupingBy { it }.eachCount()
            assertEquals(odds.success, results.getOrDefault(ForgeOutcome.Success, 0))
            assertEquals(odds.great, results.getOrDefault(ForgeOutcome.GreatSuccess, 0))
            assertEquals(odds.hold, results.getOrDefault(ForgeOutcome.Hold, 0))
            assertEquals(odds.drop, results.getOrDefault(ForgeOutcome.Drop, 0))
            assertEquals(odds.destroy, results.getOrDefault(ForgeOutcome.Destroyed, 0))
            assertEquals(level >= 5, odds.drop > 0)
            assertEquals(level >= 10, odds.destroy > 0)
            assertEquals(level < 20, odds.great > 0)
        }
        assertEquals(ForgeOdds(34, 9, 31, 25, 1), CardRules.odds(12))
        assertEquals(21, CardRules.nextLevel(19, CardRules.odds(19).result(20)))
        assertEquals(7, CardRules.nextLevel(12, CardRules.odds(12).result(99)))
        assertEquals(31, CardRules.nextLevel(30, CardRules.odds(30).result(0)))
    }

    @Test fun costUsesCurrentLevelAndKeepsHalfPoints() {
        assertEquals(200L, CardRules.cost(CardGrade.Common, 0))
        assertEquals(660L, CardRules.cost(CardGrade.Rare, 12))
        assertEquals(675L, CardRules.cost(CardGrade.Epic, 5))
        assertEquals(1575L, CardRules.cost(CardGrade.Epic, 25))
    }

    @Test fun allRegionsReserveFourPercentForSpecialtiesAndOnlyCoastalRegionsDrawBeaches() {
        for (region in listOf(PackType.Seoul, PackType.Busan, PackType.Jeju)) {
            val odds = CardRules.packOdds(region)
            val groups = (0..99).map(odds::group)
            assertEquals(listOf(9120, 860, 20, 0, 0), CardGrade.entries.map(odds::gradeChance))
            assertEquals(4, groups.count { it == PrintGroup.Specialty })
            assertEquals(if (region == PackType.Seoul) 0 else 36, groups.count { it == PrintGroup.Beach })
            assertTrue(groups.contains(PrintGroup.Landmark))
        }
    }

    @Test fun hyundaiDrawsEqualSeriesAndHasNoVariantRoll() {
        val odds = CardRules.packOdds(PackType.Hyundai)
        assertEquals(0, odds.variantPercent)
        assertEquals(50, (0..99).count { odds.group(it) == PrintGroup.Studio })
        assertEquals(50, (0..99).count { odds.group(it) == PrintGroup.Epit })
        assertEquals(listOf(0, 0, 0, 5000, 5000), CardGrade.entries.map(odds::gradeChance))
    }
}
