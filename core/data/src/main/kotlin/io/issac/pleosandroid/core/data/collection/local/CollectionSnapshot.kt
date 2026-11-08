package io.issac.pleosandroid.core.data.collection.local

import io.issac.pleosandroid.core.data.collection.mapper.toDomain
import io.issac.pleosandroid.core.database.dao.CollectionDao
import io.issac.pleosandroid.core.domain.collection.model.ConfirmedVisit
import io.issac.pleosandroid.core.domain.collection.model.JourneyRecord
import io.issac.pleosandroid.core.domain.collection.model.Place

internal data class StoredCollection(
    val generation: String,
    val places: List<Place>,
    val visits: List<ConfirmedVisit>,
    val journeys: List<JourneyRecord>,
)

internal suspend fun CollectionDao.snapshot() = StoredCollection(
    checkNotNull(userState()).generation,
    places().map { it.toDomain() },
    visits().map { it.toDomain() },
    journeys().map { it.toDomain() },
)
