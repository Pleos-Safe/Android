package io.issac.pleosandroid.feature.badges

import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.hilt.lifecycle.viewmodel.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import io.issac.pleosandroid.core.navigation.LocalNavigationRouter

@Composable
internal fun CardDetailRouteContent(
    cardId: String,
    modifier: Modifier = Modifier,
    viewModel: CollectionAccessViewModel = hiltViewModel(),
) {
    val router = LocalNavigationRouter.current
    val vehicle by viewModel.vehicle.collectAsStateWithLifecycle()
    val onBack: () -> Unit = { router.pop() }
    val card = SampleCollection.cards.find { it.id == cardId }

    when {
        vehicle.uxPolicy.restrictionsRequired ->
            CollectionRestrictedScreen(onBack = onBack, modifier = modifier)

        card == null -> CollectionPage(
            title = stringResource(R.string.card_detail_collection),
            onBack = onBack,
            modifier = modifier,
        ) {
            Text(stringResource(R.string.card_detail_unavailable), color = CollectionColors.Muted)
        }

        else -> CardDetailScreen(card = card, onBack = onBack, modifier = modifier)
    }
}
