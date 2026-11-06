package io.issac.pleosandroid.core.data.collection.repository

import io.issac.pleosandroid.core.data.collection.local.CollectionStore
import io.issac.pleosandroid.core.domain.collection.model.PlaceQuery
import io.issac.pleosandroid.core.domain.collection.model.PlaceSummary
import io.issac.pleosandroid.core.domain.collection.repository.PlaceRepository
import javax.inject.Inject
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.map

internal class DefaultPlaceRepository @Inject constructor(private val store: CollectionStore) : PlaceRepository {
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
