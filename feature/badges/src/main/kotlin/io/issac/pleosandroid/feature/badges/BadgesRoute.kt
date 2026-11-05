package io.issac.pleosandroid.feature.badges

import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.hilt.lifecycle.viewmodel.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import io.issac.pleosandroid.core.navigation.CardDetailRoute
import io.issac.pleosandroid.core.navigation.LocalNavigationRouter

@Composable
internal fun BadgesRouteContent(
    modifier: Modifier = Modifier,
    viewModel: CollectionAccessViewModel = hiltViewModel(),
) {
    val router = LocalNavigationRouter.current
    val vehicle by viewModel.vehicle.collectAsStateWithLifecycle()
    var filter by rememberSaveable { mutableStateOf<CardKind?>(null) }
    val onBack: () -> Unit = { router.pop() }

    if (vehicle.uxPolicy.restrictionsRequired) {
        CollectionRestrictedScreen(onBack = onBack, modifier = modifier)
    } else {
        CollectionScreen(
            cards = SampleCollection.cards,
            filter = filter,
            onFilterChange = { filter = it },
            onCardClick = { cardId ->
                if (!viewModel.vehicle.value.uxPolicy.restrictionsRequired) {
                    router.navigate(CardDetailRoute(cardId))
                }
            },
            onBack = onBack,
            modifier = modifier,
        )
    }
}

@Composable
internal fun CollectionRestrictedScreen(onBack: () -> Unit, modifier: Modifier = Modifier) {
    CollectionPage(
        title = stringResource(R.string.collection_restricted),
        onBack = onBack,
        modifier = modifier,
    ) {
        Text(stringResource(R.string.collection_restricted_message), color = CollectionColors.Muted)
    }
}
