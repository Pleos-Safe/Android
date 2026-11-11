package io.issac.pleosandroid.feature.home

import io.issac.pleosandroid.core.domain.collection.model.VisitPackClaimResult
import io.issac.pleosandroid.core.domain.place.ClaimableVisit
import io.issac.pleosandroid.core.domain.place.DrivingSafetyMessage
import io.issac.pleosandroid.core.domain.place.PlaceMapPosition
import io.issac.pleosandroid.core.domain.place.PlaceNavigationState
import io.issac.pleosandroid.core.domain.place.TravelPlace
import io.issac.pleosandroid.core.navigation.AppRoute
import io.issac.pleosandroid.core.ui.mvi.UiEffect
import io.issac.pleosandroid.core.ui.mvi.UiIntent
import io.issac.pleosandroid.core.ui.mvi.UiState

internal data class HomeState(
    val journey: HomeUiState = HomeUiState.Loading,
    val places: HomePlacesState = HomePlacesState(),
    val claimableVisit: ClaimableVisit? = null,
    val visitResult: VisitPackClaimResult? = null,
    val safetyMessage: DrivingSafetyMessage? = null,
) : UiState

internal data class HomePlacesState(
    val featured: HomePlace? = null,
    val recommendation: TravelPlace? = null,
    val navigation: PlaceNavigationState = PlaceNavigationState(),
    val position: PlaceMapPosition? = null,
    val collectedRegionIds: Set<String> = emptySet(),
)

internal sealed interface HomeIntent : UiIntent {
    data object ReturnHome : HomeIntent
    data object FinishJourney : HomeIntent
    data object ContinueJourney : HomeIntent
    data object OpenCollection : HomeIntent
    data object OpenExplore : HomeIntent
    data object OpenRecords : HomeIntent
    data class OpenReport(val id: String) : HomeIntent
    data object RequestSpeedPermission : HomeIntent
    data object RetryVehicle : HomeIntent
    data class ClaimVisit(val visit: ClaimableVisit) : HomeIntent
    data class OpenReceivedPack(val id: String) : HomeIntent
    data object RefreshVisit : HomeIntent
    data class StartDrivingOverview(val journeyId: Long, val owner: String) : HomeIntent
    data class StopDrivingOverview(val journeyId: Long, val owner: String) : HomeIntent
    data class RequestRecommendedPlace(val placeId: String, val hasDestination: Boolean) : HomeIntent
    data object RecheckNavigation : HomeIntent
}

internal sealed interface HomeEffect : UiEffect {
    data class Navigate(val route: AppRoute) : HomeEffect
    data class OpenPack(val id: String) : HomeEffect
    data object RequestSpeedPermission : HomeEffect
}
