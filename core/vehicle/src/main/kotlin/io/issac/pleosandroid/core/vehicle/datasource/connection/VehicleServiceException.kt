package io.issac.pleosandroid.core.vehicle.datasource.connection

import kotlinx.coroutines.CancellationException

internal class VehicleServiceException(
    message: String,
    cause: Throwable? = null,
    val permissionRequired: Boolean = false,
) : RuntimeException(message, cause)

/** Only expected SDK failures are translated into observable service errors. */
internal inline fun <T> carSdkCall(message: String, action: () -> T): T = try {
    action()
} catch (exception: CancellationException) {
    throw exception
} catch (exception: SecurityException) {
    throw VehicleServiceException(message, exception, permissionRequired = true)
} catch (exception: IllegalStateException) {
    throw VehicleServiceException(message, exception)
} catch (exception: IllegalArgumentException) {
    throw VehicleServiceException(message, exception)
}
