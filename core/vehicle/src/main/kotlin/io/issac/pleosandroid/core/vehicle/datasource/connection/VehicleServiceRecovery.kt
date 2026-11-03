package io.issac.pleosandroid.core.vehicle.datasource.connection

import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.catch
import kotlinx.coroutines.flow.emitAll
import kotlinx.coroutines.flow.flow
import kotlinx.coroutines.flow.onEach
import kotlinx.coroutines.flow.retryWhen

/** Reconnect while observed, with exponential backoff capped at 30 seconds. */
internal fun <T> Flow<T>.recoverVehicleService(
    isHealthy: (T) -> Boolean,
    failureState: (VehicleServiceException) -> T,
): Flow<T> = flow {
    var failures = 0
    emitAll(this@recoverVehicleService.onEach { if (isHealthy(it)) failures = 0 }
        .retryWhen { cause, _ ->
            if (cause !is VehicleServiceException || cause.permissionRequired) return@retryWhen false
            emit(failureState(cause))
            val backoff = (1_000L shl failures.coerceAtMost(5)).coerceAtMost(30_000)
            failures = (failures + 1).coerceAtMost(5)
            delay(backoff)
            true
        }.catch { cause ->
            if (cause !is VehicleServiceException) throw cause
            emit(failureState(cause))
        })
}
