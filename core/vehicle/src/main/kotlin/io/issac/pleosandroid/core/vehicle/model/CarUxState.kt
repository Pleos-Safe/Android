package io.issac.pleosandroid.core.vehicle.model

import io.issac.pleosandroid.core.domain.model.UxAvailability
import io.issac.pleosandroid.core.domain.policy.CarUxPolicy

data class CarUxState(
    val policy: CarUxPolicy = CarUxPolicy(),
    val availability: UxAvailability = UxAvailability.Connecting,
)
