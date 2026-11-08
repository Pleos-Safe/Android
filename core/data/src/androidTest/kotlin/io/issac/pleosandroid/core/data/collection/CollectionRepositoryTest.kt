package io.issac.pleosandroid.core.data.collection

import android.content.Context
import androidx.room.Room
import androidx.test.core.app.ApplicationProvider
import androidx.test.ext.junit.runners.AndroidJUnit4
import io.issac.pleosandroid.core.data.collection.local.CatalogSource
import io.issac.pleosandroid.core.data.collection.local.CollectionStore
import io.issac.pleosandroid.core.data.collection.repository.DefaultHistoryRepository
import io.issac.pleosandroid.core.data.collection.repository.DefaultProgressRepository
import io.issac.pleosandroid.core.data.collection.repository.DefaultVisitPackRepository
import io.issac.pleosandroid.core.data.driving.DrivingClock
import io.issac.pleosandroid.core.data.points.DefaultPointRepository
import io.issac.pleosandroid.core.database.CollectionDatabase
import io.issac.pleosandroid.core.database.CollectionDatabaseFactory
import io.issac.pleosandroid.core.database.CollectionDatabaseProvider
import io.issac.pleosandroid.core.domain.collection.model.AssessmentStatus
import io.issac.pleosandroid.core.domain.collection.model.CatalogPack
import io.issac.pleosandroid.core.domain.collection.model.ConfirmedVisit
import io.issac.pleosandroid.core.domain.collection.model.DeleteHistoryRequest
import io.issac.pleosandroid.core.domain.collection.model.HistoryKind
import io.issac.pleosandroid.core.domain.collection.model.JourneyRecord
import io.issac.pleosandroid.core.domain.collection.model.MutationResult
import io.issac.pleosandroid.core.domain.collection.model.Place
import io.issac.pleosandroid.core.domain.collection.model.ProgressEvent
import io.issac.pleosandroid.core.domain.collection.model.ProgressPreview
import io.issac.pleosandroid.core.domain.collection.model.ProgressRejection
import io.issac.pleosandroid.core.domain.collection.model.RecordProgressResult
import io.issac.pleosandroid.core.domain.collection.model.VisitPackClaimRequest
import io.issac.pleosandroid.core.domain.collection.model.VisitPackClaimResult
import io.issac.pleosandroid.core.domain.driving.model.DrivingAssessment
import io.issac.pleosandroid.core.domain.points.PointSpendResult
import io.issac.pleosandroid.core.domain.points.PointTransactionKind
import java.time.Instant
import kotlinx.coroutines.async
import kotlinx.coroutines.awaitAll
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.runBlocking
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith

@RunWith(AndroidJUnit4::class)
class CollectionRepositoryTest {
    private val context: Context = ApplicationProvider.getApplicationContext()
    private val start = Instant.parse("2026-02-01T00:00:00Z")
    private val place = Place("beach", "해변", "부산", "자연", 35.0, 129.0, 100.0, 60, "art")
    private val pack = CatalogPack(1, listOf(place))
    private lateinit var db: CollectionDatabase
    private lateinit var demoDb: CollectionDatabase
    private lateinit var store: CollectionStore
    private lateinit var progress: DefaultProgressRepository
    private lateinit var generation: String
    private var rewardNow = Instant.parse("2026-11-02T14:59:59Z")
    private val rewardClock = object : DrivingClock {
        override fun elapsedNanos() = 0L
        override fun now() = rewardNow
    }
    private val source = object : CatalogSource {
        override fun load() = pack
        override fun hasAsset(key: String) = key != "missing"
    }

    @Before fun setup() = runBlocking<Unit> {
        context.getSharedPreferences("collection_demo", Context.MODE_PRIVATE).edit().clear().commit()
        db = Room.inMemoryDatabaseBuilder(context, CollectionDatabase::class.java)
            .addCallback(CollectionDatabase.constraints).build()
        demoDb = Room.inMemoryDatabaseBuilder(context, CollectionDatabase::class.java)
            .addCallback(CollectionDatabase.constraints).build()
        connect(object : CollectionDatabaseProvider {
            override fun create(demo: Boolean) = if (demo) demoDb else db
        })
        generation = progress.getDataGeneration()
    }

    private fun connect(provider: CollectionDatabaseProvider) {
        store = CollectionStore(context, provider, source)
        progress = DefaultProgressRepository(store)
    }

    @After fun close() {
        db.close()
        demoDb.close()
    }

    private fun event(id: String = "visit", operation: String = id) = ProgressEvent.VisitConfirmed(
        operation,
        generation,
        ConfirmedVisit(
            id,
            place.id,
            arrivedAt = start.plusSeconds(60),
            departedAt = start.plusSeconds(120),
            confirmedAt = start.plusSeconds(125),
            zoneId = "Asia/Seoul",
            distanceFromPlaceMeters = 10.0,
            accuracyMeters = 5.0,
        ),
        storageConsent = true,
    )
    private suspend fun record(id: String) {
        assertTrue(progress.recordProgress(event(id)) is RecordProgressResult.Applied)
    }

    @Test fun consentAndUnqualifiedVisitsNeverPersist() = runBlocking<Unit> {
        assertEquals(
            RecordProgressResult.Rejected(ProgressRejection.ConsentRequired),
            progress.recordProgress(event().copy(storageConsent = false)),
        )
        assertEquals(
            RecordProgressResult.Rejected(ProgressRejection.VisitNotQualified),
            progress.recordProgress(event().let { it.copy(visit = it.visit.copy(accuracyMeters = 200.0)) }),
        )
        assertTrue(DefaultHistoryRepository(store).observeVisits().first().isEmpty())
    }

    @Test fun previewDoesNotPersistOrConsumeAnOperationId() = runBlocking<Unit> {
        assertEquals(ProgressPreview.Eligible, progress.previewProgress(event()))
        assertTrue(db.collectionDao().ownedCards().isEmpty())
        assertNull(progress.getProgressResult("visit"))
        assertTrue(progress.recordProgress(event()) is RecordProgressResult.Applied)
    }

    @Test fun resetRejectsWorkCreatedBeforeTheReset() = runBlocking<Unit> {
        val delayed = event("delayed")
        record("one")
        progress.clearUserData()
        assertNotEquals(generation, progress.getDataGeneration())
        assertEquals(RecordProgressResult.Rejected(ProgressRejection.StaleGeneration), progress.recordProgress(delayed))
        assertTrue(db.collectionDao().ownedCards().isEmpty())
    }

    @Test fun duplicateOperationAliasCannotLaterBeUsedForAnotherSource() = runBlocking<Unit> {
        record("first")
        assertTrue(progress.recordProgress(event("first", "alias")) is RecordProgressResult.AlreadyProcessed)
        assertEquals(
            RecordProgressResult.Rejected(ProgressRejection.ConflictingOperation),
            progress.recordProgress(event("second", "alias")),
        )
        assertEquals(1, db.collectionDao().visits().size)
    }

    @Test fun committedVisitCanBeRetriedAfterPlaceIsDeactivated() = runBlocking<Unit> {
        record("first")
        store.transaction { putPlaces(places().map { it.copy(active = false) }) }
        assertTrue(progress.recordProgress(event("first")) is RecordProgressResult.AlreadyProcessed)
        assertEquals(
            RecordProgressResult.Rejected(ProgressRejection.VisitNotQualified),
            progress.recordProgress(event("new")),
        )
    }

    @Test fun journeyCreditAndEnhancementDebitAreIdempotentThroughHistoryDeletion() = runBlocking<Unit> {
        val points = DefaultPointRepository(store, rewardClock)
        val record = JourneyRecord(
            id = "rewarded-drive",
            startedAt = rewardNow.minusSeconds(600),
            endedAt = rewardNow,
            zoneId = "Asia/Seoul",
            distanceMeters = 5_000,
            assessment = AssessmentStatus.Complete,
            score = 100,
            harshStarts = 0,
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
        val event = ProgressEvent.JourneyCompleted("rewarded-drive-complete", generation, record, true)
        assertTrue(progress.recordProgress(event) is RecordProgressResult.Applied)
        val retried = progress.recordProgress(event.copy(operationId = "retry-alias"))
        assertTrue(retried is RecordProgressResult.AlreadyProcessed)
        assertEquals(2300L, points.observeWallet().first().balanceHalfPoints)

        val spent = listOf(
            async { points.spendForEnhancement("upgrade", 50, generation) },
            async { points.spendForEnhancement("upgrade", 50, generation) },
        ).awaitAll()
        assertEquals(
            (spent.single { it is PointSpendResult.Applied } as PointSpendResult.Applied).transaction,
            (spent.single { it is PointSpendResult.AlreadyProcessed } as PointSpendResult.AlreadyProcessed).transaction,
        )
        assertEquals(PointSpendResult.ConflictingOperation, points.spendForEnhancement("upgrade", 40, generation))
        assertEquals(PointSpendResult.InsufficientBalance, points.spendForEnhancement("more", 1101, generation))

        val deletion = DeleteHistoryRequest(HistoryKind.Journey, record.id, generation)
        assertEquals(MutationResult.Success, progress.deleteHistory(deletion))
        val wallet = points.observeWallet().first()
        assertEquals(2200L, wallet.balanceHalfPoints)
        val credit = wallet.transactions.single { it.kind == PointTransactionKind.JourneyEarned }
        assertNull(credit.assessedScore)
        assertEquals(5_000L, credit.eligibleDistanceMeters)
        assertEquals(MutationResult.Success, progress.clearUserData())
        assertEquals(2000L, points.observeWallet().first().balanceHalfPoints)
        assertEquals(PointSpendResult.StaleGeneration, points.spendForEnhancement("upgrade", 50, generation))
    }

    @Test fun packClaimUsesKoreaDayAndFourteenDayCooldown() = runBlocking<Unit> {
        val packs = DefaultVisitPackRepository(store, rewardClock)
        db.collectionDao().putPlaces(
            (1..11).map {
                db.collectionDao().places().first().copy(id = "place-$it", rewardPack = "Busan")
            },
        )
        val first = VisitPackClaimRequest("journey", "place-1", "장소 1", "관광지", generation)
        val applied = packs.claim(first) as VisitPackClaimResult.Applied
        for (i in 2..10) {
            assertTrue(packs.claim(first.copy(placeId = "place-$i")) is VisitPackClaimResult.Applied)
        }
        val eleventh = first.copy(placeId = "place-11")
        assertEquals(VisitPackClaimResult.DailyLimitReached, packs.claim(eleventh))
        val retried = packs.claim(first.copy(placeTitle = "변경"))
        assertEquals(VisitPackClaimResult.AlreadyClaimed(applied.receipt), retried)

        rewardNow = rewardNow.plusSeconds(1)
        assertEquals(VisitPackClaimResult.PlaceCooldown, packs.claim(first.copy(journeyId = "next")))
        assertTrue(packs.claim(eleventh) is VisitPackClaimResult.Applied)
        rewardNow = applied.receipt.claimedAt.plusSeconds(14 * 24 * 60 * 60L)
        assertTrue(packs.claim(first.copy(journeyId = "later")) is VisitPackClaimResult.Applied)
        assertEquals(12, packs.observeInbox().first().size)

        assertEquals(MutationResult.Success, progress.clearUserData())
        assertTrue(packs.observeInbox().first().isEmpty())
        assertEquals(VisitPackClaimResult.StaleGeneration, packs.claim(first))
    }

    @Test fun aClaimedPackKeepsVisitHistoryButCannotAlsoAwardALegacyCard() = runBlocking<Unit> {
        val packs = DefaultVisitPackRepository(store, rewardClock)
        val journey = JourneyRecord(
            "pack-drive",
            start,
            start.plusSeconds(180),
            "Asia/Seoul",
            6_000,
            AssessmentStatus.Pending,
        )
        db.collectionDao().putPlaces(listOf(db.collectionDao().places().single().copy(rewardPack = "Busan")))
        val request = VisitPackClaimRequest(journey.id, place.id, place.name, "관광지", generation)
        assertTrue(packs.claim(request) is VisitPackClaimResult.Applied)
        assertTrue(
            progress.recordProgress(ProgressEvent.JourneyCompleted("drive", generation, journey, true))
                is RecordProgressResult.Applied,
        )
        val confirmed = event("pack-visit").let { it.copy(visit = it.visit.copy(journeyId = journey.id)) }
        assertTrue(progress.recordProgress(confirmed) is RecordProgressResult.Applied)
        assertEquals(confirmed.visit.id, db.collectionDao().visits().single().id)
        assertTrue(db.collectionDao().ownedCards().isEmpty())

        record("normal-visit")
        assertEquals(2, db.collectionDao().visits().size)
        assertTrue(db.collectionDao().ownedCards().isEmpty())
    }

    @Test fun encryptedFactoryUsesAStableWrappedKeyAndDoesNotWritePlainSqlite() = runBlocking<Unit> {
        check(context.packageName.endsWith(".test"))
        val factory = CollectionDatabaseFactory(context)
        val encrypted = factory.create()
        encrypted.collectionDao().places()
        encrypted.close()
        val file = context.getDatabasePath("collection.db")
        val header = file.inputStream().use { it.readNBytes(16) }.decodeToString()
        assertFalse(header.startsWith("SQLite format 3"))
        val reopened = factory.create()
        assertTrue(reopened.collectionDao().places().isEmpty())
        reopened.close()
    }
}
