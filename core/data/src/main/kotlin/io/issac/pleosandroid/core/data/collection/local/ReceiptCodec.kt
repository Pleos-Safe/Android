package io.issac.pleosandroid.core.data.collection.local

import io.issac.pleosandroid.core.data.driving.encode
import io.issac.pleosandroid.core.domain.collection.model.JourneyRecord
import io.issac.pleosandroid.core.domain.collection.model.ProgressEvent
import io.issac.pleosandroid.core.domain.collection.model.ProgressReceipt
import org.json.JSONArray
import org.json.JSONObject

internal fun ProgressReceipt.encode(): String = JSONObject().put("id", operationId).toString()

// Older receipts include retired card-award fields. The original operation id remains stable across retries.
internal fun decodeReceipt(id: String, json: String?): ProgressReceipt =
    ProgressReceipt(json?.let { JSONObject(it).getString("id") } ?: id)

internal fun ProgressEvent.fingerprint(): String {
    val input = when (this) {
        is ProgressEvent.VisitConfirmed -> JSONArray(
            listOf(
                "visit", visit.id, visit.placeId, visit.journeyId,
                visit.arrivedAt.toString(), visit.departedAt.toString(),
                visit.confirmedAt.toString(), visit.zoneId, visit.distanceFromPlaceMeters, visit.accuracyMeters,
                visit.qualificationVersion,
            ),
        )

        is ProgressEvent.JourneyCompleted -> journey.fingerprintFields("journey")

        is ProgressEvent.JourneyAssessmentUpdated -> journey.fingerprintFields("assessment")
    }
    return fingerprint(JSONArray(listOf(dataGeneration, storageConsent, input)).toString())
}

private fun JourneyRecord.fingerprintFields(kind: String) = JSONArray(
    listOf(
        kind, id, startedAt.toString(), endedAt.toString(), zoneId, distanceMeters,
        assessment.name, score, harshStarts, assessmentVersion,
    ) + listOfNotNull(driving?.encode()),
)
