package io.issac.pleosandroid.core.domain.place

enum class PlaceNavigationOperation {
    Idle,
    Checking,
    Requesting,
    Applied,
    Rejected,
    Unconfirmed,
    Unavailable,
}

data class PlaceNavigationState(
    val hasDestination: Boolean? = null,
    val operation: PlaceNavigationOperation = PlaceNavigationOperation.Checking,
    val routePlaceIds: Set<String> = emptySet(),
    val destinationName: String? = null,
    val waypointNames: List<String> = emptyList(),
    val hasWaypointCapacity: Boolean = true,
)
