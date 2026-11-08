package io.issac.pleosandroid.core.domain.collection.repository

import io.issac.pleosandroid.core.domain.collection.model.ConfirmedVisit
import io.issac.pleosandroid.core.domain.collection.model.DeleteHistoryRequest
import io.issac.pleosandroid.core.domain.collection.model.HistoryQuery
import io.issac.pleosandroid.core.domain.collection.model.JourneyRecord
import io.issac.pleosandroid.core.domain.collection.model.MutationResult
import io.issac.pleosandroid.core.domain.collection.model.PlaceQuery
import io.issac.pleosandroid.core.domain.collection.model.PlaceSummary
import io.issac.pleosandroid.core.domain.collection.model.ProgressEvent
import io.issac.pleosandroid.core.domain.collection.model.ProgressPreview
import io.issac.pleosandroid.core.domain.collection.model.ProgressReceipt
import io.issac.pleosandroid.core.domain.collection.model.RecordProgressResult
import kotlinx.coroutines.flow.Flow

interface ProgressRepository {
    suspend fun getDataGeneration(): String
    suspend fun recordProgress(event: ProgressEvent): RecordProgressResult
    suspend fun previewProgress(event: ProgressEvent): ProgressPreview
    suspend fun getProgressResult(operationId: String): ProgressReceipt?
    suspend fun deleteHistory(request: DeleteHistoryRequest): MutationResult
    suspend fun clearUserData(): MutationResult
}

interface PlaceRepository {
    fun observePlaces(query: PlaceQuery = PlaceQuery()): Flow<List<PlaceSummary>>
    fun observePlace(placeId: String): Flow<PlaceSummary?>
}

interface HistoryRepository {
    fun observeJourneys(): Flow<List<JourneyRecord>>
    fun observeJourney(journeyId: String): Flow<JourneyRecord?>
    fun observeVisits(query: HistoryQuery = HistoryQuery()): Flow<List<ConfirmedVisit>>
}

/** Debug implementations use a separate database. Release always reports unavailable. */
interface CollectionDemoRepository {
    val available: Boolean
    val enabled: Flow<Boolean>
    suspend fun setEnabled(enabled: Boolean)
    suspend fun resetDemo()
}
