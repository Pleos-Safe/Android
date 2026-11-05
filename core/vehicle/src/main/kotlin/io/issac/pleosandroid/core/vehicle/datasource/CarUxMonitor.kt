package io.issac.pleosandroid.core.vehicle.datasource

import android.car.Car
import android.car.drivingstate.CarUxRestrictions
import android.car.drivingstate.CarUxRestrictionsManager
import io.issac.pleosandroid.core.domain.model.UxAvailability
import io.issac.pleosandroid.core.domain.policy.CarUxPolicy
import io.issac.pleosandroid.core.vehicle.datasource.connection.CarConnection
import io.issac.pleosandroid.core.vehicle.datasource.connection.CarConnectionState
import io.issac.pleosandroid.core.vehicle.datasource.connection.VehicleServiceException
import io.issac.pleosandroid.core.vehicle.datasource.connection.carSdkCall
import io.issac.pleosandroid.core.vehicle.datasource.connection.recoverVehicleService
import io.issac.pleosandroid.core.vehicle.datasource.connection.releaseCarResource
import io.issac.pleosandroid.core.vehicle.di.MainDispatcher
import io.issac.pleosandroid.core.vehicle.model.CarUxState
import javax.inject.Inject
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.channels.awaitClose
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.callbackFlow
import kotlinx.coroutines.flow.flatMapLatest
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.flow.flowOn
import kotlinx.coroutines.flow.onStart
import kotlinx.coroutines.isActive

@OptIn(ExperimentalCoroutinesApi::class)
internal class CarUxMonitor @Inject constructor(
    private val connection: CarConnection,
    @param:MainDispatcher private val mainDispatcher: CoroutineDispatcher,
) : CarUxDataSource {
    override fun observe(): Flow<CarUxState> = connection.observe().flatMapLatest { state ->
        when (state) {
            is CarConnectionState.Ready -> observeRestrictions(state.car)

            is CarConnectionState.Unsupported -> flowOf(
                CarUxState(
                    availability = UxAvailability.Unsupported,
                    policy = CarUxPolicy(message = state.message),
                ),
            )
        }
    }.onStart { emit(CarUxState()) }
        .recoverVehicleService(
            isHealthy = { it.availability == UxAvailability.Available },
            failureState = { error ->
                CarUxState(
                    availability = UxAvailability.Error,
                    policy = CarUxPolicy(
                        message = if (error.permissionRequired) {
                            "차량 UX 상태 접근 권한을 확인해 주세요."
                        } else {
                            "${error.message} 자동으로 다시 연결합니다."
                        },
                    ),
                )
            },
        ).flowOn(mainDispatcher)

    private fun observeRestrictions(car: Car): Flow<CarUxState> = callbackFlow {
        val manager = carSdkCall("차량 UX 제한 서비스를 확인할 수 없습니다.") {
            car.getCarManager(Car.CAR_UX_RESTRICTION_SERVICE) as? CarUxRestrictionsManager
        }
        if (manager == null) {
            trySend(
                CarUxState(
                    availability = UxAvailability.Unsupported,
                    policy = CarUxPolicy(connected = true, message = "차량에서 UX 제한 서비스를 제공하지 않습니다."),
                ),
            )
            close()
            return@callbackFlow
        }

        var open = true
        fun publish(restrictions: CarUxRestrictions?) {
            if (!open || !isActive) return
            if (restrictions == null) {
                open = false
                close(VehicleServiceException("차량 UX 제한 상태를 읽지 못했습니다."))
                return
            }
            trySend(
                CarUxState(
                    policy = restrictions.toPolicy(),
                    availability = UxAvailability.Available,
                ),
            )
        }
        try {
            carSdkCall("차량 UX 제한을 읽을 수 없습니다.") {
                // Subscribe before reading the snapshot so a concurrent transition is not missed.
                manager.registerListener(::publish)
                publish(manager.currentCarUxRestrictions)
            }
        } catch (exception: VehicleServiceException) {
            open = false
            close(exception)
        }
        awaitClose {
            open = false
            releaseCarResource { manager.unregisterListener() }
        }
    }
}

private fun CarUxRestrictions.toPolicy() = CarUxPolicy(
    connected = true,
    known = true,
    requiresDistractionOptimization = isRequiresDistractionOptimization,
    activeRestrictions = activeRestrictions,
    maxRestrictedStringLength = maxRestrictedStringLength,
    maxCumulativeContentItems = maxCumulativeContentItems,
    maxContentDepth = maxContentDepth,
    message = if (isRequiresDistractionOptimization) {
        "플랫폼에서 주행 UX 제한을 적용하고 있습니다."
    } else {
        "플랫폼의 주행 UX 제한이 해제되어 있습니다."
    },
)
