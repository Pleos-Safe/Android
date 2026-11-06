package io.issac.pleosandroid.feature.main

import io.issac.pleosandroid.core.domain.model.MotionAvailability
import io.issac.pleosandroid.core.domain.model.VehicleGear
import io.issac.pleosandroid.core.domain.model.VehicleMotion
import io.issac.pleosandroid.core.domain.model.VehicleState

enum class MainPhase {
    BeforeDriving,
    DrivingStopped,
    DrivingMoving,
    Finished,
}

data class JourneySummary(val durationMillis: Long)

sealed interface MainUiState {
    data object Loading : MainUiState

    data class Ready(
        val phase: MainPhase?,
        val vehicle: VehicleState,
        val summary: JourneySummary? = null,
        val arrivedBeforeParking: Boolean = false,
    ) : MainUiState {
        val canUseTabs: Boolean get() = vehicle.allowsTabNavigation

        val canOpenBadges: Boolean get() = canUseTabs

        val canStartJourney: Boolean
            get() = canUseTabs && (phase == MainPhase.BeforeDriving || phase == MainPhase.Finished)

        val canEndJourney: Boolean
            get() = canUseTabs && phase == MainPhase.DrivingStopped

        val canReturnHome: Boolean
            get() = canUseTabs && phase == MainPhase.Finished
    }
}

internal val VehicleState.allowsTabNavigation: Boolean
    get() = !uxPolicy.restrictionsRequired && gear == VehicleGear.Park &&
        motion.availability == MotionAvailability.Available && motion.motion == VehicleMotion.Stopped

// A parked driver can grant the speed permission before the first valid speed reading arrives.
internal val VehicleState.allowsVehicleRecovery: Boolean
    get() = !uxPolicy.restrictionsRequired && gear == VehicleGear.Park && motion.motion != VehicleMotion.Moving
