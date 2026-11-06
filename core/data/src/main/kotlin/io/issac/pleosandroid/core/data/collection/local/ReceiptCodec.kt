package io.issac.pleosandroid.core.data.collection.local

import io.issac.pleosandroid.core.data.driving.encode
import io.issac.pleosandroid.core.domain.collection.model.CardChange
import io.issac.pleosandroid.core.domain.collection.model.CardProgress
import io.issac.pleosandroid.core.domain.collection.model.JourneyRecord
import io.issac.pleosandroid.core.domain.collection.model.LevelProgress
import io.issac.pleosandroid.core.domain.collection.model.ProgressEvent
import io.issac.pleosandroid.core.domain.collection.model.ProgressReceipt
import org.json.JSONArray
import org.json.JSONObject

internal fun ProgressReceipt.encode(): String = JSONObject().apply {
    put("id", operationId)
    put(
        "changes",
        JSONArray(
            changes.map { change ->
                JSONObject().put("id", change.cardId).put("from", change.fromLevel).put("to", change.toLevel)
            },
        ),
    )
    put(
        "progress",
        JSONArray(
            progress.map { progress ->
                JSONObject().put("id", progress.cardId).put("earned", progress.earnedLevel)
                    .put("revealed", progress.revealedLevel ?: JSONObject.NULL)
                    .put(
                        "levels",
                        JSONArray(
                            progress.levels.map { level ->
                                JSONObject().put("level", level.level).put("current", level.current)
                                    .put("required", level.required).put("pending", level.pendingAssessment)
                            },
                        ),
                    )
            },
        ),
    )
    put("notices", JSONArray(noticeIds))
}.toString()

internal fun decodeReceipt(id: String, json: String?): ProgressReceipt {
    if (json == null) return ProgressReceipt(id, emptyList(), emptyList(), emptyList())
    val root = JSONObject(json)
    return ProgressReceipt(
        root.getString("id"),
        root.getJSONArray("changes").objects().map {
            CardChange(it.getString("id"), it.getInt("from"), it.getInt("to"))
        },
        root.getJSONArray("progress").objects().map { p ->
            CardProgress(
                p.getString("id"),
                p.getInt("earned"),
                p.intOrNull("revealed"),
                p.getJSONArray("levels").objects().map { l ->
                    LevelProgress(l.getInt("level"), l.getInt("current"), l.getInt("required"), l.getBoolean("pending"))
                },
            )
        },
        root.getJSONArray("notices").let { a -> (0 until a.length()).map { a.getString(it) } },
    )
}

private fun JSONArray.objects() = (0 until length()).map { getJSONObject(it) }

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
