package io.issac.pleosandroid.core.data.collection.repository

import io.issac.pleosandroid.core.data.collection.local.CollectionStore
import io.issac.pleosandroid.core.data.collection.local.StoredCollection
import io.issac.pleosandroid.core.data.collection.local.decodeReceipt
import io.issac.pleosandroid.core.data.collection.local.encode
import io.issac.pleosandroid.core.data.collection.local.fingerprint
import io.issac.pleosandroid.core.data.collection.local.snapshot
import io.issac.pleosandroid.core.data.collection.mapper.toEntity
import io.issac.pleosandroid.core.data.points.creditJourney
import io.issac.pleosandroid.core.database.dao.CollectionDao
import io.issac.pleosandroid.core.database.entity.ProgressOperationEntity
import io.issac.pleosandroid.core.database.entity.UserDataStateEntity
import io.issac.pleosandroid.core.domain.collection.model.DeleteHistoryRequest
import io.issac.pleosandroid.core.domain.collection.model.HistoryKind
import io.issac.pleosandroid.core.domain.collection.model.JourneyRecord
import io.issac.pleosandroid.core.domain.collection.model.MutationResult
import io.issac.pleosandroid.core.domain.collection.model.ProgressEvent
import io.issac.pleosandroid.core.domain.collection.model.ProgressPreview
import io.issac.pleosandroid.core.domain.collection.model.ProgressReceipt
import io.issac.pleosandroid.core.domain.collection.model.ProgressRejection
import io.issac.pleosandroid.core.domain.collection.model.RecordProgressResult
import io.issac.pleosandroid.core.domain.collection.policy.qualifies
import io.issac.pleosandroid.core.domain.collection.repository.ProgressRepository
import java.time.ZoneId
import java.util.UUID
import javax.inject.Inject
import kotlinx.coroutines.CancellationException

internal class DefaultProgressRepository @Inject constructor(private val store: CollectionStore) : ProgressRepository {

    override suspend fun getDataGeneration(): String = store.transaction { checkNotNull(userState()).generation }

    override suspend fun recordProgress(event: ProgressEvent): RecordProgressResult = storageResult(
        failure = RecordProgressResult.StorageFailure,
    ) {
        store.transaction {
            val state = snapshot()
            validateInput(event, state)?.let { return@transaction RecordProgressResult.Rejected(it) }
            val duplicate = duplicate(event)
            if (duplicate != null) {
                if (duplicate is RecordProgressResult.AlreadyProcessed && operation(event.operationId) == null) {
                    val source = event.source()
                    insertOperation(
                        ProgressOperationEntity(
                            event.operationId,
                            source.kind,
                            source.id,
                            source.version,
                            event.fingerprint(),
                            duplicate.receipt.encode(),
                        ),
                    )
                }
                return@transaction duplicate
            }
            validateQualification(event, state)?.let { return@transaction RecordProgressResult.Rejected(it) }
            when (event) {
                is ProgressEvent.VisitConfirmed -> insertVisit(event.visit.toEntity())

                is ProgressEvent.JourneyCompleted -> {
                    putJourney(event.journey.toEntity())
                    creditJourney(event.journey)
                }

                is ProgressEvent.JourneyAssessmentUpdated -> putJourney(event.journey.toEntity())
            }
            val receipt = ProgressReceipt(event.operationId)
            val source = event.source()
            insertOperation(
                ProgressOperationEntity(
                    event.operationId,
                    source.kind,
                    source.id,
                    source.version,
                    event.fingerprint(),
                    receipt.encode(),
                ),
            )
            touch()
            RecordProgressResult.Applied(receipt)
        }
    }

    override suspend fun previewProgress(event: ProgressEvent): ProgressPreview = storageResult(
        failure = ProgressPreview.StorageFailure,
    ) {
        store.transaction {
            val state = snapshot()
            validateInput(event, state)?.let { return@transaction ProgressPreview.Rejected(it) }
            when (val duplicate = duplicate(event)) {
                is RecordProgressResult.Rejected -> return@transaction ProgressPreview.Rejected(duplicate.reason)
                is RecordProgressResult.AlreadyProcessed -> return@transaction ProgressPreview.Eligible
                else -> Unit
            }
            validateQualification(event, state)?.let { return@transaction ProgressPreview.Rejected(it) }
            ProgressPreview.Eligible
        }
    }

    override suspend fun getProgressResult(operationId: String): ProgressReceipt? = store.transaction {
        operation(operationId)?.takeUnless { it.invalidated }?.let { decodeReceipt(it.id, it.receipt) }
    }

    override suspend fun deleteHistory(request: DeleteHistoryRequest): MutationResult =
        storageResult(MutationResult.StorageFailure) {
            store.transaction {
                val state = snapshot()
                if (state.generation != request.dataGeneration) return@transaction MutationResult.Stale
                val visitIds = when (request.kind) {
                    HistoryKind.Visit -> state.visits.filter { it.id == request.id }.map { it.id }
                    HistoryKind.Journey -> state.visits.filter { it.journeyId == request.id }.map { it.id }
                }
                val removed = when (request.kind) {
                    HistoryKind.Visit -> deleteVisit(request.id)
                    HistoryKind.Journey -> deleteJourney(request.id)
                }
                if (removed == 0) return@transaction MutationResult.NotFound
                visitIds.forEach { invalidateSource("visit", it) }
                if (request.kind == HistoryKind.Journey) {
                    invalidateSource("journey", request.id)
                    // Keep settled points and cap usage, but remove the deleted journey's assessment details.
                    redactPointAssessment("journey:${request.id}")
                }
                redactReceipts()
                touch()
                MutationResult.Success
            }
        }

    override suspend fun clearUserData(): MutationResult = storageResult(MutationResult.StorageFailure) {
        store.resetProfile {
            clearOwned()
            clearVisits()
            clearJourneys()
            clearOperations()
            clearDrivingDraft()
            clearPointTransactions()
            clearVisitPacks()
            putUserState(
                UserDataStateEntity(
                    generation = UUID.randomUUID().toString(),
                    revision =
                        checkNotNull(userState()).revision + 1,
                ),
            )
            MutationResult.Success
        }
    }

    private fun validateInput(event: ProgressEvent, state: StoredCollection): ProgressRejection? {
        if (event.dataGeneration != state.generation) return ProgressRejection.StaleGeneration
        if (!event.storageConsent) return ProgressRejection.ConsentRequired
        if (event.operationId.isBlank()) return ProgressRejection.InvalidInput
        return when (event) {
            is ProgressEvent.VisitConfirmed -> {
                val v = event.visit
                if (v.id.isBlank() || v.qualificationVersion < 1 ||
                    !validZone(v.zoneId) || !v.distanceFromPlaceMeters.isFinite() || !v.accuracyMeters.isFinite() ||
                    v.distanceFromPlaceMeters < 0 || v.accuracyMeters < 0
                ) {
                    return ProgressRejection.InvalidInput
                }
                null
            }

            else -> {
                val j = checkNotNull(event.journeyOrNull())
                if (j.id.isBlank() || !validZone(j.zoneId) || !j.endedAt.isAfter(j.startedAt) ||
                    j.distanceMeters < 0 || j.assessmentVersion < 1 || (j.score != null && j.score !in 0..100) ||
                    (j.harshStarts?.let { it < 0 } == true) || !validDrivingAssessment(j)
                ) {
                    ProgressRejection.InvalidInput
                } else {
                    null
                }
            }
        }
    }

    private fun validateQualification(event: ProgressEvent, state: StoredCollection): ProgressRejection? {
        if (event !is ProgressEvent.VisitConfirmed) return null
        val visit = event.visit
        val place = state.places.find { it.id == visit.placeId } ?: return ProgressRejection.UnknownPlace
        if (visit.journeyId != null && state.journeys.none { it.id == visit.journeyId }) {
            return ProgressRejection.UnknownJourney
        }
        return if (place.qualifies(visit)) null else ProgressRejection.VisitNotQualified
    }

    private suspend fun CollectionDao.duplicate(event: ProgressEvent): RecordProgressResult? {
        val source = event.source()
        val operations = sourceOperations(source.kind, source.id)
        if (operations.any { it.invalidated }) return RecordProgressResult.Rejected(ProgressRejection.DeletedSource)
        operation(event.operationId)?.let {
            return if (!it.invalidated && it.fingerprint == event.fingerprint()) {
                RecordProgressResult.AlreadyProcessed(decodeReceipt(it.id, it.receipt))
            } else {
                RecordProgressResult.Rejected(ProgressRejection.ConflictingOperation)
            }
        }
        if (event is ProgressEvent.VisitConfirmed) {
            visits().find { it.id == event.visit.id }?.let { existing ->
                return if (existing == event.visit.toEntity()) {
                    val previous = operations.firstOrNull()
                    RecordProgressResult.AlreadyProcessed(
                        decodeReceipt(previous?.id ?: event.operationId, previous?.receipt),
                    )
                } else {
                    RecordProgressResult.Rejected(ProgressRejection.ConflictingSource)
                }
            }
        } else {
            val journey = checkNotNull(event.journeyOrNull())
            val existing = journeys().find { it.id == journey.id }
            if (existing == null && event is ProgressEvent.JourneyAssessmentUpdated) {
                return RecordProgressResult.Rejected(ProgressRejection.UnknownJourney)
            }
            if (existing != null) {
                if (existing == journey.toEntity()) {
                    val previous = operations.maxByOrNull { it.sourceVersion }
                    return RecordProgressResult.AlreadyProcessed(
                        decodeReceipt(previous?.id ?: event.operationId, previous?.receipt),
                    )
                }
                if (event !is ProgressEvent.JourneyAssessmentUpdated) {
                    return RecordProgressResult.Rejected(
                        ProgressRejection.ConflictingSource,
                    )
                }
                if (journey.assessmentVersion <=
                    existing.assessmentVersion
                ) {
                    return RecordProgressResult.Rejected(ProgressRejection.StaleAssessment)
                }
                if (journey.startedAt.toEpochMilli() != existing.startedAt ||
                    journey.endedAt.toEpochMilli() != existing.endedAt ||
                    journey.zoneId != existing.zoneId || journey.distanceMeters != existing.distanceMeters
                ) {
                    return RecordProgressResult.Rejected(ProgressRejection.ConflictingSource)
                }
            }
        }
        return null
    }
}

internal data class ProgressSource(val kind: String, val id: String, val version: Int)
internal fun ProgressEvent.source(): ProgressSource = when (this) {
    is ProgressEvent.VisitConfirmed -> ProgressSource("visit", visit.id, visit.qualificationVersion)
    else -> checkNotNull(journeyOrNull()).let { ProgressSource("journey", it.id, it.assessmentVersion) }
}
internal fun ProgressEvent.journeyOrNull(): JourneyRecord? = when (this) {
    is ProgressEvent.VisitConfirmed -> null
    is ProgressEvent.JourneyCompleted -> journey
    is ProgressEvent.JourneyAssessmentUpdated -> journey
}
private fun validZone(value: String): Boolean = try {
    ZoneId.of(value)
    true
} catch (_: Exception) {
    false
}

internal suspend fun <T> storageResult(failure: T, action: suspend () -> T): T = try {
    action()
} catch (cancelled: CancellationException) {
    throw cancelled
} catch (_: android.database.SQLException) {
    failure
} catch (_: java.io.IOException) {
    failure
} catch (_: java.security.GeneralSecurityException) {
    failure
}
