package io.issac.pleosandroid.core.data.collection.repository

import io.issac.pleosandroid.core.data.collection.local.CollectionStore
import io.issac.pleosandroid.core.domain.collection.model.HistoryQuery
import io.issac.pleosandroid.core.domain.collection.repository.HistoryRepository
import javax.inject.Inject
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.map

internal class DefaultHistoryRepository @Inject constructor(private val store: CollectionStore) : HistoryRepository {
    override fun observeJourneys() = store.observe().map {
        it.journeys.sortedByDescending { journey -> journey.endedAt }
    }.distinctUntilChanged()
    override fun observeJourney(journeyId: String) = store.observe().map {
        it.journeys.find { journey ->
            journey.id ==
                journeyId
        }
    }.distinctUntilChanged()
    override fun observeVisits(query: HistoryQuery) = store.observe().map { state ->
        state.visits.filter {
            (query.placeId == null || it.placeId == query.placeId) &&
                (query.journeyId == null || it.journeyId == query.journeyId)
        }.sortedByDescending { it.confirmedAt }
    }.distinctUntilChanged()
}
