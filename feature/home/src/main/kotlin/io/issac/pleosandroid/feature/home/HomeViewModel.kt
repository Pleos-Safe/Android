package io.issac.pleosandroid.feature.home

import androidx.lifecycle.SavedStateHandle
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import dagger.hilt.android.lifecycle.HiltViewModel
import io.issac.pleosandroid.core.domain.collection.model.MutationResult
import io.issac.pleosandroid.core.domain.collection.model.VisitPackClaimResult
import io.issac.pleosandroid.core.domain.collection.repository.HistoryRepository
import io.issac.pleosandroid.core.domain.driving.repository.DrivingRecorder
import io.issac.pleosandroid.core.domain.driving.repository.JourneyRepository
import io.issac.pleosandroid.core.domain.driving.repository.JourneyState
import io.issac.pleosandroid.core.domain.driving.repository.RecordingPhase
import io.issac.pleosandroid.core.domain.model.MotionAvailability
import io.issac.pleosandroid.core.domain.model.VehicleMotion
import io.issac.pleosandroid.core.domain.place.JourneyArrivalSource
import io.issac.pleosandroid.core.domain.place.PlaceSessionRepository
import io.issac.pleosandroid.core.domain.place.allowsDrivingPlaceRecommendation
import io.issac.pleosandroid.core.domain.points.PointRepository
import io.issac.pleosandroid.core.domain.points.PointWallet
import io.issac.pleosandroid.core.domain.repository.VehicleRepository
import io.issac.pleosandroid.core.navigation.AppRoute
import io.issac.pleosandroid.core.navigation.BadgesRoute
import io.issac.pleosandroid.core.navigation.DrivingReportRoute
import io.issac.pleosandroid.core.navigation.ExploreRoute
import io.issac.pleosandroid.core.navigation.RecordsRoute
import io.issac.pleosandroid.core.ui.flow.catchUiError
import javax.inject.Inject
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.collectLatest
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.flatMapLatest
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.receiveAsFlow
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch

@OptIn(ExperimentalCoroutinesApi::class)
@HiltViewModel
internal class HomeViewModel @Inject constructor(
    private val vehicleRepository: VehicleRepository,
    private val savedStateHandle: SavedStateHandle,
    private val journeys: JourneyRepository,
    private val recorder: DrivingRecorder,
    history: HistoryRepository,
    arrivalMonitor: JourneyArrivalSource? = null,
    points: PointRepository? = null,
    private val places: PlaceSessionRepository? = null,
) : ViewModel() {
    private val placeLease = places?.acquire()
    private val effects = Channel<HomeEffect>(Channel.BUFFERED)
    internal val effect = effects.receiveAsFlow()
    private val action = MutableStateFlow(JourneyAction())
    private val wallet =
        points?.observeWallet()?.map<PointWallet, PointWallet?> { it }?.catchUiError { emit(null) }
            ?: flowOf<PointWallet?>(null)
    private val arrivedJourney = savedStateHandle.getStateFlow<String?>("arrived_journey_id", null)
    private val report = journeys.state.map { it.session?.id }.distinctUntilChanged().flatMapLatest { id ->
        if (id == null) flowOf(null) else history.observeJourney(id).catchUiError { emit(null) }
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
        HomeUiState.Ready(
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
            arrivedBeforeParking = phase == HomePhase.DrivingStopped && session?.id == arrivedId,
            journeyStartedAtMillis = session?.takeUnless { it.finished }?.startedAt?.toEpochMilli(),
            progress = recording.takeIf { it.journeyId != null && it.journeyId == session?.id }
                ?.let { JourneyProgressDisplay(it.distanceMeters, it.movingMillis) },
            journeyPaused = session?.paused == true && !session.finished,
            pausedAtNanos = session?.pausedAtNanos,
            recoveryRequired = recording.recoveryRequired || session?.recoveryRequired == true,
        )
    }

    private val journeyState: StateFlow<HomeUiState> = combine(journeyUi, wallet, action) { ui, currentWallet, action ->
        ui.copy(
            summary = ui.summary?.withPoints(currentWallet),
            pointBalance = currentWallet?.balanceHalfPoints,
            demoWallet = currentWallet?.demo == true,
            journeyActionPending = action.pending,
            journeyActionError = action.error,
        )
    }.stateIn(viewModelScope, SharingStarted.Eagerly, HomeUiState.Loading)

    private val placeState: Flow<HomePlacesState> = places?.let { session ->
        combine(
            session.places,
            session.drivingPlace,
            session.navigationState,
            session.drivingPosition,
            session.regionVisitPackCounts,
        ) { catalog, recommendation, navigation, position, packCounts ->
            val featured = (catalog.firstOrNull { it.id == "haeundae" } ?: catalog.firstOrNull())
                ?.let { HomePlace(it.name, it.district, it.category, it.imageKey) }
            HomePlacesState(featured, recommendation, navigation, position, packCounts)
        }
    } ?: flowOf(HomePlacesState())

    private val visitState = places?.let { session ->
        combine(session.claimableVisit, session.visitResult) { candidate, result -> candidate to result }
    } ?: flowOf(null to null)

    internal val state = combine(
        journeyState,
        placeState,
        visitState,
        places?.drivingSafetyMessage ?: flowOf(null),
    ) { journey, place, visit, safety ->
        HomeState(journey, place, visit.first, visit.second, safety)
    }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), HomeState())

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

    internal fun onIntent(intent: HomeIntent) {
        when (intent) {
            HomeIntent.ReturnHome -> returnHome()

            HomeIntent.FinishJourney -> finishJourney()

            HomeIntent.ContinueJourney -> continueJourney()

            HomeIntent.OpenCollection -> navigate(BadgesRoute)

            HomeIntent.OpenExplore -> navigate(ExploreRoute)

            HomeIntent.OpenRecords -> navigate(RecordsRoute)

            is HomeIntent.OpenReport -> if ((journeyState.value as? HomeUiState.Ready)?.summary?.reportId ==
                intent.id
            ) {
                navigate(DrivingReportRoute(intent.id))
            }

            HomeIntent.RequestSpeedPermission -> if (canRecoverVehicle()) {
                effects.trySend(HomeEffect.RequestSpeedPermission)
            }

            HomeIntent.RetryVehicle -> if (canRecoverVehicle()) retryVehicle()

            is HomeIntent.ClaimVisit -> places?.claimVisit(intent.visit)

            is HomeIntent.OpenReceivedPack -> openReceivedPack(intent.id)

            HomeIntent.RefreshVisit -> places?.refreshParkedVisit()

            is HomeIntent.StartDrivingOverview -> places?.startDrivingOverview(intent.journeyId, intent.owner)

            is HomeIntent.StopDrivingOverview -> places?.stopDrivingOverview(intent.journeyId, intent.owner)

            is HomeIntent.RequestRecommendedPlace -> requestRecommendedPlace(intent)

            HomeIntent.RecheckNavigation -> if (canUseDrivingRecommendation()) places?.recheckWhileDriving()
        }
    }

    internal fun finishJourney() = runJourneyAction { recorder.finish() }

    internal fun continueJourney() = runJourneyAction { recorder.continueRecoveredJourney() }

    internal fun returnHome(): Boolean = canUseTabs() && journeys.returnHome()
    internal fun canUseTabs(): Boolean = vehicleRepository.state.value.allowsTabNavigation
    internal fun canUseDrivingRecommendation(): Boolean {
        val state = journeys.state.value
        return vehicleRepository.state.value.allowsDrivingPlaceRecommendation && state.session?.finished == false &&
            !(state.phase() == HomePhase.DrivingStopped && arrivedJourney.value == state.session?.id)
    }
    internal fun canOpenBadges(): Boolean = canUseTabs()
    internal fun canRecoverVehicle(): Boolean = vehicleRepository.state.value.allowsVehicleRecovery
    internal fun retryVehicle() = vehicleRepository.retry()

    override fun onCleared() {
        placeLease?.close()
        effects.close()
    }
    private fun navigate(route: AppRoute) {
        if (canUseTabs()) effects.trySend(HomeEffect.Navigate(route))
    }

    private fun openReceivedPack(id: String) {
        if (!canUseTabs()) return
        val receipt = when (val result = places?.visitResult?.value) {
            is VisitPackClaimResult.Applied -> result.receipt
            is VisitPackClaimResult.AlreadyClaimed -> result.receipt
            else -> return
        }
        if (receipt.albumPackId == id) effects.trySend(HomeEffect.OpenPack(id))
    }

    private fun requestRecommendedPlace(intent: HomeIntent.RequestRecommendedPlace) {
        if (!canUseDrivingRecommendation()) return
        val session = places ?: return
        val selected = session.drivingPlace.value?.takeIf { it.id == intent.placeId } ?: return
        val hasDestination =
            session.navigationState.value.hasDestination?.takeIf { it == intent.hasDestination } ?: return
        session.requestDrivingPlace(selected, hasDestination)
    }

    private fun runJourneyAction(operation: suspend () -> MutationResult) {
        if (!canUseTabs() || action.value.pending) return
        action.value = JourneyAction(pending = true)
        viewModelScope.launch {
            try {
                val result = operation()
                action.value = JourneyAction(
                    error = result != MutationResult.Success && result != MutationResult.AlreadyApplied,
                )
            } catch (cancelled: CancellationException) {
                throw cancelled
            } catch (_: Exception) {
                action.value = JourneyAction(error = true)
            }
        }
    }
}

private data class JourneyAction(val pending: Boolean = false, val error: Boolean = false)

private fun JourneyState.phase(): HomePhase? = when {
    session == null -> HomePhase.BeforeDriving
    session?.finished == true -> HomePhase.Finished
    vehicle.motion.availability != MotionAvailability.Available -> null
    vehicle.motion.motion == VehicleMotion.Moving -> HomePhase.DrivingMoving
    vehicle.motion.motion == VehicleMotion.Stopped -> HomePhase.DrivingStopped
    else -> null
}
