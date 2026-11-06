package io.issac.pleosandroid.feature.main.map

import io.issac.pleosandroid.core.domain.model.MotionAvailability
import io.issac.pleosandroid.core.domain.model.UxAvailability
import io.issac.pleosandroid.core.domain.model.VehicleGear
import io.issac.pleosandroid.core.domain.model.VehicleMotion
import io.issac.pleosandroid.core.domain.model.VehicleState

internal val VehicleState.allowsDrivingPlaceRecommendation: Boolean
    get() = gear == VehicleGear.Drive && motion.availability == MotionAvailability.Available &&
        motion.motion != VehicleMotion.Unknown && uxAvailability == UxAvailability.Available &&
        uxPolicy.connected && uxPolicy.known &&
        (!uxPolicy.contentLimited || (uxPolicy.maxCumulativeContentItems > 0 && uxPolicy.maxContentDepth > 0)) &&
        (!uxPolicy.stringLengthLimited || uxPolicy.maxRestrictedStringLength > 0)

internal fun recommendDrivingPlace(
    position: PlaceMapPosition,
    places: List<TravelPlace> = busanTravelPlaces,
): TravelPlace? {
    if (!validCoordinates(position.latitude, position.longitude)) return null

    return places.asSequence()
        .filter { validCoordinates(it.latitude, it.longitude) }
        .map { it to position.distanceMetersTo(PlaceMapPosition(it.latitude, it.longitude)) }
        .filter { (_, distance) -> distance <= RECOMMENDATION_RADIUS_METERS }
        .minByOrNull { (_, distance) -> distance }
        ?.first
}

private fun validCoordinates(latitude: Double, longitude: Double): Boolean =
    latitude in -90.0..90.0 && longitude in -180.0..180.0

private const val RECOMMENDATION_RADIUS_METERS = 10_000.0
