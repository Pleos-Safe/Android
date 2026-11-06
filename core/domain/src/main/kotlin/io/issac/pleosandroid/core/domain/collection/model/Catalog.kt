package io.issac.pleosandroid.core.domain.collection.model

import java.time.Instant

enum class CardKind { Place, Safety }
enum class CardTemplate { Standard, Postcard }

data class Place(
    val id: String,
    val name: String,
    val district: String,
    val category: String,
    val latitude: Double,
    val longitude: Double,
    val radiusMeters: Double,
    val minimumDwellSeconds: Long,
    val imageKey: String,
    val active: Boolean = true,
    val regionId: String? = null,
)

data class CardFront(
    val title: String,
    val subtitle: String,
    val context: String,
    val series: String,
    val artworkKey: String,
    val glossKey: String,
    val engravingKey: String,
    val template: CardTemplate,
)

data class CardDefinition(
    val id: String,
    val kind: CardKind,
    val placeId: String?,
    val front: CardFront,
    val ruleVersion: Int,
    val active: Boolean = true,
)

/** A level needs this many distinct qualifying visits/journeys. Thresholds are product data. */
data class CardLevelRule(
    val cardId: String,
    val level: Int,
    val version: Int,
    val requiredCount: Int,
    val eligibleFrom: Instant,
    val minimumScore: Int? = null,
    val minimumDistanceMeters: Long = 0,
    val maximumHarshStarts: Int? = null,
)

data class CatalogPack(
    val version: Int,
    val places: List<Place>,
    val cards: List<CardDefinition>,
    val rules: List<CardLevelRule>,
)

data class PlaceQuery(val text: String = "", val bounds: GeoBounds? = null)
data class GeoBounds(val south: Double, val west: Double, val north: Double, val east: Double) {
    fun contains(place: Place): Boolean = place.latitude in south..north &&
        if (west <= east) place.longitude in west..east else place.longitude >= west || place.longitude <= east
}
data class PlaceSummary(val place: Place, val ownedCardIds: Set<String>)

sealed interface CatalogResult {
    data object Applied : CatalogResult
    data object Unchanged : CatalogResult
    data class Rejected(val reason: String) : CatalogResult
}
