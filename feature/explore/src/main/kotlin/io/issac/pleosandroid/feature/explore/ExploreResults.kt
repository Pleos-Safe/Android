package io.issac.pleosandroid.feature.explore

import android.location.Location
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalResources
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import io.issac.pleosandroid.core.designsystem.component.PleosButton
import io.issac.pleosandroid.core.designsystem.component.PleosInfoPanel
import io.issac.pleosandroid.core.designsystem.component.PleosTextLink
import io.issac.pleosandroid.core.designsystem.modifier.customClickable
import io.issac.pleosandroid.core.designsystem.theme.PleosLayout
import io.issac.pleosandroid.core.designsystem.theme.PleosShape
import io.issac.pleosandroid.core.designsystem.theme.PleosTextStyles
import io.issac.pleosandroid.core.designsystem.theme.PleosTheme
import io.issac.pleosandroid.core.domain.place.GENERIC_PLACE_IMAGE
import io.issac.pleosandroid.core.domain.place.PlaceMapPosition
import io.issac.pleosandroid.core.domain.place.PlaceNavigationOperation
import io.issac.pleosandroid.core.domain.place.PlaceNavigationState
import io.issac.pleosandroid.core.domain.place.TravelPlace

@Composable
internal fun PlaceResults(
    places: List<TravelPlace>,
    selected: TravelPlace?,
    navigation: PlaceNavigationState,
    currentPosition: PlaceMapPosition?,
    wideList: Boolean,
    onSelect: (String) -> Unit,
    onNavigate: (TravelPlace) -> Unit,
    onRecheck: () -> Unit,
) {
    val selection: @Composable () -> Unit = {
        if (selected != null) SelectedPlaceCard(selected, navigation, currentPosition, onNavigate)
    }
    val results: @Composable () -> Unit = {
        Column(Modifier.fillMaxWidth(), verticalArrangement = Arrangement.spacedBy(PleosLayout.itemGap)) {
            PlaceRouteStatus(navigation, onRecheck)
            Text(
                text = stringResource(R.string.place_map_results, places.size),
                style = PleosTextStyles.section,
                color = PleosTheme.colors.textPrimary,
            )
            if (places.isEmpty()) {
                Text(stringResource(R.string.place_map_empty), color = PleosTheme.colors.textSecondary)
            }
            PlaceResultRows(places, selected?.id, currentPosition, wideList && selected == null, onSelect)
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
private fun SelectedPlaceCard(
    place: TravelPlace,
    navigation: PlaceNavigationState,
    position: PlaceMapPosition?,
    onNavigate: (TravelPlace) -> Unit,
) {
    val actionEnabled = navigation.hasDestination != null && navigation.operation in setOf(
        PlaceNavigationOperation.Idle,
        PlaceNavigationOperation.Applied,
        PlaceNavigationOperation.Rejected,
    )
    PleosInfoPanel(Modifier.fillMaxWidth()) {
        val imageId = placeImageId(place.imageKey)
        if (imageId != 0 && place.imageKey != GENERIC_PLACE_IMAGE) {
            Image(
                painter = painterResource(imageId),
                contentDescription = null,
                contentScale = ContentScale.Crop,
                modifier = Modifier.fillMaxWidth().height(136.dp * PleosTextStyles.scale).clip(PleosShape.button),
            )
        }
        if (place.collected) {
            Text("획득한 장소 카드", style = PleosTextStyles.meta, color = PleosTheme.colors.actionPrimary)
        }
        Text(place.name, style = PleosTextStyles.section, color = PleosTheme.colors.textPrimary)
        Text(
            stringResource(R.string.place_map_place_meta, place.district, place.category),
            style = PleosTextStyles.meta,
            color = PleosTheme.colors.textSecondary,
        )
        if (position != null) {
            Text(
                stringResource(R.string.place_map_distance, place.distanceMetersFrom(position) / 1_000),
                style = PleosTextStyles.meta,
                color = PleosTheme.colors.textSecondary,
            )
        }
        Text(
            if (place.id in navigation.routePlaceIds) {
                "등록한 장소예요. 근처에 주차하면 방문 카드팩을 받을 수 있어요."
            } else {
                "내비에 등록하고 장소 근처에 주차하면 방문 카드팩을 받을 수 있어요."
            },
            style = PleosTextStyles.meta,
            color = PleosTheme.colors.textSecondary,
        )
        PleosButton(
            text = stringResource(
                if (navigation.hasDestination ==
                    true
                ) {
                    R.string.place_map_add_waypoint
                } else {
                    R.string.place_map_set_destination
                },
            ),
            onClick = { onNavigate(place) },
            modifier = Modifier.fillMaxWidth(),
            enabled = actionEnabled && place.id !in navigation.routePlaceIds && navigation.hasWaypointCapacity,
            showArrow = true,
        )
    }
}

@Composable
private fun PlaceRouteStatus(navigation: PlaceNavigationState, onRecheck: () -> Unit) {
    navigation.destinationName?.let { destination ->
        PleosInfoPanel(Modifier.fillMaxWidth()) {
            Text("이번 여정", style = PleosTextStyles.section, color = PleosTheme.colors.textPrimary)
            navigation.waypointNames.forEach { name ->
                Text("경유지 · $name", style = PleosTextStyles.meta, color = PleosTheme.colors.textSecondary)
            }
            Text("목적지 · $destination", style = PleosTextStyles.body, color = PleosTheme.colors.textPrimary)
        }
    }
    val status = when (navigation.operation) {
        PlaceNavigationOperation.Idle -> null
        PlaceNavigationOperation.Checking -> R.string.place_map_checking_route
        PlaceNavigationOperation.Requesting -> R.string.place_map_requesting
        PlaceNavigationOperation.Applied -> R.string.place_map_applied
        PlaceNavigationOperation.Rejected -> R.string.place_map_rejected
        PlaceNavigationOperation.Unconfirmed -> R.string.place_map_unconfirmed
        PlaceNavigationOperation.Unavailable -> R.string.place_map_navigation_unavailable
    }
    if (status != null) Text(stringResource(status), color = PleosTheme.colors.textSecondary)
    if (navigation.operation == PlaceNavigationOperation.Unconfirmed ||
        navigation.operation == PlaceNavigationOperation.Unavailable
    ) {
        PleosTextLink(stringResource(R.string.place_map_check_route), onRecheck)
    }
}

@Composable
private fun PlaceResultRows(
    places: List<TravelPlace>,
    selectedId: String?,
    position: PlaceMapPosition?,
    twoColumns: Boolean,
    onSelect: (String) -> Unit,
) {
    places.chunked(if (twoColumns) 2 else 1).forEach { row ->
        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(PleosLayout.itemGap)) {
            row.forEach { place ->
                Box(Modifier.weight(1f)) {
                    PlaceResultRow(place, selectedId == place.id, position, onClick = { onSelect(place.id) })
                }
            }
            if (twoColumns && row.size == 1) Spacer(Modifier.weight(1f))
        }
    }
}

@Composable
private fun PlaceResultRow(place: TravelPlace, selected: Boolean, position: PlaceMapPosition?, onClick: () -> Unit) {
    val colors = PleosTheme.colors
    val scale = PleosTextStyles.scale
    val imageId = placeImageId(place.imageKey)
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

@Composable
private fun placeImageId(imageKey: String): Int {
    val resources = LocalResources.current
    val packageName = LocalContext.current.packageName
    return remember(imageKey, resources, packageName) {
        resources.getIdentifier(imageKey, "drawable", packageName)
    }
}
