package io.issac.pleosandroid.core.domain.collection

import io.issac.pleosandroid.core.domain.collection.model.Place
import io.issac.pleosandroid.core.domain.collection.policy.VisitTracker
import io.issac.pleosandroid.core.domain.collection.repository.VisitLocation
import java.time.Instant
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class VisitTrackerTest {
    private val place = Place("beach", "해변", "부산", "해변", 35.15, 129.16, 250.0, 60, "image")
    private val epoch = Instant.parse("2026-11-01T00:00:00Z")
    private fun nanos(seconds: Int) = (seconds + 1L) * 1_000_000_000
    private fun fix(seconds: Int, accuracy: Double = 10.0) =
        VisitLocation(place.latitude, place.longitude, accuracy, nanos(seconds))
    private fun VisitTracker.observe(seconds: Int, fix: VisitLocation? = fix(seconds)) =
        observe("journey", listOf(place), fix, nanos(seconds), epoch.plusSeconds(seconds.toLong()), "Asia/Seoul")

    @Test fun continuousStayProducesOneStableProofPerJourneyAndPlace() {
        val tracker = VisitTracker()
        for (second in 0..50 step 10) assertTrue(tracker.observe(second).isEmpty())
        val visit = tracker.observe(60).single()
        assertEquals("visit:journey:beach", visit.id)
        assertEquals("journey", visit.journeyId)
        assertEquals(epoch, visit.arrivedAt)
        assertEquals(epoch.plusSeconds(60), visit.departedAt)
        assertEquals(10.0, visit.accuracyMeters, 0.0)
        assertTrue(tracker.observe(70).isEmpty())
    }

    @Test fun staleDuplicateAndMissingFixesCannotAccumulateDwell() {
        val tracker = VisitTracker()
        tracker.observe(0)
        for (second in 1..15) assertTrue(tracker.observe(second, fix(0)).isEmpty())
        assertTrue(tracker.observe(60, fix(0)).isEmpty())
        assertTrue(tracker.observe(70, null).isEmpty())
        assertTrue(tracker.observe(80).isEmpty())
    }

    @Test fun inaccurateAndOutOfBoundsFixesResetTheStay() {
        val tracker = VisitTracker()
        for (second in 0..50 step 10) tracker.observe(second)
        assertTrue(tracker.observe(60, fix(60, accuracy = 251.0)).isEmpty())
        assertTrue(tracker.observe(70).isEmpty())
        assertTrue(tracker.observe(80, fix(80).copy(latitude = 35.2)).isEmpty())
        assertTrue(tracker.observe(90).isEmpty())
    }

    @Test fun aGapOrResumedDrivingRequiresAnotherFullStay() {
        val tracker = VisitTracker()
        tracker.observe(0)
        assertTrue(tracker.observe(60).isEmpty())
        for (second in 70..110 step 10) tracker.observe(second)
        tracker.breakContinuity()
        assertTrue(tracker.observe(120).isEmpty())
    }

    @Test fun changingAPlaceCannotReuseTheOldStay() {
        val tracker = VisitTracker()
        for (second in 0..50 step 10) tracker.observe(second)
        val changed = place.copy(latitude = place.latitude + .0001)
        val result = tracker.observe(
            "journey",
            listOf(changed),
            fix(60),
            nanos(60),
            epoch.plusSeconds(60),
            "Asia/Seoul",
        )
        assertTrue(result.isEmpty())
    }
}
