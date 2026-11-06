package io.issac.pleosandroid.feature.main.map

import android.content.Context
import androidx.compose.runtime.staticCompositionLocalOf
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import dagger.hilt.android.lifecycle.HiltViewModel
import dagger.hilt.android.qualifiers.ApplicationContext
import io.issac.pleosandroid.core.domain.repository.BadgeRegionSource
import io.issac.pleosandroid.core.domain.repository.VehicleRepository
import io.issac.pleosandroid.feature.main.allowsTabNavigation
import javax.inject.Inject
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.stateIn

// Keep one route writer and one pending request across the parked map and driving home.
internal val LocalPlaceMapViewModel = staticCompositionLocalOf<PlaceMapViewModel?> { null }

@HiltViewModel
internal class PlaceMapViewModel @Inject constructor(
    @ApplicationContext context: Context,
    private val vehicleRepository: VehicleRepository,
    badgeRegionSource: BadgeRegionSource,
) : ViewModel() {
    private val location = PlaceMapLocation(context, viewModelScope)
    private val navigationDelegate = lazy { PlaceMapNavigation(context, viewModelScope) }
    private val navigation by navigationDelegate
    private var parkedMapVisible = false
    private val drivingJourney = MutableStateFlow<Long?>(null)

    val locationState = location.state
    val navigationState get() = navigation.state
    val badgeRegionIds = badgeRegionSource.collectedRegionIds

    val drivingPosition = combine(drivingJourney, location.freshFix) { journeyId, fix ->
        if (journeyId != null) currentPosition(fix) else null
    }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(replayExpirationMillis = 0), null)

    val drivingPlace by lazy {
        combine(drivingJourney, location.freshFix, navigation.state, vehicleRepository.state) {
                journeyId,
                fix,
                route,
                vehicle,
            ->
            if (journeyId == null || !vehicle.allowsDrivingPlaceRecommendation) {
                null
            } else {
                recommendPlace(currentPosition(fix), route)
            }
        }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(replayExpirationMillis = 0), null)
    }

    fun canInteract(): Boolean = vehicleRepository.state.value.allowsTabNavigation

    fun refreshLocation() {
        if (!canInteract()) return
        parkedMapVisible = true
        location.refresh()
    }

    fun stopLocation() {
        parkedMapVisible = false
        if (drivingJourney.value == null) location.close()
    }

    fun request(place: TravelPlace) = navigation.request(place, canRequest = ::canInteract)

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
        if (!parkedMapVisible) location.close()
    }

    fun requestDrivingPlace(place: TravelPlace, hasDestination: Boolean, isAllowed: () -> Boolean) {
        navigation.request(place) {
            val route = navigation.state.value
            drivingJourney.value != null && isAllowed() && route.hasDestination == hasDestination &&
                recommendPlace(currentPosition(location.freshFix.value), route) == place
        }
    }

    fun recheckWhileDriving(isAllowed: () -> Boolean) {
        if (drivingJourney.value != null && isAllowed()) navigation.recheck()
    }

    private fun currentPosition(fix: FreshPlaceFix?): PlaceMapPosition? =
        fix?.takeIf { it.isFresh() && location.hasPermission() }?.position

    private fun recommendPlace(position: PlaceMapPosition?, route: PlaceNavigationState): TravelPlace? {
        if (position == null || !route.hasWaypointCapacity) return null
        return recommendDrivingPlace(position, busanTravelPlaces.filterNot { it.id in route.routePlaceIds })
    }

    override fun onCleared() {
        location.close()
        if (navigationDelegate.isInitialized()) navigation.close()
    }
}
