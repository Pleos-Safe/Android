package io.issac.pleosandroid.core.domain.repository

import kotlinx.coroutines.flow.Flow

/** Cumulative visit reward packs grouped by the visited place's map region, including unopened packs. */
interface RegionVisitPackSource {
    val regionVisitPackCounts: Flow<Map<String, Int>>
}
