package io.issac.pleosandroid.feature.badges.guide

import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Modifier
import androidx.hilt.lifecycle.viewmodel.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import io.issac.pleosandroid.core.designsystem.component.PleosTextLink
import io.issac.pleosandroid.core.navigation.AppRoute
import io.issac.pleosandroid.core.navigation.RecordsRoute
import io.issac.pleosandroid.feature.badges.component.Caption
import io.issac.pleosandroid.feature.badges.component.CollectionBack
import io.issac.pleosandroid.feature.badges.component.CollectionPage
import io.issac.pleosandroid.feature.badges.component.CollectionStatusScreen
import io.issac.pleosandroid.feature.badges.component.CollectionTitle
import io.issac.pleosandroid.feature.badges.model.CardKind
import io.issac.pleosandroid.feature.badges.navigation.CollectCollectionEffects
import io.issac.pleosandroid.feature.badges.navigation.CollectedPlaceRoute
import io.issac.pleosandroid.feature.badges.navigation.CollectionGuideRoute

@Composable
internal fun CollectionGuideDestination(
    route: AppRoute,
    modifier: Modifier,
    viewModel: CollectionGuideViewModel = hiltViewModel(),
) {
    val state by viewModel.state.collectAsStateWithLifecycle()
    CollectCollectionEffects(viewModel.effect, route)
    val back = { viewModel.handleIntent(CollectionGuideIntent.Back) }
    if (state.loading || state.error != null) {
        CollectionStatusScreen(
            state.error ?: "장소를 불러오고 있어요",
            modifier,
            back,
            if (state.error != null) {
                { viewModel.handleIntent(CollectionGuideIntent.Retry) }
            } else {
                null
            },
        )
    } else {
        when (route) {
            is CollectedPlaceRoute -> {
                val place = state.places.find { it.id == route.placeId }
                if (place != null) {
                    CollectedPlaceScreen(place, back, modifier)
                } else {
                    CollectionStatusScreen("이 장소를 찾을 수 없어요", modifier, back)
                }
            }

            is CollectionGuideRoute -> if (route.kind == CardKind.Safety) {
                CollectionGuideScreen(route.kind, back, {
                    viewModel.handleIntent(CollectionGuideIntent.Open(RecordsRoute))
                }, modifier)
            } else {
                CollectionPage(modifier) { window ->
                    CollectionBack("컬렉션으로", back)
                    CollectionTitle("카드를 만날 수 있는 장소", window.full)
                    Caption("방문 기록을 남기면 새로운 카드를 만날 수 있어요.")
                    state.places.forEach { place ->
                        PleosTextLink("${place.displayName} · ${place.district}", {
                            viewModel.handleIntent(CollectionGuideIntent.Open(CollectedPlaceRoute(place.id)))
                        })
                    }
                }
            }
        }
    }
}
