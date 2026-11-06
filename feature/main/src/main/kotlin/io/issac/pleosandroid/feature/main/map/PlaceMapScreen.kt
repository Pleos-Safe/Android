package io.issac.pleosandroid.feature.main.map

import android.location.Location
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.Image
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.widthIn
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.saveable.rememberSaveableStateHolder
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalFocusManager
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import androidx.hilt.lifecycle.viewmodel.compose.hiltViewModel
import androidx.lifecycle.compose.LifecycleResumeEffect
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import io.issac.pleosandroid.core.designsystem.R as DesignR
import io.issac.pleosandroid.core.designsystem.component.PleosButton
import io.issac.pleosandroid.core.designsystem.component.PleosChip
import io.issac.pleosandroid.core.designsystem.component.PleosDialog
import io.issac.pleosandroid.core.designsystem.component.PleosInfoPanel
import io.issac.pleosandroid.core.designsystem.component.PleosScreen
import io.issac.pleosandroid.core.designsystem.component.PleosSearchField
import io.issac.pleosandroid.core.designsystem.component.PleosTextLink
import io.issac.pleosandroid.core.designsystem.theme.PleosSpacing
import io.issac.pleosandroid.core.designsystem.theme.PleosTextStyles
import io.issac.pleosandroid.core.designsystem.theme.PleosTheme
import io.issac.pleosandroid.core.navigation.LocalNavigationRouter
import io.issac.pleosandroid.feature.main.R

@Composable
internal fun PlaceMapRouteContent(modifier: Modifier = Modifier) {
    val viewModel = LocalPlaceMapViewModel.current ?: hiltViewModel<PlaceMapViewModel>()
    val router = LocalNavigationRouter.current
    val location by viewModel.locationState.collectAsStateWithLifecycle()
    val navigation by viewModel.navigationState.collectAsStateWithLifecycle()
    val permission = rememberLauncherForActivityResult(ActivityResultContracts.RequestPermission()) {
        viewModel.refreshLocation()
    }
    LifecycleResumeEffect(viewModel) {
        viewModel.refreshLocation()
        viewModel.recheck()
        onPauseOrDispose { viewModel.stopLocation() }
    }

    PlaceMapScreen(
        location = location,
        navigation = navigation,
        onRequestLocation = { if (viewModel.canInteract()) permission.launch(PLEOS_LOCATION_PERMISSION) },
        onRequestNavigation = viewModel::request,
        onRecheckRoute = viewModel::recheck,
        onBack = { router.pop() },
        modifier = modifier,
    )
}

@Composable
private fun PlaceMapScreen(
    location: PlaceLocationState,
    navigation: PlaceNavigationState,
    onRequestLocation: () -> Unit,
    onRequestNavigation: (TravelPlace) -> Unit,
    onRecheckRoute: () -> Unit,
    onBack: () -> Unit,
    modifier: Modifier = Modifier,
) {
    var query by rememberSaveable { mutableStateOf("") }
    var selectedId by rememberSaveable { mutableStateOf<String?>(null) }
    var waypointId by rememberSaveable { mutableStateOf<String?>(null) }
    var listOnly by rememberSaveable { mutableStateOf(false) }
    var regionCoordinates by rememberSaveable { mutableStateOf<DoubleArray?>(null) }
    var visibleBounds by remember { mutableStateOf<PlaceMapBounds?>(null) }
    val appliedBounds = regionCoordinates?.let { PlaceMapBounds(it[0], it[1], it[2], it[3]) }
    val position = (location as? PlaceLocationState.Available)?.position
    val places = remember(query, appliedBounds, position) {
        val matches = searchTravelPlaces(query, appliedBounds)
        if (position == null) matches else matches.sortedBy { it.distanceMetersFrom(position) }
    }
    val selected = places.firstOrNull { it.id == selectedId }
    val mapState = rememberSaveableStateHolder()
    val focus = LocalFocusManager.current

    PleosScreen(title = stringResource(R.string.place_map_title), modifier = modifier) {
        PleosTextLink(stringResource(R.string.place_map_back), onBack)
        PleosSearchField(
            value = query,
            onValueChange = { query = it },
            onSearch = { focus.clearFocus() },
            modifier = Modifier.fillMaxWidth(),
        )
        FlowRow(
            horizontalArrangement = Arrangement.spacedBy(PleosSpacing.space8),
            verticalArrangement = Arrangement.spacedBy(PleosSpacing.space8),
        ) {
            PleosChip(
                label = stringResource(R.string.place_map_all),
                selected = appliedBounds == null,
                onSelectedChange = { regionCoordinates = null },
            )
            if (visibleBounds != null) {
                PleosChip(
                    label = stringResource(R.string.place_map_this_area),
                    selected = appliedBounds == visibleBounds,
                    onSelectedChange = {
                        visibleBounds?.let { bounds ->
                            regionCoordinates = doubleArrayOf(bounds.south, bounds.west, bounds.north, bounds.east)
                        }
                    },
                )
            }
            PleosChip(
                label = stringResource(if (listOnly) R.string.place_map_show_map else R.string.place_map_show_list),
                selected = listOnly,
                onSelectedChange = { listOnly = it },
            )
        }
        if (location == PlaceLocationState.PermissionRequired) {
            PleosTextLink(stringResource(R.string.place_map_location_permission), onRequestLocation)
        } else if (location == PlaceLocationState.Unavailable) {
            Text(stringResource(R.string.place_map_location_unavailable), color = PleosTheme.colors.textSecondary)
        }

        val results: @Composable () -> Unit = {
            PlaceResults(
                places = places,
                selected = selected,
                navigation = navigation,
                currentPosition = position,
                onSelect = { selectedId = it },
                onNavigate = { place ->
                    if (navigation.hasDestination == true) waypointId = place.id else onRequestNavigation(place)
                },
                onRecheck = onRecheckRoute,
            )
        }
        BoxWithConstraints(Modifier.fillMaxWidth()) {
            val wide = maxWidth >= 920.dp && !listOnly
            val map: @Composable (Modifier) -> Unit = { mapModifier ->
                mapState.SaveableStateProvider("travel-map") {
                    PlaceMapViewport(
                        places = places,
                        selectedPlaceId = selected?.id,
                        onPlaceSelected = { selectedId = it },
                        modifier = mapModifier,
                        currentPosition = position,
                        onVisibleBoundsChanged = { visibleBounds = it },
                    )
                }
            }
            if (wide) {
                Row(horizontalArrangement = Arrangement.spacedBy(PleosSpacing.space16)) {
                    map(Modifier.weight(1f).height(460.dp))
                    Column(Modifier.widthIn(max = 320.dp)) { results() }
                }
            } else {
                Column(verticalArrangement = Arrangement.spacedBy(PleosSpacing.space16)) {
                    if (!listOnly) map(Modifier.fillMaxWidth().height(320.dp))
                    results()
                }
            }
        }
    }

    val waypoint = places.firstOrNull { it.id == waypointId }
    if (waypoint != null) {
        PleosDialog(
            title = stringResource(R.string.place_map_waypoint_confirmation),
            message = stringResource(R.string.place_map_waypoint_message, waypoint.name),
            confirmLabel = stringResource(R.string.place_map_add_waypoint),
            dismissLabel = stringResource(R.string.place_map_cancel),
            onConfirm = {
                waypointId = null
                if (navigation.hasDestination == true) onRequestNavigation(waypoint)
            },
            onDismissRequest = { waypointId = null },
        )
    }
}

@Composable
private fun PlaceResults(
    places: List<TravelPlace>,
    selected: TravelPlace?,
    navigation: PlaceNavigationState,
    currentPosition: PlaceMapPosition?,
    onSelect: (String) -> Unit,
    onNavigate: (TravelPlace) -> Unit,
    onRecheck: () -> Unit,
) {
    val status = when (navigation.operation) {
        PlaceNavigationOperation.Idle -> null
        PlaceNavigationOperation.Checking -> R.string.place_map_checking_route
        PlaceNavigationOperation.Requesting -> R.string.place_map_requesting
        PlaceNavigationOperation.Applied -> R.string.place_map_applied
        PlaceNavigationOperation.Rejected -> R.string.place_map_rejected
        PlaceNavigationOperation.Unconfirmed -> R.string.place_map_unconfirmed
        PlaceNavigationOperation.Unavailable -> R.string.place_map_navigation_unavailable
    }
    val actionEnabled = navigation.hasDestination != null && (
        navigation.operation == PlaceNavigationOperation.Idle ||
            navigation.operation == PlaceNavigationOperation.Applied ||
            navigation.operation == PlaceNavigationOperation.Rejected
        )
    Column(verticalArrangement = Arrangement.spacedBy(PleosSpacing.space8)) {
        if (selected != null) {
            PleosInfoPanel(Modifier.fillMaxWidth()) {
                if (selected.id == "haeundae") {
                    Image(
                        painter = painterResource(DesignR.drawable.milestone_art_haeundae),
                        contentDescription = null,
                        contentScale = ContentScale.Crop,
                        modifier = Modifier.fillMaxWidth().height(112.dp),
                    )
                }
                Text(selected.name, style = PleosTextStyles.titleSplit, color = PleosTheme.colors.textPrimary)
                Text(
                    stringResource(R.string.place_map_place_meta, selected.district, selected.category),
                    style = PleosTextStyles.meta,
                    color = PleosTheme.colors.textSecondary,
                )
                if (currentPosition != null) {
                    Text(
                        stringResource(
                            R.string.place_map_distance,
                            selected.distanceMetersFrom(currentPosition) / 1_000,
                        ),
                        style = PleosTextStyles.meta,
                        color = PleosTheme.colors.textSecondary,
                    )
                }
                PleosButton(
                    text = stringResource(
                        if (navigation.hasDestination == true) {
                            R.string.place_map_add_waypoint
                        } else {
                            R.string.place_map_set_destination
                        },
                    ),
                    onClick = { onNavigate(selected) },
                    enabled = actionEnabled,
                    showArrow = true,
                )
            }
        }
        if (status != null) {
            Text(stringResource(status), color = PleosTheme.colors.textSecondary)
        }
        if (navigation.operation == PlaceNavigationOperation.Unconfirmed ||
            navigation.operation == PlaceNavigationOperation.Unavailable
        ) {
            PleosTextLink(stringResource(R.string.place_map_check_route), onRecheck)
        }
        Text(
            text = stringResource(R.string.place_map_results, places.size),
            style = PleosTextStyles.section,
            color = PleosTheme.colors.textPrimary,
        )
        if (places.isEmpty()) {
            Text(stringResource(R.string.place_map_empty), color = PleosTheme.colors.textSecondary)
        }
        places.forEach { place ->
            PleosTextLink(place.name, onClick = { onSelect(place.id) }, modifier = Modifier.fillMaxWidth())
        }
    }
}

private fun TravelPlace.distanceMetersFrom(position: PlaceMapPosition): Float {
    val result = FloatArray(1)
    Location.distanceBetween(position.latitude, position.longitude, latitude, longitude, result)
    return result[0]
}
