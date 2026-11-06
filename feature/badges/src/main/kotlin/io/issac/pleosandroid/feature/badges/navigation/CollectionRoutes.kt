package io.issac.pleosandroid.feature.badges.navigation

import io.issac.pleosandroid.core.navigation.AppRoute
import io.issac.pleosandroid.feature.badges.model.CardKind
import kotlinx.serialization.Serializable

@Serializable
internal data object UnopenedCardsRoute : AppRoute

@Serializable
internal data class RevealCardRoute(val cardId: String) : AppRoute

@Serializable
internal data class CardMemoriesRoute(val cardId: String) : AppRoute

@Serializable
internal data class RepresentativeCardRoute(val initialCardId: String?) : AppRoute

@Serializable
internal data class CollectionGuideRoute(val kind: CardKind) : AppRoute

@Serializable
internal data class CollectedPlaceRoute(val placeId: String) : AppRoute

@Serializable
internal data class CollectionMemoryRoute(val cardId: String, val memoryId: String) : AppRoute
