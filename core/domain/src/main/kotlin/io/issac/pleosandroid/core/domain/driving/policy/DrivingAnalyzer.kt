package io.issac.pleosandroid.core.domain.driving.policy

import io.issac.pleosandroid.core.domain.driving.model.DrivingAssessment
import io.issac.pleosandroid.core.domain.driving.model.DrivingResult
import io.issac.pleosandroid.core.domain.driving.model.InsufficientReason
import io.issac.pleosandroid.core.domain.driving.model.SpeedSample
import kotlin.math.abs
import kotlin.math.exp
import kotlin.math.max
import kotlin.math.roundToInt

/** MVP policy: all thresholds are tunable product rules and must be calibrated with vehicle traces. */
data class DrivingPolicy(
    val version: Int = 1,
    val minimumMovingMillis: Long = 120_000,
    val minimumDistanceMeters: Long = 1_000,
    val minimumSamples: Int = 300,
    val minimumCoverage: Double = .9,
    val maximumIntervalMillis: Long = 2_000,
    val maximumGapMillis: Long = 10_000,
    val accelerationThreshold: Double = 2.5,
    val brakingThreshold: Double = 3.0,
    val eventHoldSeconds: Double = .6,
    val releaseSeconds: Double = .8,
)

/** Constant-memory integration. Repeated UI snapshots never substitute for timestamped speed events. */
class DrivingAnalyzer(
    private val startedNanos: Long,
    private val startedWhileMoving: Boolean = false,
    private val policy: DrivingPolicy = DrivingPolicy(),
    private val restored: DrivingResult? = null,
) {
    private var previous: SpeedSample? = null
    private var lastAcceptedTimestamp = startedNanos + (restored?.assessment?.elapsedMillis ?: 0) * 1_000_000
    private var lastTimestamp = lastAcceptedTimestamp - 1
    private var observedNanos = (restored?.assessment?.observedMillis ?: 0) * 1_000_000
    private var movingNanos = (restored?.assessment?.movingMillis ?: 0) * 1_000_000
    private var distance = restored?.distanceMeters?.toDouble() ?: 0.0
    private var samples = restored?.assessment?.sampleCount ?: 0
    private var rejected = restored?.assessment?.rejectedSamples ?: 0
    private var longestGap = (restored?.assessment?.longestGapMillis ?: 0) * 1_000_000
    private var filteredAcceleration = 0.0
    private val accelerating = EventLatch(policy)
    private val braking = EventLatch(policy)
    private var accelerations = restored?.assessment?.harshAccelerations ?: 0
    private var brakings = restored?.assessment?.harshBrakings ?: 0
    private var starts = restored?.assessment?.harshStarts ?: 0
    val accelerationCount: Int get() = accelerations
    val brakingCount: Int get() = brakings
    val distanceMeters: Long get() = distance.toLong()
    val movingMillis: Long get() = movingNanos / 1_000_000

    fun add(sample: SpeedSample) {
        // A gear/UX update may re-emit the last speed value: it is not a new observation.
        if (sample.timestampNanos <= lastTimestamp) return
        lastTimestamp = sample.timestampNanos
        if (!sample.metersPerSecond.isFinite() || abs(sample.metersPerSecond) > 85) {
            rejected++
            breakContinuity()
            return
        }
        val old = previous
        previous = sample
        if (old == null) {
            accept(sample)
            return
        }
        val interval = sample.timestampNanos - old.timestampNanos
        val dt = interval / 1e9
        if (interval > policy.maximumIntervalMillis * 1_000_000) {
            longestGap = max(longestGap, interval)
            accept(sample)
            resetEvents()
            return
        }
        val rawAcceleration = (abs(sample.metersPerSecond) - abs(old.metersPerSecond)) / dt
        if (abs(rawAcceleration) > 12) {
            rejected++
            // Discard both sides of an implausible jump; do not manufacture its return as braking.
            breakContinuity()
            return
        }
        accept(sample)
        observedNanos += interval
        val meanSpeed = (abs(old.metersPerSecond) + abs(sample.metersPerSecond)) / 2
        distance += meanSpeed * dt
        if (meanSpeed >= .5 && sample.forward && old.forward) movingNanos += interval
        if (!sample.forward || !old.forward || sample.metersPerSecond < 0 || old.metersPerSecond < 0) {
            resetEvents()
            return
        }
        filteredAcceleration += (1 - exp(-dt / .4)) * (rawAcceleration - filteredAcceleration)
        if (accelerating.update(
                filteredAcceleration >= policy.accelerationThreshold,
                filteredAcceleration < policy.accelerationThreshold * .5,
                dt,
                old.metersPerSecond < 2.8,
            )
        ) {
            accelerations++
            if (accelerating.startedLow) starts++
        }
        if (braking.update(
                -filteredAcceleration >= policy.brakingThreshold,
                -filteredAcceleration < policy.brakingThreshold * .5,
                dt,
                false,
            )
        ) {
            brakings++
        }
    }

    private fun accept(sample: SpeedSample) {
        longestGap = max(longestGap, sample.timestampNanos - lastAcceptedTimestamp)
        lastAcceptedTimestamp = sample.timestampNanos
        samples++
    }

    fun breakContinuity() {
        previous = null
        resetEvents()
    }

    private fun resetEvents() {
        filteredAcceleration = 0.0
        accelerating.reset()
        braking.reset()
    }

    fun result(endedNanos: Long, interrupted: Boolean = false): DrivingResult {
        val elapsed = ((endedNanos - startedNanos) / 1_000_000).coerceAtLeast(1)
        val observed = observedNanos / 1_000_000
        val moving = movingNanos / 1_000_000
        val tailGap = (endedNanos - lastAcceptedTimestamp).coerceAtLeast(0)
        val gap = max(longestGap, tailGap) / 1_000_000
        val reasons = buildSet {
            if (moving < policy.minimumMovingMillis) add(InsufficientReason.TooShort)
            if (distance < policy.minimumDistanceMeters) add(InsufficientReason.TooLittleDistance)
            if (samples < policy.minimumSamples) add(InsufficientReason.TooFewSamples)
            if (observed.toDouble() / elapsed < policy.minimumCoverage) add(InsufficientReason.LowCoverage)
            if (gap > policy.maximumGapMillis) add(InsufficientReason.SignalGap)
            if (rejected.toDouble() / max(1, samples + rejected) > .05) add(InsufficientReason.InvalidSignal)
            if (startedWhileMoving) add(InsufficientReason.StartedWhileMoving)
            if (interrupted || restored != null) add(InsufficientReason.Interrupted)
        }
        // Braking may be an appropriate response to a hazard. Report its count without a penalty.
        val score = if (reasons.isEmpty()) {
            accelerationReferenceScore(accelerations, moving)
        } else {
            null
        }
        return DrivingResult(
            distance.toLong(),
            score,
            DrivingAssessment(
                policy.version, elapsed, observed, moving, samples, rejected,
                accelerations, brakings, starts, gap, reasons,
            ),
        )
    }
}

fun accelerationReferenceScore(harshAccelerations: Int, movingMillis: Long): Int =
    (100 - 6 * harshAccelerations * 10 / max(5.0, movingMillis / 60_000.0)).roundToInt().coerceIn(0, 100)

private class EventLatch(private val policy: DrivingPolicy) {
    private var held = 0.0
    private var released = 0.0
    private var latched = false
    var startedLow = false
        private set

    fun update(above: Boolean, belowRelease: Boolean, seconds: Double, lowSpeed: Boolean): Boolean {
        if (latched) {
            released = if (belowRelease) released + seconds else 0.0
            if (released >= policy.releaseSeconds) reset()
            return false
        }
        if (!above) {
            held = 0.0
            return false
        }
        if (held == 0.0) startedLow = lowSpeed
        held += seconds
        if (held + 1e-6 < policy.eventHoldSeconds) return false
        latched = true
        return true
    }

    fun reset() {
        held = 0.0
        released = 0.0
        latched = false
        startedLow = false
    }
}
