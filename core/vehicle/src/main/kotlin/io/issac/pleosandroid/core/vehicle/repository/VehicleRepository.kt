package io.issac.pleosandroid.core.vehicle.repository

import io.issac.pleosandroid.core.vehicle.model.VehicleState
import kotlinx.coroutines.flow.StateFlow

interface VehicleRepository {
    val state: StateFlow<VehicleState>
    fun retry()
}
