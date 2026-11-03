package io.issac.pleosandroid.core.vehicle.policy

import io.issac.pleosandroid.core.vehicle.model.VehicleMotion
import kotlin.math.abs

/** Ignore very small sensor jitter. This classification never controls UX permissions. */
internal fun classifyVehicleMotion(speedMetersPerSecond: Float?): VehicleMotion = when {
    speedMetersPerSecond == null || !speedMetersPerSecond.isFinite() -> VehicleMotion.Unknown
    abs(speedMetersPerSecond) > 0.1f -> VehicleMotion.Moving
    else -> VehicleMotion.Stopped
}
