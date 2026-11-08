package io.issac.pleosandroid.core.domain.collection.model

import java.time.Instant

data class ConfirmedVisit(
    val id: String,
    val placeId: String,
    val journeyId: String? = null,
    val arrivedAt: Instant,
    val departedAt: Instant,
    val confirmedAt: Instant,
    val zoneId: String,
    val distanceFromPlaceMeters: Double,
    val accuracyMeters: Double,
    val qualificationVersion: Int = 1,
)

enum class AssessmentStatus { Pending, Insufficient, Complete }
data class JourneyRecord(
    val id: String,
    val startedAt: Instant,
    val endedAt: Instant,
    val zoneId: String,
    val distanceMeters: Long,
    val assessment: AssessmentStatus,
    val score: Int? = null,
    val harshStarts: Int? = null,
    val assessmentVersion: Int = 1,
    val driving: io.issac.pleosandroid.core.domain.driving.model.DrivingAssessment? = null,
)

sealed interface ProgressEvent {
    val operationId: String
    val dataGeneration: String
    val storageConsent: Boolean

    data class VisitConfirmed(
        override val operationId: String,
        override val dataGeneration: String,
        val visit: ConfirmedVisit,
        override val storageConsent: Boolean,
    ) : ProgressEvent

    data class JourneyCompleted(
        override val operationId: String,
        override val dataGeneration: String,
        val journey: JourneyRecord,
        override val storageConsent: Boolean,
    ) : ProgressEvent

    data class JourneyAssessmentUpdated(
        override val operationId: String,
        override val dataGeneration: String,
        val journey: JourneyRecord,
        override val storageConsent: Boolean,
    ) : ProgressEvent
}

data class ProgressReceipt(val operationId: String)

enum class ProgressRejection {
    ConsentRequired,
    InvalidInput,
    UnknownPlace,
    UnknownJourney,
    VisitNotQualified,
    StaleGeneration,
    ConflictingOperation,
    ConflictingSource,
    StaleAssessment,
    DeletedSource,
}
sealed interface RecordProgressResult {
    data class Applied(val receipt: ProgressReceipt) : RecordProgressResult
    data class AlreadyProcessed(val receipt: ProgressReceipt) : RecordProgressResult
    data class Rejected(val reason: ProgressRejection) : RecordProgressResult
    data object StorageFailure : RecordProgressResult
}

sealed interface ProgressPreview {
    data object Eligible : ProgressPreview
    data class Rejected(val reason: ProgressRejection) : ProgressPreview
    data object StorageFailure : ProgressPreview
}

data class HistoryQuery(val placeId: String? = null, val journeyId: String? = null)
enum class HistoryKind { Visit, Journey }
data class DeleteHistoryRequest(val kind: HistoryKind, val id: String, val dataGeneration: String)

sealed interface MutationResult {
    data object Success : MutationResult
    data object AlreadyApplied : MutationResult
    data object NotFound : MutationResult
    data object Restricted : MutationResult
    data object Stale : MutationResult
    data object StorageFailure : MutationResult
}
