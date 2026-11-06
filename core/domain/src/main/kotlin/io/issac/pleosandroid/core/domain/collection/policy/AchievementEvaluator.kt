package io.issac.pleosandroid.core.domain.collection.policy

import io.issac.pleosandroid.core.domain.collection.model.AssessmentStatus
import io.issac.pleosandroid.core.domain.collection.model.CardDefinition
import io.issac.pleosandroid.core.domain.collection.model.CardKind
import io.issac.pleosandroid.core.domain.collection.model.CardLevelRule
import io.issac.pleosandroid.core.domain.collection.model.CardProgress
import io.issac.pleosandroid.core.domain.collection.model.ConfirmedVisit
import io.issac.pleosandroid.core.domain.collection.model.JourneyRecord
import io.issac.pleosandroid.core.domain.collection.model.LevelProgress
import io.issac.pleosandroid.core.domain.collection.model.Place
import java.time.Duration

/** Pure evaluation; persistence, device state and UI never participate in the calculation. */
class AchievementEvaluator {
    fun qualifies(place: Place, visit: ConfirmedVisit): Boolean =
        place.active && visit.distanceFromPlaceMeters.isFinite() && visit.accuracyMeters.isFinite() &&
            visit.distanceFromPlaceMeters >= 0 && visit.accuracyMeters >= 0 &&
            visit.distanceFromPlaceMeters + visit.accuracyMeters <= place.radiusMeters &&
            !visit.departedAt.isBefore(visit.arrivedAt) && !visit.confirmedAt.isBefore(visit.departedAt) &&
            Duration.between(visit.arrivedAt, visit.departedAt).seconds >= place.minimumDwellSeconds

    fun evaluate(
        card: CardDefinition,
        rules: List<CardLevelRule>,
        visits: List<ConfirmedVisit>,
        journeys: List<JourneyRecord>,
        revealedLevel: Int? = null,
    ): CardProgress {
        val progress = rules.filter { it.cardId == card.id && it.version == card.ruleVersion }.sortedBy { it.level }
            .map { rule ->
                val count = when (card.kind) {
                    CardKind.Place -> visits.filter {
                        it.placeId == card.placeId && !it.confirmedAt.isBefore(rule.eligibleFrom)
                    }.distinctBy { it.id }.size

                    CardKind.Safety -> journeys.filter { qualifies(rule, it) }.distinctBy { it.id }.size
                }
                LevelProgress(
                    rule.level,
                    count,
                    rule.requiredCount,
                    card.kind == CardKind.Safety && journeys.any {
                        !it.endedAt.isBefore(rule.eligibleFrom) && it.assessment == AssessmentStatus.Pending
                    },
                )
            }
        val earned = progress.takeWhile { it.current >= it.required }.lastOrNull()?.level ?: 0
        return CardProgress(card.id, earned, revealedLevel, progress)
    }

    fun qualifies(rule: CardLevelRule, journey: JourneyRecord): Boolean =
        journey.assessment == AssessmentStatus.Complete && !journey.endedAt.isBefore(rule.eligibleFrom) &&
            journey.distanceMeters >= rule.minimumDistanceMeters &&
            (rule.minimumScore == null || (journey.score != null && journey.score >= rule.minimumScore)) &&
            (
                rule.maximumHarshStarts == null ||
                    (journey.harshStarts != null && journey.harshStarts <= rule.maximumHarshStarts)
                )
}
