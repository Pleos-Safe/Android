package io.issac.pleosandroid.core.domain.collection.model

import io.issac.pleosandroid.core.domain.cards.PackType

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
    val rewardPack: PackType? = null,
    val cardPrintId: String? = null,
)

data class CatalogPack(val version: Int, val places: List<Place>)

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
