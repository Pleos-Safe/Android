package io.issac.pleosandroid.core.domain.repository

import io.issac.pleosandroid.core.domain.model.VehicleState
import kotlinx.coroutines.flow.StateFlow

interface VehicleRepository {
    val state: StateFlow<VehicleState>
    fun retry()
}
