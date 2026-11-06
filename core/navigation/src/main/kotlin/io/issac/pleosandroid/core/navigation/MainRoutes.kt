package io.issac.pleosandroid.core.navigation

import kotlinx.serialization.Serializable

/** Roots of the three main tabs. Other routes open inside the current tab. */
sealed interface MainTabRoute : AppRoute

@Serializable
data object MainRoute : MainTabRoute

@Serializable
data object BadgesRoute : MainTabRoute

@Serializable
data object RecordsRoute : MainTabRoute

@Serializable
data object TravelMapRoute : AppRoute

@Serializable
data object SettingsRoute : AppRoute

@Serializable
data class CardDetailRoute(val cardId: String) : AppRoute
