package io.issac.pleosandroid.core.domain.driving.model

/** Versioned observations from vehicle speed, not an accident-risk or certified safety rating. */
data class DrivingAssessment(
    val policyVersion: Int = 1,
    val elapsedMillis: Long,
    val observedMillis: Long,
    val movingMillis: Long,
    val sampleCount: Int,
    val rejectedSamples: Int,
    val harshAccelerations: Int,
    val harshBrakings: Int,
    val harshStarts: Int,
    val longestGapMillis: Long,
    val reasons: Set<InsufficientReason> = emptySet(),
    val pausedMillis: Long = 0,
) {
    val coverage: Double get() = if (elapsedMillis > 0) {
        (observedMillis.toDouble() / elapsedMillis).coerceIn(0.0, 1.0)
    } else {
        0.0
    }
}

enum class InsufficientReason {
    TooShort,
    TooLittleDistance,
    TooFewSamples,
    LowCoverage,
    SignalGap,
    InvalidSignal,
    StartedWhileMoving,
    Interrupted,
}

data class SpeedSample(val timestampNanos: Long, val metersPerSecond: Double, val forward: Boolean)

data class DrivingResult(val distanceMeters: Long, val score: Int?, val assessment: DrivingAssessment)
