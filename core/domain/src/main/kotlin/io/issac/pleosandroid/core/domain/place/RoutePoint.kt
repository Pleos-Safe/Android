package io.issac.pleosandroid.core.domain.place

data class RoutePoint(val latitude: Double, val longitude: Double, val name: String = "") {
    private val missingCoordinates: Boolean get() = latitude == 0.0 && longitude == 0.0

    val hasCoordinates: Boolean
        get() = !missingCoordinates && latitude in -90.0..90.0 && longitude in -180.0..180.0

    val valid: Boolean get() = hasCoordinates || (missingCoordinates && name.isNotBlank())

    fun matches(other: RoutePoint): Boolean {
        if (!valid || !other.valid) return false
        if (hasCoordinates && other.hasCoordinates) {
            val from = PlaceMapPosition(latitude, longitude)
            val to = PlaceMapPosition(other.latitude, other.longitude)
            return from.distanceMetersTo(to) <= LOCATION_TOLERANCE_METERS
        }

        // Pleos emulator waypoint readback can keep the name while reporting both coordinates as zero.
        val trimmedName = name.trim()
        return trimmedName.isNotEmpty() && trimmedName == other.name.trim()
    }
}

private const val LOCATION_TOLERANCE_METERS = 30.0
