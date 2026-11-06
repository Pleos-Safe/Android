package io.issac.pleosandroid.feature.main

import androidx.lifecycle.SavedStateHandle
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import dagger.hilt.android.lifecycle.HiltViewModel
import io.issac.pleosandroid.core.domain.collection.repository.HistoryRepository
import io.issac.pleosandroid.core.domain.driving.repository.DrivingRecorder
import io.issac.pleosandroid.core.domain.driving.repository.JourneyRepository
import io.issac.pleosandroid.core.domain.driving.repository.JourneyState
import io.issac.pleosandroid.core.domain.driving.repository.RecordingPhase
import io.issac.pleosandroid.core.domain.model.MotionAvailability
import io.issac.pleosandroid.core.domain.model.VehicleMotion
import io.issac.pleosandroid.core.domain.repository.VehicleRepository
import io.issac.pleosandroid.feature.main.map.JourneyArrivalMonitor
import io.issac.pleosandroid.feature.main.map.allowsDrivingPlaceRecommendation
import javax.inject.Inject
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.catch
import kotlinx.coroutines.flow.collectLatest
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.flatMapLatest
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch

@OptIn(ExperimentalCoroutinesApi::class)
@HiltViewModel
class MainViewModel @Inject constructor(
    private val vehicleRepository: VehicleRepository,
    private val savedStateHandle: SavedStateHandle,
    private val journeys: JourneyRepository,
    private val recorder: DrivingRecorder,
    history: HistoryRepository,
    arrivalMonitor: JourneyArrivalMonitor? = null,
) : ViewModel() {
    private val arrivedJourney = savedStateHandle.getStateFlow<String?>("arrived_journey_id", null)
    private val report = journeys.state.map { it.session?.id }.distinctUntilChanged().flatMapLatest { id ->
        if (id == null) flowOf(null) else history.observeJourney(id).catch { emit(null) }
    }

    init {
        // Legacy UI-only trip state must not override the shared vehicle journey.
        savedStateHandle.remove<String>("journey_progress")
        if (arrivalMonitor != null) {
            viewModelScope.launch {
                journeys.state.map { it.session?.takeUnless { session -> session.finished }?.id }
                    .distinctUntilChanged().collectLatest { id ->
                        if (id != null) {
                            arrivalMonitor.arrivals().collect {
                                if (journeys.state.value.session?.id == id) savedStateHandle["arrived_journey_id"] = id
                            }
                        }
                    }
            }
        }
        viewModelScope.launch {
            var previous = VehicleMotion.Unknown
            journeys.state.collect { state ->
                val motion = state.vehicle.motion
                if (motion.availability == MotionAvailability.Available) {
                    if (previous == VehicleMotion.Stopped && motion.motion == VehicleMotion.Moving) {
                        savedStateHandle["arrived_journey_id"] = null
                    }
                    previous = motion.motion
                }
            }
        }
    }

    val uiState: StateFlow<MainUiState> = combine(
        journeys.state,
        recorder.state,
        recorder.enabled,
        report,
        arrivedJourney,
    ) { journey, recording, enabled, record, arrivedId ->
        val phase = journey.phase()
        val session = journey.session
        MainUiState.Ready(
            phase = phase,
            vehicle = journey.vehicle,
            summary = session?.takeIf { it.finished }?.let {
                JourneySummary(
                    durationMillis = checkNotNull(it.durationMillis),
                    reportId = record?.takeIf { record -> record.id == it.id }?.id,
                    recordingPhase = if (recording.journeyId == it.id) {
                        recording.phase
                    } else if (enabled) {
                        RecordingPhase.Waiting
                    } else {
                        RecordingPhase.Disabled
                    },
                )
            },
            arrivedBeforeParking = phase == MainPhase.DrivingStopped && session?.id == arrivedId,
            journeyStartedAtMillis = session?.takeUnless { it.finished }?.startedAt?.toEpochMilli(),
        )
    }.stateIn(viewModelScope, SharingStarted.Eagerly, MainUiState.Loading)

    fun returnHome(): Boolean = canUseTabs() && journeys.returnHome()
    fun canUseTabs(): Boolean = vehicleRepository.state.value.allowsTabNavigation
    internal fun canUseDrivingRecommendation(): Boolean {
        val state = journeys.state.value
        return vehicleRepository.state.value.allowsDrivingPlaceRecommendation && state.session?.finished == false &&
            !(state.phase() == MainPhase.DrivingStopped && arrivedJourney.value == state.session?.id)
    }
    fun canOpenBadges(): Boolean = canUseTabs()
    fun canRecoverVehicle(): Boolean = vehicleRepository.state.value.allowsVehicleRecovery
    fun retryVehicle() = vehicleRepository.retry()
}

private fun JourneyState.phase(): MainPhase? = when {
    session == null -> MainPhase.BeforeDriving
    session?.finished == true -> MainPhase.Finished
    vehicle.motion.availability != MotionAvailability.Available -> null
    vehicle.motion.motion == VehicleMotion.Moving -> MainPhase.DrivingMoving
    vehicle.motion.motion == VehicleMotion.Stopped -> MainPhase.DrivingStopped
    else -> null
}
