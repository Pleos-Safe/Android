package io.issac.pleosandroid.core.data.collection.repository

import io.issac.pleosandroid.core.data.collection.local.CollectionStore
import io.issac.pleosandroid.core.domain.repository.RegionVisitPackSource
import javax.inject.Inject
import kotlinx.coroutines.flow.map

internal class DefaultRegionVisitPackSource @Inject constructor(store: CollectionStore) : RegionVisitPackSource {
    override val regionVisitPackCounts = store.observeDatabase { db ->
        db.collectionDao().observeRegionVisitPackCounts().map { counts ->
            counts.associate { it.regionId to it.packCount }
        }
    }
}
