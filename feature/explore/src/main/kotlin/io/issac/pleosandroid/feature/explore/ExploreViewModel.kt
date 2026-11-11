package io.issac.pleosandroid.feature.explore

import androidx.annotation.MainThread
import androidx.lifecycle.SavedStateHandle
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import dagger.hilt.android.lifecycle.HiltViewModel
import io.issac.pleosandroid.core.domain.place.PlaceLocationState
import io.issac.pleosandroid.core.domain.place.PlaceMapBounds
import io.issac.pleosandroid.core.domain.place.PlaceMapPosition
import io.issac.pleosandroid.core.domain.place.PlaceNavigationState
import io.issac.pleosandroid.core.domain.place.PlaceSessionRepository
import io.issac.pleosandroid.core.domain.place.TravelPlace
import io.issac.pleosandroid.core.domain.place.searchTravelPlaces
import java.util.UUID
import javax.inject.Inject
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.receiveAsFlow
import kotlinx.coroutines.flow.stateIn

@HiltViewModel
internal class ExploreViewModel @Inject constructor(
    private val places: PlaceSessionRepository,
    private val savedStateHandle: SavedStateHandle,
) : ViewModel() {
    private val locationOwner = UUID.randomUUID().toString()
    private val selection = MutableStateFlow(restoreSelection())
    private val visibleBounds = MutableStateFlow<PlaceMapBounds?>(null)
    private val effects = Channel<ExploreEffect>(Channel.BUFFERED)
    val effect = effects.receiveAsFlow()

    private val source = combine(
        places.places,
        places.catalogError,
        places.locationState,
        places.navigationState,
        ::ExploreSource,
    )
    val state = combine(source, selection, visibleBounds, ::buildState)
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), ExploreState())
    private val session = places.acquire()

    @MainThread
    fun handleIntent(intent: ExploreIntent) {
        when (intent) {
            ExploreIntent.Resume -> {
                places.refreshLocation(locationOwner)
                places.recheck()
            }

            ExploreIntent.Pause -> places.stopLocation(locationOwner)

            is ExploreIntent.ViewportChanged -> visibleBounds.value = intent.bounds

            ExploreIntent.LocationPermissionChanged -> places.refreshLocation(locationOwner)

            else -> if (places.canInteract()) handleInteraction(intent)
        }
    }

    fun canInteract(): Boolean = places.canInteract()

    private fun handleInteraction(intent: ExploreIntent) {
        when (intent) {
            is ExploreIntent.SetQuery -> updateSelection { copy(query = intent.query) }

            is ExploreIntent.SelectPlace -> updateSelection { copy(selectedId = intent.id) }

            is ExploreIntent.SelectRegion -> updateSelection {
                copy(region = intent.id.takeUnless { it == region }, bounds = null)
            }

            ExploreIntent.ClearFilters -> updateSelection { copy(region = null, bounds = null) }

            ExploreIntent.SearchVisibleArea -> updateSelection { copy(region = null, bounds = visibleBounds.value) }

            ExploreIntent.ToggleList -> updateSelection { copy(listOnly = !listOnly) }

            is ExploreIntent.RequestNavigation -> requestNavigation(intent.placeId, intent.hasDestination)

            ExploreIntent.ConfirmWaypoint -> confirmWaypoint()

            ExploreIntent.DismissWaypoint -> updateSelection { copy(waypointId = null) }

            ExploreIntent.RequestLocationPermission -> effects.trySend(ExploreEffect.RequestLocationPermission)

            ExploreIntent.RetryCatalog -> places.retryCatalog()

            ExploreIntent.RecheckRoute -> places.recheck()

            ExploreIntent.Back -> effects.trySend(ExploreEffect.Back)

            else -> Unit
        }
    }

    private fun requestNavigation(id: String, hasDestination: Boolean?) {
        if (hasDestination == null || places.navigationState.value.hasDestination != hasDestination) {
            places.recheck()
            return
        }
        val place = places.places.value.firstOrNull { it.id == id } ?: return
        if (hasDestination) {
            updateSelection { copy(waypointId = id) }
        } else {
            places.request(place)
        }
    }

    private fun confirmWaypoint() {
        val id = selection.value.waypointId
        updateSelection { copy(waypointId = null) }
        val place = places.places.value.firstOrNull { it.id == id } ?: return
        if (places.navigationState.value.hasDestination == true) places.request(place)
    }

    private fun updateSelection(reduce: ExploreSelection.() -> ExploreSelection) {
        val next = selection.value.reduce()
        savedStateHandle[QUERY] = next.query
        savedStateHandle[SELECTED] = next.selectedId
        savedStateHandle[WAYPOINT] = next.waypointId
        savedStateHandle[LIST_ONLY] = next.listOnly
        savedStateHandle[REGION] = next.region
        savedStateHandle[BOUNDS] = next.bounds?.let { doubleArrayOf(it.south, it.west, it.north, it.east) }
        selection.value = next
    }

    private fun restoreSelection(): ExploreSelection {
        val coordinates = savedStateHandle.get<DoubleArray>(BOUNDS)
        return ExploreSelection(
            query = savedStateHandle[QUERY] ?: "",
            selectedId = savedStateHandle[SELECTED],
            waypointId = savedStateHandle[WAYPOINT],
            listOnly = savedStateHandle[LIST_ONLY] ?: false,
            region = savedStateHandle[REGION],
            bounds = coordinates?.takeIf { it.size == 4 }?.let { PlaceMapBounds(it[0], it[1], it[2], it[3]) },
        )
    }

    override fun onCleared() {
        places.stopLocation(locationOwner)
        session.close()
        effects.close()
    }
}

private data class ExploreSelection(
    val query: String = "",
    val selectedId: String? = null,
    val waypointId: String? = null,
    val listOnly: Boolean = false,
    val region: String? = null,
    val bounds: PlaceMapBounds? = null,
)

private data class ExploreSource(
    val catalog: List<TravelPlace>,
    val error: Boolean,
    val location: PlaceLocationState,
    val navigation: PlaceNavigationState,
)

private fun buildState(
    source: ExploreSource,
    selection: ExploreSelection,
    visibleBounds: PlaceMapBounds?,
): ExploreState {
    val position = (source.location as? PlaceLocationState.Available)?.position
    val matches = searchTravelPlaces(selection.query, selection.bounds, source.catalog).filter {
        selection.region == null || it.regionId == selection.region
    }
    val sorted = if (position == null) {
        matches
    } else {
        matches.sortedBy {
            position.distanceMetersTo(PlaceMapPosition(it.latitude, it.longitude))
        }
    }
    return ExploreState(
        catalog = source.catalog,
        places = sorted,
        catalogError = source.error,
        location = source.location,
        navigation = source.navigation,
        query = selection.query,
        selectedPlaceId = selection.selectedId,
        waypointPlaceId = selection.waypointId,
        listOnly = selection.listOnly,
        region = selection.region,
        appliedBounds = selection.bounds,
        visibleBounds = visibleBounds,
    )
}

private const val QUERY = "explore_query"
private const val SELECTED = "explore_selected_place"
private const val WAYPOINT = "explore_waypoint_place"
private const val LIST_ONLY = "explore_list_only"
private const val REGION = "explore_region"
private const val BOUNDS = "explore_bounds"
