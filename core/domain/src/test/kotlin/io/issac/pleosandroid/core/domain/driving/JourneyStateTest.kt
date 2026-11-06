package io.issac.pleosandroid.core.domain.driving

import io.issac.pleosandroid.core.domain.driving.repository.JourneyState
import io.issac.pleosandroid.core.domain.driving.repository.withVehicle
import io.issac.pleosandroid.core.domain.model.MotionAvailability
import io.issac.pleosandroid.core.domain.model.VehicleGear
import io.issac.pleosandroid.core.domain.model.VehicleMotion
import io.issac.pleosandroid.core.domain.model.VehicleMotionState
import io.issac.pleosandroid.core.domain.model.VehicleState
import java.time.Instant
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class JourneyStateTest {
    private val parked = VehicleState(
        motion = VehicleMotionState(VehicleMotion.Stopped, 0f, MotionAvailability.Available),
        gear = VehicleGear.Park,
    )
    private val moving = parked.copy(
        motion = parked.motion.copy(motion = VehicleMotion.Moving, speedMetersPerSecond = 5f),
        gear = VehicleGear.Drive,
    )
    private fun JourneyState.receive(vehicle: VehicleState, second: Long, id: String = "one") =
        withVehicle(vehicle, Instant.ofEpochSecond(second), second * 1_000_000_000) { id }

    @Test fun parkEndsImmediatelyAndRetainsCompletionAfterHomeIsDismissed() {
        val before = JourneyState().receive(parked, 1)
        val started = before.receive(moving, 2)
        assertFalse(checkNotNull(started.session).startedWhileMoving)
        val finished = started.receive(parked, 12)
        assertEquals(10_000L, finished.session?.durationMillis)
        assertEquals(finished.session, finished.lastFinished)
        val home = finished.copy(session = null).receive(parked, 13)
        assertNull(home.session)
        assertEquals("one", home.lastFinished?.id)
        val next = home.receive(moving, 20, "two")
        assertEquals("two", next.session?.id)
        assertEquals("one", next.lastFinished?.id)
    }

    @Test fun stoppingInDriveOrLosingSpeedDoesNotEndTheJourney() {
        val started = JourneyState().receive(moving, 2)
        assertTrue(checkNotNull(started.session).startedWhileMoving)
        val stopped = started.receive(parked.copy(gear = VehicleGear.Drive), 3)
        assertFalse(checkNotNull(stopped.session).finished)
        val unavailable = stopped.receive(
            parked.copy(motion = parked.motion.copy(availability = MotionAvailability.Error)),
            4,
        )
        assertFalse(checkNotNull(unavailable.session).finished)
    }

    @Test fun aGearChangeToParkDoesNotBackdateCompletionToTheLastSpeedSample() {
        val started = JourneyState().receive(parked, 1).receive(moving, 2)
        val stopped = parked.copy(motion = parked.motion.copy(timestampNanos = 10_000_000_000))
        val waiting = started.receive(stopped.copy(gear = VehicleGear.Drive), 10)
        val finished = waiting.receive(stopped, 12)
        assertEquals(12_000_000_000L, finished.session?.endedNanos)
        assertEquals(10_000L, finished.session?.durationMillis)
    }

    @Test fun aDisconnectedParkedObservationCannotCertifyAnUnseenDeparture() {
        val parkedState = JourneyState().receive(parked, 1)
        val disconnected = parkedState.receive(
            parked.copy(motion = parked.motion.copy(availability = MotionAvailability.Error)),
            2,
        )
        assertTrue(checkNotNull(disconnected.receive(moving, 3).session).startedWhileMoving)
    }
}
