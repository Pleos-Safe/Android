package io.issac.pleosandroid.core.domain.repository

import kotlinx.coroutines.flow.Flow

interface BadgeRegionSource {
    val collectedRegionIds: Flow<Set<String>>
}
