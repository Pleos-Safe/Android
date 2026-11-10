package io.issac.pleosandroid.core.domain.points

import io.issac.pleosandroid.core.domain.collection.model.AssessmentStatus
import io.issac.pleosandroid.core.domain.collection.model.JourneyRecord
import java.time.DayOfWeek
import java.time.Instant
import java.time.LocalDate
import java.time.ZoneId
import java.time.temporal.TemporalAdjusters

object PointEarningPolicy {
    const val MINIMUM_DISTANCE_METERS = 2_000L
    const val WEEKLY_DISTANCE_LIMIT_METERS = 1_000_000L
    const val POINTS_PER_KILOMETER = 20L
    private const val SAFETY_MULTIPLIER_TENTHS = 15
    private const val RULE_VERSION = 4
    private val drivingZone = ZoneId.of("Asia/Seoul")

    fun drivingDate(occurredAt: Instant): LocalDate = occurredAt.atZone(drivingZone).toLocalDate()

    fun weekStart(date: LocalDate): LocalDate = date.with(TemporalAdjusters.previousOrSame(DayOfWeek.MONDAY))

    fun settle(
        journey: JourneyRecord,
        alreadyRecognizedMeters: Long,
        membershipDistanceMeters: Long = 0,
    ): PointTransaction? {
        val score = journey.score ?: return null
        if (journey.assessment != AssessmentStatus.Complete || score !in 0..100 ||
            journey.driving == null ||
            journey.distanceMeters < MINIMUM_DISTANCE_METERS
        ) {
            return null
        }
        val endedDate = drivingDate(journey.endedAt)
        // A whole-trip aggregate cannot allocate distance to both weeks.
        if (weekStart(drivingDate(journey.startedAt)) != weekStart(endedDate)) return null

        val availableMeters = WEEKLY_DISTANCE_LIMIT_METERS -
            alreadyRecognizedMeters.coerceIn(0, WEEKLY_DISTANCE_LIMIT_METERS)
        val eligibleMeters = minOf(journey.distanceMeters, availableMeters)
        if (eligibleMeters == 0L) return null

        val baseHundredths = eligibleMeters * POINTS_PER_KILOMETER * 100 / 1_000
        val multiplierThousandths = score * SAFETY_MULTIPLIER_TENTHS
        // Carry fractional values through the calculation and round down once, at settlement.
        val points = baseHundredths * multiplierThousandths / 100_000
        // One bonus half-point per normal point. Share a partially capped trip by its covered distance.
        val covered = membershipDistanceMeters.coerceIn(0, journey.distanceMeters)
        val weightedPoints = points.toBigInteger() * covered.toBigInteger()
        val bonusHalfPoints = (weightedPoints / journey.distanceMeters.toBigInteger()).toLong()
        return PointTransaction(
            operationId = "journey:${journey.id}",
            sourceId = journey.id,
            kind = PointTransactionKind.JourneyEarned,
            deltaHalfPoints = points * 2 + bonusHalfPoints,
            membershipBonusHalfPoints = bonusHalfPoints,
            occurredAt = journey.endedAt,
            eligibleDistanceMeters = eligibleMeters,
            drivingDate = endedDate,
            assessedScore = score,
            basePointsHundredths = baseHundredths,
            multiplierThousandths = multiplierThousandths,
            ruleVersion = RULE_VERSION,
        )
    }
}
