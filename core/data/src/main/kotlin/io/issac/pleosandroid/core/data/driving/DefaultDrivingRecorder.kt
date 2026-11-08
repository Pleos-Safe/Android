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
import io.issac.pleosandroid.core.domain.driving.model.DrivingResult
import io.issac.pleosandroid.core.domain.driving.model.InsufficientReason
import io.issac.pleosandroid.core.domain.driving.model.SpeedSample
import io.issac.pleosandroid.core.domain.driving.policy.DrivingAnalyzer
import io.issac.pleosandroid.core.domain.driving.repository.DrivingObservation
import io.issac.pleosandroid.core.domain.driving.repository.DrivingObservationKind
import io.issac.pleosandroid.core.domain.driving.repository.DrivingRecorder
import io.issac.pleosandroid.core.domain.driving.repository.JourneyRepository
import io.issac.pleosandroid.core.domain.driving.repository.JourneySession
import io.issac.pleosandroid.core.domain.driving.repository.JourneyState
import io.issac.pleosandroid.core.domain.driving.repository.RecordingPhase
import io.issac.pleosandroid.core.domain.driving.repository.RecordingState
import io.issac.pleosandroid.core.domain.model.MotionAvailability
import io.issac.pleosandroid.core.domain.model.VehicleGear
import io.issac.pleosandroid.core.domain.model.VehicleMotion
import io.issac.pleosandroid.core.domain.model.VehicleState
import io.issac.pleosandroid.core.domain.repository.VehicleRepository
import java.time.Duration
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
    private var lastCheckpoint: Long? = null
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
                journeys.discardRecovery()
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
                    if (!enabled || demo) journeys.discardRecovery()
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
                            mutex.withLock {
                                safely {
                                    val now = clock.elapsedNanos()
                                    checkpointIfDue(now)
                                    if (mutableState.value.phase != RecordingPhase.Error) publishRecording(now)
                                }
                            }
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
            showStorageError()
        } catch (_: android.database.SQLException) {
            showStorageError()
        } catch (_: java.security.GeneralSecurityException) {
            showStorageError()
        }
    }

    private suspend fun recover(): MutationResult {
        val draft = store.realTransaction {
            drivingDraft()?.takeIf { it.generation == userState()?.generation }.also {
                if (it == null) clearDrivingDraft()
            }
        } ?: return MutationResult.AlreadyApplied
        if (draft.finished) {
            pending = draft
            return savePending()
        }
        val record = decodeDraft(draft.payload)
        val assessment = record.driving ?: DrivingAnalyzer(0).result(
            Duration.between(record.startedAt, record.endedAt).toMillis().coerceAtLeast(1) * 1_000_000,
            interrupted = true,
        ).assessment
        val now = clock.elapsedNanos()
        val session = JourneySession(
            id = record.id,
            startedAt = record.startedAt,
            startedNanos = now,
            startedWhileMoving = InsufficientReason.StartedWhileMoving in assessment.reasons,
            pausedAtNanos = now,
            recoveredActiveNanos = assessment.elapsedMillis * 1_000_000,
            recoveryRequired = true,
        )
        active = ActiveJourney(
            session,
            draft.generation,
            record.zoneId,
            DrivingAnalyzer(
                startedNanos = 0,
                startedWhileMoving = session.startedWhileMoving,
                restored = DrivingResult(record.distanceMeters, record.score, assessment),
            ),
        )
        lastStartedJourneyId = session.id
        lastCheckpoint = now
        journeys.restore(session)
        publishRecording(now)
        return MutationResult.Success
    }

    private suspend fun onJourney(journey: JourneyState) {
        val value = journey.vehicle
        val now = clock.elapsedNanos()
        val sample = value.sample(now)
        val finished = journey.lastFinished?.takeIf { it.id == active?.id }
        if (finished != null && active?.session?.finished != true) {
            active?.session = finished
            finishLocked(now)
        }
        if (pending != null) return
        if (sample != null && value.gear == VehicleGear.Park && value.motion.motion == VehicleMotion.Stopped) {
            sawPark = true
        }
        val session = journey.session?.takeUnless { it.finished }
        if (session == null) return
        // If an entire boundary was missed, preserve the partial facts without certifying a complete trip.
        val prior = active
        if (prior != null && prior.id != session.id) {
            finishLocked(now, interrupted = !prior.session.finished)
            if (pending != null) return
        }
        if (active == null && session.id == lastStartedJourneyId) return
        if (active == null && sample != null && value.motion.motion == VehicleMotion.Moving &&
            (value.gear == VehicleGear.Drive || value.gear == VehicleGear.Reverse)
        ) {
            val generation = store.realTransaction { checkNotNull(userState()).generation }
            active = ActiveJourney(
                session,
                generation,
                ZoneId.systemDefault().id,
                DrivingAnalyzer(
                    0,
                    startedWhileMoving = !sawPark || session.startedWhileMoving ||
                        sample.timestampNanos - session.startedNanos > 2_000_000_000L,
                ),
            )
            lastStartedJourneyId = session.id
            lastCheckpoint = null
        }
        val current = active ?: return
        val previous = current.session
        current.session = session
        if (sample == null || previous.paused != session.paused || previous.pausedNanos != session.pausedNanos) {
            current.analyzer.breakContinuity()
        }
        if (!session.paused && sample != null) {
            val kind = current.add(sample)
            if (kind != null) {
                mutableObservations.tryEmit(DrivingObservation(current.id, session.startedAt.toEpochMilli(), kind))
            }
        }
        checkpointIfDue(now, justParked = !previous.paused && session.paused)
        publishRecording(now)
    }

    private fun publishRecording(now: Long) {
        val current = active?.takeUnless { it.session.finished } ?: return
        mutableState.value = RecordingState(
            phase = RecordingPhase.Recording,
            journeyId = current.id,
            distanceMeters = current.analyzer.distanceMeters,
            activeMillis = current.session.activeDurationMillis(now),
            movingMillis = current.analyzer.movingMillis,
            recoveryRequired = current.session.recoveryRequired,
            paused = current.session.paused,
        )
    }

    private suspend fun checkpointIfDue(now: Long, justParked: Boolean = false) {
        val current = active?.takeUnless { it.session.finished } ?: return
        val previous = lastCheckpoint
        val periodicCheckpoint = !current.session.paused && previous != null && now - previous >= 5_000_000_000L
        if (justParked || previous == null || periodicCheckpoint) {
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
            payload = current.record(now, clock.now(), interrupted).encodeDraft(),
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
        if (!isSafelyParked()) return@withLock MutationResult.Restricted
        if (!journeys.finish()) return@withLock MutationResult.NotFound
        val finished = journeys.state.value.lastFinished
        val current = active?.takeIf { it.id == finished?.id }
        if (current == null || !enabled.value || store.demoMode.value) return@withLock MutationResult.Success
        current.session = checkNotNull(finished)
        storageResult(MutationResult.StorageFailure) { finishLocked(clock.elapsedNanos()) }.also { result ->
            if (result == MutationResult.StorageFailure) showStorageError()
        }
    }

    override suspend fun continueRecoveredJourney(): MutationResult = mutex.withLock {
        if (!isSafelyParked() || !enabled.value || store.demoMode.value) {
            return@withLock MutationResult.Restricted
        }
        val current = active?.takeIf { it.session.recoveryRequired } ?: return@withLock MutationResult.NotFound
        storageResult(MutationResult.StorageFailure) {
            if (store.realTransaction { userState()?.generation } != current.generation) {
                journeys.discardRecovery()
                active = null
                store.realTransaction { clearDrivingDraft() }
                mutableState.value = RecordingState(RecordingPhase.Waiting)
                return@storageResult MutationResult.Stale
            }
            if (!journeys.continueRecovered()) return@storageResult MutationResult.Restricted
            current.session = checkNotNull(journeys.state.value.session)
            publishRecording(clock.elapsedNanos())
            MutationResult.Success
        }.also { if (it == MutationResult.StorageFailure) showStorageError() }
    }

    private fun showStorageError() {
        mutableState.value = mutableState.value.copy(
            phase = RecordingPhase.Error,
            journeyId = active?.id ?: pending?.let { decodeDraft(it.payload).id },
        )
    }

    private fun isSafelyParked(): Boolean {
        val value = vehicle.state.value
        return value.allowsTabNavigation && value.sample(clock.elapsedNanos()) != null
    }

    private suspend fun finishLocked(now: Long, interrupted: Boolean = false): MutationResult {
        val current = active ?: return if (pending != null) savePending() else MutationResult.NotFound
        if (!checkpoint(current, now, finished = true, interrupted = interrupted)) return MutationResult.Stale
        active = null
        return savePending()
    }

    override suspend fun retrySave(): MutationResult = mutex.withLock {
        if (!vehicle.state.value.allowsTabNavigation || !enabled.value || store.demoMode.value) {
            return@withLock MutationResult.Restricted
        }
        storageResult(MutationResult.StorageFailure) {
            if (pending != null) return@storageResult savePending()
            val current = active ?: return@storageResult recover()
            val now = clock.elapsedNanos()
            if (current.session.finished) return@storageResult finishLocked(now)
            if (!checkpoint(current, now, finished = false)) return@storageResult MutationResult.Stale
            lastCheckpoint = now
            publishRecording(now)
            MutationResult.Success
        }.also { if (it == MutationResult.StorageFailure) showStorageError() }
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
    var session: JourneySession,
    val generation: String,
    val zone: String,
    val analyzer: DrivingAnalyzer,
) {
    val id: String get() = session.id

    fun add(sample: SpeedSample): DrivingObservationKind? {
        if (sample.timestampNanos < session.startedNanos) return null
        val acceleration = analyzer.accelerationCount
        val braking = analyzer.brakingCount
        analyzer.add(sample.copy(timestampNanos = session.activeDurationNanos(sample.timestampNanos)))
        return when {
            analyzer.accelerationCount > acceleration -> DrivingObservationKind.Acceleration
            analyzer.brakingCount > braking -> DrivingObservationKind.Braking
            else -> null
        }
    }

    fun record(nowNanos: Long, now: Instant, interrupted: Boolean): JourneyRecord {
        val result = analyzer.result(session.activeDurationNanos(nowNanos), interrupted)
        val startedAt = Instant.ofEpochMilli(session.startedAt.toEpochMilli())
        val endedAt = maxOf(
            Instant.ofEpochMilli((session.endedAt ?: now).toEpochMilli()),
            startedAt.plusMillis(result.assessment.elapsedMillis),
        )
        val assessment = result.assessment.copy(
            pausedMillis = Duration.between(startedAt, endedAt).toMillis() - result.assessment.elapsedMillis,
        )
        return JourneyRecord(
            id, startedAt, endedAt, zone,
            result.distanceMeters,
            if (result.score == null) AssessmentStatus.Insufficient else AssessmentStatus.Complete,
            result.score, assessment.harshStarts, assessment.policyVersion, assessment,
        )
    }
}
