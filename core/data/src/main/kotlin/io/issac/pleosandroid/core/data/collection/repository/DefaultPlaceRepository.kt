package io.issac.pleosandroid.core.data.collection.repository

import androidx.room.withTransaction
import io.issac.pleosandroid.core.data.collection.local.CollectionStore
import io.issac.pleosandroid.core.data.collection.mapper.toDomain
import io.issac.pleosandroid.core.domain.collection.model.PlaceQuery
import io.issac.pleosandroid.core.domain.collection.model.PlaceSummary
import io.issac.pleosandroid.core.domain.collection.repository.PlaceRepository
import javax.inject.Inject
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.map

internal class DefaultPlaceRepository @Inject constructor(private val store: CollectionStore) : PlaceRepository {
    override fun observePlaces(query: PlaceQuery) = store.observeDatabase { db ->
        db.invalidationTracker.createFlow("place", "album_copy").map {
            db.withTransaction {
                val collected = db.albumDao().collectedPlaceCards().groupBy({ it.placeId }, { it.cardId })
                val text = query.text.trim()
                db.collectionDao().places().map { it.toDomain() }.filter { place ->
                    val searchable = "${place.name} ${place.district} ${place.category}"
                    place.active && (query.bounds?.contains(place) != false) &&
                        (text.isEmpty() || searchable.contains(text, ignoreCase = true))
                }.map { place ->
                    PlaceSummary(place, collected[place.id].orEmpty().toSet())
                }
            }
        }
    }.distinctUntilChanged()

    override fun observePlace(placeId: String) = observePlaces(PlaceQuery()).map { list ->
        list.find {
            it.place.id ==
                placeId
        }
    }
}
