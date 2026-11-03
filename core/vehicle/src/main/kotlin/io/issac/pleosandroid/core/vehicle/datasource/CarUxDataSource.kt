package io.issac.pleosandroid.core.vehicle.datasource

import io.issac.pleosandroid.core.vehicle.model.CarUxState
import kotlinx.coroutines.flow.Flow

interface CarUxDataSource {
    fun observe(): Flow<CarUxState>
}
