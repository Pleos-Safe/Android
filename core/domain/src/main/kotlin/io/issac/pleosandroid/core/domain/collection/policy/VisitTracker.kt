package io.issac.pleosandroid.core.domain.collection.policy

import io.issac.pleosandroid.core.domain.collection.model.ConfirmedVisit
import io.issac.pleosandroid.core.domain.collection.model.Place
import io.issac.pleosandroid.core.domain.collection.repository.VisitLocation
import java.time.Instant
import kotlin.math.asin
import kotlin.math.cos
import kotlin.math.sin
import kotlin.math.sqrt

/** A continuous parked stay; every fix's uncertainty circle must fit inside the place. */
class VisitTracker {
    private data class Stay(
        val start: Long,
        val last: Long,
        val worstDistance: Double,
        val worstAccuracy: Double,
        val place: Place,
    )
    private val stays = mutableMapOf<String, Stay>()
    private val confirmed = mutableSetOf<String>()

    fun observe(
        journeyId: String,
        places: List<Place>,
        fix: VisitLocation?,
        nowNanos: Long,
        now: Instant,
        zoneId: String,
    ): List<ConfirmedVisit> {
        if (fix == null || fix.elapsedNanos <= 0 || nowNanos - fix.elapsedNanos !in 0..15_000_000_000L ||
            fix.latitude !in -90.0..90.0 || fix.longitude !in -180.0..180.0 ||
            !fix.accuracyMeters.isFinite() || fix.accuracyMeters < 0
        ) {
            stays.clear()
            return emptyList()
        }
        stays.keys.retainAll(places.filter { it.active }.map { it.id }.toSet())
        return places.filter { it.active && it.id !in confirmed }.mapNotNull { place ->
            val distance = distanceMeters(fix.latitude, fix.longitude, place.latitude, place.longitude)
            if (distance + fix.accuracyMeters > place.radiusMeters) {
                stays.remove(place.id)
                return@mapNotNull null
            }
            val previous = stays[place.id]?.takeIf { it.place == place }
            if (previous != null && fix.elapsedNanos <= previous.last) return@mapNotNull null
            val continuous = previous?.takeIf { fix.elapsedNanos - it.last <= 15_000_000_000L }
            val stay = Stay(
                continuous?.start ?: fix.elapsedNanos,
                fix.elapsedNanos,
                maxOf(continuous?.worstDistance ?: 0.0, distance),
                maxOf(continuous?.worstAccuracy ?: 0.0, fix.accuracyMeters),
                place,
            )
            stays[place.id] = stay
            if ((stay.last - stay.start) / 1_000_000_000 < place.minimumDwellSeconds ||
                stay.worstDistance + stay.worstAccuracy > place.radiusMeters
            ) {
                return@mapNotNull null
            }
            confirmed += place.id
            ConfirmedVisit(
                id = "visit:$journeyId:${place.id}", placeId = place.id, journeyId = journeyId,
                arrivedAt = now.minusNanos(nowNanos - stay.start),
                departedAt = now.minusNanos(nowNanos - stay.last), confirmedAt = now, zoneId = zoneId,
                distanceFromPlaceMeters = stay.worstDistance, accuracyMeters = stay.worstAccuracy,
            )
        }
    }

    fun breakContinuity() = stays.clear()
}

private fun distanceMeters(lat1: Double, lon1: Double, lat2: Double, lon2: Double): Double {
    val latitude = sin(Math.toRadians(lat2 - lat1) / 2)
    val longitude = sin(Math.toRadians(lon2 - lon1) / 2)
    val haversine = latitude * latitude + cos(Math.toRadians(lat1)) * cos(Math.toRadians(lat2)) * longitude * longitude
    return 12_742_000 * asin(sqrt(haversine.coerceIn(0.0, 1.0)))
}
