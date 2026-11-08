package io.issac.pleosandroid.core.domain.collection.policy

import io.issac.pleosandroid.core.domain.collection.model.ConfirmedVisit
import io.issac.pleosandroid.core.domain.collection.model.Place
import java.time.Duration

fun Place.qualifies(visit: ConfirmedVisit): Boolean =
    active && visit.distanceFromPlaceMeters.isFinite() && visit.accuracyMeters.isFinite() &&
        visit.distanceFromPlaceMeters >= 0 && visit.accuracyMeters >= 0 &&
        visit.distanceFromPlaceMeters + visit.accuracyMeters <= radiusMeters &&
        !visit.departedAt.isBefore(visit.arrivedAt) && !visit.confirmedAt.isBefore(visit.departedAt) &&
        Duration.between(visit.arrivedAt, visit.departedAt).seconds >= minimumDwellSeconds
