package io.issac.pleosandroid.core.data.cards

import android.content.Context
import android.os.Bundle
import androidx.room.Room
import androidx.test.core.app.ApplicationProvider
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import io.issac.pleosandroid.core.data.collection.local.CatalogLoader
import io.issac.pleosandroid.core.data.collection.local.CatalogSource
import io.issac.pleosandroid.core.data.collection.local.CollectionStore
import io.issac.pleosandroid.core.data.collection.repository.DefaultProgressRepository
import io.issac.pleosandroid.core.data.collection.repository.DefaultRegionVisitPackSource
import io.issac.pleosandroid.core.data.collection.repository.DefaultVisitPackRepository
import io.issac.pleosandroid.core.data.driving.DrivingClock
import io.issac.pleosandroid.core.data.points.DefaultPointRepository
import io.issac.pleosandroid.core.database.CollectionDatabase
import io.issac.pleosandroid.core.database.CollectionDatabaseProvider
import io.issac.pleosandroid.core.domain.cards.CardGrade
import io.issac.pleosandroid.core.domain.cards.CopyStatus
import io.issac.pleosandroid.core.domain.cards.ForgeOutcome
import io.issac.pleosandroid.core.domain.cards.PackType
import io.issac.pleosandroid.core.domain.collection.model.AssessmentStatus
import io.issac.pleosandroid.core.domain.collection.model.JourneyRecord
import io.issac.pleosandroid.core.domain.collection.model.ProgressEvent
import io.issac.pleosandroid.core.domain.collection.model.RecordProgressResult
import io.issac.pleosandroid.core.domain.collection.model.VisitPackClaimRequest
import io.issac.pleosandroid.core.domain.collection.model.VisitPackClaimResult
import io.issac.pleosandroid.core.domain.driving.model.SpeedSample
import io.issac.pleosandroid.core.domain.driving.policy.DrivingAnalyzer
import io.issac.pleosandroid.core.domain.points.PointEarningPolicy
import io.issac.pleosandroid.core.domain.points.PointTransactionKind
import java.io.File
import java.time.Instant
import kotlin.random.Random
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.runBlocking
import org.json.JSONArray
import org.json.JSONObject
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith

/** Synthetic speed samples and scripted rolls exercise real settlement and forge transactions in an isolated DB. */
@RunWith(AndroidJUnit4::class)
class BukangiGrowthIntegrationTest {
    private val context: Context = ApplicationProvider.getApplicationContext()
    private val database = Room.inMemoryDatabaseBuilder(context, CollectionDatabase::class.java).build()
    private val catalog = object : CatalogSource {
        override fun load() = CatalogLoader(context).load()
        override fun hasAsset(key: String) = true
    }
    private val provider = object : CollectionDatabaseProvider {
        override fun create(demo: Boolean): CollectionDatabase {
            check(!demo) { "This scenario only uses its isolated real-profile test database" }
            return database
        }
    }
    private val endedAt = Instant.parse("2026-11-12T04:00:00Z")
    private val clock = object : DrivingClock {
        override fun now() = endedAt
        override fun elapsedNanos() = 0L
    }
    private val checkpoints = JSONArray()
    private lateinit var store: CollectionStore
    private lateinit var album: DefaultCardAlbumRepository
    private lateinit var points: DefaultPointRepository

    @Before fun init() {
        context.getSharedPreferences("collection_demo", Context.MODE_PRIVATE).edit().clear().commit()
        store = CollectionStore(context, provider, catalog)
        album = DefaultCardAlbumRepository(store, BukangiThenSuccessRandom())
        points = DefaultPointRepository(store, clock)
    }

    @After fun close() {
        database.close()
        context.getSharedPreferences("collection_demo", Context.MODE_PRIVATE).edit().clear().commit()
    }

    @Test fun haeundaeRewardAndMeasuredJourneyPointsGrowTheSameBukangiToTwentyFive() = runBlocking {
        val progress = DefaultProgressRepository(store)
        val generation = progress.getDataGeneration()
        val journey = measuredJourney()
        assertEquals(2_000L, points.observeWallet().first().balanceHalfPoints)
        checkpoint("before-visit")

        val visits = DefaultVisitPackRepository(store, clock)
        val request = VisitPackClaimRequest(journey.id, "haeundae", "해운대 해변", "해수욕장", generation)
        val claim = visits.claim(request) as VisitPackClaimResult.Applied
        assertTrue(visits.claim(request) is VisitPackClaimResult.AlreadyClaimed)
        val received = album.observe().first().inbox.single { it.id == claim.receipt.albumPackId }
        assertEquals(PackType.Busan, received.packType)
        assertEquals("방문 보상", received.source)
        assertEquals(journey.id, received.journeyId)
        assertEquals(mapOf("KR-26" to 1), DefaultRegionVisitPackSource(store).regionVisitPackCounts.first())
        checkpoint("visit-pack-received")

        val copyId = album.open(received.id, generation)
        assertEquals(copyId, album.open(received.id, generation))
        album.reveal(copyId, generation)
        val drawn = album.observe().first().collection.single()
        assertEquals("busan-bukangi", drawn.print.id)
        assertEquals(CardGrade.Rare, drawn.grade)
        assertFalse(drawn.variant)
        assertEquals(0, drawn.level)
        checkpoint("bukangi-revealed", copyId)

        val event = ProgressEvent.JourneyCompleted("settle:bukangi-drive", generation, journey, true)
        assertTrue(progress.recordProgress(event) is RecordProgressResult.Applied)
        assertTrue(progress.recordProgress(event) is RecordProgressResult.AlreadyProcessed)
        val earned = points.observeWallet().first().transactions.single {
            it.kind == PointTransactionKind.JourneyEarned
        }
        assertEquals(journey.id, earned.sourceId)
        assertEquals(300_000L, earned.eligibleDistanceMeters)
        assertEquals(100, earned.assessedScore)
        assertEquals(1_500, earned.multiplierThousandths)
        assertEquals(18_000L, earned.deltaHalfPoints)
        assertEquals(0L, earned.membershipBonusHalfPoints)
        assertEquals(20_000L, points.observeWallet().first().balanceHalfPoints)
        val week = PointEarningPolicy.weekStart(PointEarningPolicy.drivingDate(journey.endedAt)).toEpochDay()
        assertEquals(300_000L, database.collectionDao().recognizedDistanceForWeek(week))
        assertTrue(earned.eligibleDistanceMeters < PointEarningPolicy.WEEKLY_DISTANCE_LIMIT_METERS)
        checkpoint("journey-settled", copyId)

        repeat(25) { before ->
            val requestId = "forge:bukangi:${before + 1}"
            val receipt = album.enhance(copyId, before.toLong(), requestId, generation)
            assertEquals(ForgeOutcome.Success, receipt.outcome)
            assertEquals(before, receipt.before)
            assertEquals(before + 1, receipt.after)
            assertEquals((10L + before) * 30, receipt.costHalfPoints)
            assertEquals(receipt, album.enhance(copyId, before.toLong(), requestId, generation))
            album.acknowledge(receipt.id, generation)
            val level = before + 1
            val expectedBalance = when (level) {
                1 -> 19_700L
                10 -> 15_650L
                25 -> 3_500L
                else -> null
            }
            if (expectedBalance != null) {
                val saved = album.observe().first()
                assertEquals(level, saved.collection.single { it.id == copyId }.level)
                assertEquals(expectedBalance, saved.balanceHalfPoints)
                checkpoint("level-$level", copyId)
            }
        }
        val final = album.observe().first()
        val grown = final.collection.single()
        assertEquals(copyId, grown.id)
        assertEquals(25, grown.level)
        assertEquals(25, grown.highest)
        assertEquals(25L, grown.revision)
        assertEquals(CardGrade.Rare, grown.grade)
        assertEquals(CopyStatus.Owned, grown.status)
        assertEquals(received, grown.pack)
        assertEquals(25, final.history.size)
        assertEquals(16_500L, final.history.sumOf { it.costHalfPoints })
        assertTrue(final.history.all { it.acknowledged })
        assertEquals(3_500L, final.balanceHalfPoints)
        assertEquals(final.balanceHalfPoints, points.observeWallet().first().balanceHalfPoints)
        assertEquals(1, database.collectionDao().journeys().size)
        assertEquals(1, database.collectionDao().visitPacks().size)
        assertEquals(
            25,
            points.observeWallet().first().transactions.count {
                it.kind ==
                    PointTransactionKind.EnhancementSpent
            },
        )

        val reopened =
            DefaultCardAlbumRepository(CollectionStore(context, provider, catalog), BukangiThenSuccessRandom())
        assertEquals(final, reopened.observe().first())
        checkpoint("repository-reopened", copyId)
        writeEvidence(journey)
    }

    private fun measuredJourney(): JourneyRecord {
        val startNanos = 1_000_000_000L
        val durationSeconds = 12_025
        val analyzer = DrivingAnalyzer(startNanos)
        for (second in 0..durationSeconds) {
            val speed = when {
                second <= 25 -> second.toDouble()
                second <= 12_000 -> 25.0
                else -> (durationSeconds - second).toDouble()
            }
            analyzer.add(SpeedSample(startNanos + second * 1_000_000_000L, speed, true))
        }
        val measured = analyzer.result(startNanos + durationSeconds * 1_000_000_000L)
        assertEquals(300_000L, measured.distanceMeters)
        assertEquals(100, measured.score)
        assertEquals(12_026, measured.assessment.sampleCount)
        assertEquals(0, measured.assessment.harshAccelerations)
        assertEquals(0, measured.assessment.harshBrakings)
        assertTrue(measured.assessment.reasons.isEmpty())
        return JourneyRecord(
            "bukangi-drive-300km", endedAt.minusSeconds(durationSeconds.toLong()), endedAt, "Asia/Seoul",
            measured.distanceMeters, AssessmentStatus.Complete, measured.score, measured.assessment.harshStarts,
            driving = measured.assessment,
        )
    }

    private suspend fun checkpoint(phase: String, copyId: String? = null) {
        val snapshot = album.observe().first()
        val wallet = points.observeWallet().first()
        val card = copyId?.let { id -> snapshot.copies.single { it.id == id } }
        val earned = wallet.transactions.filter { it.kind == PointTransactionKind.JourneyEarned }
        val spent = wallet.transactions.filter { it.kind == PointTransactionKind.EnhancementSpent }
        checkpoints.put(
            JSONObject().apply {
                put("phase", phase)
                put("copyId", card?.id ?: JSONObject.NULL)
                put("printId", card?.print?.id ?: JSONObject.NULL)
                put("grade", card?.grade?.name ?: JSONObject.NULL)
                put("variant", card?.variant ?: JSONObject.NULL)
                put("level", card?.level ?: JSONObject.NULL)
                put("revision", card?.revision ?: JSONObject.NULL)
                put("sourcePackId", card?.pack?.id ?: JSONObject.NULL)
                put("sourcePlace", card?.pack?.place ?: JSONObject.NULL)
                put("sourceJourneyId", card?.pack?.journeyId ?: JSONObject.NULL)
                put("balanceHalfPoints", wallet.balanceHalfPoints)
                put("balancePoints", wallet.balanceHalfPoints / 2.0)
                put("earnedHalfPoints", earned.sumOf { it.deltaHalfPoints })
                put("spentHalfPoints", -spent.sumOf { it.deltaHalfPoints })
                put("pointSourceIds", JSONArray(earned.map { it.sourceId }))
                put(
                    "regionVisitPackCounts",
                    JSONObject(DefaultRegionVisitPackSource(store).regionVisitPackCounts.first()),
                )
                put("journeyCount", database.collectionDao().journeys().size)
                put("visitReceiptCount", database.collectionDao().visitPacks().size)
                put("forgeHistoryCount", snapshot.history.size)
            },
        )
    }

    private suspend fun writeEvidence(journey: JourneyRecord) {
        val facts = checkNotNull(journey.driving)
        val ledger = points.observeWallet().first().transactions
        val output = JSONObject().apply {
            put("testOnly", true)
            put("database", "isolated Room in-memory real-profile database")
            put("random", "scripted specialty roll 96, first specialty index 0, nonvariant roll 99, success rolls 0")
            put("journeySource", "production DrivingAnalyzer over synthetic one-second speed samples")
            put("journeyId", journey.id)
            put("distanceMeters", journey.distanceMeters)
            put("score", journey.score)
            put("policyVersion", facts.policyVersion)
            put("elapsedMillis", facts.elapsedMillis)
            put("movingMillis", facts.movingMillis)
            put("observedMillis", facts.observedMillis)
            put("sampleCount", facts.sampleCount)
            put("harshAccelerations", facts.harshAccelerations)
            put("harshBrakings", facts.harshBrakings)
            put("longestGapMillis", facts.longestGapMillis)
            put(
                "pointLedger",
                JSONArray(
                    ledger.map { entry ->
                        JSONObject().apply {
                            put("operationId", entry.operationId)
                            put("sourceId", entry.sourceId)
                            put("kind", entry.kind.name)
                            put("deltaHalfPoints", entry.deltaHalfPoints)
                            put("eligibleDistanceMeters", entry.eligibleDistanceMeters)
                            put("assessedScore", entry.assessedScore ?: JSONObject.NULL)
                            put("multiplierThousandths", entry.multiplierThousandths ?: JSONObject.NULL)
                        }
                    },
                ),
            )
            put("checkpoints", checkpoints)
        }
        val file = File(context.filesDir, "test-artifacts/bukangi-growth-state.json")
        file.parentFile?.mkdirs()
        file.writeText(output.toString(2))
        InstrumentationRegistry.getInstrumentation().sendStatus(
            0,
            Bundle().apply { putString("bukangiGrowthStatePath", file.absolutePath) },
        )
    }

    private class BukangiThenSuccessRandom : Random() {
        private val openingRolls = ArrayDeque(listOf(96, 0, 99))

        override fun nextBits(bitCount: Int): Int = error("Expected bounded test rolls")

        override fun nextInt(until: Int): Int {
            val roll = if (openingRolls.isEmpty()) 0 else openingRolls.removeFirst()
            check(roll in 0 until until)
            return roll
        }
    }
}
