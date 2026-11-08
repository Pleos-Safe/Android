package io.issac.pleosandroid.core.data.driving

import io.issac.pleosandroid.core.data.di.ApplicationScope
import io.issac.pleosandroid.core.domain.driving.repository.JourneyRepository
import io.issac.pleosandroid.core.domain.driving.repository.JourneySession
import io.issac.pleosandroid.core.domain.driving.repository.JourneyState
import io.issac.pleosandroid.core.domain.driving.repository.finishPausedJourney
import io.issac.pleosandroid.core.domain.driving.repository.withVehicle
import io.issac.pleosandroid.core.domain.repository.VehicleRepository
import java.util.UUID
import javax.inject.Inject
import javax.inject.Singleton
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch

@Singleton
internal class DefaultJourneyRepository @Inject constructor(
    private val vehicle: VehicleRepository,
    private val clock: DrivingClock,
    @ApplicationScope scope: CoroutineScope,
) : JourneyRepository {
    private val mutableState = MutableStateFlow(JourneyState())
    override val state = mutableState.asStateFlow()

    init {
        scope.launch {
            vehicle.state.collect {
                mutableState.update {
                    it.withVehicle(vehicle.state.value, clock.now(), clock.elapsedNanos()) {
                        UUID.randomUUID().toString()
                    }
                }
            }
        }
    }

    override fun returnHome(): Boolean {
        while (true) {
            val current = mutableState.value
            if (!vehicle.state.value.allowsTabNavigation || current.session?.finished != true) return false
            if (mutableState.compareAndSet(current, current.copy(session = null))) return true
        }
    }

    override fun finish(): Boolean {
        while (true) {
            val vehicle = vehicle.state.value
            if (!vehicle.allowsTabNavigation) return false
            val current = mutableState.value
            val now = clock.elapsedNanos()
            val wallTime = clock.now()
            val parked = current.withVehicle(vehicle, wallTime, now) { UUID.randomUUID().toString() }
            val finished = parked.finishPausedJourney(now, wallTime)
            if (finished.lastFinished == current.lastFinished) return false
            if (mutableState.compareAndSet(current, finished)) return true
        }
    }

    override fun restore(session: JourneySession) {
        require(session.paused && session.recoveryRequired)
        mutableState.update { it.copy(session = session) }
    }

    override fun continueRecovered(): Boolean {
        while (true) {
            if (!vehicle.state.value.allowsTabNavigation) return false
            val current = mutableState.value
            val session = current.session?.takeIf { it.recoveryRequired && it.paused } ?: return false
            if (mutableState.compareAndSet(current, current.copy(session = session.copy(recoveryRequired = false)))) {
                return true
            }
        }
    }

    override fun discardRecovery() {
        mutableState.update { if (it.session?.recoveryRequired == true) it.copy(session = null) else it }
    }
}
