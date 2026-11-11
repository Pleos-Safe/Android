package io.issac.pleosandroid.feature.explore

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.saveable.rememberSaveableStateHolder
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalFocusManager
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.heading
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import io.issac.pleosandroid.core.designsystem.component.PleosBackLink
import io.issac.pleosandroid.core.designsystem.component.PleosChip
import io.issac.pleosandroid.core.designsystem.component.PleosDialog
import io.issac.pleosandroid.core.designsystem.component.PleosSearchField
import io.issac.pleosandroid.core.designsystem.component.PleosTextLink
import io.issac.pleosandroid.core.designsystem.component.pleosShellBackground
import io.issac.pleosandroid.core.designsystem.theme.PleosLayout
import io.issac.pleosandroid.core.designsystem.theme.PleosTextStyles
import io.issac.pleosandroid.core.designsystem.theme.PleosTheme
import io.issac.pleosandroid.core.domain.place.PlaceLocationState
import io.issac.pleosandroid.core.domain.place.PlaceMapPosition
import io.issac.pleosandroid.feature.explore.map.PlaceMapViewport

@Composable
internal fun ExploreScreen(state: ExploreState, onIntent: (ExploreIntent) -> Unit, modifier: Modifier = Modifier) {
    BoxWithConstraints(modifier.fillMaxSize().pleosShellBackground()) {
        val scale = PleosTextStyles.scale
        val wide = maxWidth >= 1000.dp * scale
        val twoPane = wide && !state.listOnly
        val mapHeight = (maxHeight * .42f).coerceIn(240.dp * scale, 520.dp * scale)
        Column(
            Modifier.fillMaxSize()
                .then(if (twoPane) Modifier else Modifier.verticalScroll(rememberScrollState()))
                .padding(horizontal = PleosLayout.horizontalPadding(wide), vertical = PleosLayout.sectionGap),
            verticalArrangement = Arrangement.spacedBy(PleosLayout.itemGap),
        ) {
            ExploreHeading(wide, onBack = { onIntent(ExploreIntent.Back) })
            ExploreSearch(state, onIntent)
            ExploreLocationStatus(state.location, onIntent)
            ExploreMapAndResults(
                state = state,
                onIntent = onIntent,
                wide = wide,
                twoPane = twoPane,
                mapHeight = mapHeight,
                modifier = Modifier.fillMaxWidth()
                    .then(if (twoPane) Modifier.weight(1f) else Modifier)
                    .padding(top = 8.dp * scale),
            )
        }
    }
    ExploreWaypointConfirmation(state, onIntent)
}

@Composable
private fun ExploreHeading(wide: Boolean, onBack: () -> Unit) {
    Column(verticalArrangement = Arrangement.spacedBy(8.dp * PleosTextStyles.scale)) {
        PleosBackLink(stringResource(R.string.place_map_back), onBack)
        Text(
            stringResource(R.string.place_map_title),
            Modifier.semantics { heading() },
            style = if (wide) PleosTextStyles.titleFull else PleosTextStyles.titleSplit,
            color = PleosTheme.colors.textPrimary,
        )
    }
}

@Composable
private fun ExploreSearch(state: ExploreState, onIntent: (ExploreIntent) -> Unit) {
    if (state.catalogError) {
        Text("장소 목록을 불러오지 못했어요.", color = PleosTheme.colors.textSecondary)
        PleosTextLink("다시 불러오기", { onIntent(ExploreIntent.RetryCatalog) })
    }
    val focus = LocalFocusManager.current
    PleosSearchField(
        value = state.query,
        onValueChange = { onIntent(ExploreIntent.SetQuery(it)) },
        onSearch = { focus.clearFocus() },
        modifier = Modifier.fillMaxWidth(),
    )
    ExploreFilters(state, onIntent)
}

@Composable
private fun ExploreFilters(state: ExploreState, onIntent: (ExploreIntent) -> Unit) {
    FlowRow(
        horizontalArrangement = Arrangement.spacedBy(8.dp * PleosTextStyles.scale),
        verticalArrangement = Arrangement.spacedBy(8.dp * PleosTextStyles.scale),
    ) {
        PleosChip(
            label = stringResource(R.string.place_map_all),
            selected = state.appliedBounds == null && state.region == null,
            onSelectedChange = { onIntent(ExploreIntent.ClearFilters) },
        )
        PLACE_REGIONS.forEach { (id, label) ->
            if (state.catalog.any { it.regionId == id }) {
                PleosChip(label, selected = state.region == id, onSelectedChange = {
                    onIntent(ExploreIntent.SelectRegion(id))
                })
            }
        }
        if (state.visibleBounds != null) {
            PleosChip(
                label = stringResource(R.string.place_map_this_area),
                selected = state.appliedBounds == state.visibleBounds,
                onSelectedChange = { onIntent(ExploreIntent.SearchVisibleArea) },
            )
        }
        PleosChip(
            label = stringResource(if (state.listOnly) R.string.place_map_show_map else R.string.place_map_show_list),
            selected = state.listOnly,
            onSelectedChange = { onIntent(ExploreIntent.ToggleList) },
        )
    }
}

@Composable
private fun ExploreLocationStatus(location: PlaceLocationState, onIntent: (ExploreIntent) -> Unit) {
    when (location) {
        PlaceLocationState.PermissionRequired -> PleosTextLink(
            stringResource(R.string.place_map_location_permission),
            { onIntent(ExploreIntent.RequestLocationPermission) },
        )

        PlaceLocationState.Unavailable -> Text(
            stringResource(R.string.place_map_location_unavailable),
            color = PleosTheme.colors.textSecondary,
        )

        else -> Unit
    }
}

@Composable
private fun ExploreMapAndResults(
    state: ExploreState,
    onIntent: (ExploreIntent) -> Unit,
    wide: Boolean,
    twoPane: Boolean,
    mapHeight: Dp,
    modifier: Modifier,
) {
    val mapState = rememberSaveableStateHolder()
    val results: @Composable () -> Unit = {
        PlaceResults(
            places = state.places,
            selected = state.selected,
            navigation = state.navigation,
            currentPosition = state.position,
            wideList = wide && state.listOnly,
            onSelect = { onIntent(ExploreIntent.SelectPlace(it)) },
            onNavigate = { onIntent(ExploreIntent.RequestNavigation(it.id, state.navigation.hasDestination)) },
            onRecheck = { onIntent(ExploreIntent.RecheckRoute) },
        )
    }
    val map: @Composable (Modifier) -> Unit = { mapModifier ->
        mapState.SaveableStateProvider("travel-map") {
            PlaceMapViewport(
                places = state.places,
                selectedPlaceId = state.selected?.id,
                onPlaceSelected = { onIntent(ExploreIntent.SelectPlace(it)) },
                modifier = mapModifier,
                currentPosition = state.position,
                focusPosition = state.selected?.let { PlaceMapPosition(it.latitude, it.longitude) }
                    ?: state.places.firstOrNull { state.region != null }
                        ?.let { PlaceMapPosition(it.latitude, it.longitude) },
                onVisibleBoundsChanged = { onIntent(ExploreIntent.ViewportChanged(it)) },
            )
        }
    }
    Box(modifier) {
        if (twoPane) {
            Row(Modifier.fillMaxSize(), horizontalArrangement = Arrangement.spacedBy(PleosLayout.sectionGap)) {
                map(Modifier.weight(1f).fillMaxHeight())
                Column(
                    Modifier.width(
                        340.dp * PleosTextStyles.scale,
                    ).fillMaxHeight().verticalScroll(rememberScrollState()),
                ) { results() }
            }
        } else {
            Column(verticalArrangement = Arrangement.spacedBy(PleosLayout.sectionGap)) {
                if (!state.listOnly) map(Modifier.fillMaxWidth().height(mapHeight))
                results()
            }
        }
    }
}

@Composable
private fun ExploreWaypointConfirmation(state: ExploreState, onIntent: (ExploreIntent) -> Unit) {
    val waypoint = state.waypoint ?: return
    PleosDialog(
        title = stringResource(R.string.place_map_waypoint_confirmation),
        message = stringResource(R.string.place_map_waypoint_message, waypoint.name),
        confirmLabel = stringResource(R.string.place_map_add_waypoint),
        dismissLabel = stringResource(R.string.place_map_cancel),
        onConfirm = { onIntent(ExploreIntent.ConfirmWaypoint) },
        onDismissRequest = { onIntent(ExploreIntent.DismissWaypoint) },
    )
}

private val PLACE_REGIONS = listOf(
    "KR-11" to "서울",
    "KR-26" to "부산",
    "KR-41" to "경기",
    "KR-49" to "제주",
)
