package io.issac.pleosandroid.core.domain.model

import io.issac.pleosandroid.core.domain.policy.CarUxPolicy

data class VehicleState(
    val motion: VehicleMotionState = VehicleMotionState(),
    val uxPolicy: CarUxPolicy = CarUxPolicy(),
    val uxAvailability: UxAvailability = UxAvailability.Connecting,
    val gear: VehicleGear = VehicleGear.Unknown,
)
