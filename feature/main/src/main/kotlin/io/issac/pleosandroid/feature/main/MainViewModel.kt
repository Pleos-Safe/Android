package io.issac.pleosandroid.feature.main

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import dagger.hilt.android.lifecycle.HiltViewModel
import io.issac.pleosandroid.core.domain.driving.repository.DrivingRecorder
import io.issac.pleosandroid.core.domain.driving.repository.JourneyRepository
import io.issac.pleosandroid.core.domain.repository.VehicleRepository
import javax.inject.Inject
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.stateIn

@HiltViewModel
class MainViewModel @Inject constructor(
    private val vehicle: VehicleRepository,
    journeys: JourneyRepository,
    recorder: DrivingRecorder,
) : ViewModel() {
    internal val state = combine(vehicle.state, journeys.state, recorder.state) { car, journey, recording ->
        val session = journey.session
        MainState(
            canUseTabs = car.allowsTabNavigation,
            showJourneyResult = car.allowsTabNavigation &&
                (
                    session?.let {
                        it.finished || it.paused || it.recoveryRequired
                    } == true || recording.recoveryRequired
                    ),
        )
    }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), MainState())

    fun canUseTabs(): Boolean = vehicle.state.value.allowsTabNavigation
}
