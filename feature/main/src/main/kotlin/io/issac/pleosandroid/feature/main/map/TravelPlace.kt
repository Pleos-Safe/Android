package io.issac.pleosandroid.feature.main.map

import kotlin.math.asin
import kotlin.math.cos
import kotlin.math.sin
import kotlin.math.sqrt

internal data class TravelPlace(
    val id: String,
    val name: String,
    val latitude: Double,
    val longitude: Double,
    val district: String,
    val category: String,
    val imageKey: String = "",
    val collected: Boolean = false,
    val regionId: String? = null,
    val visitRadiusMeters: Double = 0.0,
)

internal const val GENERIC_PLACE_IMAGE = "collection_map_pin"

internal data class PlaceMapPosition(val latitude: Double, val longitude: Double) {
    fun distanceMetersTo(other: PlaceMapPosition): Double {
        val fromLatitude = Math.toRadians(latitude)
        val toLatitude = Math.toRadians(other.latitude)
        val latitudeTerm = sin((toLatitude - fromLatitude) / 2)
        val longitudeTerm = sin(Math.toRadians(other.longitude - longitude) / 2)
        val haversine = latitudeTerm * latitudeTerm +
            cos(fromLatitude) * cos(toLatitude) * longitudeTerm * longitudeTerm
        return 2 * EARTH_RADIUS_METERS * asin(sqrt(haversine.coerceIn(0.0, 1.0)))
    }
}

internal data class PlaceMapBounds(val south: Double, val west: Double, val north: Double, val east: Double) {
    fun contains(place: TravelPlace): Boolean {
        val longitudeInBounds = if (west <= east) {
            place.longitude in west..east
        } else {
            place.longitude >= west || place.longitude <= east
        }
        return place.latitude in south..north && longitudeInBounds
    }
}

internal fun searchTravelPlaces(query: String, bounds: PlaceMapBounds?, catalog: List<TravelPlace>): List<TravelPlace> {
    val terms = query.trim().split(Regex("\\s+")).filter(String::isNotEmpty)
    return catalog.filter { place ->
        val searchable = "${place.name} ${place.district} ${place.category}"
        (bounds == null || bounds.contains(place)) && terms.all { searchable.contains(it, ignoreCase = true) }
    }
}

private const val EARTH_RADIUS_METERS = 6_371_000.0
