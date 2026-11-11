package io.issac.pleosandroid.core.domain.place

const val PLEOS_LOCATION_PERMISSION = "pleos.car.permission.FUSED_LOCATION"

sealed interface PlaceLocationState {
    data object PermissionRequired : PlaceLocationState
    data object Loading : PlaceLocationState
    data object Unavailable : PlaceLocationState
    data class Available(val position: PlaceMapPosition) : PlaceLocationState
}
