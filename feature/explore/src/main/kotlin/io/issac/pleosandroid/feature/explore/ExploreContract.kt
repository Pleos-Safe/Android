package io.issac.pleosandroid.feature.explore

import io.issac.pleosandroid.core.domain.place.PlaceLocationState
import io.issac.pleosandroid.core.domain.place.PlaceMapBounds
import io.issac.pleosandroid.core.domain.place.PlaceMapPosition
import io.issac.pleosandroid.core.domain.place.PlaceNavigationState
import io.issac.pleosandroid.core.domain.place.TravelPlace
import io.issac.pleosandroid.core.ui.mvi.UiEffect
import io.issac.pleosandroid.core.ui.mvi.UiIntent
import io.issac.pleosandroid.core.ui.mvi.UiState

internal data class ExploreState(
    val catalog: List<TravelPlace> = emptyList(),
    val places: List<TravelPlace> = emptyList(),
    val catalogError: Boolean = false,
    val location: PlaceLocationState = PlaceLocationState.Loading,
    val navigation: PlaceNavigationState = PlaceNavigationState(),
    val query: String = "",
    val selectedPlaceId: String? = null,
    val waypointPlaceId: String? = null,
    val listOnly: Boolean = false,
    val region: String? = null,
    val appliedBounds: PlaceMapBounds? = null,
    val visibleBounds: PlaceMapBounds? = null,
) : UiState {
    val position: PlaceMapPosition? get() = (location as? PlaceLocationState.Available)?.position
    val selected: TravelPlace? get() = places.firstOrNull { it.id == selectedPlaceId }
    val waypoint: TravelPlace? get() = places.firstOrNull { it.id == waypointPlaceId }
}

internal sealed interface ExploreIntent : UiIntent {
    data class SetQuery(val query: String) : ExploreIntent
    data class SelectPlace(val id: String) : ExploreIntent
    data class SelectRegion(val id: String) : ExploreIntent
    data class ViewportChanged(val bounds: PlaceMapBounds) : ExploreIntent
    data class RequestNavigation(val placeId: String, val hasDestination: Boolean?) : ExploreIntent
    data object ClearFilters : ExploreIntent
    data object SearchVisibleArea : ExploreIntent
    data object ToggleList : ExploreIntent
    data object ConfirmWaypoint : ExploreIntent
    data object DismissWaypoint : ExploreIntent
    data object RequestLocationPermission : ExploreIntent
    data object LocationPermissionChanged : ExploreIntent
    data object Resume : ExploreIntent
    data object Pause : ExploreIntent
    data object RetryCatalog : ExploreIntent
    data object RecheckRoute : ExploreIntent
    data object Back : ExploreIntent
}

internal sealed interface ExploreEffect : UiEffect {
    data object RequestLocationPermission : ExploreEffect
    data object Back : ExploreEffect
}
