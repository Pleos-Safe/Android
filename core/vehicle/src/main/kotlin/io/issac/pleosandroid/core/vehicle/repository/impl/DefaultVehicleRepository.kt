package io.issac.pleosandroid.core.vehicle.repository.impl

import io.issac.pleosandroid.core.vehicle.datasource.CarUxDataSource
import io.issac.pleosandroid.core.vehicle.datasource.VehicleMotionDataSource
import io.issac.pleosandroid.core.vehicle.datasource.VehicleGearDataSource
import io.issac.pleosandroid.core.vehicle.di.ApplicationScope
import io.issac.pleosandroid.core.domain.model.VehicleState
import io.issac.pleosandroid.core.domain.repository.VehicleRepository
import javax.inject.Inject
import javax.inject.Singleton
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.flatMapLatest
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.flow.update

@Singleton
@OptIn(ExperimentalCoroutinesApi::class)
class DefaultVehicleRepository @Inject constructor(
    motionSource: VehicleMotionDataSource,
    uxSource: CarUxDataSource,
    gearSource: VehicleGearDataSource,
    @ApplicationScope scope: CoroutineScope,
) : VehicleRepository {
    private val retryRequests = MutableStateFlow(0L)

    override val state = retryRequests.flatMapLatest {
        combine(motionSource.observe(), uxSource.observe(), gearSource.observe()) { motion, ux, gear ->
            VehicleState(motion = motion, uxPolicy = ux.policy, uxAvailability = ux.availability, gear = gear)
        }
    }.stateIn(
        scope = scope,
        started = SharingStarted.WhileSubscribed(stopTimeoutMillis = 0, replayExpirationMillis = 0),
        initialValue = VehicleState(),
    )

    override fun retry() {
        retryRequests.update { it + 1 }
    }
}
