package io.issac.pleosandroid.core.domain.points

import io.issac.pleosandroid.core.domain.collection.model.AssessmentStatus
import io.issac.pleosandroid.core.domain.collection.model.JourneyRecord
import io.issac.pleosandroid.core.domain.driving.model.DrivingAssessment
import java.time.Instant
import java.time.LocalDate
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class PointEarningPolicyTest {
    @Test fun `two measured kilometers earn points with a completed score regardless of duration`() {
        val journey = trip(distanceMeters = 2_000, durationSeconds = 90)
        assertEquals(96L, PointEarningPolicy.settle(journey, 0)?.deltaHalfPoints)
        assertNull(PointEarningPolicy.settle(journey.copy(distanceMeters = 1_999), 0))
        assertNull(PointEarningPolicy.settle(journey.copy(driving = null), 0))
        assertNull(PointEarningPolicy.settle(journey.copy(assessment = AssessmentStatus.Insufficient, score = null), 0))
    }

    @Test fun `limits the final journey to remaining weekly meters and rounds once`() {
        assertEquals(5_000L, PointEarningPolicy.settle(trip(), 500_000)?.eligibleDistanceMeters)
        val credit = checkNotNull(PointEarningPolicy.settle(trip(score = 79), 999_501))
        assertEquals(499L, credit.eligibleDistanceMeters)
        assertEquals(998L, credit.basePointsHundredths)
        assertEquals(1_185, credit.multiplierThousandths)
        assertEquals(22L, credit.deltaHalfPoints)
        assertNull(PointEarningPolicy.settle(trip(), 1_000_000))

        val zero = checkNotNull(PointEarningPolicy.settle(trip(score = 0), 0))
        assertEquals(0L, zero.deltaHalfPoints)
        assertEquals(5_000L, zero.eligibleDistanceMeters)
    }

    @Test fun `plus increases only the covered share after safety scoring and weekly cap`() {
        val ride = trip(score = 79)
        val ordinary = checkNotNull(PointEarningPolicy.settle(ride, 999_501))
        assertEquals(22L, ordinary.deltaHalfPoints)
        val full = checkNotNull(PointEarningPolicy.settle(ride, 999_501, ride.distanceMeters))
        assertEquals(33L, full.deltaHalfPoints) // 11 P + 5.5 P
        assertEquals(11L, full.membershipBonusHalfPoints)
        val partial = checkNotNull(PointEarningPolicy.settle(ride, 999_501, ride.distanceMeters / 2))
        assertEquals(27L, partial.deltaHalfPoints)
        assertEquals(5L, partial.membershipBonusHalfPoints)
        assertEquals(499L, partial.eligibleDistanceMeters)
        assertNull(PointEarningPolicy.settle(ride, 1_000_000, ride.distanceMeters))
    }

    @Test fun `dates Korea time and declines aggregates spanning two weeks`() {
        val localMonday = Instant.parse("2026-11-01T15:00:00Z")
        val date = PointEarningPolicy.drivingDate(localMonday)
        assertEquals(LocalDate.parse("2026-11-02"), date)
        assertEquals(date, PointEarningPolicy.weekStart(date.plusDays(6)))
        val crossing = trip(startedAt = localMonday.minusSeconds(300), endedAt = localMonday.plusSeconds(300))
        assertNull(PointEarningPolicy.settle(crossing, 0))
    }

    private fun trip(
        score: Int = 80,
        startedAt: Instant = Instant.parse("2026-11-02T00:00:00Z"),
        durationSeconds: Long = 600,
        endedAt: Instant = startedAt.plusSeconds(durationSeconds),
        distanceMeters: Long = 5_000,
    ) = JourneyRecord(
        id = "drive",
        startedAt = startedAt,
        endedAt = endedAt,
        zoneId = "Asia/Seoul",
        distanceMeters = distanceMeters,
        assessment = AssessmentStatus.Complete,
        score = score,
        driving = DrivingAssessment(
            elapsedMillis = durationSeconds * 1_000,
            observedMillis = durationSeconds * 1_000,
            movingMillis = durationSeconds * 1_000,
            sampleCount = 600,
            rejectedSamples = 0,
            harshAccelerations = 0,
            harshBrakings = 0,
            harshStarts = 0,
            longestGapMillis = 0,
        ),
    )
}
