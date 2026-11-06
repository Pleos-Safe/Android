package io.issac.pleosandroid.core.vehicle.datasource

import android.annotation.SuppressLint
import android.car.Car
import android.car.VehiclePropertyIds
import android.car.hardware.CarPropertyValue
import android.car.hardware.property.CarPropertyManager
import android.content.Context
import android.content.pm.PackageManager
import dagger.hilt.android.qualifiers.ApplicationContext
import io.issac.pleosandroid.core.domain.model.MotionAvailability
import io.issac.pleosandroid.core.domain.model.VehicleMotion
import io.issac.pleosandroid.core.domain.model.VehicleMotionState
import io.issac.pleosandroid.core.domain.policy.classifyVehicleMotion
import io.issac.pleosandroid.core.vehicle.datasource.compat.hasAvailableStatus
import io.issac.pleosandroid.core.vehicle.datasource.compat.subscribePropertyEventsCompat
import io.issac.pleosandroid.core.vehicle.datasource.compat.unsubscribePropertyEventsCompat
import io.issac.pleosandroid.core.vehicle.datasource.connection.CarConnection
import io.issac.pleosandroid.core.vehicle.datasource.connection.CarConnectionState
import io.issac.pleosandroid.core.vehicle.datasource.connection.VehicleServiceException
import io.issac.pleosandroid.core.vehicle.datasource.connection.carSdkCall
import io.issac.pleosandroid.core.vehicle.datasource.connection.recoverVehicleService
import io.issac.pleosandroid.core.vehicle.datasource.connection.releaseCarResource
import io.issac.pleosandroid.core.vehicle.di.MainDispatcher
import io.issac.pleosandroid.core.vehicle.permission.VehiclePermissions
import javax.inject.Inject
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.Job
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

/** Read-only motion information. Platform UX restrictions independently govern access to controls. */
@OptIn(ExperimentalCoroutinesApi::class)
internal class CarSpeedMonitor @Inject constructor(
    @param:ApplicationContext private val context: Context,
    private val connection: CarConnection,
    @param:MainDispatcher private val mainDispatcher: CoroutineDispatcher,
) : VehicleMotionDataSource {
    override fun observe(): Flow<VehicleMotionState> = connection.observe().flatMapLatest { state ->
        when (state) {
            is CarConnectionState.Ready -> if (hasSpeedPermission()) {
                observeSpeed(state.car)
            } else {
                flowOf(permissionRequired())
            }

            is CarConnectionState.Unsupported -> flowOf(
                VehicleMotionState(availability = MotionAvailability.Unsupported, message = state.message),
            )
        }
    }.onStart { emit(VehicleMotionState()) }
        .recoverVehicleService(
            isHealthy = { it.availability == MotionAvailability.Available },
            failureState = { error ->
                if (error.permissionRequired || !hasSpeedPermission()) {
                    permissionRequired()
                } else {
                    VehicleMotionState(
                        availability = MotionAvailability.Error,
                        message = "${error.message} 자동으로 다시 연결합니다.",
                    )
                }
            },
        ).flowOn(mainDispatcher)

    @SuppressLint("MissingPermission") // Checked before subscribing; revocation is translated at the SDK boundary.
    private fun observeSpeed(car: Car): Flow<VehicleMotionState> = callbackFlow {
        val manager = carSdkCall("차량 속도 서비스를 확인할 수 없습니다.") {
            car.getCarManager(Car.PROPERTY_SERVICE) as? CarPropertyManager
        }
        if (manager == null) {
            trySend(
                VehicleMotionState(
                    availability = MotionAvailability.Unsupported,
                    message = "이 차량에서 속도 정보를 제공하지 않습니다.",
                ),
            )
            close()
            return@callbackFlow
        }

        var open = true
        var deadline: Job? = null
        fun fail(message: String) {
            open = false
            close(VehicleServiceException(message, permissionRequired = !hasSpeedPermission()))
        }
        fun resetDeadline() {
            deadline?.cancel()
            deadline = launch {
                delay(10_000)
                fail("차량 속도 응답 시간이 초과되었습니다.")
            }
        }
        val callback = object : CarPropertyManager.CarPropertyEventCallback {
            override fun onChangeEvent(value: CarPropertyValue<*>) {
                if (!open || !isActive || value.propertyId != VehiclePropertyIds.PERF_VEHICLE_SPEED ||
                    value.areaId != 0
                ) {
                    return
                }
                val state = value.toMotionState()
                if (state == null) {
                    fail("차량 속도 정보가 현재 유효하지 않습니다.")
                    return
                }
                resetDeadline()
                trySend(state)
            }

            override fun onErrorEvent(propertyId: Int, areaId: Int) {
                if (!open || !isActive || propertyId != VehiclePropertyIds.PERF_VEHICLE_SPEED || areaId != 0) return
                fail("차량 속도를 읽을 수 없습니다.")
            }
        }
        val subscription = SpeedSubscription(manager, callback)
        resetDeadline()
        try {
            val registered = carSdkCall("차량 속도 구독에 실패했습니다.") { subscription.register() }
            if (!registered && !hasSpeedPermission()) {
                fail("차량 속도 읽기 권한이 필요합니다.")
            } else if (!registered) {
                open = false
                trySend(
                    VehicleMotionState(
                        availability = MotionAvailability.Unsupported,
                        message = "이 차량에서 속도 정보를 제공하지 않습니다.",
                    ),
                )
                close()
            }
        } catch (exception: VehicleServiceException) {
            open = false
            close(exception)
        }
        awaitClose {
            open = false
            deadline?.cancel()
            subscription.close()
        }
    }

    private fun hasSpeedPermission() =
        context.checkSelfPermission(VehiclePermissions.SPEED) == PackageManager.PERMISSION_GRANTED

    private fun permissionRequired() = VehicleMotionState(
        availability = MotionAvailability.PermissionRequired,
        message = "차량 속도 읽기 권한이 필요합니다.",
    )
}

private class SpeedSubscription(
    private val manager: CarPropertyManager,
    private val callback: CarPropertyManager.CarPropertyEventCallback,
) {
    fun register(): Boolean = manager.subscribePropertyEventsCompat(
        callback,
        VehiclePropertyIds.PERF_VEHICLE_SPEED,
        CarPropertyManager.SENSOR_RATE_NORMAL,
    )

    fun close() = releaseCarResource { manager.unsubscribePropertyEventsCompat(callback) }
}

private fun CarPropertyValue<*>.toMotionState(): VehicleMotionState? {
    if (!hasAvailableStatus()) return null
    val speed = (value as? Number)?.toFloat()
    val motion = classifyVehicleMotion(speed)
    if (motion == VehicleMotion.Unknown) return null
    return VehicleMotionState(
        motion = motion,
        speedMetersPerSecond = speed,
        availability = MotionAvailability.Available,
        timestampNanos = timestamp.takeIf { it > 0 },
        message = if (motion == VehicleMotion.Moving) "차량이 이동 중입니다." else "차량이 멈춰 있습니다.",
    )
}
