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
import io.issac.pleosandroid.core.navigation.EnhancementRoute
import io.issac.pleosandroid.core.navigation.OpenPackRoute
import io.issac.pleosandroid.core.navigation.PackShelfRoute
import io.issac.pleosandroid.feature.badges.album.AlbumDestination
import io.issac.pleosandroid.feature.badges.album.AlbumDetailRoute
import io.issac.pleosandroid.feature.badges.album.PackOddsRoute
import io.issac.pleosandroid.feature.badges.album.WorkshopRoute
import io.issac.pleosandroid.feature.badges.component.CollectionStatusScreen

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
            BadgesRoute, EnhancementRoute, PackShelfRoute, PackOddsRoute,
            is AlbumDetailRoute, is OpenPackRoute, is WorkshopRoute,
            -> AlbumDestination(route, modifier)

            else -> error("Unsupported collection route $route")
        }
    }
}
