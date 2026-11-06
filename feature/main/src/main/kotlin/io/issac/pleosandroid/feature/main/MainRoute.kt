package io.issac.pleosandroid.feature.main

import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.padding
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Modifier
import androidx.hilt.lifecycle.viewmodel.compose.hiltViewModel
import androidx.lifecycle.compose.LifecycleResumeEffect
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import io.issac.pleosandroid.core.domain.policy.CarUxPolicy
import io.issac.pleosandroid.core.navigation.BadgesRoute
import io.issac.pleosandroid.core.navigation.LocalNavigationRouter
import io.issac.pleosandroid.core.navigation.TravelMapRoute
import io.issac.pleosandroid.feature.main.map.LocalPlaceMapViewModel
import io.issac.pleosandroid.feature.main.map.PlaceMapViewModel
import io.issac.pleosandroid.feature.main.map.allowsDrivingPlaceRecommendation

@Composable
internal fun MainRouteContent(contentPadding: PaddingValues) {
    val viewModel = LocalMainViewModel.current ?: hiltViewModel<MainViewModel>()
    val uiState by viewModel.uiState.collectAsStateWithLifecycle()
    val router = LocalNavigationRouter.current
    JourneyContent(
        uiState = uiState,
        viewModel = viewModel,
        onOpenBadges = { if (viewModel.canOpenBadges()) router.navigate(BadgesRoute) },
        modifier = Modifier.padding(contentPadding),
        onOpenMap = { router.navigate(TravelMapRoute) },
    )
}

@Composable
internal fun JourneyContent(
    uiState: MainUiState,
    viewModel: MainViewModel,
    onOpenBadges: () -> Unit,
    modifier: Modifier = Modifier,
    onOpenMap: (() -> Unit)? = null,
) {
    val speedPermission = rememberLauncherForActivityResult(ActivityResultContracts.RequestPermission()) {
        viewModel.retryVehicle()
    }
    MainScreen(
        uiState = uiState,
        onStartJourney = { viewModel.startJourney() },
        onEndJourney = { viewModel.endJourney() },
        onReturnHome = { viewModel.returnHome() },
        onOpenBadges = onOpenBadges,
        onRequestSpeedPermission = {
            if (viewModel.canRecoverVehicle()) speedPermission.launch(VEHICLE_SPEED_PERMISSION)
        },
        onRetryVehicle = { if (viewModel.canRecoverVehicle()) viewModel.retryVehicle() },
        modifier = modifier,
        onOpenMap = onOpenMap,
        drivingRecommendation = {
            if (uiState is MainUiState.Ready && uiState.vehicle.allowsDrivingPlaceRecommendation) {
                JourneyDrivingRecommendation(uiState.vehicle.uxPolicy, viewModel::canUseDrivingRecommendation)
            }
        },
    )
}

@Composable
private fun JourneyDrivingRecommendation(policy: CarUxPolicy, isAllowed: () -> Boolean) {
    val viewModel = LocalPlaceMapViewModel.current ?: hiltViewModel<PlaceMapViewModel>()
    val place by viewModel.drivingPlace.collectAsStateWithLifecycle()
    val navigation by viewModel.navigationState.collectAsStateWithLifecycle()
    LifecycleResumeEffect(viewModel) {
        viewModel.startDrivingRecommendation(isAllowed)
        onPauseOrDispose { viewModel.stopDrivingRecommendation() }
    }

    val placeName = place?.name?.let { name ->
        val limit = policy.maxRestrictedStringLength
        if (policy.stringLengthLimited && name.length > limit) {
            name.take((limit - 1).coerceAtLeast(0)) + "…"
        } else {
            name
        }
    }
    DrivingRecommendation(
        placeName = placeName,
        hasDestination = navigation.hasDestination,
        operation = navigation.operation,
        onRequest = {
            val selected = place
            val hasDestination = navigation.hasDestination
            if (selected != null && hasDestination != null) {
                viewModel.requestDrivingPlace(selected, hasDestination, isAllowed)
            }
        },
        onRecheck = { viewModel.recheckWhileDriving(isAllowed) },
    )
}

private const val VEHICLE_SPEED_PERMISSION = "android.car.permission.CAR_SPEED"
