package io.issac.pleosandroid.feature.main.map

import android.content.Context
import androidx.compose.runtime.staticCompositionLocalOf
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import dagger.hilt.android.lifecycle.HiltViewModel
import dagger.hilt.android.qualifiers.ApplicationContext
import io.issac.pleosandroid.core.domain.repository.VehicleRepository
import io.issac.pleosandroid.feature.main.allowsTabNavigation
import javax.inject.Inject
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.stateIn

// Keep one route writer and one pending request across the parked map and driving home.
internal val LocalPlaceMapViewModel = staticCompositionLocalOf<PlaceMapViewModel?> { null }

@HiltViewModel
internal class PlaceMapViewModel @Inject constructor(
    @ApplicationContext context: Context,
    private val vehicleRepository: VehicleRepository,
) : ViewModel() {
    private val location = PlaceMapLocation(context, viewModelScope)
    private val navigationDelegate = lazy { PlaceMapNavigation(context, viewModelScope) }
    private val navigation by navigationDelegate
    private var drivingRecommendationVisible = false

    val locationState = location.state
    val navigationState get() = navigation.state

    val drivingPlace by lazy {
        combine(location.freshFix, navigation.state) { fix, route ->
            recommendPlace(fix, route)
        }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(replayExpirationMillis = 0), null)
    }

    fun canInteract(): Boolean = vehicleRepository.state.value.allowsTabNavigation

    fun refreshLocation() {
        if (canInteract()) location.refresh()
    }

    fun stopLocation() = location.close()

    fun request(place: TravelPlace) = navigation.request(place, canRequest = ::canInteract)

    fun recheck() {
        if (canInteract()) navigation.recheck()
    }

    fun startDrivingRecommendation(isAllowed: () -> Boolean) {
        if (!isAllowed()) return
        drivingRecommendationVisible = true
        location.refresh()
        navigation.recheck()
    }

    fun stopDrivingRecommendation() {
        drivingRecommendationVisible = false
        location.close()
    }

    fun requestDrivingPlace(place: TravelPlace, hasDestination: Boolean, isAllowed: () -> Boolean) {
        navigation.request(place) {
            val route = navigation.state.value
            drivingRecommendationVisible && isAllowed() && route.hasDestination == hasDestination &&
                recommendPlace(location.freshFix.value, route) == place
        }
    }

    fun recheckWhileDriving(isAllowed: () -> Boolean) {
        if (drivingRecommendationVisible && isAllowed()) navigation.recheck()
    }

    private fun recommendPlace(fix: FreshPlaceFix?, route: PlaceNavigationState): TravelPlace? {
        if (fix == null || !fix.isFresh() || !location.hasPermission() || !route.hasWaypointCapacity) return null
        return recommendDrivingPlace(fix.position, busanTravelPlaces.filterNot { it.id in route.routePlaceIds })
    }

    override fun onCleared() {
        location.close()
        if (navigationDelegate.isInitialized()) navigation.close()
    }
}
