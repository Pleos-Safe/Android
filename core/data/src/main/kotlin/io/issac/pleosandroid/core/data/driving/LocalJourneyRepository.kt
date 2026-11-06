package io.issac.pleosandroid.core.data.driving

import io.issac.pleosandroid.core.data.di.ApplicationScope
import io.issac.pleosandroid.core.domain.driving.repository.JourneyRepository
import io.issac.pleosandroid.core.domain.driving.repository.JourneyState
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
internal class LocalJourneyRepository @Inject constructor(
    private val vehicle: VehicleRepository,
    clock: DrivingClock,
    @ApplicationScope scope: CoroutineScope,
) : JourneyRepository {
    private val mutableState = MutableStateFlow(JourneyState())
    override val state = mutableState.asStateFlow()

    init {
        scope.launch {
            vehicle.state.collect { value ->
                mutableState.update {
                    it.withVehicle(value, clock.now(), clock.elapsedNanos()) { UUID.randomUUID().toString() }
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
}
