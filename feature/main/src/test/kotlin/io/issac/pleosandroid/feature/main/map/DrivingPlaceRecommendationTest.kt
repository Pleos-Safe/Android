package io.issac.pleosandroid.feature.main.map

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

        assertEquals("gwangalli", recommendDrivingPlace(position)?.id)
        val invalid = busanTravelPlaces.first().copy(latitude = 91.0)
        assertEquals("gwangalli", recommendDrivingPlace(position, listOf(invalid) + busanTravelPlaces)?.id)
    }

    @Test
    fun testFarAwayOrInvalidPositionsDoNotRecommendAPlace() {
        assertNull(recommendDrivingPlace(PlaceMapPosition(37.5665, 126.9780)))
        assertNull(recommendDrivingPlace(PlaceMapPosition(Double.NaN, 129.115)))
        assertNull(recommendDrivingPlace(PlaceMapPosition(35.152, 129.115), places = emptyList()))
    }
}
