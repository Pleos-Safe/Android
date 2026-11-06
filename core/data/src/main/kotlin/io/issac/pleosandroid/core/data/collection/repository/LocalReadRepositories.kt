package io.issac.pleosandroid.core.data.collection.repository

import io.issac.pleosandroid.core.data.collection.local.CollectionStore
import io.issac.pleosandroid.core.domain.collection.model.HistoryQuery
import io.issac.pleosandroid.core.domain.collection.model.PlaceQuery
import io.issac.pleosandroid.core.domain.collection.model.PlaceSummary
import io.issac.pleosandroid.core.domain.collection.repository.HistoryRepository
import io.issac.pleosandroid.core.domain.collection.repository.PlaceRepository
import io.issac.pleosandroid.core.domain.repository.BadgeRegionSource
import javax.inject.Inject
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.map

internal class LocalPlaceRepository @Inject constructor(private val store: CollectionStore) : PlaceRepository {
    override fun observePlaces(query: PlaceQuery) = store.observe().map { state ->
        val text = query.text.trim()
        state.places.filter { place ->
            val searchable = "${place.name} ${place.district} ${place.category}"
            place.active && (query.bounds?.contains(place) != false) &&
                (text.isEmpty() || searchable.contains(text, ignoreCase = true))
        }.map { place ->
            val ids = state.definitions.filter { it.placeId == place.id }.map { it.id }.toSet()
            PlaceSummary(place, state.owned.filter { it.cardId in ids }.map { it.cardId }.toSet())
        }
    }.distinctUntilChanged()

    override fun observePlace(placeId: String) = observePlaces(PlaceQuery()).map { list ->
        list.find {
            it.place.id ==
                placeId
        }
    }
}

internal class LocalHistoryRepository @Inject constructor(private val store: CollectionStore) : HistoryRepository {
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

internal class LocalBadgeRegionSource @Inject constructor(store: CollectionStore) : BadgeRegionSource {
    override val collectedRegionIds = store.observe().map { state ->
        val owned = state.owned.mapTo(mutableSetOf()) { it.cardId }
        val placeIds = state.definitions.filter { it.id in owned }.mapNotNull { it.placeId }.toSet()
        // Retired places still belong to the driver's collection.
        state.places.filter { it.id in placeIds }.mapNotNull { it.regionId }.toSet()
    }.distinctUntilChanged()
}
