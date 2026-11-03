package io.issac.pleosandroid.core.vehicle.datasource

import android.annotation.SuppressLint
import android.car.Car
import android.car.VehiclePropertyIds
import android.car.hardware.CarPropertyValue
import android.car.hardware.property.CarPropertyManager
import android.content.Context
import android.content.pm.PackageManager
import dagger.hilt.android.qualifiers.ApplicationContext
import io.issac.pleosandroid.core.vehicle.datasource.connection.CarConnection
import io.issac.pleosandroid.core.vehicle.datasource.connection.CarConnectionState
import io.issac.pleosandroid.core.vehicle.datasource.connection.VehicleServiceException
import io.issac.pleosandroid.core.vehicle.datasource.connection.carSdkCall
import io.issac.pleosandroid.core.vehicle.datasource.connection.recoverVehicleService
import io.issac.pleosandroid.core.vehicle.datasource.connection.releaseCarResource
import io.issac.pleosandroid.core.vehicle.di.MainDispatcher
import io.issac.pleosandroid.core.vehicle.model.VehicleGear
import io.issac.pleosandroid.core.vehicle.permission.VehiclePermissions
import io.issac.pleosandroid.core.vehicle.policy.classifyVehicleGear
import javax.inject.Inject
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.channels.awaitClose
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.callbackFlow
import kotlinx.coroutines.flow.flatMapLatest
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.flow.flowOn
import kotlinx.coroutines.flow.onStart
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch

/** Selected gear is independent from speed; an unknown gear must never imply parking. */
@OptIn(ExperimentalCoroutinesApi::class)
internal class CarGearMonitor @Inject constructor(
    @param:ApplicationContext private val context: Context,
    private val connection: CarConnection,
    @param:MainDispatcher private val mainDispatcher: CoroutineDispatcher,
) : VehicleGearDataSource {
    override fun observe(): Flow<VehicleGear> = connection.observe().flatMapLatest { state ->
        when (state) {
            is CarConnectionState.Ready -> if (hasPermission()) observeGear(state.car)
                else flowOf(VehicleGear.Unknown)
            is CarConnectionState.Unsupported -> flowOf(VehicleGear.Unknown)
        }
    }.onStart { emit(VehicleGear.Unknown) }
        .recoverVehicleService(
            isHealthy = { it != VehicleGear.Unknown },
            failureState = { VehicleGear.Unknown },
        ).flowOn(mainDispatcher)

    @Suppress("DEPRECATION") // registerCallback is supported by the Pleos API 34 image.
    @SuppressLint("MissingPermission") // The normal powertrain permission is checked before subscribing.
    private fun observeGear(car: Car): Flow<VehicleGear> = callbackFlow {
        val manager = carSdkCall("차량 기어 서비스를 확인할 수 없습니다.") {
            car.getCarManager(Car.PROPERTY_SERVICE) as? CarPropertyManager
        }
        if (manager == null) {
            trySend(VehicleGear.Unknown)
            close()
            return@callbackFlow
        }

        var open = true
        fun fail() {
            open = false
            close(VehicleServiceException("차량 기어 정보를 읽을 수 없습니다.", permissionRequired = !hasPermission()))
        }
        // GEAR_SELECTION is ONCHANGE: only the first response has a deadline.
        val initialDeadline = launch {
            delay(10_000)
            fail()
        }
        val callback = object : CarPropertyManager.CarPropertyEventCallback {
            override fun onChangeEvent(value: CarPropertyValue<*>) {
                if (!open || !isActive || value.propertyId != VehiclePropertyIds.GEAR_SELECTION || value.areaId != 0) return
                if (value.status != CarPropertyValue.STATUS_AVAILABLE) {
                    fail()
                    return
                }
                initialDeadline.cancel()
                trySend(classifyVehicleGear(value.value as? Int))
            }

            override fun onErrorEvent(propertyId: Int, areaId: Int) {
                if (!open || !isActive || propertyId != VehiclePropertyIds.GEAR_SELECTION || areaId != 0) return
                fail()
            }
        }
        try {
            val registered = carSdkCall("차량 기어 구독에 실패했습니다.") {
                manager.registerCallback(callback, VehiclePropertyIds.GEAR_SELECTION, CarPropertyManager.SENSOR_RATE_ONCHANGE)
            }
            if (!registered) {
                open = false
                trySend(VehicleGear.Unknown)
                close()
            }
        } catch (exception: VehicleServiceException) {
            open = false
            close(exception)
        }
        awaitClose {
            open = false
            initialDeadline.cancel()
            releaseCarResource { manager.unregisterCallback(callback) }
        }
    }

    private fun hasPermission() =
        context.checkSelfPermission(VehiclePermissions.Powertrain) == PackageManager.PERMISSION_GRANTED
}
