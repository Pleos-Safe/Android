package io.issac.pleosandroid.core.vehicle.policy

import android.car.VehicleGear as PlatformGear
import io.issac.pleosandroid.core.domain.model.VehicleGear

internal fun classifyVehicleGear(value: Int?): VehicleGear = when (value) {
    PlatformGear.GEAR_PARK -> VehicleGear.Park
    PlatformGear.GEAR_REVERSE -> VehicleGear.Reverse
    PlatformGear.GEAR_NEUTRAL -> VehicleGear.Neutral
    PlatformGear.GEAR_DRIVE,
    PlatformGear.GEAR_FIRST,
    PlatformGear.GEAR_SECOND,
    PlatformGear.GEAR_THIRD,
    PlatformGear.GEAR_FOURTH,
    PlatformGear.GEAR_FIFTH,
    PlatformGear.GEAR_SIXTH,
    PlatformGear.GEAR_SEVENTH,
    PlatformGear.GEAR_EIGHTH,
    PlatformGear.GEAR_NINTH -> VehicleGear.Drive
    else -> VehicleGear.Unknown
}
