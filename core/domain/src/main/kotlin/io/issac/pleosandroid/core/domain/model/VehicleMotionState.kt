package io.issac.pleosandroid.core.domain.model

data class VehicleMotionState(
    val motion: VehicleMotion = VehicleMotion.Unknown,
    val speedMetersPerSecond: Float? = null,
    val availability: MotionAvailability = MotionAvailability.Connecting,
    val message: String = "차량 움직임 확인 중",
    val timestampNanos: Long? = null,
)
