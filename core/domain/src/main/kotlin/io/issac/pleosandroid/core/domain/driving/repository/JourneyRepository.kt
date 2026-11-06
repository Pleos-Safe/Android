package io.issac.pleosandroid.core.domain.driving.repository

import io.issac.pleosandroid.core.domain.model.MotionAvailability
import io.issac.pleosandroid.core.domain.model.VehicleGear
import io.issac.pleosandroid.core.domain.model.VehicleMotion
import io.issac.pleosandroid.core.domain.model.VehicleState
import java.time.Instant
import kotlinx.coroutines.flow.StateFlow

data class JourneySession(
    val id: String,
    val startedAt: Instant,
    val startedNanos: Long,
    val startedWhileMoving: Boolean,
    val endedNanos: Long? = null,
) {
    val finished: Boolean get() = endedNanos != null
    val durationMillis: Long? get() = endedNanos?.let { ((it - startedNanos) / 1_000_000).coerceAtLeast(1) }
}

data class JourneyState(
    val vehicle: VehicleState = VehicleState(),
    val session: JourneySession? = null,
    val hasObservedPark: Boolean = false,
    val lastFinished: JourneySession? = null,
)

/** One in-memory journey for home, recommendations and opt-in recording. Never persists location traces. */
interface JourneyRepository {
    val state: StateFlow<JourneyState>
    fun returnHome(): Boolean
}

/** Depart automatically; D at zero speed remains active, a confirmed stop in P ends the journey. */
fun JourneyState.withVehicle(value: VehicleState, now: Instant, nanos: Long, newId: () -> String): JourneyState {
    if (value.motion.availability != MotionAvailability.Available) {
        return copy(vehicle = value, hasObservedPark = false)
    }
    val parked = value.motion.motion == VehicleMotion.Stopped && value.gear == VehicleGear.Park
    val sampleNanos = value.motion.timestampNanos?.takeIf { it in 1..nanos && nanos - it <= 2_000_000_000L }
        ?: nanos
    val next = when {
        value.motion.motion == VehicleMotion.Moving && (session == null || session.finished) -> JourneySession(
            newId(),
            now.minusNanos(nanos - sampleNanos),
            sampleNanos,
            startedWhileMoving = !hasObservedPark,
        )

        parked && session != null && !session.finished -> session.copy(
            // P may arrive after the last zero-speed sample; the journey ends at this observation.
            endedNanos = nanos.coerceAtLeast(session.startedNanos + 1_000_000),
        )

        else -> session
    }
    return JourneyState(value, next, hasObservedPark || parked, next?.takeIf { it.finished } ?: lastFinished)
}
