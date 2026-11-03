package io.issac.pleosandroid.core.vehicle.datasource

import io.issac.pleosandroid.core.vehicle.model.VehicleMotionState
import kotlinx.coroutines.flow.Flow

interface VehicleMotionDataSource {
    fun observe(): Flow<VehicleMotionState>
}
