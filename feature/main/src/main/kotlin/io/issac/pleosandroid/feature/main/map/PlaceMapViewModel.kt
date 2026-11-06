package io.issac.pleosandroid.feature.main.map

import android.content.Context
import androidx.compose.runtime.staticCompositionLocalOf
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import dagger.hilt.android.lifecycle.HiltViewModel
import dagger.hilt.android.qualifiers.ApplicationContext
import io.issac.pleosandroid.core.domain.collection.model.PlaceQuery
import io.issac.pleosandroid.core.domain.collection.repository.PlaceRepository
import io.issac.pleosandroid.core.domain.driving.repository.DrivingObservationKind
import io.issac.pleosandroid.core.domain.driving.repository.DrivingRecorder
import io.issac.pleosandroid.core.domain.repository.BadgeRegionSource
import io.issac.pleosandroid.core.domain.repository.VehicleRepository
import io.issac.pleosandroid.feature.main.DrivingSafetyMessage
import javax.inject.Inject
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.catch
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.flatMapLatest
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.onStart
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch

// Keep one route writer and one pending request across the parked map and driving home.
internal val LocalPlaceMapViewModel = staticCompositionLocalOf<PlaceMapViewModel?> { null }

@OptIn(ExperimentalCoroutinesApi::class)
@HiltViewModel
internal class PlaceMapViewModel @Inject constructor(
    @ApplicationContext context: Context,
    private val vehicleRepository: VehicleRepository,
    badgeRegionSource: BadgeRegionSource,
    placeRepository: PlaceRepository,
    recorder: DrivingRecorder,
) : ViewModel() {
    private val location = PlaceMapLocation(context, viewModelScope)
    private val navigationDelegate = lazy {
        PlaceMapNavigation(context, viewModelScope).also { it.updateCatalog(this.places.value) }
    }
    private val navigation by navigationDelegate
    private var parkedMapVisible = false
    private val drivingJourney = MutableStateFlow<Long?>(null)
    private val recommendationSession = DrivingRecommendationSession()
    private var messageJob: Job? = null
    private val mutableSafetyMessage = MutableStateFlow<DrivingSafetyMessage?>(null)

    private val catalogRefresh = MutableStateFlow(0)
    private val mutableCatalogError = MutableStateFlow(false)
    val catalogError = mutableCatalogError.asStateFlow()
    val places = catalogRefresh.flatMapLatest {
        placeRepository.observePlaces(PlaceQuery()).onStart { mutableCatalogError.value = false }.catch {
            mutableCatalogError.value = true
            emit(emptyList())
        }
    }.map { rows ->
        rows.map { row ->
            val p = row.place
            TravelPlace(
                p.id,
                p.name,
                p.latitude,
                p.longitude,
                p.district,
                p.category,
                p.imageKey,
                row.ownedCardIds.isNotEmpty(),
            )
        }
    }.stateIn(viewModelScope, SharingStarted.Eagerly, emptyList())

    init {
        viewModelScope.launch {
            recorder.observations.collect { observation ->
                if (drivingJourney.value == observation.startedAtMillis) {
                    messageJob?.cancel()
                    mutableSafetyMessage.value = when (observation.kind) {
                        DrivingObservationKind.Acceleration -> DrivingSafetyMessage.Acceleration
                        DrivingObservationKind.Braking -> DrivingSafetyMessage.Braking
                    }
                    messageJob = viewModelScope.launch {
                        delay(5_000)
                        mutableSafetyMessage.value = null
                    }
                }
            }
        }
        viewModelScope.launch {
            this@PlaceMapViewModel.places.collect { list ->
                if (navigationDelegate.isInitialized()) navigation.updateCatalog(list)
            }
        }
    }

    val locationState = location.state
    val navigationState get() = navigation.state
    val badgeRegionIds = badgeRegionSource.collectedRegionIds.catch { emit(emptySet()) }
    val drivingSafetyMessage = mutableSafetyMessage.asStateFlow()

    val drivingPosition = combine(drivingJourney, location.freshFix) { journeyId, fix ->
        if (journeyId != null) currentPosition(fix) else null
    }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(replayExpirationMillis = 0), null)

    val drivingPlace by lazy {
        combine(drivingJourney, location.freshFix, navigation.state, vehicleRepository.state, this.places) {
                journeyId,
                fix,
                route,
                vehicle,
                catalog,
            ->
            if (journeyId == null || !vehicle.allowsDrivingPlaceRecommendation) {
                null
            } else {
                recommendationSession.select(journeyId, currentPosition(fix), route, catalog)
            }
        }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(replayExpirationMillis = 0), null)
    }

    fun canInteract(): Boolean = vehicleRepository.state.value.allowsTabNavigation

    fun retryCatalog() {
        if (canInteract()) catalogRefresh.value += 1
    }

    fun refreshLocation() {
        if (!canInteract()) return
        if (catalogError.value) retryCatalog()
        parkedMapVisible = true
        location.refresh()
    }

    fun stopLocation() {
        parkedMapVisible = false
        if (drivingJourney.value == null) location.close()
    }

    fun request(place: TravelPlace) = navigation.request(place) { canInteract() && place in places.value }

    fun recheck() {
        if (canInteract()) navigation.recheck()
    }

    fun startDrivingOverview(journeyId: Long) {
        drivingJourney.value = journeyId
        location.refresh()
        navigation.recheck()
    }

    fun stopDrivingOverview(journeyId: Long) {
        if (drivingJourney.value != journeyId) return
        drivingJourney.value = null
        messageJob?.cancel()
        mutableSafetyMessage.value = null
        if (!parkedMapVisible) location.close()
    }

    fun requestDrivingPlace(place: TravelPlace, hasDestination: Boolean, isAllowed: () -> Boolean) {
        navigation.request(place) {
            val route = navigation.state.value
            val position = currentPosition(location.freshFix.value)
            drivingJourney.value != null && isAllowed() && route.hasDestination == hasDestination &&
                route.hasWaypointCapacity && place.id !in route.routePlaceIds && drivingPlace.value == place &&
                position != null && place.nearbyDistanceMeters(position) != null
        }
    }

    fun recheckWhileDriving(isAllowed: () -> Boolean) {
        if (drivingJourney.value != null && isAllowed()) navigation.recheck()
    }

    private fun currentPosition(fix: FreshPlaceFix?): PlaceMapPosition? =
        fix?.takeIf { it.isFresh() && location.hasPermission() }?.position

    override fun onCleared() {
        location.close()
        if (navigationDelegate.isInitialized()) navigation.close()
    }
}
