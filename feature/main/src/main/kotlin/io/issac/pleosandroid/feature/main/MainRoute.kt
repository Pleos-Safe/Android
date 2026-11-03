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
import io.issac.pleosandroid.core.vehicle.permission.VehiclePermissions

@Composable
internal fun MainRouteContent(
    contentPadding: PaddingValues,
    viewModel: MainViewModel = hiltViewModel(),
) {
    val uiState by viewModel.uiState.collectAsStateWithLifecycle()
    val router = LocalNavigationRouter.current
    val speedPermission = rememberLauncherForActivityResult(ActivityResultContracts.RequestPermission()) {
        viewModel.retryVehicle()
    }

    MainScreen(
        uiState = uiState,
        onStartJourney = { viewModel.startJourney() },
        onEndJourney = { viewModel.endJourney() },
        onOpenBadges = {
            if (viewModel.canOpenBadges()) router.navigate(BadgesRoute)
        },
        onRequestSpeedPermission = {
            if (viewModel.canOpenBadges()) speedPermission.launch(VehiclePermissions.Speed)
        },
        onRetryVehicle = viewModel::retryVehicle,
        modifier = Modifier.padding(contentPadding),
    )
}
