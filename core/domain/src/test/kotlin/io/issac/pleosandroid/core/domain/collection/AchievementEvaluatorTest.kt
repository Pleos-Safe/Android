package io.issac.pleosandroid.core.domain.collection

import io.issac.pleosandroid.core.domain.collection.model.AssessmentStatus
import io.issac.pleosandroid.core.domain.collection.model.CardDefinition
import io.issac.pleosandroid.core.domain.collection.model.CardFront
import io.issac.pleosandroid.core.domain.collection.model.CardKind
import io.issac.pleosandroid.core.domain.collection.model.CardLevelRule
import io.issac.pleosandroid.core.domain.collection.model.CardTemplate
import io.issac.pleosandroid.core.domain.collection.model.ConfirmedVisit
import io.issac.pleosandroid.core.domain.collection.model.JourneyRecord
import io.issac.pleosandroid.core.domain.collection.model.Place
import io.issac.pleosandroid.core.domain.collection.policy.AchievementEvaluator
import java.time.Instant
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class AchievementEvaluatorTest {
    private val evaluator = AchievementEvaluator()
    private val start = Instant.parse("2026-01-01T00:00:00Z")
    private val place = Place("beach", "해변", "부산", "자연", 35.0, 129.0, 100.0, 60, "art")
    private val front = CardFront("카드", "", "", "", "art", "gloss", "engraving", CardTemplate.Standard)
    private val card = CardDefinition("card", CardKind.Place, place.id, front, 1)
    private val rules = (1..5).map { CardLevelRule(card.id, it, 1, it, start) }
    private fun visit(id: String = "visit") = ConfirmedVisit(
        id,
        place.id,
        arrivedAt = start,
        departedAt = start.plusSeconds(60),
        confirmedAt = start.plusSeconds(65),
        zoneId = "Asia/Seoul",
        distanceFromPlaceMeters = 80.0,
        accuracyMeters = 20.0,
    )

    @Test fun visitNeedsDwellAndAccuracyWithinRadius() {
        assertTrue(evaluator.qualifies(place, visit()))
        assertFalse(evaluator.qualifies(place, visit().copy(accuracyMeters = 21.0)))
        assertFalse(evaluator.qualifies(place, visit().copy(departedAt = start.plusSeconds(59))))
        assertFalse(evaluator.qualifies(place, visit().copy(distanceFromPlaceMeters = Double.NaN)))
        assertFalse(evaluator.qualifies(place.copy(active = false), visit()))
    }

    @Test fun duplicateSourcesDoNotAdvanceLevels() {
        val progress = evaluator.evaluate(card, rules, listOf(visit(), visit()), emptyList())
        assertEquals(1, progress.earnedLevel)
        assertEquals(1, progress.nextLevel?.remaining)
    }

    @Test fun newRuleOnlyCountsItsEligibleWindow() {
        val recent = visit("new").copy(confirmedAt = start.plusSeconds(120))
        val scoped = rules.map { it.copy(eligibleFrom = start.plusSeconds(100)) }
        assertEquals(1, evaluator.evaluate(card, scoped, listOf(visit(), recent), emptyList()).earnedLevel)
    }

    @Test fun maximumLevelStillReportsAllQualifyingSources() {
        val progress = evaluator.evaluate(card, rules, (1..9).map { visit("$it") }, emptyList())
        assertEquals(5, progress.earnedLevel)
        assertEquals(9, progress.levels.last().current)
        assertNull(progress.nextLevel)
    }

    @Test fun pendingOrInsufficientAnalysisCannotGrantASafetyCard() {
        val safety = card.copy(kind = CardKind.Safety, placeId = null)
        val qualifiedRules = rules.map {
            it.copy(minimumScore = 85, minimumDistanceMeters = 1000, maximumHarshStarts = 0)
        }
        val trip = JourneyRecord(
            "j",
            start,
            start.plusSeconds(600),
            "Asia/Seoul",
            1500,
            AssessmentStatus.Complete,
            90,
            0,
        )
        assertEquals(1, evaluator.evaluate(safety, qualifiedRules, emptyList(), listOf(trip)).earnedLevel)
        for (invalid in listOf(
            trip.copy(assessment = AssessmentStatus.Pending),
            trip.copy(assessment = AssessmentStatus.Insufficient),
            trip.copy(score = null),
            trip.copy(harshStarts = 1),
            trip.copy(distanceMeters = 999),
        )) {
            assertEquals(0, evaluator.evaluate(safety, qualifiedRules, emptyList(), listOf(invalid)).earnedLevel)
        }
        assertTrue(
            evaluator.evaluate(
                safety,
                qualifiedRules,
                emptyList(),
                listOf(trip.copy(assessment = AssessmentStatus.Pending)),
            )
                .levels.first().pendingAssessment,
        )
    }

    @Test fun earnedLevelDoesNotPretendUnseenLevelsWereRevealed() {
        val result = evaluator.evaluate(card, rules, (1..4).map { visit("$it") }, emptyList(), revealedLevel = 2)
        assertEquals(4, result.earnedLevel)
        assertEquals(2, result.revealedLevel)
        assertTrue(result.hasPendingReveal)
    }
}
