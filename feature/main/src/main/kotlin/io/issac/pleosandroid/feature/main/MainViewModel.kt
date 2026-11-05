package io.issac.pleosandroid.feature.main

import androidx.lifecycle.SavedStateHandle
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import dagger.hilt.android.lifecycle.HiltViewModel
import io.issac.pleosandroid.core.domain.model.MotionAvailability
import io.issac.pleosandroid.core.domain.model.VehicleGear
import io.issac.pleosandroid.core.domain.model.VehicleMotion
import io.issac.pleosandroid.core.domain.model.VehicleState
import io.issac.pleosandroid.core.domain.repository.VehicleRepository
import javax.inject.Inject
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.onEach
import kotlinx.coroutines.flow.stateIn

@HiltViewModel
class MainViewModel @Inject constructor(
    private val vehicleRepository: VehicleRepository,
    private val savedStateHandle: SavedStateHandle,
) : ViewModel() {
    private val progress = savedStateHandle.getStateFlow(
        key = JOURNEY_PROGRESS_KEY,
        initialValue = JourneyProgress.Before.name,
    )

    private val observedVehicle = vehicleRepository.state.onEach { vehicle ->
        val current = journeyProgress(progress.value)
        val updated = current.onVehicleChanged(vehicle)
        if (updated != current) savedStateHandle[JOURNEY_PROGRESS_KEY] = updated.name
    }

    val uiState: StateFlow<MainUiState> = combine(observedVehicle, progress) { vehicle, value ->
        createUiState(vehicle, value)
    }.stateIn(
        scope = viewModelScope,
        started = SharingStarted.WhileSubscribed(5_000, replayExpirationMillis = 0),
        initialValue = MainUiState.Loading,
    )

    fun startJourney(): Boolean {
        val current = createUiState(vehicleRepository.state.value, progress.value)
        if (!current.canStartJourney) return false
        savedStateHandle[JOURNEY_PROGRESS_KEY] = JourneyProgress.Prepared.name
        return true
    }

    fun endJourney(): Boolean {
        val current = createUiState(vehicleRepository.state.value, progress.value)
        if (!current.canEndJourney) return false
        savedStateHandle[JOURNEY_PROGRESS_KEY] = JourneyProgress.Finished.name
        return true
    }

    fun canUseTabs(): Boolean = vehicleRepository.state.value.allowsTabNavigation

    fun canOpenBadges(): Boolean = canUseTabs()

    fun canRecoverVehicle(): Boolean = vehicleRepository.state.value.allowsVehicleRecovery

    fun retryVehicle() {
        vehicleRepository.retry()
    }

    private fun createUiState(vehicle: VehicleState, value: String): MainUiState.Ready {
        val journey = journeyProgress(value).onVehicleChanged(vehicle)
        val phase = when (journey) {
            JourneyProgress.Before -> MainPhase.BeforeDriving

            JourneyProgress.Finished -> MainPhase.Finished

            JourneyProgress.Prepared, JourneyProgress.Active -> when {
                vehicle.motion.availability != MotionAvailability.Available -> null
                vehicle.motion.motion == VehicleMotion.Moving -> MainPhase.DrivingMoving
                vehicle.motion.motion == VehicleMotion.Stopped -> MainPhase.DrivingStopped
                else -> null
            }
        }
        return MainUiState.Ready(phase = phase, vehicle = vehicle)
    }

    private fun journeyProgress(value: String): JourneyProgress =
        JourneyProgress.entries.firstOrNull { it.name == value } ?: JourneyProgress.Before

    private fun JourneyProgress.onVehicleChanged(vehicle: VehicleState): JourneyProgress {
        if (vehicle.motion.availability != MotionAvailability.Available) return this
        return when {
            vehicle.motion.motion == VehicleMotion.Moving -> JourneyProgress.Active

            this == JourneyProgress.Active && vehicle.motion.motion == VehicleMotion.Stopped &&
                vehicle.gear == VehicleGear.Park -> JourneyProgress.Finished

            else -> this
        }
    }

    // A manual start can precede departure; repeated parked snapshots must not end that trip.
    private enum class JourneyProgress { Before, Prepared, Active, Finished }

    private companion object {
        const val JOURNEY_PROGRESS_KEY = "journey_progress"
    }
}
