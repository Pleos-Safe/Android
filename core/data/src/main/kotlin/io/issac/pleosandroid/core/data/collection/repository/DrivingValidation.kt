package io.issac.pleosandroid.core.data.collection.repository

import io.issac.pleosandroid.core.domain.collection.model.AssessmentStatus
import io.issac.pleosandroid.core.domain.collection.model.JourneyRecord
import io.issac.pleosandroid.core.domain.driving.policy.DrivingPolicy
import io.issac.pleosandroid.core.domain.driving.policy.accelerationReferenceScore
import java.time.Duration
import kotlin.math.max

internal fun validDrivingAssessment(journey: JourneyRecord): Boolean {
    val d = journey.driving ?: return true // Existing imported records have no detailed observations.
    if (d.policyVersion < 1 || d.elapsedMillis <= 0 || d.observedMillis !in 0..d.elapsedMillis ||
        d.movingMillis !in 0..d.observedMillis || d.sampleCount < 0 || d.rejectedSamples < 0 ||
        d.harshAccelerations < 0 || d.harshBrakings < 0 || d.harshStarts !in 0..d.harshAccelerations ||
        d.longestGapMillis < 0 || d.elapsedMillis != Duration.between(journey.startedAt, journey.endedAt).toMillis()
    ) {
        return false
    }
    return if (journey.assessment == AssessmentStatus.Complete) {
        val policy = DrivingPolicy()
        d.policyVersion == policy.version && d.reasons.isEmpty() && journey.harshStarts == d.harshStarts &&
            d.movingMillis >= policy.minimumMovingMillis && journey.distanceMeters >= policy.minimumDistanceMeters &&
            d.sampleCount >= policy.minimumSamples && d.coverage >= policy.minimumCoverage &&
            d.longestGapMillis <= policy.maximumGapMillis &&
            d.rejectedSamples.toDouble() / max(1, d.sampleCount + d.rejectedSamples) <= .05 &&
            journey.score == accelerationReferenceScore(d.harshAccelerations, d.movingMillis)
    } else {
        journey.score == null
    }
}
