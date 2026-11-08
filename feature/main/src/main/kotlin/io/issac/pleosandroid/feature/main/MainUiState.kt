package io.issac.pleosandroid.feature.main

import io.issac.pleosandroid.core.domain.driving.repository.RecordingPhase
import io.issac.pleosandroid.core.domain.model.VehicleGear
import io.issac.pleosandroid.core.domain.model.VehicleMotion
import io.issac.pleosandroid.core.domain.model.VehicleState
import java.time.Instant

enum class MainPhase {
    BeforeDriving,
    DrivingStopped,
    DrivingMoving,
    Finished,
}

data class HomePlace(val name: String, val district: String, val category: String, val imageKey: String)

data class JourneySummary(
    val durationMillis: Long,
    val reportId: String? = null,
    val recordingPhase: RecordingPhase =
        RecordingPhase.Disabled,
    val distanceMeters: Long? = null,
    val safetyScore: Int? = null,
    val earnedPoints: Long? = null,
    val recognizedDistanceMeters: Long? = null,
    val settlementReason: String? = null,
    val endedAt: Instant? = null,
    val membershipBonusHalfPoints: Long = 0,
)

data class JourneyProgressDisplay(val distanceMeters: Long, val movingMillis: Long)

sealed interface MainUiState {
    data object Loading : MainUiState

    data class Ready(
        val phase: MainPhase?,
        val vehicle: VehicleState,
        val summary: JourneySummary? = null,
        val arrivedBeforeParking: Boolean = false,
        val journeyStartedAtMillis: Long? = null,
        val progress: JourneyProgressDisplay? = null,
        val journeyPaused: Boolean = false,
        val pausedAtNanos: Long? = null,
        val recoveryRequired: Boolean = false,
        val journeyActionPending: Boolean = false,
        val journeyActionError: Boolean = false,
        val pointBalance: Long? = null,
        val demoWallet: Boolean = false,
    ) : MainUiState {
        val canUseTabs: Boolean get() = vehicle.allowsTabNavigation

        val canManagePausedJourney: Boolean
            get() = canUseTabs && (journeyPaused || recoveryRequired)

        val canOpenBadges: Boolean get() = canUseTabs

        val canReturnHome: Boolean
            get() = canUseTabs && phase == MainPhase.Finished
    }
}

// A parked driver can grant the speed permission before the first valid speed reading arrives.
internal val VehicleState.allowsVehicleRecovery: Boolean
    get() = !uxPolicy.restrictionsRequired && gear == VehicleGear.Park && motion.motion != VehicleMotion.Moving
