package io.issac.pleosandroid.core.data.collection.repository

import io.issac.pleosandroid.core.data.BuildConfig
import io.issac.pleosandroid.core.data.collection.local.CardAwardWriter
import io.issac.pleosandroid.core.data.collection.local.CollectionStore
import io.issac.pleosandroid.core.data.collection.mapper.toEntity
import io.issac.pleosandroid.core.data.driving.encode
import io.issac.pleosandroid.core.database.entity.CollectionDisplayEntity
import io.issac.pleosandroid.core.database.entity.UserDataStateEntity
import io.issac.pleosandroid.core.domain.collection.model.AssessmentStatus
import io.issac.pleosandroid.core.domain.collection.model.ConfirmedVisit
import io.issac.pleosandroid.core.domain.collection.model.JourneyRecord
import io.issac.pleosandroid.core.domain.collection.repository.CollectionDemoRepository
import io.issac.pleosandroid.core.domain.driving.model.DrivingAssessment
import io.issac.pleosandroid.core.domain.driving.model.InsufficientReason
import java.time.LocalDate
import java.time.ZoneId
import java.util.UUID
import javax.inject.Inject

internal class LocalDemoRepository @Inject constructor(
    private val store: CollectionStore,
    private val awards: CardAwardWriter,
) : CollectionDemoRepository {
    override val available = BuildConfig.DEBUG
    override val enabled = store.demoMode

    override suspend fun setEnabled(enabled: Boolean) {
        require(available || !enabled)
        if (enabled) {
            val empty = store.demoTransaction { ownedCards().isEmpty() }
            if (empty) resetDemo()
            prepareDemoReports()
        }
        store.setDemoMode(enabled)
    }

    override suspend fun resetDemo() {
        check(available)
        store.demoTransaction {
            clearOwned()
            clearVisits()
            clearJourneys()
            clearOperations()
            clearDrivingDraft()
            putUserState(
                UserDataStateEntity(
                    generation = UUID.randomUUID().toString(),
                    revision =
                        checkNotNull(userState()).revision + 1,
                ),
            )
            val days = mapOf(
                "haeundae" to listOf("10-18", "10-25", "11-02"),
                "gwangalli" to listOf("10-19", "10-31"),
                "cheongsapo" to listOf("10-20", "10-28"),
                "huinnyeoul" to listOf("10-21", "10-24", "10-27", "11-01"),
                "igidae" to listOf("10-22"),
                "songdo" to listOf("11-02"),
            )
            days.forEach { (place, dates) ->
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
            awards.reconcile(this, "demo-initial")
            val revealed = mapOf(
                "place-haeundae-001" to 2,
                "place-gwangalli-001" to 2,
                "place-cheongsapo-001" to 1,
                "place-huinnyeoul-001" to 3,
                "place-igidae-001" to 1,
                "safety-smooth-001" to 4,
                "safety-gentle-start-001" to 1,
            )
            ownedCards().forEach { card ->
                val level = revealed[card.cardId]
                putOwned(card.copy(revealedLevel = level))
                putLevels(
                    levels().filter { it.ownershipId == card.ownershipId }.map {
                        if (level != null && it.level <= level) it.copy(revealedAt = it.earnedAt) else it
                    },
                )
            }
            notices().forEach { acknowledgeNotice(it.id, it.createdAt) }
            putDisplay(CollectionDisplayEntity(cardId = "place-haeundae-001"))
            touch()
        }
        prepareDemoReports()
    }

    private suspend fun prepareDemoReports() = store.demoTransaction {
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
        touch()
    }
}

private fun demoAssessment(score: Int, starts: Int) = DrivingAssessment(
    elapsedMillis = 1_800_000, observedMillis = 1_799_000, movingMillis = 1_440_000,
    sampleCount = 9_000, rejectedSamples = 0, harshAccelerations = if (score == 75) 10 else 2,
    harshBrakings = 1, harshStarts = starts, longestGapMillis = 200,
)
