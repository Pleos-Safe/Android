package io.issac.pleosandroid.core.data.collection.local

import io.issac.pleosandroid.core.data.collection.mapper.toDomain
import io.issac.pleosandroid.core.data.collection.mapper.toEntity
import io.issac.pleosandroid.core.data.driving.encode
import io.issac.pleosandroid.core.data.points.creditJourney
import io.issac.pleosandroid.core.database.dao.CollectionDao
import io.issac.pleosandroid.core.domain.collection.model.AssessmentStatus
import io.issac.pleosandroid.core.domain.collection.model.ConfirmedVisit
import io.issac.pleosandroid.core.domain.collection.model.JourneyRecord
import io.issac.pleosandroid.core.domain.driving.model.DrivingAssessment
import io.issac.pleosandroid.core.domain.driving.model.InsufficientReason
import java.time.LocalDate
import java.time.ZoneId

internal suspend fun CollectionDao.prepareDemoHistory() {
    if (checkNotNull(userState()).demoSeeded) return
    if (journeys().isEmpty() && visits().isEmpty()) seedHistory()
    prepareDemoReports()
    putUserState(checkNotNull(userState()).copy(demoSeeded = true))
}

private suspend fun CollectionDao.seedHistory() {
    val days = mapOf(
        "haeundae" to listOf("10-18", "10-25", "11-02"),
        "gwangalli" to listOf("10-19", "10-31"),
        "cheongsapo" to listOf("10-20", "10-28"),
        "huinnyeoul" to listOf("10-21", "10-24", "10-27", "11-01"),
        "igidae" to listOf("10-22"),
        "songdo" to listOf("11-02"),
    )
    val catalog = places().mapTo(mutableSetOf()) { it.id }
    days.filterKeys { it in catalog }.forEach { (place, dates) ->
        dates.forEachIndexed { index, date ->
            val at = LocalDate.parse("2026-$date").atTime(18, 0).atZone(ZoneId.of("Asia/Seoul")).toInstant()
            insertVisit(
                ConfirmedVisit(
                    "demo-$place-$index",
                    place,
                    arrivedAt = at.minusSeconds(120),
                    departedAt = at,
                    confirmedAt = at,
                    zoneId = "Asia/Seoul",
                    distanceFromPlaceMeters = 10.0,
                    accuracyMeters = 5.0,
                ).toEntity(),
            )
        }
    }
    listOf("10-24", "10-25", "10-27", "10-28", "10-30", "10-26").forEachIndexed { index, date ->
        val end = LocalDate.parse("2026-$date").atTime(19, 0).atZone(ZoneId.of("Asia/Seoul")).toInstant()
        putJourney(
            JourneyRecord(
                "demo-journey-$index",
                end.minusSeconds(1800),
                end,
                "Asia/Seoul",
                10_000,
                AssessmentStatus.Complete,
                if (index == 5) 75 else 95,
                if (index == 5) 0 else 1,
                driving = demoAssessment(if (index == 5) 75 else 95, if (index == 5) 0 else 1),
            ).toEntity(),
        )
    }
}

private suspend fun CollectionDao.prepareDemoReports() {
    // These IDs are exclusively synthetic fixtures; existing user journeys are never enriched this way.
    journeys().filter { it.id in (0..5).map { index -> "demo-journey-$index" } && it.drivingJson == null }
        .forEach { value ->
            putJourney(value.copy(drivingJson = demoAssessment(value.score ?: 95, value.harshStarts ?: 0).encode()))
        }
    if (journeys().none { it.id == "demo-insufficient" }) {
        val start = LocalDate.of(2026, 10, 29).atTime(18, 0).atZone(ZoneId.of("Asia/Seoul")).toInstant()
        putJourney(
            JourneyRecord(
                "demo-insufficient",
                start,
                start.plusSeconds(90),
                "Asia/Seoul",
                300,
                AssessmentStatus.Insufficient,
                driving = DrivingAssessment(
                    elapsedMillis = 90_000, observedMillis = 40_000, movingMillis = 30_000,
                    sampleCount = 200, rejectedSamples = 0, harshAccelerations = 0, harshBrakings = 0,
                    harshStarts = 0, longestGapMillis = 20_000,
                    reasons = setOf(InsufficientReason.TooShort, InsufficientReason.LowCoverage),
                ),
            ).toEntity(),
        )
    }
    val samples = (0..5).mapTo(mutableSetOf()) { "demo-journey-$it" }
    journeys().filter { it.id in samples }.forEach { journey ->
        if (pointTransaction("journey:${journey.id}") == null) creditJourney(journey.toDomain())
    }
    touch()
}

private fun demoAssessment(score: Int, starts: Int) = DrivingAssessment(
    elapsedMillis = 1_800_000, observedMillis = 1_799_000, movingMillis = 1_440_000,
    sampleCount = 9_000, rejectedSamples = 0, harshAccelerations = if (score == 75) 10 else 2,
    harshBrakings = 1, harshStarts = starts, longestGapMillis = 200,
)
