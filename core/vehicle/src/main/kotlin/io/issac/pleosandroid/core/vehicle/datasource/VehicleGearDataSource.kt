package io.issac.pleosandroid.core.vehicle.datasource

import io.issac.pleosandroid.core.vehicle.model.VehicleGear
import kotlinx.coroutines.flow.Flow

interface VehicleGearDataSource {
    fun observe(): Flow<VehicleGear>
}
