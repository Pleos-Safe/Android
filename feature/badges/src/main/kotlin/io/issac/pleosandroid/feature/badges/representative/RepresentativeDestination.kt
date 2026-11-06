package io.issac.pleosandroid.feature.badges.representative

import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Modifier
import androidx.hilt.lifecycle.viewmodel.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import io.issac.pleosandroid.feature.badges.component.CollectionStatusScreen
import io.issac.pleosandroid.feature.badges.navigation.CollectCollectionEffects
import io.issac.pleosandroid.feature.badges.navigation.RepresentativeCardRoute

@Composable
internal fun RepresentativeDestination(
    route: RepresentativeCardRoute,
    modifier: Modifier,
    viewModel: RepresentativeViewModel = hiltViewModel(),
) {
    val state by viewModel.state.collectAsStateWithLifecycle()
    CollectCollectionEffects(viewModel.effect, route)
    if (state.loading || state.error != null) {
        CollectionStatusScreen(
            state.error ?: "카드를 불러오고 있어요",
            modifier,
            onBack = { viewModel.handleIntent(RepresentativeIntent.Back) },
            onRetry = if (state.error != null) {
                { viewModel.handleIntent(RepresentativeIntent.Retry) }
            } else {
                null
            },
        )
    } else {
        RepresentativeCardScreen(
            state.cards,
            state.currentId,
            route.initialCardId,
            onConfirm = { viewModel.handleIntent(RepresentativeIntent.Confirm(it)) },
            onBack = { viewModel.handleIntent(RepresentativeIntent.Back) },
            modifier = modifier,
            saving = state.saving,
        )
    }
}
