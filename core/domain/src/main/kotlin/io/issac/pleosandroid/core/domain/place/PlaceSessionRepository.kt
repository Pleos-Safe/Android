package io.issac.pleosandroid.core.domain.place

import io.issac.pleosandroid.core.domain.collection.model.VisitPackClaimResult
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.StateFlow

/** One route writer shared by home and exploration. Close each acquired lease with its owner. */
interface PlaceSessionRepository {
    val places: StateFlow<List<TravelPlace>>
    val catalogError: StateFlow<Boolean>
    val locationState: StateFlow<PlaceLocationState>
    val navigationState: StateFlow<PlaceNavigationState>
    val regionVisitPackCounts: Flow<Map<String, Int>>
    val drivingSafetyMessage: StateFlow<DrivingSafetyMessage?>
    val claimableVisit: StateFlow<ClaimableVisit?>
    val visitResult: StateFlow<VisitPackClaimResult?>
    val drivingPosition: StateFlow<PlaceMapPosition?>
    val drivingPlace: StateFlow<TravelPlace?>

    fun acquire(): AutoCloseable
    fun canInteract(): Boolean
    fun retryCatalog()
    fun refreshLocation(owner: String = "explore")
    fun stopLocation(owner: String = "explore")
    fun request(place: TravelPlace)
    fun recheck()
    fun refreshParkedVisit()
    fun claimVisit(visit: ClaimableVisit)
    fun startDrivingOverview(journeyId: Long, owner: String = "home")
    fun stopDrivingOverview(journeyId: Long, owner: String = "home")
    fun requestDrivingPlace(place: TravelPlace, hasDestination: Boolean)
    fun recheckWhileDriving()
}

enum class DrivingSafetyMessage { Acceleration, Braking }

data class ClaimableVisit(val journeyId: String, val place: TravelPlace, val generation: String)
