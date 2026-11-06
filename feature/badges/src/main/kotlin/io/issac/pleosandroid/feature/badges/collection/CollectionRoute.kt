package io.issac.pleosandroid.feature.badges.collection

import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.ui.Modifier
import androidx.hilt.lifecycle.viewmodel.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import io.issac.pleosandroid.core.navigation.AppRoute
import io.issac.pleosandroid.core.navigation.SafetyCollectionRoute
import io.issac.pleosandroid.feature.badges.component.CollectionStatusScreen
import io.issac.pleosandroid.feature.badges.inbox.CollectionInbox
import io.issac.pleosandroid.feature.badges.model.CardKind
import io.issac.pleosandroid.feature.badges.navigation.CollectCollectionEffects
import io.issac.pleosandroid.feature.badges.navigation.RevealCardRoute
import io.issac.pleosandroid.feature.badges.navigation.UnopenedCardsRoute
import io.issac.pleosandroid.feature.badges.navigation.collectionNavigationViewModel

@Composable
internal fun CollectionRoute(route: AppRoute, modifier: Modifier, viewModel: CollectionViewModel = hiltViewModel()) {
    val state by viewModel.state.collectAsStateWithLifecycle()
    LaunchedEffect(route) {
        if (route == SafetyCollectionRoute) {
            viewModel.handleIntent(
                CollectionIntent.FilterChanged(CollectionFilter(kind = CardKind.Safety)),
            )
        }
    }
    val navigation = collectionNavigationViewModel()
    val target by navigation.shelfTarget.collectAsStateWithLifecycle()
    CollectCollectionEffects(viewModel.effect, route)
    if (state.loading || state.error != null) {
        CollectionStatusScreen(
            state.error ?: "카드를 불러오고 있어요",
            modifier,
            onRetry = if (state.error != null) {
                { viewModel.handleIntent(CollectionIntent.Retry) }
            } else {
                null
            },
        )
        return
    }
    if (route == UnopenedCardsRoute) {
        CollectionInbox(
            state.cards.filter { it.pending },
            onBack = { viewModel.handleIntent(CollectionIntent.Back) },
            onSelect = { viewModel.handleIntent(CollectionIntent.Open(RevealCardRoute(it))) },
            modifier = modifier,
            onRestoreSamples = if (state.demoEnabled && !state.busy) {
                { viewModel.handleIntent(CollectionIntent.ResetDemo) }
            } else {
                null
            },
        )
    } else {
        CollectionScreen(
            state.cards, state.representativeId, { viewModel.handleIntent(CollectionIntent.Open(it)) }, modifier,
            canRevisitReveals = state.demoEnabled,
            shelfTarget = target,
            onShelfTargetHandled = navigation::targetHandled,
            selectedFilter = state.filter,
            onFilterChanged = { viewModel.handleIntent(CollectionIntent.FilterChanged(it)) },
            places = state.places,
            demoLabel = when {
                !state.demoAvailable -> null
                state.busy -> "카드를 준비하고 있어요"
                state.demoEnabled -> "데모 체험 중 · 내 카드로 돌아가기"
                else -> "데모 카드 체험"
            },
            onDemoClick = { viewModel.handleIntent(CollectionIntent.ToggleDemo) },
        )
    }
}
