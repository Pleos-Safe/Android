package io.issac.pleosandroid.core.navigation

import kotlinx.serialization.Serializable

@Serializable
data object MainRoute : AppRoute

@Serializable
data object BadgesRoute : AppRoute

@Serializable
data class CardDetailRoute(val cardId: String) : AppRoute
