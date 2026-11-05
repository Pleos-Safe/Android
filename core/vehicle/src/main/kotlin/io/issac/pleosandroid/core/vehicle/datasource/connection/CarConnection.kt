package io.issac.pleosandroid.core.vehicle.datasource.connection

import android.car.Car
import android.content.Context
import android.content.pm.PackageManager
import android.os.Build
import android.os.Looper
import android.util.Log
import dagger.hilt.android.qualifiers.ApplicationContext
import io.issac.pleosandroid.core.vehicle.di.MainDispatcher
import javax.inject.Inject
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.Job
import kotlinx.coroutines.channels.awaitClose
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.callbackFlow
import kotlinx.coroutines.flow.flowOn
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch

internal sealed interface CarConnectionState {
    data class Ready(val car: Car) : CarConnectionState
    data class Unsupported(val message: String) : CarConnectionState
}

/** Each collection owns one connection and invalidates callbacks when that session closes. */
internal class CarConnection @Inject constructor(
    @param:ApplicationContext private val context: Context,
    @param:MainDispatcher private val mainDispatcher: CoroutineDispatcher,
) {
    fun observe(): Flow<CarConnectionState> = callbackFlow<CarConnectionState> {
        check(Looper.myLooper() == Looper.getMainLooper()) {
            "Car connections must run on the main thread."
        }
        val unsupported = unsupportedReason()
        if (unsupported != null) {
            trySend(CarConnectionState.Unsupported(unsupported))
            close()
            return@callbackFlow
        }

        var connection: Car? = null
        var closed = false
        fun fail(exception: VehicleServiceException) {
            closed = true
            close(exception)
        }
        val deadline: Job = launch {
            delay(10_000)
            fail(VehicleServiceException("차량 서비스 연결 시간이 초과되었습니다."))
        }
        try {
            val created: Car? = carSdkCall<Car?>("차량 서비스에 연결할 수 없습니다.") {
                Car.createCar(context, null, Car.CAR_WAIT_TIMEOUT_DO_NOT_WAIT) { car, ready ->
                    if (closed || !isActive) return@createCar
                    // The callback may run synchronously before createCar returns.
                    connection = car
                    if (!ready) {
                        fail(VehicleServiceException("차량 서비스 연결이 끊겼습니다."))
                        return@createCar
                    }
                    deadline.cancel()
                    trySend(CarConnectionState.Ready(car))
                }
            }
            connection = created
            if (created == null) fail(VehicleServiceException("차량 서비스에 연결할 수 없습니다."))
        } catch (exception: VehicleServiceException) {
            fail(exception)
        }

        awaitClose {
            closed = true
            deadline.cancel()
            releaseCarResource { connection?.disconnect() }
            connection = null
        }
    }.flowOn(mainDispatcher)

    private fun unsupportedReason(): String? = when {
        !context.packageManager.hasSystemFeature(PackageManager.FEATURE_AUTOMOTIVE) ->
            "AAOS 차량 환경에서 확인할 수 있습니다."

        Build.VERSION.SDK_INT < Build.VERSION_CODES.Q -> "차량 연결에는 Android 10 이상이 필요합니다."

        else -> null
    }
}

internal inline fun releaseCarResource(action: () -> Unit) {
    try {
        action()
    } catch (exception: SecurityException) {
        Log.w("CarConnection", "Car permission changed before cleanup", exception)
    } catch (exception: IllegalStateException) {
        Log.w("CarConnection", "Car service disconnected before cleanup", exception)
    }
}
