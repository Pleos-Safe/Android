package io.issac.pleosandroid.core.domain.collection

import io.issac.pleosandroid.core.domain.collection.model.ConfirmedVisit
import io.issac.pleosandroid.core.domain.collection.model.Place
import io.issac.pleosandroid.core.domain.collection.policy.qualifies
import java.time.Instant
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class VisitQualificationTest {
    private val start = Instant.parse("2026-01-01T00:00:00Z")
    private val place = Place("beach", "해변", "부산", "자연", 35.0, 129.0, 100.0, 60, "art")
    private fun visit(id: String = "visit") = ConfirmedVisit(
        id,
        place.id,
        arrivedAt = start,
        departedAt = start.plusSeconds(60),
        confirmedAt = start.plusSeconds(65),
        zoneId = "Asia/Seoul",
        distanceFromPlaceMeters = 80.0,
        accuracyMeters = 20.0,
    )

    @Test fun visitNeedsDwellAndAccuracyWithinRadius() {
        assertTrue(place.qualifies(visit()))
        assertFalse(place.qualifies(visit().copy(accuracyMeters = 21.0)))
        assertFalse(place.qualifies(visit().copy(departedAt = start.plusSeconds(59))))
        assertFalse(place.qualifies(visit().copy(distanceFromPlaceMeters = Double.NaN)))
        assertFalse(place.copy(active = false).qualifies(visit()))
    }
}
