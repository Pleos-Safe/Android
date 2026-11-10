package io.issac.pleosandroid.feature.main.map

import io.issac.pleosandroid.core.domain.collection.repository.VisitLocation
import io.issac.pleosandroid.core.domain.driving.repository.JourneySession
import java.time.Instant
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class ParkedVisitEligibilityTest {
    private val place = busanTravelPlaces.first().copy(visitRadiusMeters = 200.0)
    private val session = JourneySession("journey", Instant.EPOCH, 1L, false, pausedAtNanos = 6 * SECOND)
    private val accurateFix = VisitLocation(place.latitude, place.longitude, 5.0, 7 * SECOND)

    @Test fun routeEndedArrivalStillQualifiesThisJourneyWithFreshParkedGps() {
        val route = VisitRouteEvidence(emptySet(), VisitArrival(session.id, place.id, 5 * SECOND))
        val now = 8 * SECOND

        assertEquals(place, selectParkedVisitPlace(session, accurateFix, route, listOf(place), now))
        assertNull(selectParkedVisitPlace(session.copy(id = "other"), accurateFix, route, listOf(place), now))
        assertNull(selectParkedVisitPlace(session, accurateFix, VisitRouteEvidence(emptySet()), listOf(place), now))
        val beforeParking = accurateFix.copy(elapsedNanos = 5 * SECOND)
        assertNull(selectParkedVisitPlace(session, beforeParking, route, listOf(place), now))
        val muchLater = 306 * SECOND
        val refreshedFix = accurateFix.copy(elapsedNanos = muchLater)
        assertNull(selectParkedVisitPlace(session, refreshedFix, route, listOf(place), muchLater))
    }

    @Test fun weakerLocationDoesNotReplaceFreshGpsAndExpiredGpsDoesNotSurvive() {
        val coarseFix = accurateFix.copy(accuracyMeters = 80.0, elapsedNanos = 8 * SECOND)
        val parkedAt = checkNotNull(session.pausedAtNanos)
        assertEquals(accurateFix, chooseParkedVisitFix(accurateFix, coarseFix, parkedAt, 9 * SECOND))
        assertNull(chooseParkedVisitFix(accurateFix, null, parkedAt, 9 * SECOND))
        assertNull(chooseParkedVisitFix(accurateFix, coarseFix, parkedAt, 19 * SECOND))
        val freshFix = coarseFix.copy(elapsedNanos = 19 * SECOND)
        assertEquals(freshFix, chooseParkedVisitFix(accurateFix, freshFix, parkedAt, 19 * SECOND))
    }

    private companion object {
        const val SECOND = 1_000_000_000L
    }
}
