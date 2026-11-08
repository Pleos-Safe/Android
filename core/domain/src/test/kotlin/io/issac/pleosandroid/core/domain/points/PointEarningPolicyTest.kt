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
    @Test fun `requires both measured thresholds and a completed score`() {
        val journey = trip()
        assertEquals(240L, PointEarningPolicy.settle(journey, 0)?.deltaHalfPoints)
        assertNull(PointEarningPolicy.settle(journey.copy(distanceMeters = 4_999), 0))
        assertNull(PointEarningPolicy.settle(journey.copy(driving = journey.driving!!.copy(movingMillis = 599_999)), 0))
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
        endedAt: Instant = startedAt.plusSeconds(600),
    ) = JourneyRecord(
        id = "drive",
        startedAt = startedAt,
        endedAt = endedAt,
        zoneId = "Asia/Seoul",
        distanceMeters = 5_000,
        assessment = AssessmentStatus.Complete,
        score = score,
        driving = DrivingAssessment(
            elapsedMillis = 600_000,
            observedMillis = 600_000,
            movingMillis = 600_000,
            sampleCount = 600,
            rejectedSamples = 0,
            harshAccelerations = 0,
            harshBrakings = 0,
            harshStarts = 0,
            longestGapMillis = 0,
        ),
    )
}
