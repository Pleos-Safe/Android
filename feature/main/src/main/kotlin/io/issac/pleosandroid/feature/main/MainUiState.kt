package io.issac.pleosandroid.feature.main

import io.issac.pleosandroid.core.domain.driving.repository.RecordingPhase
import io.issac.pleosandroid.core.domain.model.VehicleGear
import io.issac.pleosandroid.core.domain.model.VehicleMotion
import io.issac.pleosandroid.core.domain.model.VehicleState

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
)

sealed interface MainUiState {
    data object Loading : MainUiState

    data class Ready(
        val phase: MainPhase?,
        val vehicle: VehicleState,
        val summary: JourneySummary? = null,
        val arrivedBeforeParking: Boolean = false,
        val journeyStartedAtMillis: Long? = null,
    ) : MainUiState {
        val canUseTabs: Boolean get() = vehicle.allowsTabNavigation

        val canOpenBadges: Boolean get() = canUseTabs

        val canReturnHome: Boolean
            get() = canUseTabs && phase == MainPhase.Finished
    }
}

// A parked driver can grant the speed permission before the first valid speed reading arrives.
internal val VehicleState.allowsVehicleRecovery: Boolean
    get() = !uxPolicy.restrictionsRequired && gear == VehicleGear.Park && motion.motion != VehicleMotion.Moving
