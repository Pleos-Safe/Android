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
import io.issac.pleosandroid.feature.main.map.JourneyArrivalMonitor
import io.issac.pleosandroid.feature.main.map.allowsDrivingPlaceRecommendation
import javax.inject.Inject
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.collectLatest
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch

@HiltViewModel
class MainViewModel @Inject constructor(
    private val vehicleRepository: VehicleRepository,
    private val savedStateHandle: SavedStateHandle,
    arrivalMonitor: JourneyArrivalMonitor? = null,
) : ViewModel() {
    private val progress = savedStateHandle.getStateFlow(
        key = JOURNEY_PROGRESS_KEY,
        initialValue = JourneyProgress.Before.name,
    )

    private val arrived = savedStateHandle.getStateFlow(JOURNEY_ARRIVED_KEY, false)
    private var previousMotion = VehicleMotion.Unknown

    init {
        // Older versions let a driver prepare a journey without departing.
        if (progress.value == "Prepared") updateProgress(JourneyProgress.Before)
        if (arrivalMonitor != null) {
            viewModelScope.launch {
                progress.collectLatest { value ->
                    if (journeyProgress(value) == JourneyProgress.Active) {
                        arrivalMonitor.arrivals().collect {
                            if (journeyProgress(progress.value) == JourneyProgress.Active) {
                                savedStateHandle[JOURNEY_ARRIVED_KEY] = true
                            }
                        }
                    }
                }
            }
        }
    }

    val uiState: StateFlow<MainUiState> = combine(vehicleRepository.state, progress, arrived) { vehicle, _, _ ->
        updateJourneyFromVehicle(vehicle)
        createUiState(vehicle)
    }.stateIn(
        scope = viewModelScope,
        // Keep tracking the trip while system navigation covers the activity.
        started = SharingStarted.Eagerly,
        initialValue = MainUiState.Loading,
    )

    private fun updateJourneyFromVehicle(vehicle: VehicleState) {
        val current = journeyProgress(progress.value)
        val updated = current.onVehicleChanged(vehicle)
        if (updated != current) updateProgress(updated)
        if (vehicle.motion.availability == MotionAvailability.Available) {
            if (previousMotion == VehicleMotion.Stopped && vehicle.motion.motion == VehicleMotion.Moving) {
                savedStateHandle[JOURNEY_ARRIVED_KEY] = false
            }
            previousMotion = vehicle.motion.motion
        }
    }

    fun returnHome(): Boolean {
        val current = createUiState(vehicleRepository.state.value)
        if (!current.canReturnHome) return false
        updateProgress(JourneyProgress.Before)
        return true
    }

    fun canUseTabs(): Boolean = vehicleRepository.state.value.allowsTabNavigation

    internal fun canUseDrivingRecommendation(): Boolean {
        val current = createUiState(vehicleRepository.state.value)
        return current.vehicle.allowsDrivingPlaceRecommendation && !current.arrivedBeforeParking &&
            (current.phase == MainPhase.DrivingMoving || current.phase == MainPhase.DrivingStopped)
    }

    fun canOpenBadges(): Boolean = canUseTabs()

    fun canRecoverVehicle(): Boolean = vehicleRepository.state.value.allowsVehicleRecovery

    fun retryVehicle() {
        vehicleRepository.retry()
    }

    private fun createUiState(vehicle: VehicleState): MainUiState.Ready {
        val journey = journeyProgress(progress.value).onVehicleChanged(vehicle)
        val phase = when (journey) {
            JourneyProgress.Before -> MainPhase.BeforeDriving

            JourneyProgress.Finished -> MainPhase.Finished

            JourneyProgress.Active -> when {
                vehicle.motion.availability != MotionAvailability.Available -> null
                vehicle.motion.motion == VehicleMotion.Moving -> MainPhase.DrivingMoving
                vehicle.motion.motion == VehicleMotion.Stopped -> MainPhase.DrivingStopped
                else -> null
            }
        }
        val summary = if (phase == MainPhase.Finished) journeySummary() else null
        return MainUiState.Ready(
            phase = phase,
            vehicle = vehicle,
            summary = summary,
            arrivedBeforeParking = phase == MainPhase.DrivingStopped && arrived.value,
            journeyStartedAtMillis = if (phase == MainPhase.DrivingMoving || phase == MainPhase.DrivingStopped) {
                savedStateHandle[JOURNEY_START_KEY]
            } else {
                null
            },
        )
    }

    private fun updateProgress(next: JourneyProgress) {
        when (next) {
            JourneyProgress.Active -> {
                savedStateHandle[JOURNEY_START_KEY] = System.currentTimeMillis()
                savedStateHandle.remove<Long>(JOURNEY_END_KEY)
            }

            JourneyProgress.Finished -> savedStateHandle[JOURNEY_END_KEY] = System.currentTimeMillis()

            JourneyProgress.Before -> {
                savedStateHandle.remove<Long>(JOURNEY_START_KEY)
                savedStateHandle.remove<Long>(JOURNEY_END_KEY)
            }
        }
        savedStateHandle[JOURNEY_ARRIVED_KEY] = false
        savedStateHandle[JOURNEY_PROGRESS_KEY] = next.name
    }

    private fun journeySummary(): JourneySummary? {
        val start = savedStateHandle.get<Long>(JOURNEY_START_KEY) ?: return null
        val end = savedStateHandle.get<Long>(JOURNEY_END_KEY) ?: return null
        return (end - start).takeIf { it >= 0 }?.let(::JourneySummary)
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

    private enum class JourneyProgress { Before, Active, Finished }

    private companion object {
        const val JOURNEY_PROGRESS_KEY = "journey_progress"
        const val JOURNEY_START_KEY = "journey_start_millis"
        const val JOURNEY_END_KEY = "journey_end_millis"
        const val JOURNEY_ARRIVED_KEY = "journey_arrived"
    }
}
