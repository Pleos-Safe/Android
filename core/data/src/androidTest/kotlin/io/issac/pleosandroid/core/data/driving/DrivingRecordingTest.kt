package io.issac.pleosandroid.core.data.driving

import android.content.Context
import androidx.room.Room
import androidx.test.core.app.ApplicationProvider
import androidx.test.ext.junit.runners.AndroidJUnit4
import io.issac.pleosandroid.core.data.collection.local.CardAwardWriter
import io.issac.pleosandroid.core.data.collection.local.CatalogSource
import io.issac.pleosandroid.core.data.collection.local.CollectionStore
import io.issac.pleosandroid.core.data.collection.repository.LocalBadgeRegionSource
import io.issac.pleosandroid.core.data.collection.repository.LocalCollectionRepository
import io.issac.pleosandroid.core.data.collection.repository.LocalHistoryRepository
import io.issac.pleosandroid.core.data.collection.repository.LocalPlaceRepository
import io.issac.pleosandroid.core.data.collection.repository.LocalProgressRepository
import io.issac.pleosandroid.core.database.CollectionDatabase
import io.issac.pleosandroid.core.database.CollectionDatabaseProvider
import io.issac.pleosandroid.core.database.entity.DrivingDraftEntity
import io.issac.pleosandroid.core.domain.collection.model.AssessmentStatus
import io.issac.pleosandroid.core.domain.collection.model.CardDefinition
import io.issac.pleosandroid.core.domain.collection.model.CardFront
import io.issac.pleosandroid.core.domain.collection.model.CardKind
import io.issac.pleosandroid.core.domain.collection.model.CardLevelRule
import io.issac.pleosandroid.core.domain.collection.model.CardTemplate
import io.issac.pleosandroid.core.domain.collection.model.CatalogPack
import io.issac.pleosandroid.core.domain.collection.model.DeleteHistoryRequest
import io.issac.pleosandroid.core.domain.collection.model.HistoryKind
import io.issac.pleosandroid.core.domain.collection.model.JourneyRecord
import io.issac.pleosandroid.core.domain.collection.model.MutationResult
import io.issac.pleosandroid.core.domain.collection.model.Place
import io.issac.pleosandroid.core.domain.collection.model.ProgressEvent
import io.issac.pleosandroid.core.domain.collection.model.ProgressRejection
import io.issac.pleosandroid.core.domain.collection.model.RecordProgressResult
import io.issac.pleosandroid.core.domain.collection.repository.ProgressRepository
import io.issac.pleosandroid.core.domain.collection.repository.VisitLocation
import io.issac.pleosandroid.core.domain.collection.repository.VisitLocationSource
import io.issac.pleosandroid.core.domain.driving.model.DrivingAssessment
import io.issac.pleosandroid.core.domain.driving.model.InsufficientReason
import io.issac.pleosandroid.core.domain.driving.repository.RecordingPhase
import io.issac.pleosandroid.core.domain.model.MotionAvailability
import io.issac.pleosandroid.core.domain.model.VehicleGear
import io.issac.pleosandroid.core.domain.model.VehicleMotion
import io.issac.pleosandroid.core.domain.model.VehicleMotionState
import io.issac.pleosandroid.core.domain.model.VehicleState
import io.issac.pleosandroid.core.domain.policy.CarUxPolicy
import io.issac.pleosandroid.core.domain.repository.VehicleRepository
import java.time.Instant
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.cancelAndJoin
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.withTimeout
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith

@RunWith(AndroidJUnit4::class)
class DrivingRecordingTest {
    private val context: Context = ApplicationProvider.getApplicationContext()
    private lateinit var db: CollectionDatabase
    private lateinit var store: CollectionStore
    private lateinit var progress: LocalProgressRepository
    private lateinit var generation: String
    private val clock = TestClock()
    private val vehicle = TestVehicle(clock)
    private val sessionScope = CoroutineScope(SupervisorJob() + Dispatchers.Default)
    private val place = Place("beach", "해변", "부산", "해변", 35.15, 129.16, 250.0, 60, "art", regionId = "KR-26")
    private val journeys by lazy { LocalJourneyRepository(vehicle, clock, sessionScope) }

    @Before fun prepare() = runBlocking<Unit> {
        context.getSharedPreferences("collection_demo", Context.MODE_PRIVATE).edit().clear().commit()
        context.getSharedPreferences("driving_recording", Context.MODE_PRIVATE).edit().clear().commit()
        db =
            Room.inMemoryDatabaseBuilder(
                context,
                CollectionDatabase::class.java,
            ).addCallback(CollectionDatabase.constraints).build()
        val card =
            CardDefinition(
                "safe",
                CardKind.Safety,
                null,
                CardFront("기억", "", "", "", "art", "gloss", "engraving", CardTemplate.Standard),
                1,
            )
        val source = object : CatalogSource {
            override fun load() = CatalogPack(
                1,
                listOf(place),
                listOf(card, card.copy(id = "place-card", kind = CardKind.Place, placeId = place.id)),
                (1..5).map {
                    CardLevelRule(card.id, it, 1, it, Instant.EPOCH, minimumScore = 85, minimumDistanceMeters = 1_000)
                } + (1..5).map { CardLevelRule("place-card", it, 1, it, Instant.EPOCH) },
            )
            override fun hasAsset(key: String) = true
        }
        store = CollectionStore(
            context,
            object : CollectionDatabaseProvider {
                override fun create(demo: Boolean) = db
            },
            source,
        )
        progress = LocalProgressRepository(store, CardAwardWriter())
        generation = progress.getDataGeneration()
    }

    @After fun close() {
        sessionScope.cancel()
        db.close()
    }

    @Test fun completedMeasurementIsAtomicIdempotentAndObservable() = runBlocking {
        val journey = complete()
        val event = ProgressEvent.JourneyCompleted("drive", generation, journey, true)
        val first = progress.recordProgress(event) as RecordProgressResult.Applied
        assertEquals(listOf("safe"), first.receipt.newCards.map { it.cardId })
        assertEquals(journey, LocalHistoryRepository(store).observeJourney(journey.id).first())
        assertTrue(progress.recordProgress(event) is RecordProgressResult.AlreadyProcessed)
        assertEquals(1, LocalCollectionRepository(store).observeCard("safe").first()?.earnedLevel)
        assertEquals(1, db.collectionDao().journeys().size)
    }

    @Test fun insufficientMeasurementNeverAwardsOrInventsScore() = runBlocking {
        val record = complete().copy(
            assessment = AssessmentStatus.Insufficient,
            score = null,
            driving = complete().driving!!.copy(reasons = setOf(InsufficientReason.Interrupted)),
        )
        val result = progress.recordProgress(
            ProgressEvent.JourneyCompleted("short", generation, record, true),
        ) as RecordProgressResult.Applied
        assertTrue(result.receipt.changes.isEmpty())
        assertNull(LocalHistoryRepository(store).observeJourney(record.id).first()?.score)
    }

    @Test fun inconsistentCompleteScoreAndPoorCoverageAreRejected() = runBlocking {
        listOf(
            complete().copy(score = 100),
            complete().copy(driving = complete().driving!!.copy(observedMillis = 1000)),
        ).forEach { record ->
            assertEquals(
                RecordProgressResult.Rejected(ProgressRejection.InvalidInput),
                progress.recordProgress(ProgressEvent.JourneyCompleted("bad", generation, record, true)),
            )
        }
        assertTrue(db.collectionDao().journeys().isEmpty())
    }

    @Test fun retryAfterCommitBeforeCheckpointDeletionDoesNotAwardTwice() = runBlocking {
        val record = complete()
        progress.recordProgress(ProgressEvent.JourneyCompleted("driving:${record.id}", generation, record, true))
        db.collectionDao().putDrivingDraft(
            DrivingDraftEntity(generation = generation, payload = record.encodeDraft(), finished = true),
        )
        val recorder = recorder()
        assertEquals(MutationResult.Success, recorder.setEnabled(true))
        val job = launch(Dispatchers.Default) { recorder.monitor() }
        try {
            withTimeout(10_000) { recorder.state.first { it.phase == RecordingPhase.Saved } }
            assertNull(db.collectionDao().drivingDraft())
            assertEquals(1, LocalCollectionRepository(store).observeCard("safe").first()?.earnedLevel)
            assertEquals(1, db.collectionDao().journeys().size)
        } finally {
            job.cancelAndJoin()
        }
    }

    @Test fun recoveryOfInterruptedCheckpointPreservesFactsAndWithholdsAward() = runBlocking {
        val record = complete().copy(
            assessment = AssessmentStatus.Insufficient,
            score = null,
            driving = complete().driving!!.copy(reasons = setOf(InsufficientReason.Interrupted)),
        )
        db.collectionDao().putDrivingDraft(
            DrivingDraftEntity(generation = generation, payload = record.encodeDraft(), finished = false),
        )
        val recorder = recorder()
        recorder.setEnabled(true)
        val job = launch(Dispatchers.Default) { recorder.monitor() }
        try {
            withTimeout(10_000) { recorder.state.first { it.phase == RecordingPhase.Saved } }
            assertEquals(record, LocalHistoryRepository(store).observeJourney(record.id).first())
            assertTrue(db.collectionDao().ownedCards().isEmpty())
        } finally {
            job.cancelAndJoin()
        }
    }

    @Test fun generationResetCannotResurrectPendingRecording() = runBlocking {
        val recorder = recorder()
        recorder.setEnabled(true)
        val record = complete()
        val stale = DrivingDraftEntity(generation = generation, payload = record.encodeDraft(), finished = true)
        progress.clearUserData()
        db.collectionDao().putDrivingDraft(stale)
        val job = launch(Dispatchers.Default) { recorder.monitor() }
        try {
            withTimeout(10_000) { while (db.collectionDao().drivingDraft() != null) delay(20) }
            assertTrue(db.collectionDao().journeys().isEmpty())
            assertTrue(db.collectionDao().ownedCards().isEmpty())
        } finally {
            job.cancelAndJoin()
        }
    }

    @Test fun consentOffDiscardsUnfinishedDraftAndCannotBeEnabledWhileMoving() = runBlocking {
        val recorder = recorder()
        recorder.setEnabled(true)
        db.collectionDao().putDrivingDraft(
            DrivingDraftEntity(generation = generation, payload = complete().encodeDraft(), finished = false),
        )
        assertEquals(MutationResult.Success, recorder.setEnabled(false))
        assertNull(db.collectionDao().drivingDraft())
        vehicle.move(10f)
        assertEquals(MutationResult.Restricted, recorder.setEnabled(true))
        assertFalse(recorder.enabled.value)
    }

    @Test fun storageFailureRetainsFinishedDraftForExplicitRetry() = runBlocking {
        var failing = true
        val flaky = object : ProgressRepository by progress {
            override suspend fun recordProgress(event: ProgressEvent) =
                if (failing) RecordProgressResult.StorageFailure else progress.recordProgress(event)
        }
        val recorder = recorder(flaky)
        recorder.setEnabled(true)
        db.collectionDao().putDrivingDraft(
            DrivingDraftEntity(generation = generation, payload = complete().encodeDraft(), finished = true),
        )
        val job = launch(Dispatchers.Default) { recorder.monitor() }
        try {
            withTimeout(10_000) { recorder.state.first { it.phase == RecordingPhase.Error } }
            assertNotNull(db.collectionDao().drivingDraft())
            failing = false
            assertEquals(MutationResult.Success, recorder.retrySave())
            assertNull(db.collectionDao().drivingDraft())
        } finally {
            job.cancelAndJoin()
        }
    }

    @Test fun interruptedOptOutNeverRestoresDiscardedDraftWhenEnabledAgain() = runBlocking {
        db.collectionDao().putDrivingDraft(
            DrivingDraftEntity(generation = generation, payload = complete().encodeDraft(), finished = true),
        )
        context.getSharedPreferences("driving_recording", Context.MODE_PRIVATE).edit()
            .putBoolean("enabled", false).putBoolean("discard_checkpoint", true).commit()
        val recorder = recorder()
        assertEquals(MutationResult.Success, recorder.setEnabled(true))
        assertNull(db.collectionDao().drivingDraft())
        assertEquals(MutationResult.AlreadyApplied, recorder.retrySave())
        assertTrue(db.collectionDao().journeys().isEmpty())
    }

    @Test fun parkingImmediatelySavesTheSharedJourneyAndDepartureCreatesANewOne() = runBlocking {
        val recorder = recorder()
        recorder.setEnabled(true)
        val job = launch(Dispatchers.Default) { recorder.monitor() }
        try {
            delay(150)
            clock.nanos += 200_000_000
            vehicle.move(1f)
            val active = withTimeout(10_000) { recorder.state.first { it.phase == RecordingPhase.Recording } }
            assertEquals(journeys.state.value.session?.id, active.journeyId)
            clock.nanos += 200_000_000
            vehicle.park()
            withTimeout(10_000) { recorder.state.first { it.phase == RecordingPhase.Saved } }
            assertEquals(active.journeyId, db.collectionDao().journeys().single().id)
            assertTrue(journeys.returnHome())
            assertEquals(active.journeyId, journeys.state.value.lastFinished?.id)
            clock.nanos += 200_000_000
            vehicle.move(1f)
            val next = withTimeout(10_000) { recorder.state.first { it.phase == RecordingPhase.Recording } }
            assertTrue(next.journeyId != active.journeyId)
        } finally {
            job.cancelAndJoin()
        }
    }

    @Test fun zeroSpeedInDriveDoesNotFinishButExplicitParkingDoes() = runBlocking {
        val recorder = recorder()
        recorder.setEnabled(true)
        val job = launch(Dispatchers.Default) { recorder.monitor() }
        try {
            delay(150)
            clock.nanos += 200_000_000
            vehicle.move(1f)
            withTimeout(10_000) { recorder.state.first { it.phase == RecordingPhase.Recording } }
            clock.nanos += 200_000_000
            vehicle.move(0f)
            delay(50)
            assertEquals(RecordingPhase.Recording, recorder.state.value.phase)
            assertEquals(MutationResult.Restricted, recorder.finish())
            clock.nanos += 200_000_000
            vehicle.park()
            withTimeout(10_000) { recorder.state.first { it.phase == RecordingPhase.Saved } }
            assertEquals(AssessmentStatus.Insufficient.name, db.collectionDao().journeys().single().assessment)
        } finally {
            job.cancelAndJoin()
        }
    }

    @Test fun parkedVisitAwardsAPlaceCardAndUpdatesMapRegionsWithoutDuplicatingTheVisit() = runBlocking {
        val recorder = recorder()
        val fixes = MutableStateFlow<VisitLocation?>(null)
        val places = LocalPlaceRepository(store)
        val visits = LocalVisitRecorder(
            store,
            progress,
            places,
            journeys,
            recorder,
            object : VisitLocationSource {
                override val locations = fixes
            },
            clock,
        )
        recorder.setEnabled(true)
        val recordingJob = launch(Dispatchers.Default) { recorder.monitor() }
        val visitJob = launch(Dispatchers.Default) { visits.monitor() }
        try {
            delay(150)
            clock.nanos += 200_000_000
            vehicle.move(1f)
            val active = withTimeout(10_000) { recorder.state.first { it.phase == RecordingPhase.Recording } }
            clock.nanos += 200_000_000
            vehicle.park()
            withTimeout(10_000) { recorder.state.first { it.phase == RecordingPhase.Saved } }
            assertTrue(journeys.returnHome())
            for (second in 0..60 step 10) {
                clock.nanos += 10_000_000_000
                vehicle.park()
                fixes.value = VisitLocation(place.latitude, place.longitude, 10.0, clock.nanos)
                delay(150)
            }
            val visit = withTimeout(10_000) {
                LocalHistoryRepository(store).observeVisits().first { it.isNotEmpty() }.single()
            }
            assertEquals(active.journeyId, visit.journeyId)
            assertEquals(setOf("place-card"), places.observePlace(place.id).first()?.ownedCardIds)
            assertEquals(setOf("KR-26"), LocalBadgeRegionSource(store).collectedRegionIds.first())
            store.realTransaction {
                putPlaces(places().map { it.copy(active = false) })
                touch()
            }
            assertEquals(setOf("KR-26"), LocalBadgeRegionSource(store).collectedRegionIds.first())
            clock.nanos += 10_000_000_000
            vehicle.park()
            fixes.value = fixes.value!!.copy(elapsedNanos = clock.nanos)
            delay(200)
            assertEquals(1, db.collectionDao().visits().size)
            assertEquals(
                MutationResult.Success,
                progress.deleteHistory(DeleteHistoryRequest(HistoryKind.Visit, visit.id, generation)),
            )
            assertTrue(LocalBadgeRegionSource(store).collectedRegionIds.first().isEmpty())
        } finally {
            visitJob.cancelAndJoin()
            recordingJob.cancelAndJoin()
        }
    }

    @Test fun resettingDataDuringMeasurementDoesNotRestartTheSameSessionWithANewGeneration() = runBlocking {
        val recorder = recorder()
        recorder.setEnabled(true)
        val job = launch(Dispatchers.Default) { recorder.monitor() }
        try {
            delay(150)
            clock.nanos += 200_000_000
            vehicle.move(1f)
            withTimeout(10_000) { recorder.state.first { it.phase == RecordingPhase.Recording } }
            assertEquals(MutationResult.Success, progress.clearUserData())
            clock.nanos += 6_000_000_000
            vehicle.move(1f)
            withTimeout(10_000) { recorder.state.first { it.phase == RecordingPhase.Waiting } }
            clock.nanos += 200_000_000
            vehicle.move(1f)
            delay(100)
            assertEquals(RecordingPhase.Waiting, recorder.state.value.phase)
            clock.nanos += 200_000_000
            vehicle.park()
            delay(150)
            assertTrue(db.collectionDao().journeys().isEmpty())
        } finally {
            job.cancelAndJoin()
        }
    }

    @Test fun cachedLocationsBeforeParkingOrReparkingCannotCountTowardDwell() = runBlocking {
        val recorder = recorder()
        val fixes = MutableStateFlow<VisitLocation?>(null)
        val visits = LocalVisitRecorder(
            store,
            progress,
            LocalPlaceRepository(store),
            journeys,
            recorder,
            object : VisitLocationSource {
                override val locations = fixes
            },
            clock,
        )
        recorder.setEnabled(true)
        val recordingJob = launch(Dispatchers.Default) { recorder.monitor() }
        val visitJob = launch(Dispatchers.Default) { visits.monitor() }
        try {
            withTimeout(10_000) { fixes.subscriptionCount.first { it > 0 } }
            clock.nanos += 200_000_000
            vehicle.move(1f)
            withTimeout(10_000) { recorder.state.first { it.phase == RecordingPhase.Recording } }
            fixes.value = VisitLocation(place.latitude, place.longitude, 10.0, clock.nanos)
            delay(150)
            clock.nanos += 10_000_000_000
            val parkedAt = clock.nanos
            vehicle.park()
            withTimeout(10_000) { recorder.state.first { it.phase == RecordingPhase.Saved } }
            delay(150)
            for (second in 10..60 step 10) {
                clock.nanos = parkedAt + second * 1_000_000_000L
                vehicle.park()
                fixes.value = fixes.value!!.copy(elapsedNanos = clock.nanos)
                delay(150)
                assertTrue("Pre-parking fix must not start the stay", db.collectionDao().visits().isEmpty())
            }

            // Leaving P at zero speed retains the journey but must start a fresh stay on returning to P.
            clock.nanos = parkedAt + 65_000_000_000
            vehicle.move(0f)
            delay(150)
            clock.nanos = parkedAt + 70_000_000_000
            vehicle.park()
            delay(150)
            for (second in 80..130 step 10) {
                clock.nanos = parkedAt + second * 1_000_000_000L
                vehicle.park()
                fixes.value = fixes.value!!.copy(elapsedNanos = clock.nanos)
                delay(150)
                assertTrue("Reparking must not reuse a cached fix", db.collectionDao().visits().isEmpty())
            }
            clock.nanos = parkedAt + 140_000_000_000
            vehicle.park()
            fixes.value = fixes.value!!.copy(elapsedNanos = clock.nanos)
            val visit = withTimeout(10_000) {
                LocalHistoryRepository(store).observeVisits().first { it.isNotEmpty() }.single()
            }
            assertEquals(clock.now().minusSeconds(60), visit.arrivedAt)
            assertEquals(clock.now(), visit.departedAt)
        } finally {
            visitJob.cancelAndJoin()
            recordingJob.cancelAndJoin()
        }
    }

    @Test fun startingVisitObservationAfterAnOldTripNeverBackfillsIt() = runBlocking {
        val recorder = recorder()
        recorder.setEnabled(true)
        val recordingJob = launch(Dispatchers.Default) { recorder.monitor() }
        var visitJob: kotlinx.coroutines.Job? = null
        try {
            delay(150)
            clock.nanos += 200_000_000
            vehicle.move(1f)
            withTimeout(10_000) { recorder.state.first { it.phase == RecordingPhase.Recording } }
            clock.nanos += 200_000_000
            vehicle.park()
            withTimeout(10_000) { recorder.state.first { it.phase == RecordingPhase.Saved } }
            val fixes = MutableStateFlow<VisitLocation?>(null)
            val visits =
                LocalVisitRecorder(
                    store,
                    progress,
                    LocalPlaceRepository(store),
                    journeys,
                    recorder,
                    object : VisitLocationSource {
                        override val locations = fixes
                    },
                    clock,
                )
            visitJob = launch(Dispatchers.Default) { visits.monitor() }
            delay(150)
            for (second in 0..60 step 10) {
                clock.nanos += 10_000_000_000
                vehicle.park()
                fixes.value = VisitLocation(place.latitude, place.longitude, 10.0, clock.nanos)
                delay(100)
            }
            assertTrue(db.collectionDao().visits().isEmpty())
        } finally {
            visitJob?.cancelAndJoin()
            recordingJob.cancelAndJoin()
        }
    }

    private fun recorder(repository: ProgressRepository = progress) =
        LocalDrivingRecorder(context, store, repository, vehicle, clock, journeys)
    private fun complete() = JourneyRecord(
        "journey", Instant.parse("2026-11-01T00:00:00Z"), Instant.parse("2026-11-01T00:10:00Z"),
        "Asia/Seoul", 5_000, AssessmentStatus.Complete, 88, 0,
        driving = DrivingAssessment(
            elapsedMillis = 600_000, observedMillis = 599_000, movingMillis = 600_000 - 1000,
            sampleCount = 3_000, rejectedSamples = 0, harshAccelerations = 2, harshBrakings = 1,
            harshStarts = 0, longestGapMillis = 200,
        ),
    )

    private class TestClock : DrivingClock {
        @Volatile var nanos = 1_000_000_000L
        override fun elapsedNanos() = nanos
        override fun now(): Instant = Instant.parse("2026-11-06T00:00:00Z").plusNanos(nanos)
    }
    private class TestVehicle(private val clock: TestClock) : VehicleRepository {
        override val state = MutableStateFlow(value(0f, VehicleGear.Park))
        override fun retry() = Unit
        fun move(speed: Float) {
            state.value = value(speed, VehicleGear.Drive)
        }
        fun park() {
            state.value = value(0f, VehicleGear.Park)
        }
        private fun value(speed: Float, gear: VehicleGear) = VehicleState(
            motion = VehicleMotionState(
                if (speed >
                    .1
                ) {
                    VehicleMotion.Moving
                } else {
                    VehicleMotion.Stopped
                },
                speed,
                MotionAvailability.Available,
                timestampNanos = clock.nanos,
            ),
            gear = gear,
            uxPolicy = CarUxPolicy(
                connected = true,
                known = true,
                requiresDistractionOptimization = false,
                activeRestrictions = 0,
            ),
        )
    }
}
