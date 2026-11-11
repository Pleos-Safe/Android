package io.issac.pleosandroid.feature.home

import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.staticCompositionLocalOf
import androidx.compose.ui.Modifier
import androidx.hilt.lifecycle.viewmodel.compose.hiltViewModel
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.compose.LifecycleStartEffect
import androidx.lifecycle.compose.LocalLifecycleOwner
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.repeatOnLifecycle
import io.issac.pleosandroid.core.navigation.AppRoute
import io.issac.pleosandroid.feature.home.component.VisitPackRewardCard
import io.issac.pleosandroid.feature.home.driving.HomeDrivingOverview

internal val LocalHomeViewModel = staticCompositionLocalOf<HomeViewModel?> { null }
internal val LocalHomeCard = staticCompositionLocalOf<@Composable () -> Unit> { {} }

/** Retains the same home session when the host swaps between tabs and its driving surface. */
@Composable
fun HomeFeatureProvider(homeCard: @Composable () -> Unit, content: @Composable () -> Unit) {
    val viewModel = hiltViewModel<HomeViewModel>()
    CompositionLocalProvider(LocalHomeViewModel provides viewModel, LocalHomeCard provides homeCard, content = content)
}

@Composable
fun HomeRouteContent(
    modifier: Modifier = Modifier,
    onNavigate: (AppRoute) -> Unit = {},
    onOpenPack: (String) -> Unit = {},
) {
    val viewModel = LocalHomeViewModel.current ?: hiltViewModel<HomeViewModel>()
    val state by viewModel.state.collectAsStateWithLifecycle()
    HomeEffects(viewModel, onNavigate, onOpenPack)
    LifecycleStartEffect(viewModel) {
        viewModel.onIntent(HomeIntent.RefreshVisit)
        onStopOrDispose { }
    }
    HomeScreen(
        uiState = state.journey,
        homePlace = state.places.featured,
        onReturnHome = { viewModel.onIntent(HomeIntent.ReturnHome) },
        onOpenBadges = { viewModel.onIntent(HomeIntent.OpenCollection) },
        onRequestSpeedPermission = { viewModel.onIntent(HomeIntent.RequestSpeedPermission) },
        onRetryVehicle = { viewModel.onIntent(HomeIntent.RetryVehicle) },
        modifier = modifier,
        onOpenMap = { viewModel.onIntent(HomeIntent.OpenExplore) },
        onOpenRecords = { viewModel.onIntent(HomeIntent.OpenRecords) },
        onOpenReport = { viewModel.onIntent(HomeIntent.OpenReport(it)) },
        onFinishJourney = { viewModel.onIntent(HomeIntent.FinishJourney) },
        onContinueJourney = { viewModel.onIntent(HomeIntent.ContinueJourney) },
        visitReward = {
            VisitPackRewardCard(
                placeName = state.claimableVisit?.place?.name,
                result = state.visitResult,
                onClaim = state.claimableVisit?.let { visit -> { viewModel.onIntent(HomeIntent.ClaimVisit(visit)) } },
                onOpenPack = { viewModel.onIntent(HomeIntent.OpenReceivedPack(it)) },
            )
        },
        drivingContent = { isMoving, drivingModifier ->
            HomeDrivingOverview(state, isMoving, viewModel::onIntent, drivingModifier)
        },
    )
}

@Composable
private fun HomeEffects(viewModel: HomeViewModel, onNavigate: (AppRoute) -> Unit, onOpenPack: (String) -> Unit) {
    val lifecycleOwner = LocalLifecycleOwner.current
    val navigate by rememberUpdatedState(onNavigate)
    val openPack by rememberUpdatedState(onOpenPack)
    val permission = rememberLauncherForActivityResult(ActivityResultContracts.RequestPermission()) {
        viewModel.onIntent(HomeIntent.RetryVehicle)
    }
    LaunchedEffect(viewModel, lifecycleOwner) {
        lifecycleOwner.lifecycle.repeatOnLifecycle(Lifecycle.State.STARTED) {
            viewModel.effect.collect { effect ->
                when (effect) {
                    is HomeEffect.Navigate -> if (viewModel.canUseTabs()) navigate(effect.route)

                    is HomeEffect.OpenPack -> if (viewModel.canUseTabs()) openPack(effect.id)

                    HomeEffect.RequestSpeedPermission -> if (viewModel.canRecoverVehicle()) {
                        permission.launch(VEHICLE_SPEED_PERMISSION)
                    }
                }
            }
        }
    }
}

private const val VEHICLE_SPEED_PERMISSION = "android.car.permission.CAR_SPEED"
