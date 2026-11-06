package io.issac.pleosandroid.core.domain.driving

import io.issac.pleosandroid.core.domain.driving.model.InsufficientReason
import io.issac.pleosandroid.core.domain.driving.model.SpeedSample
import io.issac.pleosandroid.core.domain.driving.policy.DrivingAnalyzer
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class DrivingAnalyzerTest {
    @Test fun `constant speed with new timestamps is measured and integrated`() {
        val drive = Drive()
        drive.hold(10.0, 180.0)
        val result = drive.result()
        assertEquals(100, result.score)
        assertEquals(1798, result.distanceMeters)
        assertEquals(900, result.assessment.sampleCount)
        assertTrue(result.assessment.coverage > .99)
    }

    @Test fun `sustained acceleration counts once and identifies a harsh start`() {
        val drive = Drive()
        drive.hold(0.0, 3.0)
        drive.ramp(0.0, 20.0, 5.0)
        drive.hold(20.0, 180.0)
        val result = drive.result()
        assertEquals(1, result.assessment.harshAccelerations)
        assertEquals(1, result.assessment.harshStarts)
        assertEquals(88, result.score)
    }

    @Test fun `brief spikes and ordinary speed noise are not harsh events`() {
        val drive = Drive()
        repeat(900) { drive.sample(10.0 + if (it % 2 == 0) .1 else -.1) }
        drive.sample(11.0)
        drive.sample(10.0)
        assertEquals(0, drive.result().assessment.harshAccelerations)
        assertEquals(0, drive.result().assessment.harshBrakings)
    }

    @Test fun `braking is reported without assuming fault or subtracting points`() {
        val drive = Drive()
        drive.hold(15.0, 180.0)
        drive.ramp(15.0, 0.0, 4.0)
        drive.hold(0.0, 3.0)
        val result = drive.result()
        assertEquals(1, result.assessment.harshBrakings)
        assertEquals(100, result.score)
    }

    @Test fun `release hysteresis separates independent events`() {
        val drive = Drive()
        drive.hold(0.0, 3.0)
        drive.ramp(0.0, 12.0, 3.0)
        drive.hold(12.0, 5.0)
        drive.ramp(12.0, 24.0, 3.0)
        drive.hold(24.0, 180.0)
        assertEquals(2, drive.result().assessment.harshAccelerations)
        assertEquals(1, drive.result().assessment.harshStarts)
    }

    @Test fun `duplicate and reordered snapshots add neither distance nor time`() {
        val drive = Drive()
        drive.hold(10.0, 180.0)
        val before = drive.result()
        repeat(200) { drive.analyzer.add(SpeedSample(drive.now - it, 50.0, true)) }
        assertEquals(before, drive.result())
    }

    @Test fun `unavailable interval never becomes interpolated distance or an event`() {
        val drive = Drive()
        drive.hold(10.0, 180.0)
        drive.analyzer.breakContinuity()
        drive.now += 20_000_000_000L
        drive.hold(30.0, 5.0)
        val result = drive.result()
        assertNull(result.score)
        assertTrue(InsufficientReason.SignalGap in result.assessment.reasons)
        assertEquals(0, result.assessment.harshAccelerations)
        assertTrue(result.distanceMeters < 2_000)
    }

    @Test fun `implausible speed jump is discarded on both sides`() {
        val drive = Drive()
        drive.hold(10.0, 180.0)
        drive.sample(80.0)
        drive.sample(10.0)
        drive.sample(Double.NaN)
        drive.hold(10.0, 3.0)
        assertEquals(2, drive.result().assessment.rejectedSamples)
        assertEquals(0, drive.result().assessment.harshBrakings)
        assertEquals(0, drive.result().assessment.harshAccelerations)
    }

    @Test fun `short trips unknown beginnings and interrupted sessions have no score`() {
        val short = Drive().apply { hold(10.0, 10.0) }.result()
        assertNull(short.score)
        assertTrue(InsufficientReason.TooShort in short.assessment.reasons)
        val late = Drive(late = true).apply { hold(10.0, 180.0) }.result()
        assertNull(late.score)
        assertTrue(InsufficientReason.StartedWhileMoving in late.assessment.reasons)
        val drive = Drive().apply { hold(10.0, 180.0) }
        assertNull(drive.analyzer.result(drive.now, interrupted = true).score)
    }

    @Test fun `reverse motion cannot count as forward acceleration or a qualified drive`() {
        val drive = Drive()
        repeat(900) { drive.sample(-10.0, forward = false) }
        val result = drive.result()
        assertEquals(1798, result.distanceMeters)
        assertEquals(0, result.assessment.harshAccelerations)
        assertEquals(0L, result.assessment.movingMillis)
        assertNull(result.score)
    }

    @Test fun `different supported sampling rates observe the same sustained event`() {
        val results = listOf(.1, .2, .5).map { rate ->
            Drive(step = rate).apply {
                hold(0.0, 3.0)
                ramp(0.0, 20.0, 5.0)
                hold(20.0, 180.0)
            }.result()
        }
        results.forEach {
            assertEquals(1, it.assessment.harshAccelerations)
            assertEquals(88, it.score)
        }
    }

    @Test fun `a stale tail does not produce a perfect score`() {
        val drive = Drive()
        drive.hold(10.0, 180.0)
        drive.now += 30_000_000_000L
        assertNull(drive.result().score)
        assertTrue(InsufficientReason.LowCoverage in drive.result().assessment.reasons)
    }

    private class Drive(val step: Double = .2, late: Boolean = false) {
        var now = 1_000_000_000L
        val analyzer = DrivingAnalyzer(now, startedWhileMoving = late)
        fun sample(speed: Double, forward: Boolean = true) {
            now += (step * 1e9).toLong()
            analyzer.add(SpeedSample(now, speed, forward))
        }
        fun hold(speed: Double, seconds: Double) = repeat((seconds / step).toInt()) { sample(speed) }
        fun ramp(from: Double, to: Double, seconds: Double) {
            val count = (seconds / step).toInt()
            repeat(count) { sample(from + (to - from) * (it + 1) / count) }
        }
        fun result() = analyzer.result(now)
    }
}
