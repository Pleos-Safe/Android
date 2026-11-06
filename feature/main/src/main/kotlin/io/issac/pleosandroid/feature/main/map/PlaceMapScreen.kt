package io.issac.pleosandroid.feature.main.map

import android.location.Location
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.saveable.rememberSaveableStateHolder
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalFocusManager
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.heading
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.hilt.lifecycle.viewmodel.compose.hiltViewModel
import androidx.lifecycle.compose.LifecycleResumeEffect
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import io.issac.pleosandroid.core.designsystem.component.PleosBackLink
import io.issac.pleosandroid.core.designsystem.component.PleosButton
import io.issac.pleosandroid.core.designsystem.component.PleosChip
import io.issac.pleosandroid.core.designsystem.component.PleosDialog
import io.issac.pleosandroid.core.designsystem.component.PleosInfoPanel
import io.issac.pleosandroid.core.designsystem.component.PleosSearchField
import io.issac.pleosandroid.core.designsystem.component.PleosTextLink
import io.issac.pleosandroid.core.designsystem.component.pleosShellBackground
import io.issac.pleosandroid.core.designsystem.modifier.customClickable
import io.issac.pleosandroid.core.designsystem.theme.PleosLayout
import io.issac.pleosandroid.core.designsystem.theme.PleosShape
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
    val places by viewModel.places.collectAsStateWithLifecycle()
    val catalogError by viewModel.catalogError.collectAsStateWithLifecycle()
    val permission = rememberLauncherForActivityResult(ActivityResultContracts.RequestPermission()) {
        viewModel.refreshLocation()
    }
    LifecycleResumeEffect(viewModel) {
        viewModel.refreshLocation()
        viewModel.recheck()
        onPauseOrDispose { viewModel.stopLocation() }
    }

    PlaceMapScreen(
        catalog = places,
        catalogError = catalogError,
        onRetryCatalog = viewModel::retryCatalog, location = location,
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
    catalog: List<TravelPlace>,
    catalogError: Boolean,
    onRetryCatalog: () -> Unit,
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
    val places = remember(query, appliedBounds, position, catalog) {
        val matches = searchTravelPlaces(query, appliedBounds, catalog)
        if (position == null) matches else matches.sortedBy { it.distanceMetersFrom(position) }
    }
    val selected = places.firstOrNull { it.id == selectedId }
    val mapState = rememberSaveableStateHolder()
    val focus = LocalFocusManager.current

    BoxWithConstraints(modifier.fillMaxSize().pleosShellBackground()) {
        val scale = PleosTextStyles.scale
        val wide = maxWidth >= 1000.dp * scale
        val twoPane = wide && !listOnly
        val mapHeight = (maxHeight * .42f).coerceIn(240.dp * scale, 520.dp * scale)
        Column(
            Modifier.fillMaxSize()
                .then(if (twoPane) Modifier else Modifier.verticalScroll(rememberScrollState()))
                .padding(horizontal = PleosLayout.horizontalPadding(wide), vertical = PleosLayout.sectionGap),
            verticalArrangement = Arrangement.spacedBy(PleosLayout.itemGap),
        ) {
            Column(verticalArrangement = Arrangement.spacedBy(8.dp * scale)) {
                PleosBackLink(stringResource(R.string.place_map_back), onBack)
                Text(
                    stringResource(R.string.place_map_title),
                    Modifier.semantics { heading() },
                    style = if (wide) PleosTextStyles.titleFull else PleosTextStyles.titleSplit,
                    color = PleosTheme.colors.textPrimary,
                )
            }
            if (catalogError) {
                Text("장소 목록을 불러오지 못했어요.", color = PleosTheme.colors.textSecondary)
                PleosTextLink("다시 불러오기", onRetryCatalog)
            }
            PleosSearchField(
                value = query,
                onValueChange = { query = it },
                onSearch = { focus.clearFocus() },
                modifier = Modifier.fillMaxWidth(),
            )
            FlowRow(
                horizontalArrangement = Arrangement.spacedBy(8.dp * scale),
                verticalArrangement = Arrangement.spacedBy(8.dp * scale),
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
                    wideList = wide && listOnly,
                    onSelect = { selectedId = it },
                    onNavigate = { place ->
                        if (navigation.hasDestination == true) waypointId = place.id else onRequestNavigation(place)
                    },
                    onRecheck = onRecheckRoute,
                )
            }
            Box(
                Modifier.fillMaxWidth()
                    .then(if (twoPane) Modifier.weight(1f) else Modifier)
                    .padding(top = 8.dp * scale),
            ) {
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
                if (twoPane) {
                    Row(Modifier.fillMaxSize(), horizontalArrangement = Arrangement.spacedBy(PleosLayout.sectionGap)) {
                        map(Modifier.weight(1f).fillMaxHeight())
                        Column(
                            Modifier.width(340.dp * scale).fillMaxHeight()
                                .verticalScroll(rememberScrollState()),
                        ) { results() }
                    }
                } else {
                    Column(verticalArrangement = Arrangement.spacedBy(PleosLayout.sectionGap)) {
                        if (!listOnly) map(Modifier.fillMaxWidth().height(mapHeight))
                        results()
                    }
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
    wideList: Boolean,
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
    val selection: @Composable () -> Unit = {
        if (selected != null) {
            PleosInfoPanel(Modifier.fillMaxWidth()) {
                val context = LocalContext.current
                val imageId = remember(selected.imageKey, context) {
                    context.resources.getIdentifier(selected.imageKey, "drawable", context.packageName)
                }
                if (imageId != 0) {
                    Image(
                        painter = painterResource(imageId),
                        contentDescription = null,
                        contentScale = ContentScale.Crop,
                        modifier = Modifier.fillMaxWidth().height(
                            136.dp * PleosTextStyles.scale,
                        ).clip(PleosShape.button),
                    )
                }
                if (selected.collected) {
                    Text(
                        "획득한 장소 카드",
                        style = PleosTextStyles.meta,
                        color = PleosTheme.colors.actionPrimary,
                    )
                }
                Text(selected.name, style = PleosTextStyles.section, color = PleosTheme.colors.textPrimary)
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
                    modifier = Modifier.fillMaxWidth(),
                    enabled = actionEnabled,
                    showArrow = true,
                )
            }
        }
    }
    val results: @Composable () -> Unit = {
        Column(Modifier.fillMaxWidth(), verticalArrangement = Arrangement.spacedBy(PleosLayout.itemGap)) {
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
            places.chunked(if (wideList && selected == null) 2 else 1).forEach { row ->
                Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(PleosLayout.itemGap)) {
                    row.forEach { place ->
                        Box(Modifier.weight(1f)) {
                            PlaceResultRow(place, selected?.id == place.id, currentPosition, onClick = {
                                onSelect(place.id)
                            })
                        }
                    }
                    if (wideList && selected == null && row.size == 1) Spacer(Modifier.weight(1f))
                }
            }
        }
    }
    if (wideList && selected != null) {
        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(PleosLayout.columnGap)) {
            Box(Modifier.weight(.85f)) { selection() }
            Box(Modifier.weight(1.15f)) { results() }
        }
    } else {
        Column(Modifier.fillMaxWidth(), verticalArrangement = Arrangement.spacedBy(PleosLayout.itemGap)) {
            selection()
            results()
        }
    }
}

@Composable
private fun PlaceResultRow(place: TravelPlace, selected: Boolean, position: PlaceMapPosition?, onClick: () -> Unit) {
    val colors = PleosTheme.colors
    val scale = PleosTextStyles.scale
    val context = LocalContext.current
    val imageId = remember(place.imageKey, context) {
        context.resources.getIdentifier(place.imageKey, "drawable", context.packageName)
    }
    Row(
        Modifier.fillMaxWidth().customClickable(role = Role.Button, onClick = onClick)
            .background(if (selected) colors.chipSelected else colors.surface, PleosShape.button)
            .border(1.dp, if (selected) colors.actionPrimary else colors.borderDefault, PleosShape.button)
            .padding(PleosLayout.itemGap),
        horizontalArrangement = Arrangement.spacedBy(PleosLayout.itemGap),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        if (imageId != 0) {
            Image(
                painterResource(imageId),
                null,
                Modifier.size(64.dp * scale).clip(PleosShape.button),
                contentScale = ContentScale.Crop,
            )
        }
        Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(4.dp * scale)) {
            Text(
                place.name,
                style = PleosTextStyles.body,
                color = colors.textPrimary,
                maxLines = 2,
                overflow = TextOverflow.Ellipsis,
            )
            Text(
                "${place.district} · ${place.category}",
                style = PleosTextStyles.meta,
                color = colors.textSecondary,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
            )
            if (position != null) {
                Text(
                    stringResource(R.string.place_map_distance, place.distanceMetersFrom(position) / 1_000),
                    style = PleosTextStyles.meta,
                    color = if (selected) colors.actionPrimary else colors.textSecondary,
                )
            }
        }
    }
}

private fun TravelPlace.distanceMetersFrom(position: PlaceMapPosition): Float {
    val result = FloatArray(1)
    Location.distanceBetween(position.latitude, position.longitude, latitude, longitude, result)
    return result[0]
}
