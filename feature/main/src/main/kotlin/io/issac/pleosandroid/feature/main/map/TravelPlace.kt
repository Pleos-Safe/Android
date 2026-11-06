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
)

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

// Initial Busan catalog. Coordinates describe the selected place, not live vehicle positions.
internal val busanTravelPlaces = listOf(
    TravelPlace("haeundae", "해운대 해수욕장", 35.15855, 129.16053, "부산 해운대구", "해변"),
    TravelPlace("gwangalli", "광안리 해수욕장", 35.15089, 129.11910, "부산 수영구", "해변"),
    TravelPlace("cheongsapo", "청사포 정거장", 35.16129, 129.19137, "부산 해운대구", "명소"),
    TravelPlace("igidae", "이기대 해안산책로", 35.13145, 129.12042, "부산 남구", "산책"),
)

internal fun searchTravelPlaces(query: String, bounds: PlaceMapBounds?): List<TravelPlace> {
    val terms = query.trim().split(Regex("\\s+")).filter(String::isNotEmpty)
    return busanTravelPlaces.filter { place ->
        val searchable = "${place.name} ${place.district} ${place.category}"
        (bounds == null || bounds.contains(place)) && terms.all { searchable.contains(it, ignoreCase = true) }
    }
}

private const val EARTH_RADIUS_METERS = 6_371_000.0
