package io.issac.pleosandroid.core.domain.place

import kotlinx.coroutines.flow.Flow

interface JourneyArrivalSource {
    fun arrivals(): Flow<Unit>
}
