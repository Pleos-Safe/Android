package io.issac.pleosandroid.core.data.driving

import android.content.Context
import dagger.hilt.android.qualifiers.ApplicationContext
import io.issac.pleosandroid.core.data.collection.local.CollectionStore
import io.issac.pleosandroid.core.data.collection.repository.storageResult
import io.issac.pleosandroid.core.database.entity.DrivingDraftEntity
import io.issac.pleosandroid.core.domain.collection.model.AssessmentStatus
import io.issac.pleosandroid.core.domain.collection.model.JourneyRecord
import io.issac.pleosandroid.core.domain.collection.model.MutationResult
import io.issac.pleosandroid.core.domain.collection.model.ProgressEvent
import io.issac.pleosandroid.core.domain.collection.model.ProgressRejection
import io.issac.pleosandroid.core.domain.collection.model.RecordProgressResult
import io.issac.pleosandroid.core.domain.collection.repository.ProgressRepository
import io.issac.pleosandroid.core.domain.driving.model.SpeedSample
import io.issac.pleosandroid.core.domain.driving.policy.DrivingAnalyzer
import io.issac.pleosandroid.core.domain.driving.repository.DrivingObservation
import io.issac.pleosandroid.core.domain.driving.repository.DrivingObservationKind
import io.issac.pleosandroid.core.domain.driving.repository.DrivingRecorder
import io.issac.pleosandroid.core.domain.driving.repository.JourneyRepository
import io.issac.pleosandroid.core.domain.driving.repository.JourneyState
import io.issac.pleosandroid.core.domain.driving.repository.RecordingPhase
import io.issac.pleosandroid.core.domain.driving.repository.RecordingState
import io.issac.pleosandroid.core.domain.model.MotionAvailability
import io.issac.pleosandroid.core.domain.model.VehicleGear
import io.issac.pleosandroid.core.domain.model.VehicleMotion
import io.issac.pleosandroid.core.domain.model.VehicleState
import io.issac.pleosandroid.core.domain.repository.VehicleRepository
import java.time.Instant
import java.time.ZoneId
import javax.inject.Inject
import javax.inject.Singleton
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asSharedFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.collectLatest
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.withContext

@Singleton
internal class DefaultDrivingRecorder @Inject constructor(
    @param:ApplicationContext context: Context,
    private val store: CollectionStore,
    private val progress: ProgressRepository,
    private val vehicle: VehicleRepository,
    private val clock: DrivingClock,
    private val journeys: JourneyRepository,
) : DrivingRecorder {
    private val preferences = context.getSharedPreferences("driving_recording", Context.MODE_PRIVATE)
    private val mutableEnabled = MutableStateFlow(preferences.getBoolean("enabled", false))
    override val enabled = mutableEnabled.asStateFlow()
    private val mutableState = MutableStateFlow(RecordingState())
    override val state = mutableState.asStateFlow()
    private val mutableObservations = MutableSharedFlow<DrivingObservation>(extraBufferCapacity = 4)
    override val observations = mutableObservations.asSharedFlow()
    private val owner = Mutex()
    private val mutex = Mutex()
    private var active: ActiveJourney? = null
    private var pending: DrivingDraftEntity? = null
    private var sawPark = false
    private var lastCheckpoint = 0L
    private var lastStartedJourneyId: String? = null

    override fun monitoringUnavailable() {
        mutableState.value = RecordingState(RecordingPhase.Unavailable, active?.id)
    }

    override suspend fun setEnabled(enabled: Boolean): MutationResult = mutex.withLock {
        if (enabled && !vehicle.state.value.allowsTabNavigation) return@withLock MutationResult.Restricted
        if (enabled && store.demoMode.value) return@withLock MutationResult.Restricted
        withContext(Dispatchers.IO) {
            if (enabled && preferences.getBoolean("discard_checkpoint", false)) {
                val discarded = storageResult(false) {
                    store.realTransaction { clearDrivingDraft() }
                    true
                }
                if (!discarded) return@withContext MutationResult.StorageFailure
            }
            if (!preferences.edit().putBoolean("enabled", enabled)
                    .putBoolean("discard_checkpoint", !enabled).commit()
            ) {
                return@withContext MutationResult.StorageFailure
            }
            mutableEnabled.value = enabled
            if (!enabled) {
                active = null
                pending = null
                sawPark = false
                mutableState.value = RecordingState(RecordingPhase.Disabled)
                // Turning storage off discards an unfinished trip, never backfills it later.
                return@withContext storageResult(MutationResult.StorageFailure) {
                    store.realTransaction { clearDrivingDraft() }
                    MutationResult.Success
                }
            }
            MutationResult.Success
        }
    }

    override suspend fun monitor(): Unit = owner.withLock {
        try {
            combine(enabled, store.demoMode) { enabled, demo -> enabled to demo }.collectLatest { (enabled, demo) ->
                mutex.withLock {
                    active = null
                    pending = null
                    sawPark = false
                    mutableState.value = RecordingState(
                        if (demo) {
                            RecordingPhase.Demo
                        } else if (enabled) {
                            RecordingPhase.Waiting
                        } else {
                            RecordingPhase.Disabled
                        },
                    )
                }
                if (!enabled || demo) return@collectLatest
                mutex.withLock { safely { recover() } }
                coroutineScope {
                    launch {
                        while (isActive) {
                            delay(1_000)
                            mutex.withLock { safely { checkpointIfDue() } }
                        }
                    }
                    journeys.state.collect { value -> mutex.withLock { safely { onJourney(value) } } }
                }
            }
        } finally {
            // Recovery uses the last encrypted checkpoint. A process/service restart cannot certify continuity.
            mutex.withLock { active = null }
        }
    }

    private suspend fun safely(block: suspend () -> Unit) {
        try {
            block()
        } catch (cancelled: CancellationException) {
            throw cancelled
        } catch (_: java.io.IOException) {
            mutableState.value = RecordingState(RecordingPhase.Error, active?.id)
        } catch (_: android.database.SQLException) {
            mutableState.value = RecordingState(RecordingPhase.Error, active?.id)
        } catch (_: java.security.GeneralSecurityException) {
            mutableState.value = RecordingState(RecordingPhase.Error, active?.id)
        }
    }

    private suspend fun recover(): MutationResult {
        val draft = store.realTransaction {
            drivingDraft()?.takeIf { it.generation == userState()?.generation }.also {
                if (it == null) clearDrivingDraft()
            }
        } ?: return MutationResult.AlreadyApplied
        // In-progress snapshots are already marked interrupted and have no score.
        pending = draft.copy(finished = true)
        return savePending()
    }

    private suspend fun onJourney(journey: JourneyState) {
        val value = journey.vehicle
        val now = clock.elapsedNanos()
        val sample = value.sample(now)
        val finished = journey.lastFinished?.takeIf { it.id == active?.id }
        if (finished != null) {
            sample?.let { active?.analyzer?.add(it) }
            finishLocked(checkNotNull(finished.endedNanos))
        }
        if (pending != null) return
        if (sample == null) {
            active?.analyzer?.breakContinuity()
            return
        }
        if (value.gear == VehicleGear.Park && value.motion.motion == VehicleMotion.Stopped) sawPark = true
        val session = journey.session?.takeUnless { it.finished }
        if (session == null) return
        // If an entire boundary was missed, preserve the partial facts without certifying a complete trip.
        if (active != null && active?.id != session.id) {
            finishLocked(now, interrupted = true)
            if (pending != null) return
        }
        if (active == null && session.id == lastStartedJourneyId) return
        if (active == null && value.motion.motion == VehicleMotion.Moving &&
            (value.gear == VehicleGear.Drive || value.gear == VehicleGear.Reverse)
        ) {
            val generation = store.realTransaction { checkNotNull(userState()).generation }
            active = ActiveJourney(
                session.id,
                generation,
                session.startedAt,
                session.startedNanos,
                ZoneId.systemDefault().id,
                DrivingAnalyzer(
                    session.startedNanos,
                    startedWhileMoving = !sawPark || session.startedWhileMoving ||
                        sample.timestampNanos - session.startedNanos > 2_000_000_000L,
                ),
            )
            lastStartedJourneyId = session.id
            lastCheckpoint = 0
            mutableState.value = RecordingState(RecordingPhase.Recording, active?.id)
        }
        active?.let { current ->
            val acceleration = current.analyzer.accelerationCount
            val braking = current.analyzer.brakingCount
            current.analyzer.add(sample)
            val kind = when {
                current.analyzer.accelerationCount > acceleration -> DrivingObservationKind.Acceleration
                current.analyzer.brakingCount > braking -> DrivingObservationKind.Braking
                else -> null
            }
            if (kind !=
                null
            ) {
                mutableObservations.tryEmit(DrivingObservation(current.id, current.startedAt.toEpochMilli(), kind))
            }
        }
        checkpointIfDue()
    }

    private suspend fun checkpointIfDue() {
        val current = active ?: return
        val now = clock.elapsedNanos()
        if (now - lastCheckpoint >= 5_000_000_000L) {
            if (checkpoint(current, now, finished = false)) lastCheckpoint = now
        }
    }

    private suspend fun checkpoint(
        current: ActiveJourney,
        now: Long,
        finished: Boolean,
        interrupted: Boolean = !finished,
    ): Boolean {
        val draft = DrivingDraftEntity(
            generation = current.generation,
            payload = current.record(now, interrupted = interrupted).encodeDraft(),
            finished = finished,
        )
        return store.realTransaction {
            if (!enabled.value || current.generation != userState()?.generation) {
                active = null
                sawPark = false
                clearDrivingDraft()
                mutableState.value = RecordingState(RecordingPhase.Waiting)
                false
            } else {
                putDrivingDraft(draft)
                if (finished) pending = draft
                true
            }
        }
    }

    override suspend fun finish(): MutationResult = mutex.withLock {
        if (!vehicle.state.value.allowsTabNavigation || vehicle.state.value.sample(clock.elapsedNanos()) == null) {
            return@withLock MutationResult.Restricted
        }
        storageResult(MutationResult.StorageFailure) { finishLocked(clock.elapsedNanos()) }.also {
            if (it ==
                MutationResult.StorageFailure
            ) {
                mutableState.value =
                    RecordingState(RecordingPhase.Error, active?.id ?: pending?.let { decodeDraft(it.payload).id })
            }
        }
    }

    private suspend fun finishLocked(now: Long, interrupted: Boolean = false): MutationResult {
        val current = active ?: return if (pending != null) savePending() else MutationResult.NotFound
        if (!checkpoint(current, now, finished = true, interrupted = interrupted)) return MutationResult.Stale
        active = null
        return savePending()
    }

    override suspend fun retrySave(): MutationResult = mutex.withLock {
        if (!vehicle.state.value.allowsTabNavigation) return@withLock MutationResult.Restricted
        storageResult(MutationResult.StorageFailure) {
            if (active != null) return@storageResult finishLocked(clock.elapsedNanos())
            if (pending == null) recover() else savePending()
        }
    }

    private suspend fun savePending(): MutationResult {
        val draft = pending ?: return MutationResult.NotFound
        if (!enabled.value || store.demoMode.value) return MutationResult.Restricted
        val record = decodeDraft(draft.payload)
        mutableState.value = RecordingState(RecordingPhase.Saving, record.id)
        // This stable ID makes a crash between commit and checkpoint deletion harmless.
        return when (
            val result = progress.recordProgress(
                ProgressEvent.JourneyCompleted("driving:${record.id}", draft.generation, record, storageConsent = true),
            )
        ) {
            is RecordProgressResult.Applied, is RecordProgressResult.AlreadyProcessed -> {
                store.realTransaction { clearDrivingDraft() }
                pending = null
                mutableState.value = RecordingState(RecordingPhase.Saved, record.id)
                MutationResult.Success
            }

            is RecordProgressResult.Rejected -> {
                val deleted = result.reason == ProgressRejection.DeletedSource ||
                    (
                        result.reason == ProgressRejection.StaleGeneration &&
                            store.realTransaction { userState()?.generation } != draft.generation
                        )
                if (!deleted) {
                    mutableState.value = RecordingState(RecordingPhase.Error, record.id)
                    return MutationResult.StorageFailure
                }
                // A reset/deletion cannot resurrect or award an old recording.
                store.realTransaction { clearDrivingDraft() }
                pending = null
                sawPark = false
                mutableState.value = RecordingState(RecordingPhase.Waiting)
                MutationResult.Stale
            }

            RecordProgressResult.StorageFailure -> {
                mutableState.value = RecordingState(RecordingPhase.Error, record.id)
                MutationResult.StorageFailure
            }
        }
    }
}

private fun VehicleState.sample(now: Long): SpeedSample? {
    if (motion.availability != MotionAvailability.Available) return null
    val timestamp = motion.timestampNanos ?: return null
    val speed = motion.speedMetersPerSecond?.toDouble() ?: return null
    if (timestamp <= 0 || timestamp > now || now - timestamp > 2_000_000_000L ||
        gear == VehicleGear.Unknown
    ) {
        return null
    }
    return SpeedSample(timestamp, speed, gear == VehicleGear.Drive)
}

private data class ActiveJourney(
    val id: String,
    val generation: String,
    val startedAt: Instant,
    val startedNanos: Long,
    val zone: String,
    val analyzer: DrivingAnalyzer,
) {
    fun record(now: Long, interrupted: Boolean): JourneyRecord {
        val result = analyzer.result(now, interrupted)
        return JourneyRecord(
            id, startedAt, startedAt.plusMillis(result.assessment.elapsedMillis), zone,
            result.distanceMeters,
            if (result.score ==
                null
            ) {
                AssessmentStatus.Insufficient
            } else {
                AssessmentStatus.Complete
            },
            result.score, result.assessment.harshStarts, result.assessment.policyVersion, result.assessment,
        )
    }
}
