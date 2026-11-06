package io.issac.pleosandroid.core.data.collection

import android.content.Context
import androidx.room.Room
import androidx.test.core.app.ApplicationProvider
import androidx.test.ext.junit.runners.AndroidJUnit4
import io.issac.pleosandroid.core.data.collection.local.CardAwardWriter
import io.issac.pleosandroid.core.data.collection.local.CatalogSource
import io.issac.pleosandroid.core.data.collection.local.CollectionStore
import io.issac.pleosandroid.core.data.collection.repository.LocalCatalogRepository
import io.issac.pleosandroid.core.data.collection.repository.LocalCollectionRepository
import io.issac.pleosandroid.core.data.collection.repository.LocalHistoryRepository
import io.issac.pleosandroid.core.data.collection.repository.LocalPlaceRepository
import io.issac.pleosandroid.core.data.collection.repository.LocalProgressRepository
import io.issac.pleosandroid.core.database.CollectionDatabase
import io.issac.pleosandroid.core.database.CollectionDatabaseFactory
import io.issac.pleosandroid.core.database.CollectionDatabaseProvider
import io.issac.pleosandroid.core.database.entity.CardMemoryEntity
import io.issac.pleosandroid.core.domain.collection.model.AssessmentStatus
import io.issac.pleosandroid.core.domain.collection.model.BeginRevealResult
import io.issac.pleosandroid.core.domain.collection.model.CardDefinition
import io.issac.pleosandroid.core.domain.collection.model.CardFront
import io.issac.pleosandroid.core.domain.collection.model.CardKind
import io.issac.pleosandroid.core.domain.collection.model.CardLevelRule
import io.issac.pleosandroid.core.domain.collection.model.CardTemplate
import io.issac.pleosandroid.core.domain.collection.model.CatalogPack
import io.issac.pleosandroid.core.domain.collection.model.CatalogResult
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
import io.issac.pleosandroid.core.domain.collection.model.RevealSession
import java.time.Instant
import java.util.UUID
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
    private val front = CardFront("해변 카드", "", "", "", "art", "gloss", "engraving", CardTemplate.Standard)
    private val card = CardDefinition("card", CardKind.Place, place.id, front, 1)
    private val rules = (1..5).map { CardLevelRule(card.id, it, 1, it, start) }
    private val pack = CatalogPack(1, listOf(place), listOf(card), rules)
    private lateinit var db: CollectionDatabase
    private lateinit var demoDb: CollectionDatabase
    private lateinit var store: CollectionStore
    private lateinit var progress: LocalProgressRepository
    private lateinit var collection: LocalCollectionRepository
    private lateinit var catalog: LocalCatalogRepository
    private lateinit var generation: String
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
        val awards = CardAwardWriter()
        progress = LocalProgressRepository(store, awards)
        collection = LocalCollectionRepository(store)
        catalog = LocalCatalogRepository(store, source, awards)
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
    private suspend fun snapshot() = collection.observeCollection().first()
    private suspend fun record(id: String) {
        assertTrue(progress.recordProgress(event(id)) is RecordProgressResult.Applied)
    }
    private suspend fun openNext(): RevealSession = (collection.beginReveal(card.id) as BeginRevealResult.Ready).session

    @Test fun newDatabaseContainsCatalogButNoOwnedCards() = runBlocking<Unit> {
        assertTrue(snapshot().cards.isEmpty())
        assertEquals(listOf(place.id), LocalPlaceRepository(store).observePlaces().first().map { it.place.id })
        assertEquals(0, collection.observeCardProgress(card.id).first()!!.earnedLevel)
    }

    @Test fun confirmedVisitGrantsAnUnopenedCardAndDurableNotice() = runBlocking<Unit> {
        val result = progress.recordProgress(event()) as RecordProgressResult.Applied
        assertEquals(1, result.receipt.newCards.size)
        val card = snapshot().cards.single()
        assertNull(card.front)
        assertNull(card.revealedLevel)
        assertEquals(1, card.earnedLevel)
        assertEquals(1, card.memories.size)
        assertEquals(result.receipt, progress.getProgressResult("visit"))
        assertEquals(1, collection.observeRewardNotices().first().size)
    }

    @Test fun concurrentDuplicateRequestsGrantExactlyOneMemoryAndNotice() = runBlocking<Unit> {
        val results = (1..2).map { async { progress.recordProgress(event()) } }.awaitAll()
        assertEquals(1, results.count { it is RecordProgressResult.Applied })
        assertEquals(1, results.count { it is RecordProgressResult.AlreadyProcessed })
        assertEquals(1, snapshot().cards.single().memories.size)
        assertEquals(1, collection.observeRewardNotices().first().size)
    }

    @Test fun duplicateSourceWithDifferentOperationDoesNotAwardAgain() = runBlocking<Unit> {
        record("v")
        assertTrue(progress.recordProgress(event("v", "retry")) is RecordProgressResult.AlreadyProcessed)
        assertEquals(1, snapshot().cards.single().earnedLevel)
        assertEquals(
            RecordProgressResult.Rejected(ProgressRejection.ConflictingOperation),
            progress.recordProgress(event("other", "v")),
        )
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
        assertTrue(LocalHistoryRepository(store).observeVisits().first().isEmpty())
    }

    @Test fun previewDoesNotPersistOrConsumeAnOperationId() = runBlocking<Unit> {
        val preview = progress.previewProgress(event()) as ProgressPreview.Eligible
        assertEquals(1, preview.changes.single().toLevel)
        assertTrue(snapshot().cards.isEmpty())
        assertNull(progress.getProgressResult("visit"))
        assertTrue(progress.recordProgress(event()) is RecordProgressResult.Applied)
    }

    @Test fun acknowledgingANoticeDoesNotOpenTheCard() = runBlocking<Unit> {
        record("v")
        val notice = collection.observeRewardNotices().first().single()
        assertEquals(MutationResult.Success, collection.acknowledgeRewardNotice(notice.id))
        assertTrue(collection.observeRewardNotices().first().isEmpty())
        assertNull(snapshot().cards.single().revealedLevel)
        assertEquals(MutationResult.NotFound, collection.setRepresentativeCard(card.id))
    }

    @Test fun revealAcknowledgesOnlyItsTargetWhileAdditionalGrowthArrives() = runBlocking<Unit> {
        record("one")
        collection.completeReveal(openNext().token)
        record("two")
        val session = openNext()
        assertEquals(2, session.token.targetLevel)
        record("three")
        assertEquals(MutationResult.Success, collection.completeReveal(session.token))
        assertEquals(MutationResult.AlreadyApplied, collection.completeReveal(session.token))
        val result = snapshot().cards.single()
        assertEquals(3, result.earnedLevel)
        assertEquals(2, result.revealedLevel)
        assertEquals(3, openNext().token.targetLevel)
    }

    @Test fun unopenedAndRepresentativeStateSurviveDatabaseReopen() = runBlocking<Unit> {
        val name = "collection-test-${UUID.randomUUID()}.db"
        db.close()
        db = Room.databaseBuilder(context, CollectionDatabase::class.java, name)
            .addCallback(CollectionDatabase.constraints).build()
        val provider = object : CollectionDatabaseProvider {
            override fun create(demo: Boolean) = db
        }
        connect(provider)
        generation = progress.getDataGeneration()
        record("one")
        record("two")
        collection.completeReveal(openNext().token)
        collection.setRepresentativeCard(card.id)
        db.close()
        db = Room.databaseBuilder(context, CollectionDatabase::class.java, name)
            .addCallback(CollectionDatabase.constraints).build()
        connect(provider)
        assertEquals(2, snapshot().cards.single().earnedLevel)
        assertEquals(1, snapshot().cards.single().revealedLevel)
        assertEquals(card.id, snapshot().representativeId)
        assertEquals(2, openNext().token.targetLevel)
        db.close()
        context.deleteDatabase(name)
    }

    @Test fun deletingLastEvidenceClearsRepresentativeAndRejectsOldRevealAndRetry() = runBlocking<Unit> {
        record("one")
        val old = openNext().token
        collection.completeReveal(old)
        collection.setRepresentativeCard(card.id)
        assertEquals(
            MutationResult.Success,
            progress.deleteHistory(DeleteHistoryRequest(HistoryKind.Visit, "one", generation)),
        )
        assertTrue(snapshot().cards.isEmpty())
        assertNull(snapshot().representativeId)
        assertTrue(collection.observeRewardNotices().first().isEmpty())
        assertEquals(
            RecordProgressResult.Rejected(ProgressRejection.DeletedSource),
            progress.recordProgress(event("one")),
        )
        record("new")
        assertNotEquals(old.ownershipId, snapshot().cards.single().ownershipId)
        assertEquals(MutationResult.Stale, collection.completeReveal(old))
    }

    @Test fun resetRejectsWorkCreatedBeforeTheReset() = runBlocking<Unit> {
        val delayed = event("delayed")
        record("one")
        val token = openNext().token
        progress.clearUserData()
        assertNotEquals(generation, progress.getDataGeneration())
        assertEquals(RecordProgressResult.Rejected(ProgressRejection.StaleGeneration), progress.recordProgress(delayed))
        assertEquals(MutationResult.Stale, collection.completeReveal(token))
        assertTrue(snapshot().cards.isEmpty())
    }

    @Test fun failureInAwardWriteRollsBackSourceAndReceipt() = runBlocking<Unit> {
        db.openHelper.writableDatabase.execSQL(
            "CREATE TRIGGER fail_notice BEFORE INSERT ON reward_notice " +
                "BEGIN SELECT RAISE(ABORT, 'injected failure'); END",
        )
        assertEquals(RecordProgressResult.StorageFailure, progress.recordProgress(event()))
        assertTrue(LocalHistoryRepository(store).observeVisits().first().isEmpty())
        assertTrue(snapshot().cards.isEmpty())
        assertNull(progress.getProgressResult("visit"))
        db.openHelper.writableDatabase.execSQL("DROP TRIGGER fail_notice")
        assertTrue(progress.recordProgress(event()) is RecordProgressResult.Applied)
    }

    @Test fun catalogUpdateAddsUnownedPlacesAndPreservesOwnershipAndOpenState() = runBlocking<Unit> {
        record("one")
        collection.completeReveal(openNext().token)
        val newPlace = place.copy(id = "new-place", name = "새 장소")
        val updated = pack.copy(
            version = 2,
            places = pack.places + newPlace,
            cards = listOf(card.copy(front = front.copy(title = "새 이름"))),
        )
        assertEquals(CatalogResult.Applied, catalog.applyCatalog(updated))
        assertEquals(2, LocalPlaceRepository(store).observePlaces().first().size)
        assertEquals(1, snapshot().cards.single().revealedLevel)
        assertEquals("새 이름", snapshot().cards.single().front!!.title)
        assertEquals(CatalogResult.Unchanged, catalog.applyCatalog(updated))
        assertTrue(
            catalog.applyCatalog(
                updated.copy(
                    version = 3,
                    rules = rules.map { it.copy(requiredCount = it.requiredCount + 1) },
                ),
            ) is CatalogResult.Rejected,
        )
    }

    @Test fun oneVisitCanAwardMultipleCardsWithoutDuplicatingThePlacePin() = runBlocking<Unit> {
        val second = card.copy(id = "second")
        catalog.applyCatalog(
            pack.copy(
                version = 2,
                cards = pack.cards + second,
                rules = rules + rules.map { it.copy(cardId = second.id) },
            ),
        )
        val result = progress.recordProgress(event()) as RecordProgressResult.Applied
        assertEquals(2, result.receipt.newCards.size)
        assertEquals(2, snapshot().cards.size)
        assertEquals(1, LocalPlaceRepository(store).observePlaces().first().size)
    }

    @Test fun maxLevelDoesNotPreventNewMemories() = runBlocking<Unit> {
        (1..7).forEach { record("visit-$it") }
        assertEquals(5, snapshot().cards.single().earnedLevel)
        assertEquals(7, snapshot().cards.single().memories.size)
        assertEquals(5, collection.observeRewardNotices().first().size)
    }

    @Test fun catalogRejectsJourneyConditionsThatAPlaceRuleCannotEvaluate() = runBlocking<Unit> {
        val added = card.copy(id = "unsupported")
        val result = catalog.applyCatalog(
            pack.copy(
                version = 2,
                cards = listOf(added),
                rules = rules.map { it.copy(cardId = added.id, minimumScore = 80) },
            ),
        )
        assertTrue(result is CatalogResult.Rejected)
        assertNull(collection.observeCardProgress(added.id).first())
    }

    @Test fun correctedAssessmentPreviewAndWriteRetainOnlyStillQualifiedHistoricalGrants() = runBlocking<Unit> {
        val safety = card.copy(id = "safety", kind = CardKind.Safety, placeId = null)
        val originalRules = rules.map { it.copy(cardId = safety.id, minimumScore = 85) }
        catalog.applyCatalog(pack.copy(version = 2, cards = listOf(safety), rules = originalRules))
        val journey = JourneyRecord(
            "journey",
            start,
            start.plusSeconds(600),
            "Asia/Seoul",
            2000,
            AssessmentStatus.Complete,
            score = 90,
        )
        progress.recordProgress(ProgressEvent.JourneyCompleted("initial", generation, journey, true))
        val futureRules = originalRules.map { it.copy(version = 2, eligibleFrom = start.plusSeconds(10_000)) }
        catalog.applyCatalog(
            pack.copy(version = 3, cards = listOf(safety.copy(ruleVersion = 2)), rules = futureRules),
        )

        val corrected = journey.copy(score = 88, assessmentVersion = 2)
        val event = ProgressEvent.JourneyAssessmentUpdated("correction", generation, corrected, true)
        val preview = progress.previewProgress(event) as ProgressPreview.Eligible
        assertEquals(1, preview.progress.single().earnedLevel)
        val written = progress.recordProgress(event) as RecordProgressResult.Applied
        assertEquals(preview.progress, written.receipt.progress)
        assertEquals(1, snapshot().cards.single().earnedLevel)

        val revoked = event.copy(operationId = "revoke", journey = corrected.copy(score = 70, assessmentVersion = 3))
        val revokedPreview = progress.previewProgress(revoked) as ProgressPreview.Eligible
        val revokedWrite = progress.recordProgress(revoked) as RecordProgressResult.Applied
        assertEquals(revokedPreview.progress, revokedWrite.receipt.progress)
        assertEquals(0, revokedWrite.receipt.changes.single().toLevel)
        assertTrue(snapshot().cards.isEmpty())
    }

    @Test fun pendingJourneyAwardsOnlyAfterItsAssessmentIsCompleted() = runBlocking<Unit> {
        val safety = card.copy(id = "safety", kind = CardKind.Safety, placeId = null)
        val safetyRules = rules.map {
            it.copy(cardId = safety.id, minimumScore = 85, minimumDistanceMeters = 1000)
        }
        catalog.applyCatalog(pack.copy(version = 2, cards = pack.cards + safety, rules = rules + safetyRules))
        val pending = JourneyRecord(
            "journey",
            start,
            start.plusSeconds(600),
            "Asia/Seoul",
            2000,
            AssessmentStatus.Pending,
        )
        val first = progress.recordProgress(ProgressEvent.JourneyCompleted("j1", generation, pending, true))
        assertTrue(first is RecordProgressResult.Applied)
        assertTrue(snapshot().cards.isEmpty())
        val complete = pending.copy(assessment = AssessmentStatus.Complete, score = 90, assessmentVersion = 2)
        val updated = progress.recordProgress(
            ProgressEvent.JourneyAssessmentUpdated("j2", generation, complete, true),
        )
        assertTrue(updated is RecordProgressResult.Applied)
        assertEquals(1, snapshot().cards.single().earnedLevel)
        val retried = progress.recordProgress(
            ProgressEvent.JourneyAssessmentUpdated("j3", generation, complete, true),
        )
        assertTrue(retried is RecordProgressResult.AlreadyProcessed)
        assertEquals(1, snapshot().cards.single().memories.size)
    }

    @Test fun databaseConstraintRejectsMemoryWithoutSource() = runBlocking<Unit> {
        val result = runCatching {
            db.collectionDao().putMemories(listOf(CardMemoryEntity("invalid", card.id, null, null, 0, "2026-01-01")))
        }
        assertTrue(result.isFailure)
    }

    @Test fun duplicateOperationAliasCannotLaterBeUsedForAnotherSource() = runBlocking<Unit> {
        record("first")
        assertTrue(progress.recordProgress(event("first", "alias")) is RecordProgressResult.AlreadyProcessed)
        assertEquals(
            RecordProgressResult.Rejected(ProgressRejection.ConflictingOperation),
            progress.recordProgress(event("second", "alias")),
        )
        assertEquals(1, snapshot().cards.single().memories.size)
    }

    @Test fun committedVisitCanBeRetriedAfterPlaceIsDeactivated() = runBlocking<Unit> {
        record("first")
        catalog.applyCatalog(pack.copy(version = 2, places = listOf(place.copy(active = false))))
        assertTrue(progress.recordProgress(event("first")) is RecordProgressResult.AlreadyProcessed)
        assertEquals(
            RecordProgressResult.Rejected(ProgressRejection.VisitNotQualified),
            progress.recordProgress(event("new")),
        )
    }

    @Test fun deletingEvidenceNeverGrantsNewCatalogCards() = runBlocking<Unit> {
        record("first")
        record("second")
        val added = card.copy(id = "added")
        catalog.applyCatalog(
            pack.copy(
                version = 2,
                cards = listOf(added),
                rules = rules.map { it.copy(cardId = added.id) },
            ),
        )
        progress.deleteHistory(DeleteHistoryRequest(HistoryKind.Visit, "first", generation))
        assertEquals(listOf(card.id), snapshot().cards.map { it.id })
        assertEquals(1, snapshot().cards.single().earnedLevel)
    }

    @Test fun demoDatabaseIsIsolatedFromUserOwnership() = runBlocking<Unit> {
        record("user-visit")
        store.setDemoMode(true)
        assertTrue(snapshot().cards.isEmpty())
        generation = progress.getDataGeneration()
        record("demo-visit")
        store.setDemoMode(false)
        assertEquals("user-visit", snapshot().cards.single().memories.single().visitId)
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
