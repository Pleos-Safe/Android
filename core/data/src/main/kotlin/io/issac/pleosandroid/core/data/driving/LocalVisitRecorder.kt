package io.issac.pleosandroid.core.data.driving

import io.issac.pleosandroid.core.data.collection.local.CollectionStore
import io.issac.pleosandroid.core.data.collection.repository.storageResult
import io.issac.pleosandroid.core.domain.collection.model.ProgressEvent
import io.issac.pleosandroid.core.domain.collection.model.RecordProgressResult
import io.issac.pleosandroid.core.domain.collection.policy.VisitTracker
import io.issac.pleosandroid.core.domain.collection.repository.PlaceRepository
import io.issac.pleosandroid.core.domain.collection.repository.ProgressRepository
import io.issac.pleosandroid.core.domain.collection.repository.VisitLocationSource
import io.issac.pleosandroid.core.domain.collection.repository.VisitRecorder
import io.issac.pleosandroid.core.domain.collection.repository.VisitRecordingStatus
import io.issac.pleosandroid.core.domain.driving.repository.DrivingRecorder
import io.issac.pleosandroid.core.domain.driving.repository.JourneyRepository
import java.time.ZoneId
import javax.inject.Inject
import javax.inject.Singleton
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.collectLatest
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.retryWhen
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock

@Singleton
internal class LocalVisitRecorder @Inject constructor(
    private val store: CollectionStore,
    private val progress: ProgressRepository,
    private val places: PlaceRepository,
    private val journeys: JourneyRepository,
    private val recorder: DrivingRecorder,
    private val location: VisitLocationSource,
    private val clock: DrivingClock,
) : VisitRecorder {
    private val owner = Mutex()
    private val mutableStatus = MutableStateFlow(VisitRecordingStatus.Waiting)
    override val status = mutableStatus.asStateFlow()

    override suspend fun monitor(): Unit = owner.withLock {
        combine(recorder.enabled, store.demoMode) { enabled, demo -> enabled && !demo }.collectLatest { enabled ->
            mutableStatus.value = VisitRecordingStatus.Waiting
            if (!enabled) return@collectLatest
            val previousFinished = journeys.state.value.lastFinished?.id
            var journeyId: String? = null
            var generation: String? = null
            var tracker = VisitTracker()
            var parkedSinceNanos: Long? = null
            val pending = mutableMapOf<String, ProgressEvent.VisitConfirmed>()
            combine(
                journeys.state,
                location.locations,
                places.observePlaces().retryWhen { _, _ ->
                    mutableStatus.value = VisitRecordingStatus.StorageError
                    delay(2_000)
                    true
                },
            ) { journey, fix, catalog ->
                Triple(journey, fix, catalog)
            }.collect { (journey, fix, catalog) ->
                val session = (journey.session ?: journey.lastFinished)?.takeUnless { it.id == previousFinished }
                val stored = storageResult(false) {
                    if (session?.id != journeyId) {
                        // Capture once per session. Reset/deletion never retokens an old visit.
                        generation = store.realTransaction { checkNotNull(userState()).generation }
                        journeyId = session?.id
                        tracker = VisitTracker()
                        parkedSinceNanos = null
                    }
                    val parked = journey.vehicle.allowsTabNavigation && session?.finished == true
                    parkedSinceNanos = if (parked) parkedSinceNanos ?: clock.elapsedNanos() else null
                    // combine can replay a fix taken before P, or before parking again without moving.
                    val parkedFix = fix?.takeIf { it.elapsedNanos >= (parkedSinceNanos ?: Long.MAX_VALUE) }
                    val saved = if (parked) {
                        store.realTransaction { journeys().any { it.id == session?.id } }
                    } else {
                        false
                    }
                    if (!saved || session == null) {
                        tracker.breakContinuity()
                        mutableStatus.value = VisitRecordingStatus.Waiting
                    } else {
                        val visits = tracker.observe(
                            session.id,
                            catalog.map { it.place },
                            parkedFix,
                            clock.elapsedNanos(),
                            clock.now(),
                            ZoneId.systemDefault().id,
                        )
                        visits.forEach { visit ->
                            pending[visit.id] = ProgressEvent.VisitConfirmed(
                                visit.id,
                                checkNotNull(generation),
                                visit,
                                true,
                            )
                        }
                        if (parkedFix != null && mutableStatus.value != VisitRecordingStatus.Saved) {
                            mutableStatus.value = VisitRecordingStatus.Observing
                        }
                    }
                    for ((id, event) in pending.toMap()) {
                        when (progress.recordProgress(event)) {
                            is RecordProgressResult.Applied, is RecordProgressResult.AlreadyProcessed -> {
                                pending.remove(id)
                                mutableStatus.value = VisitRecordingStatus.Saved
                            }

                            is RecordProgressResult.Rejected -> pending.remove(id)

                            RecordProgressResult.StorageFailure -> {
                                mutableStatus.value = VisitRecordingStatus.StorageError
                            }
                        }
                    }
                    true
                }
                if (!stored) mutableStatus.value = VisitRecordingStatus.StorageError
            }
        }
    }
}
