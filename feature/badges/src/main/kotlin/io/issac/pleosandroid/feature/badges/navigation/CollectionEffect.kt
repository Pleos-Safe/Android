package io.issac.pleosandroid.feature.badges.navigation

import io.issac.pleosandroid.core.navigation.AppRoute
import io.issac.pleosandroid.core.ui.mvi.UiEffect

internal sealed interface CollectionEffect : UiEffect {
    data class Navigate(val route: AppRoute) : CollectionEffect
    data object Back : CollectionEffect
    data class ShowOnShelf(val cardId: String) : CollectionEffect
    data object RepresentativeSaved : CollectionEffect
}
