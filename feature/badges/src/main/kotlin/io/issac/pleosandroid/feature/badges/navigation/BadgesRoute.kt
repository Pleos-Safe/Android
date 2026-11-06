package io.issac.pleosandroid.feature.badges.navigation

import androidx.activity.ComponentActivity
import androidx.activity.compose.LocalActivity
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.saveable.rememberSaveableStateHolder
import androidx.compose.ui.Modifier
import androidx.hilt.lifecycle.viewmodel.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import io.issac.pleosandroid.core.navigation.AppRoute
import io.issac.pleosandroid.core.navigation.BadgesRoute
import io.issac.pleosandroid.core.navigation.CardDetailRoute
import io.issac.pleosandroid.core.navigation.SafetyCollectionRoute
import io.issac.pleosandroid.feature.badges.collection.CollectionRoute
import io.issac.pleosandroid.feature.badges.component.CollectionStatusScreen
import io.issac.pleosandroid.feature.badges.detail.CardDetailDestination
import io.issac.pleosandroid.feature.badges.guide.CollectionGuideDestination
import io.issac.pleosandroid.feature.badges.representative.RepresentativeDestination
import io.issac.pleosandroid.feature.badges.reveal.CardRevealDestination

@Composable
internal fun collectionNavigationViewModel(): CollectionNavigationViewModel =
    hiltViewModel(checkNotNull(LocalActivity.current as? ComponentActivity))

@Composable
internal fun BadgesRouteContent(route: AppRoute, modifier: Modifier = Modifier) {
    val navigation = collectionNavigationViewModel()
    val vehicle by navigation.vehicle.collectAsStateWithLifecycle()
    val parkedContent = rememberSaveableStateHolder()
    if (!vehicle.allowsTabNavigation) {
        CollectionStatusScreen("주차 후에 카드를 만나보세요", modifier = modifier)
        return
    }
    parkedContent.SaveableStateProvider("parked") {
        when (route) {
            BadgesRoute, SafetyCollectionRoute, UnopenedCardsRoute -> CollectionRoute(route, modifier)
            is RevealCardRoute -> CardRevealDestination(route, modifier)
            is RepresentativeCardRoute -> RepresentativeDestination(route, modifier)
            is CardDetailRoute, is CardMemoriesRoute, is CollectionMemoryRoute -> CardDetailDestination(route, modifier)
            is CollectionGuideRoute, is CollectedPlaceRoute -> CollectionGuideDestination(route, modifier)
            else -> error("Unsupported collection route $route")
        }
    }
}
