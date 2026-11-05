package io.issac.pleosandroid.feature.main

import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.padding
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Modifier
import androidx.hilt.lifecycle.viewmodel.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import io.issac.pleosandroid.core.navigation.BadgesRoute
import io.issac.pleosandroid.core.navigation.LocalNavigationRouter

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
    )
}

@Composable
internal fun JourneyContent(
    uiState: MainUiState,
    viewModel: MainViewModel,
    onOpenBadges: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val speedPermission = rememberLauncherForActivityResult(ActivityResultContracts.RequestPermission()) {
        viewModel.retryVehicle()
    }
    MainScreen(
        uiState = uiState,
        onStartJourney = { viewModel.startJourney() },
        onEndJourney = { viewModel.endJourney() },
        onOpenBadges = onOpenBadges,
        onRequestSpeedPermission = {
            if (viewModel.canRecoverVehicle()) speedPermission.launch(VEHICLE_SPEED_PERMISSION)
        },
        onRetryVehicle = { if (viewModel.canRecoverVehicle()) viewModel.retryVehicle() },
        modifier = modifier,
    )
}

private const val VEHICLE_SPEED_PERMISSION = "android.car.permission.CAR_SPEED"
