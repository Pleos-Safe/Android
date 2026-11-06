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

data class CardChange(val cardId: String, val fromLevel: Int, val toLevel: Int)
data class LevelProgress(val level: Int, val current: Int, val required: Int, val pendingAssessment: Boolean) {
    val remaining: Int get() = (required - current).coerceAtLeast(0)
}
data class CardProgress(
    val cardId: String,
    val earnedLevel: Int,
    val revealedLevel: Int?,
    val levels: List<LevelProgress>,
) {
    val isOwned get() = earnedLevel > 0
    val nextLevel get() = levels.firstOrNull { it.level > earnedLevel }
    val hasPendingReveal get() = earnedLevel > (revealedLevel ?: 0)
}

data class ProgressReceipt(
    val operationId: String,
    val changes: List<CardChange>,
    val progress: List<CardProgress>,
    val noticeIds: List<String>,
) {
    val newCards get() = changes.filter { it.fromLevel == 0 && it.toLevel > 0 }
    val upgrades get() = changes.filter { it.fromLevel > 0 && it.toLevel > it.fromLevel }
    val adjustments get() = changes.filter { it.toLevel < it.fromLevel }
}

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
    data class Eligible(val changes: List<CardChange>, val progress: List<CardProgress>) : ProgressPreview
    data class Rejected(val reason: ProgressRejection) : ProgressPreview
    data object StorageFailure : ProgressPreview
}

data class HistoryQuery(val placeId: String? = null, val journeyId: String? = null)
enum class HistoryKind { Visit, Journey }
data class DeleteHistoryRequest(val kind: HistoryKind, val id: String, val dataGeneration: String)
data class ReevaluateRulesRequest(val operationId: String, val cardIds: Set<String>, val dataGeneration: String)

sealed interface MutationResult {
    data object Success : MutationResult
    data object AlreadyApplied : MutationResult
    data object NotFound : MutationResult
    data object Restricted : MutationResult
    data object Stale : MutationResult
    data object StorageFailure : MutationResult
}
