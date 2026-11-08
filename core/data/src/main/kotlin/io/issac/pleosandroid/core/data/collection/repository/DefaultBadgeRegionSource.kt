package io.issac.pleosandroid.core.data.collection.repository

import io.issac.pleosandroid.core.data.collection.local.CollectionStore
import io.issac.pleosandroid.core.domain.repository.BadgeRegionSource
import javax.inject.Inject
import kotlinx.coroutines.flow.map

internal class DefaultBadgeRegionSource @Inject constructor(store: CollectionStore) : BadgeRegionSource {
    override val collectedRegionIds = store.observeDatabase { db ->
        db.albumDao().collectedMapRegions().map { it.toSet() }
    }
}
