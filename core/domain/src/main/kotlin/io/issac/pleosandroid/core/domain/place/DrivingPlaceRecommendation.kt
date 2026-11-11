package io.issac.pleosandroid.core.domain.place

import io.issac.pleosandroid.core.domain.model.MotionAvailability
import io.issac.pleosandroid.core.domain.model.UxAvailability
import io.issac.pleosandroid.core.domain.model.VehicleGear
import io.issac.pleosandroid.core.domain.model.VehicleMotion
import io.issac.pleosandroid.core.domain.model.VehicleState

val VehicleState.allowsDrivingPlaceRecommendation: Boolean
    get() = gear == VehicleGear.Drive && motion.availability == MotionAvailability.Available &&
        motion.motion != VehicleMotion.Unknown && uxAvailability == UxAvailability.Available &&
        uxPolicy.connected && uxPolicy.known &&
        (!uxPolicy.contentLimited || (uxPolicy.maxCumulativeContentItems > 0 && uxPolicy.maxContentDepth > 0)) &&
        (!uxPolicy.stringLengthLimited || uxPolicy.maxRestrictedStringLength > 0)

fun recommendDrivingPlace(position: PlaceMapPosition, places: List<TravelPlace>): TravelPlace? {
    if (!validCoordinates(position.latitude, position.longitude)) return null

    return places.asSequence()
        .mapNotNull { place -> place.nearbyDistanceMeters(position)?.let { place to it } }
        .minByOrNull { (_, distance) -> distance }
        ?.first
}

fun TravelPlace.nearbyDistanceMeters(position: PlaceMapPosition): Double? {
    if (!validCoordinates(position.latitude, position.longitude) || !validCoordinates(latitude, longitude)) return null
    return position.distanceMetersTo(PlaceMapPosition(latitude, longitude))
        .takeIf { it <= RECOMMENDATION_RADIUS_METERS }
}

class DrivingRecommendationSession {
    private var journeyId: Long? = null
    private var current: TravelPlace? = null
    private val shown = mutableSetOf<String>()

    fun select(
        journeyId: Long,
        position: PlaceMapPosition?,
        route: PlaceNavigationState,
        places: List<TravelPlace>,
    ): TravelPlace? {
        if (this.journeyId != journeyId) {
            this.journeyId = journeyId
            current = null
            shown.clear()
        }
        if (position == null || route.hasDestination == null) return null
        if (!route.hasWaypointCapacity) {
            current = null
            return null
        }
        val previous = current?.let { old -> places.find { it.id == old.id } }
        if (previous != null && previous.id !in route.routePlaceIds &&
            previous.nearbyDistanceMeters(position) != null
        ) {
            return previous
        }
        return recommendDrivingPlace(
            position,
            places.filterNot {
                it.id in shown ||
                    it.id in route.routePlaceIds
            },
        )
            .also { place ->
                current = place
                if (place != null) shown += place.id
            }
    }
}

private fun validCoordinates(latitude: Double, longitude: Double): Boolean =
    latitude in -90.0..90.0 && longitude in -180.0..180.0

private const val RECOMMENDATION_RADIUS_METERS = 10_000.0
