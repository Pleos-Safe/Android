package io.issac.pleosandroid.core.data.driving

import io.issac.pleosandroid.core.domain.collection.model.AssessmentStatus
import io.issac.pleosandroid.core.domain.collection.model.JourneyRecord
import io.issac.pleosandroid.core.domain.driving.model.DrivingAssessment
import io.issac.pleosandroid.core.domain.driving.model.InsufficientReason
import java.time.Instant
import org.json.JSONArray
import org.json.JSONObject

internal fun DrivingAssessment.encode(): String = JSONObject().apply {
    put("version", policyVersion)
    put("elapsed", elapsedMillis)
    put("observed", observedMillis)
    put("moving", movingMillis)
    put("samples", sampleCount)
    put("rejected", rejectedSamples)
    put("accelerations", harshAccelerations)
    put("brakings", harshBrakings)
    put("starts", harshStarts)
    put("gap", longestGapMillis)
    put("reasons", JSONArray(reasons.sortedBy { it.name }.map { it.name }))
}.toString()

internal fun decodeDriving(json: String?): DrivingAssessment? = json?.let {
    val value = JSONObject(it)
    val reasons = value.getJSONArray("reasons")
    DrivingAssessment(
        value.getInt("version"), value.getLong("elapsed"), value.getLong("observed"), value.getLong("moving"),
        value.getInt("samples"), value.getInt("rejected"), value.getInt("accelerations"), value.getInt("brakings"),
        value.getInt("starts"), value.getLong("gap"),
        (0 until reasons.length()).map { index -> InsufficientReason.valueOf(reasons.getString(index)) }.toSet(),
    )
}

internal fun JourneyRecord.encodeDraft(): String = JSONObject().apply {
    put("id", id)
    put("start", startedAt.toEpochMilli())
    put("end", endedAt.toEpochMilli())
    put("zone", zoneId)
    put("distance", distanceMeters)
    put("status", assessment.name)
    put("score", score ?: JSONObject.NULL)
    put("starts", harshStarts ?: JSONObject.NULL)
    put("version", assessmentVersion)
    put("driving", driving?.encode() ?: JSONObject.NULL)
}.toString()

internal fun decodeDraft(payload: String): JourneyRecord {
    val j = JSONObject(payload)
    return JourneyRecord(
        j.getString("id"), Instant.ofEpochMilli(j.getLong("start")), Instant.ofEpochMilli(j.getLong("end")),
        j.getString("zone"), j.getLong("distance"), AssessmentStatus.valueOf(j.getString("status")),
        if (j.isNull("score")) null else j.getInt("score"),
        if (j.isNull("starts")) null else j.getInt("starts"), j.getInt("version"),
        decodeDriving(if (j.isNull("driving")) null else j.getString("driving")),
    )
}
