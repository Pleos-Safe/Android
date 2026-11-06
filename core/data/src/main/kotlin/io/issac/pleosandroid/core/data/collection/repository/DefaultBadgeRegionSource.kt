package io.issac.pleosandroid.core.data.collection.repository

import io.issac.pleosandroid.core.data.collection.local.CollectionStore
import io.issac.pleosandroid.core.domain.repository.BadgeRegionSource
import javax.inject.Inject
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.map

internal class DefaultBadgeRegionSource @Inject constructor(store: CollectionStore) : BadgeRegionSource {
    override val collectedRegionIds = store.observe().map { state ->
        val owned = state.owned.mapTo(mutableSetOf()) { it.cardId }
        val placeIds = state.definitions.filter { it.id in owned }.mapNotNull { it.placeId }.toSet()
        // Retired places still belong to the driver's collection.
        state.places.filter { it.id in placeIds }.mapNotNull { it.regionId }.toSet()
    }.distinctUntilChanged()
}
