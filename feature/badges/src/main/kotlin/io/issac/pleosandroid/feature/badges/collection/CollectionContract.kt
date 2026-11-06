package io.issac.pleosandroid.feature.badges.collection

import io.issac.pleosandroid.core.navigation.AppRoute
import io.issac.pleosandroid.core.ui.mvi.UiIntent
import io.issac.pleosandroid.core.ui.mvi.UiState
import io.issac.pleosandroid.feature.badges.model.CollectionCard
import io.issac.pleosandroid.feature.badges.model.CollectionPlace

internal data class CollectionUiState(
    val loading: Boolean = true,
    val cards: List<CollectionCard> = emptyList(),
    val representativeId: String? = null,
    val places: List<CollectionPlace> = emptyList(),
    val filter: CollectionFilter = CollectionFilter(),
    val error: String? = null,
    val busy: Boolean = false,
    val demoAvailable: Boolean = false,
    val demoEnabled: Boolean = false,
) : UiState

internal sealed interface CollectionIntent : UiIntent {
    data object Retry : CollectionIntent
    data class Open(val route: AppRoute) : CollectionIntent
    data object Back : CollectionIntent
    data class FilterChanged(val filter: CollectionFilter) : CollectionIntent
    data object ToggleDemo : CollectionIntent
    data object ResetDemo : CollectionIntent
}
