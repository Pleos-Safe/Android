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

sealed interface MainUiState {
    data object Loading : MainUiState

    data class Ready(val phase: MainPhase?, val vehicle: VehicleState) : MainUiState {
        val canOpenBadges: Boolean
            get() = !vehicle.uxPolicy.restrictionsRequired

        val canStartJourney: Boolean
            get() = canOpenBadges && vehicle.motion.availability == MotionAvailability.Available &&
                vehicle.motion.motion == VehicleMotion.Stopped &&
                phase in setOf(MainPhase.BeforeDriving, MainPhase.Finished)

        val canEndJourney: Boolean
            get() = canOpenBadges && vehicle.motion.availability == MotionAvailability.Available &&
                vehicle.gear == VehicleGear.Park &&
                phase == MainPhase.DrivingStopped
    }
}
