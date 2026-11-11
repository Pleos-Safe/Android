package io.issac.pleosandroid.feature.home.driving

import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.lifecycle.compose.LifecycleStartEffect
import io.issac.pleosandroid.core.domain.place.PlaceMapPosition
import io.issac.pleosandroid.core.domain.place.PlaceNavigationOperation
import io.issac.pleosandroid.core.domain.place.allowsDrivingPlaceRecommendation
import io.issac.pleosandroid.core.domain.policy.CarUxPolicy
import io.issac.pleosandroid.feature.home.HomeIntent
import io.issac.pleosandroid.feature.home.HomeState
import io.issac.pleosandroid.feature.home.HomeUiState
import java.util.UUID

@Composable
internal fun HomeDrivingOverview(
    state: HomeState,
    isMoving: Boolean,
    onIntent: (HomeIntent) -> Unit,
    modifier: Modifier = Modifier,
) {
    val journey = state.journey as? HomeUiState.Ready ?: return
    val startedAt = journey.journeyStartedAtMillis ?: return
    val place = state.places.recommendation
    val navigation = state.places.navigation
    val policy = journey.vehicle.uxPolicy
    val canRecommend = journey.vehicle.allowsDrivingPlaceRecommendation
    val owner = remember { UUID.randomUUID().toString() }
    LifecycleStartEffect(startedAt, owner) {
        onIntent(HomeIntent.StartDrivingOverview(startedAt, owner))
        onStopOrDispose { onIntent(HomeIntent.StopDrivingOverview(startedAt, owner)) }
    }
    val distanceKm = place?.let {
        state.places.position?.distanceMetersTo(PlaceMapPosition(it.latitude, it.longitude))?.div(1_000)
    }
    DrivingOverviewScreen(
        isMoving = isMoving,
        modifier = modifier,
        progress = journey.progress,
        destination = navigation.destinationName?.let { policy.drivingLabel("$it 방면") },
        safetyMessage = state.safetyMessage.takeIf {
            isMoving && canRecommend && navigation.operation == PlaceNavigationOperation.Idle
        },
        map = { DrivingRegionMap(state.places.position, state.places.collectedRegionIds, isMoving, it) },
        recommendation = {
            if (canRecommend) {
                DrivingRecommendation(
                    placeName = place?.name?.let(policy::drivingLabel),
                    hasDestination = navigation.hasDestination,
                    operation = navigation.operation,
                    onRequest = {
                        val hasDestination = navigation.hasDestination
                        if (place != null && hasDestination != null) {
                            onIntent(HomeIntent.RequestRecommendedPlace(place.id, hasDestination))
                        }
                    },
                    onRecheck = { onIntent(HomeIntent.RecheckNavigation) },
                    distanceKm = distanceKm,
                )
            } else {
                NoDrivingRecommendation()
            }
        },
    )
}

private fun CarUxPolicy.drivingLabel(label: String): String? = when {
    !stringLengthLimited || label.length <= maxRestrictedStringLength -> label
    maxRestrictedStringLength <= 0 -> null
    else -> label.take(maxRestrictedStringLength - 1) + "…"
}
