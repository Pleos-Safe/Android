package io.issac.pleosandroid.feature.main

import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Modifier
import androidx.hilt.lifecycle.viewmodel.compose.hiltViewModel
import androidx.lifecycle.compose.LifecycleStartEffect
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import io.issac.pleosandroid.core.designsystem.component.PleosButton
import io.issac.pleosandroid.core.designsystem.component.PleosInfoPanel
import io.issac.pleosandroid.core.designsystem.theme.PleosTextStyles
import io.issac.pleosandroid.core.designsystem.theme.PleosTheme
import io.issac.pleosandroid.core.domain.collection.model.VisitPackClaimResult
import io.issac.pleosandroid.core.domain.collection.model.VisitPackPolicy
import io.issac.pleosandroid.core.domain.policy.CarUxPolicy
import io.issac.pleosandroid.core.navigation.BadgesRoute
import io.issac.pleosandroid.core.navigation.DrivingReportRoute
import io.issac.pleosandroid.core.navigation.LocalNavigationRouter
import io.issac.pleosandroid.core.navigation.OpenPackRoute
import io.issac.pleosandroid.core.navigation.PackShelfRoute
import io.issac.pleosandroid.core.navigation.RecordsRoute
import io.issac.pleosandroid.core.navigation.TravelMapRoute
import io.issac.pleosandroid.feature.main.map.DrivingRegionMap
import io.issac.pleosandroid.feature.main.map.LocalPlaceMapViewModel
import io.issac.pleosandroid.feature.main.map.PlaceMapPosition
import io.issac.pleosandroid.feature.main.map.PlaceMapViewModel
import io.issac.pleosandroid.feature.main.map.PlaceNavigationOperation
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
        onOpenRecords = { router.navigate(RecordsRoute) },
        onOpenReport = { router.navigate(DrivingReportRoute(it)) },
    )
}

@Composable
internal fun JourneyContent(
    uiState: MainUiState,
    viewModel: MainViewModel,
    onOpenBadges: () -> Unit,
    modifier: Modifier = Modifier,
    onOpenMap: (() -> Unit)? = null,
    onOpenRecords: () -> Unit = {},
    onOpenReport: (String) -> Unit = {},
) {
    val places = LocalPlaceMapViewModel.current ?: hiltViewModel<PlaceMapViewModel>()
    val catalog by places.places.collectAsStateWithLifecycle()
    val homePlace = (catalog.firstOrNull { it.id == "haeundae" } ?: catalog.firstOrNull())
        ?.let { HomePlace(it.name, it.district, it.category, it.imageKey) }
    val speedPermission = rememberLauncherForActivityResult(ActivityResultContracts.RequestPermission()) {
        viewModel.retryVehicle()
    }
    MainScreen(
        uiState = uiState,
        homePlace = homePlace,
        onReturnHome = { viewModel.returnHome() },
        onOpenBadges = onOpenBadges,
        onRequestSpeedPermission = {
            if (viewModel.canRecoverVehicle()) speedPermission.launch(VEHICLE_SPEED_PERMISSION)
        },
        onRetryVehicle = { if (viewModel.canRecoverVehicle()) viewModel.retryVehicle() },
        modifier = modifier,
        onOpenMap = onOpenMap,
        onOpenRecords = onOpenRecords,
        onOpenReport = onOpenReport,
        onFinishJourney = viewModel::finishJourney,
        onContinueJourney = viewModel::continueJourney,
        visitReward = { ParkedVisitReward(places) },
        drivingContent = { isMoving, drivingModifier ->
            val ready = uiState as MainUiState.Ready
            JourneyDrivingOverview(
                isMoving = isMoving,
                journeyId = ready.journeyStartedAtMillis ?: 0L,
                progress = ready.progress,
                policy = ready.vehicle.uxPolicy,
                canRecommend = ready.vehicle.allowsDrivingPlaceRecommendation,
                isRequestAllowed = viewModel::canUseDrivingRecommendation,
                modifier = drivingModifier,
            )
        },
    )
}

@Composable
private fun ParkedVisitReward(viewModel: PlaceMapViewModel) {
    val candidate by viewModel.claimableVisit.collectAsStateWithLifecycle()
    val result by viewModel.visitResult.collectAsStateWithLifecycle()
    LifecycleStartEffect(viewModel) {
        viewModel.refreshParkedVisit()
        onStopOrDispose {}
    }
    val onClaim = candidate?.let { visit -> { viewModel.claimVisit(visit) } }
    val router = LocalNavigationRouter.current
    VisitPackRewardCard(candidate?.place?.name, result, onClaim) { id ->
        router.replaceAll(BadgesRoute, PackShelfRoute, OpenPackRoute(id))
    }
}

@Composable
fun VisitPackRewardCard(
    placeName: String?,
    result: VisitPackClaimResult?,
    onClaim: (() -> Unit)?,
    onOpenPack: ((String) -> Unit)? = null,
) {
    if (placeName == null && result == null) return
    val message = when (result) {
        is VisitPackClaimResult.Applied, is VisitPackClaimResult.AlreadyClaimed -> "방문 카드팩 1개를 받았어요"
        VisitPackClaimResult.DailyLimitReached -> "오늘 방문팩 ${VisitPackPolicy.DAILY_CLAIM_LIMIT}개를 모두 받았어요"
        VisitPackClaimResult.PlaceCooldown -> "이 장소의 방문 카드팩은 14일 뒤 다시 받을 수 있어요"
        VisitPackClaimResult.InvalidInput -> "주차와 현재 위치를 다시 확인해 주세요"
        VisitPackClaimResult.StaleGeneration, VisitPackClaimResult.StorageFailure -> "카드팩을 저장하지 못했어요. 다시 시도해 주세요"
        null -> "방문 보상을 받을 수 있어요"
    }
    val receipt = when (result) {
        is VisitPackClaimResult.Applied -> result.receipt
        is VisitPackClaimResult.AlreadyClaimed -> result.receipt
        else -> null
    }
    val displayName = receipt?.placeTitle ?: placeName
    PleosInfoPanel(Modifier.fillMaxWidth()) {
        Text(message, style = PleosTextStyles.section, color = PleosTheme.colors.textPrimary)
        if (displayName != null) {
            val detail = if (result == null && onClaim != null) {
                "$displayName · 하루 최대 ${VisitPackPolicy.DAILY_CLAIM_LIMIT}팩"
            } else {
                displayName
            }
            Text(detail, style = PleosTextStyles.body, color = PleosTheme.colors.textSecondary)
        }
        if (receipt != null && onOpenPack != null) {
            PleosButton("받은 팩 열어보기", { onOpenPack(receipt.albumPackId) }, Modifier.fillMaxWidth(), showArrow = true)
        } else if (onClaim != null && result !is VisitPackClaimResult.DailyLimitReached) {
            PleosButton("카드팩 받기", onClaim, modifier = Modifier.fillMaxWidth(), showArrow = true)
        }
    }
}

@Composable
private fun JourneyDrivingOverview(
    isMoving: Boolean,
    journeyId: Long,
    progress: JourneyProgressDisplay?,
    policy: CarUxPolicy,
    canRecommend: Boolean,
    isRequestAllowed: () -> Boolean,
    modifier: Modifier = Modifier,
) {
    val viewModel = LocalPlaceMapViewModel.current ?: hiltViewModel<PlaceMapViewModel>()
    val place by viewModel.drivingPlace.collectAsStateWithLifecycle()
    val navigation by viewModel.navigationState.collectAsStateWithLifecycle()
    val location by viewModel.drivingPosition.collectAsStateWithLifecycle()
    val badgeRegionIds by viewModel.badgeRegionIds.collectAsStateWithLifecycle(initialValue = emptySet())
    val safetyMessage by viewModel.drivingSafetyMessage.collectAsStateWithLifecycle()
    LifecycleStartEffect(viewModel, journeyId) {
        viewModel.startDrivingOverview(journeyId)
        onStopOrDispose { viewModel.stopDrivingOverview(journeyId) }
    }

    val placeName = place?.name?.let(policy::drivingLabel)
    val distanceKm = place?.let { selected ->
        location?.distanceMetersTo(PlaceMapPosition(selected.latitude, selected.longitude))?.div(1_000)
    }
    DrivingOverviewScreen(
        isMoving = isMoving,
        modifier = modifier,
        progress = progress,
        destination = navigation.destinationName?.let { policy.drivingLabel("$it 방면") },
        safetyMessage = safetyMessage.takeIf {
            isMoving && canRecommend && navigation.operation == PlaceNavigationOperation.Idle
        },
        map = { mapModifier ->
            DrivingRegionMap(location, badgeRegionIds, isMoving, mapModifier)
        },
        recommendation = {
            if (canRecommend) {
                DrivingRecommendation(
                    placeName = placeName,
                    hasDestination = navigation.hasDestination,
                    operation = navigation.operation,
                    onRequest = {
                        val selected = place
                        val hasDestination = navigation.hasDestination
                        if (selected != null && hasDestination != null) {
                            viewModel.requestDrivingPlace(selected, hasDestination, isRequestAllowed)
                        }
                    },
                    onRecheck = { viewModel.recheckWhileDriving(isRequestAllowed) },
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

private const val VEHICLE_SPEED_PERMISSION = "android.car.permission.CAR_SPEED"
