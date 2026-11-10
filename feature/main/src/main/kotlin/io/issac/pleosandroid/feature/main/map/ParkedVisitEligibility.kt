package io.issac.pleosandroid.feature.main.map

import io.issac.pleosandroid.core.domain.collection.repository.VisitLocation
import io.issac.pleosandroid.core.domain.driving.repository.JourneySession

internal data class VisitArrival(val journeyId: String, val placeId: String, val arrivedAtNanos: Long)

internal data class VisitRouteEvidence(val activePlaceIds: Set<String>, val arrival: VisitArrival? = null) {
    fun contains(placeId: String, journeyId: String, nowNanos: Long): Boolean = placeId in activePlaceIds || (
        arrival?.placeId == placeId && arrival.journeyId == journeyId &&
            nowNanos - arrival.arrivedAtNanos in 0..ARRIVAL_VALIDITY_NANOS
        )
}

/** Keep the more precise fresh fix while the car remains at the same stop. */
internal fun chooseParkedVisitFix(
    previous: VisitLocation?,
    next: VisitLocation?,
    parkedAtNanos: Long,
    nowNanos: Long,
): VisitLocation? {
    if (next == null) return null
    return sequenceOf(next, previous).filterNotNull().filter { it.isUsable(parkedAtNanos, nowNanos) }
        .minWithOrNull(compareBy<VisitLocation> { it.accuracyMeters }.thenByDescending { it.elapsedNanos })
}

internal fun selectParkedVisitPlace(
    session: JourneySession,
    fix: VisitLocation?,
    route: VisitRouteEvidence,
    catalog: List<TravelPlace>,
    nowNanos: Long,
): TravelPlace? {
    val parkedAt = session.pausedAtNanos ?: return null
    if (!session.paused || session.recoveryRequired || fix?.isUsable(parkedAt, nowNanos) != true) return null
    val position = PlaceMapPosition(fix.latitude, fix.longitude)
    return catalog.asSequence().filter { route.contains(it.id, session.id, nowNanos) }.map { place ->
        place to position.distanceMetersTo(PlaceMapPosition(place.latitude, place.longitude))
    }.filter { (place, distance) -> distance + fix.accuracyMeters <= place.visitRadiusMeters }
        .minByOrNull { it.second }?.first
}

private fun VisitLocation.isUsable(parkedAtNanos: Long, nowNanos: Long): Boolean =
    elapsedNanos >= parkedAtNanos && nowNanos - elapsedNanos in 0..MAX_VISIT_FIX_AGE_NANOS &&
        accuracyMeters in 0.0..100.0 && latitude in -90.0..90.0 && longitude in -180.0..180.0

internal const val MAX_VISIT_FIX_AGE_NANOS = 10_000_000_000L
private const val ARRIVAL_VALIDITY_NANOS = 5 * 60 * 1_000_000_000L
