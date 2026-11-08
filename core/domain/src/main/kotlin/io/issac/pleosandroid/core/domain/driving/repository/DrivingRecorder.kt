package io.issac.pleosandroid.core.domain.driving.repository

import io.issac.pleosandroid.core.domain.collection.model.MutationResult
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.emptyFlow

enum class RecordingPhase { Disabled, Waiting, Recording, Saving, Saved, Error, Unavailable, Demo }

data class RecordingState(
    val phase: RecordingPhase = RecordingPhase.Disabled,
    val journeyId: String? = null,
    val distanceMeters: Long = 0,
    val activeMillis: Long = 0,
    val movingMillis: Long = 0,
    val recoveryRequired: Boolean = false,
    val paused: Boolean = false,
)

enum class DrivingObservationKind { Acceleration, Braking }
data class DrivingObservation(val journeyId: String, val startedAtMillis: Long, val kind: DrivingObservationKind)

interface DrivingRecorder {
    val observations: Flow<DrivingObservation> get() = emptyFlow()
    val enabled: StateFlow<Boolean>
    val state: StateFlow<RecordingState>
    suspend fun setEnabled(enabled: Boolean): MutationResult

    /** A single foreground service owns this suspending monitoring loop. */
    suspend fun monitor()
    suspend fun finish(): MutationResult
    suspend fun continueRecoveredJourney(): MutationResult = MutationResult.NotFound
    suspend fun retrySave(): MutationResult
    fun monitoringUnavailable()
}
