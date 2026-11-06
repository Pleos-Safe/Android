package io.issac.pleosandroid.feature.badges.reveal

import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.ui.Modifier
import androidx.hilt.lifecycle.viewmodel.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import io.issac.pleosandroid.core.navigation.LocalNavigationRouter
import io.issac.pleosandroid.feature.badges.component.CollectionStatusScreen
import io.issac.pleosandroid.feature.badges.navigation.CollectCollectionEffects
import io.issac.pleosandroid.feature.badges.navigation.RevealCardRoute
import io.issac.pleosandroid.feature.badges.navigation.collectionNavigationViewModel

@Composable
internal fun CardRevealDestination(
    route: RevealCardRoute,
    modifier: Modifier,
    viewModel: CardRevealViewModel = hiltViewModel(),
) {
    val state by viewModel.state.collectAsStateWithLifecycle()
    val router = LocalNavigationRouter.current
    val navigation = collectionNavigationViewModel()
    CollectCollectionEffects(viewModel.effect, route)
    LaunchedEffect(route.cardId) { viewModel.handleIntent(CardRevealIntent.Load(route.cardId)) }
    val card = state.card
    if (state.loading || card == null) {
        CollectionStatusScreen(
            state.error ?: "카드를 준비하고 있어요",
            modifier,
            onBack = { viewModel.handleIntent(CardRevealIntent.Back) },
            onRetry = if (state.error !=
                null
            ) {
                { viewModel.handleIntent(CardRevealIntent.Load(route.cardId)) }
            } else {
                null
            },
        )
        return
    }
    CollectionRevealScreen(
        card,
        onBack = { viewModel.handleIntent(CardRevealIntent.Back) },
        onViewCollection = { viewModel.handleIntent(CardRevealIntent.ViewCollection) },
        onSetRepresentative = { viewModel.handleIntent(CardRevealIntent.SetRepresentative) },
        onLater = { viewModel.handleIntent(CardRevealIntent.Later) },
        onComplete = { viewModel.handleIntent(CardRevealIntent.AnimationCompleted(it)) },
        canOpen = {
            router.isActive(route) && navigation.vehicle.value.allowsTabNavigation &&
                !viewModel.state.value.saving
        },
        modifier = modifier, failedAttempt = state.failedAttempt, saveError = state.error,
    )
}
