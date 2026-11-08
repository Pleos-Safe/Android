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
    val pausedAtNanos: Long? = null,
    val pausedNanos: Long = 0,
    val recoveredActiveNanos: Long = 0,
    val recoveryRequired: Boolean = false,
    val endedAt: Instant? = null,
) {
    val finished: Boolean get() = endedNanos != null
    val paused: Boolean get() = !finished && pausedAtNanos != null
    val durationMillis: Long? get() = endedNanos?.let { activeDurationMillis(it).coerceAtLeast(1) }

    fun activeDurationMillis(nowNanos: Long): Long = activeDurationNanos(nowNanos) / 1_000_000

    fun activeDurationNanos(nowNanos: Long): Long {
        val cutoff = minOf(nowNanos, pausedAtNanos ?: Long.MAX_VALUE, endedNanos ?: Long.MAX_VALUE)
        return recoveredActiveNanos + (cutoff - startedNanos - pausedNanos).coerceAtLeast(0)
    }
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
    fun finish(): Boolean = false
    fun restore(session: JourneySession) = Unit
    fun continueRecovered(): Boolean = false
    fun discardRecovery() = Unit
}

/** Depart automatically. D at zero speed stays active. P pauses the same journey until departure or explicit finish. */
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

        parked && session != null && !session.finished && !session.paused -> session.copy(pausedAtNanos = nanos)

        value.motion.motion == VehicleMotion.Moving && session?.paused == true && !session.recoveryRequired -> {
            val pausedAt = checkNotNull(session.pausedAtNanos)
            val resumedAt = maxOf(sampleNanos, pausedAt)
            session.copy(pausedAtNanos = null, pausedNanos = session.pausedNanos + (resumedAt - pausedAt))
        }

        else -> session
    }
    return JourneyState(value, next, hasObservedPark || parked, lastFinished)
}

/** The repository separately checks the latest platform UX permission before committing this transition. */
fun JourneyState.finishPausedJourney(nanos: Long, now: Instant): JourneyState {
    val active = session?.takeIf { it.paused } ?: return this
    val finished = active.copy(endedNanos = maxOf(nanos, active.startedNanos), endedAt = now, recoveryRequired = false)
    return copy(session = finished, lastFinished = finished)
}
