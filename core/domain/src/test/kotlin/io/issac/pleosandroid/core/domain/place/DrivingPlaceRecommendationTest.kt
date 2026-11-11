package io.issac.pleosandroid.core.domain.place

import io.issac.pleosandroid.core.domain.model.MotionAvailability
import io.issac.pleosandroid.core.domain.model.UxAvailability
import io.issac.pleosandroid.core.domain.model.VehicleGear
import io.issac.pleosandroid.core.domain.model.VehicleMotion
import io.issac.pleosandroid.core.domain.model.VehicleMotionState
import io.issac.pleosandroid.core.domain.model.VehicleState
import io.issac.pleosandroid.core.domain.policy.CarUxPolicy
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class DrivingPlaceRecommendationTest {
    private val driving = VehicleState(
        gear = VehicleGear.Drive,
        motion = VehicleMotionState(VehicleMotion.Moving, availability = MotionAvailability.Available),
        uxAvailability = UxAvailability.Available,
        uxPolicy = CarUxPolicy(
            connected = true,
            known = true,
            activeRestrictions = CarUxPolicy.FULLY_RESTRICTED,
            maxCumulativeContentItems = 1,
            maxContentDepth = 1,
            maxRestrictedStringLength = 24,
        ),
    )

    @Test
    fun testMovingAndStoppedInDriveAllowOneRecommendation() {
        assertTrue(driving.allowsDrivingPlaceRecommendation)
        assertTrue(
            driving.copy(motion = driving.motion.copy(motion = VehicleMotion.Stopped)).allowsDrivingPlaceRecommendation,
        )
    }

    @Test
    fun testNonDriveGearAndUnknownOrUnavailableMotionCannotRecommend() {
        for (gear in VehicleGear.entries.filter { it != VehicleGear.Drive }) {
            assertFalse(driving.copy(gear = gear).allowsDrivingPlaceRecommendation)
        }
        assertFalse(
            driving.copy(motion = driving.motion.copy(motion = VehicleMotion.Unknown)).allowsDrivingPlaceRecommendation,
        )
        assertFalse(
            driving.copy(
                motion = driving.motion.copy(availability = MotionAvailability.Error),
            ).allowsDrivingPlaceRecommendation,
        )
    }

    @Test
    fun testUnknownUxOrInsufficientActiveBudgetCannotRecommend() {
        val policy = driving.uxPolicy
        assertFalse(driving.copy(uxAvailability = UxAvailability.Error).allowsDrivingPlaceRecommendation)
        assertFalse(driving.copy(uxPolicy = policy.copy(known = false)).allowsDrivingPlaceRecommendation)
        assertFalse(driving.copy(uxPolicy = policy.copy(connected = false)).allowsDrivingPlaceRecommendation)
        assertFalse(
            driving.copy(uxPolicy = policy.copy(maxCumulativeContentItems = 0)).allowsDrivingPlaceRecommendation,
        )
        assertFalse(driving.copy(uxPolicy = policy.copy(maxContentDepth = 0)).allowsDrivingPlaceRecommendation)
        assertFalse(
            driving.copy(uxPolicy = policy.copy(maxRestrictedStringLength = 0)).allowsDrivingPlaceRecommendation,
        )
    }

    @Test
    fun testBudgetFieldsDoNotApplyWhenTheirRestrictionsAreInactive() {
        val policy = driving.uxPolicy.copy(
            activeRestrictions = CarUxPolicy.NO_KEYBOARD or CarUxPolicy.NO_FILTERING or CarUxPolicy.NO_SETUP,
            maxCumulativeContentItems = 0,
            maxContentDepth = 0,
            maxRestrictedStringLength = 0,
        )
        assertTrue(driving.copy(uxPolicy = policy).allowsDrivingPlaceRecommendation)
    }

    @Test
    fun testNearestPlaceIsRecommendedWhenWithinRange() {
        val position = PlaceMapPosition(35.152, 129.115)

        assertEquals("gwangalli", recommendDrivingPlace(position, busanTravelPlaces)?.id)
        val invalid = busanTravelPlaces.first().copy(latitude = 91.0)
        assertEquals("gwangalli", recommendDrivingPlace(position, listOf(invalid) + busanTravelPlaces)?.id)
    }

    @Test
    fun testFarAwayOrInvalidPositionsDoNotRecommendAPlace() {
        assertNull(recommendDrivingPlace(PlaceMapPosition(37.5665, 126.9780), busanTravelPlaces))
        assertNull(recommendDrivingPlace(PlaceMapPosition(Double.NaN, 129.115), busanTravelPlaces))
        assertNull(recommendDrivingPlace(PlaceMapPosition(35.152, 129.115), places = emptyList()))
    }

    @Test
    fun testRecommendationIsStableUntilUsedAndNewJourneyCanShowItAgain() {
        val session = DrivingRecommendationSession()
        val noRoute = PlaceNavigationState(hasDestination = false)
        val haeundae = PlaceMapPosition(35.1580, 129.1519)
        val gwangalli = PlaceMapPosition(35.152, 129.115)

        assertNull(session.select(1, haeundae, PlaceNavigationState(), busanTravelPlaces))
        assertEquals("haeundae", session.select(1, haeundae, noRoute, busanTravelPlaces)?.id)
        assertNull(session.select(1, null, noRoute, busanTravelPlaces))
        assertEquals("haeundae", session.select(1, gwangalli, noRoute, busanTravelPlaces)?.id)

        val route = noRoute.copy(hasDestination = true, routePlaceIds = setOf("haeundae"))
        assertEquals("gwangalli", session.select(1, gwangalli, route, busanTravelPlaces)?.id)
        assertNull(session.select(1, gwangalli, route.copy(hasWaypointCapacity = false), busanTravelPlaces))
        assertEquals("igidae", session.select(1, gwangalli, route, busanTravelPlaces)?.id)
        assertEquals("haeundae", session.select(2, haeundae, noRoute, busanTravelPlaces)?.id)
    }

    @Test
    fun catalogUpdatesReplaceRemovedCandidatesAndSearchNewPlaces() {
        val session = DrivingRecommendationSession()
        val position = PlaceMapPosition(35.158, 129.1519)
        val route = PlaceNavigationState(hasDestination = false)
        assertEquals("haeundae", session.select(1, position, route, busanTravelPlaces)?.id)
        val added = busanTravelPlaces.first().copy(id = "new", name = "새 장소")
        assertEquals("new", session.select(1, position, route, listOf(added))?.id)
        assertEquals(listOf(added), searchTravelPlaces("새 장소", null, listOf(added)))
        assertNull(session.select(1, position, route, emptyList()))
    }
}
