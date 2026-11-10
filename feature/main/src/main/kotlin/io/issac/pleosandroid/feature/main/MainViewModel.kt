package io.issac.pleosandroid.feature.main

import androidx.lifecycle.SavedStateHandle
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import dagger.hilt.android.lifecycle.HiltViewModel
import io.issac.pleosandroid.core.domain.collection.model.MutationResult
import io.issac.pleosandroid.core.domain.collection.repository.HistoryRepository
import io.issac.pleosandroid.core.domain.driving.repository.DrivingRecorder
import io.issac.pleosandroid.core.domain.driving.repository.JourneyRepository
import io.issac.pleosandroid.core.domain.driving.repository.JourneyState
import io.issac.pleosandroid.core.domain.driving.repository.RecordingPhase
import io.issac.pleosandroid.core.domain.model.MotionAvailability
import io.issac.pleosandroid.core.domain.model.VehicleMotion
import io.issac.pleosandroid.core.domain.points.PointEarningPolicy
import io.issac.pleosandroid.core.domain.points.PointRepository
import io.issac.pleosandroid.core.domain.points.PointTransactionKind
import io.issac.pleosandroid.core.domain.points.PointWallet
import io.issac.pleosandroid.core.domain.repository.VehicleRepository
import io.issac.pleosandroid.feature.main.map.JourneyArrivalMonitor
import io.issac.pleosandroid.feature.main.map.allowsDrivingPlaceRecommendation
import javax.inject.Inject
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.MutableStateFlow
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
    points: PointRepository? = null,
) : ViewModel() {
    private val action = MutableStateFlow(JourneyAction())
    private val wallet =
        points?.observeWallet()?.map<PointWallet, PointWallet?> { it }?.catch { emit(null) }
            ?: flowOf<PointWallet?>(null)
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

    private val journeyUi = combine(
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
                    durationMillis = record?.driving?.movingMillis ?: checkNotNull(it.durationMillis),
                    distanceMeters = record?.distanceMeters,
                    safetyScore = record?.score,
                    endedAt = record?.endedAt,
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
            progress = recording.takeIf { it.journeyId != null && it.journeyId == session?.id }
                ?.let { JourneyProgressDisplay(it.distanceMeters, it.movingMillis) },
            journeyPaused = session?.paused == true && !session.finished,
            pausedAtNanos = session?.pausedAtNanos,
            recoveryRequired = recording.recoveryRequired || session?.recoveryRequired == true,
        )
    }

    val uiState: StateFlow<MainUiState> = combine(journeyUi, wallet, action) { ui, currentWallet, currentAction ->
        val summary = ui.summary?.let { summary ->
            val credit = currentWallet?.transactions?.firstOrNull {
                it.kind == PointTransactionKind.JourneyEarned && it.sourceId == summary.reportId
            }
            val week = summary.endedAt?.let { PointEarningPolicy.weekStart(PointEarningPolicy.drivingDate(it)) }
            val recognizedThisWeek = currentWallet?.transactions.orEmpty().sumOf {
                if (week != null && it.drivingDate?.let(PointEarningPolicy::weekStart) == week) {
                    it.eligibleDistanceMeters
                } else {
                    0L
                }
            }
            summary.copy(
                earnedPoints = if (summary.reportId != null &&
                    currentWallet != null
                ) {
                    credit?.deltaHalfPoints ?: 0
                } else {
                    null
                },
                recognizedDistanceMeters = credit?.eligibleDistanceMeters,
                membershipBonusHalfPoints = credit?.membershipBonusHalfPoints ?: 0,
                settlementReason = when {
                    credit != null && credit.eligibleDistanceMeters < (summary.distanceMeters ?: 0) ->
                        "이번 주 한도까지 남은 거리만 포인트에 반영했어요."

                    credit != null || summary.reportId == null || currentWallet == null -> null

                    (summary.distanceMeters ?: 0) < PointEarningPolicy.MINIMUM_DISTANCE_METERS ->
                        "2km 이상 달리면 포인트를 받아요."

                    summary.safetyScore == null -> "안전점수 자료가 부족해 이번 정산은 기록만 남겼어요."

                    recognizedThisWeek >= PointEarningPolicy.WEEKLY_DISTANCE_LIMIT_METERS ->
                        "이번 주 포인트 인정 거리를 모두 사용했어요."

                    else -> "이번 주행의 포인트 인정 조건을 확인해 주세요."
                },
            )
        }
        ui.copy(
            summary = summary,
            pointBalance = currentWallet?.balanceHalfPoints,
            demoWallet = currentWallet?.demo == true,
            journeyActionPending = currentAction.pending,
            journeyActionError = currentAction.error,
        )
    }.stateIn(viewModelScope, SharingStarted.Eagerly, MainUiState.Loading)

    fun finishJourney() = runJourneyAction { recorder.finish() }

    fun continueJourney() = runJourneyAction { recorder.continueRecoveredJourney() }

    private fun runJourneyAction(operation: suspend () -> MutationResult) {
        if (!canUseTabs() || action.value.pending) return
        action.value = JourneyAction(pending = true)
        viewModelScope.launch {
            val result = operation()
            action.value =
                JourneyAction(error = result != MutationResult.Success && result != MutationResult.AlreadyApplied)
        }
    }

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

private data class JourneyAction(val pending: Boolean = false, val error: Boolean = false)

private fun JourneyState.phase(): MainPhase? = when {
    session == null -> MainPhase.BeforeDriving
    session?.finished == true -> MainPhase.Finished
    vehicle.motion.availability != MotionAvailability.Available -> null
    vehicle.motion.motion == VehicleMotion.Moving -> MainPhase.DrivingMoving
    vehicle.motion.motion == VehicleMotion.Stopped -> MainPhase.DrivingStopped
    else -> null
}
