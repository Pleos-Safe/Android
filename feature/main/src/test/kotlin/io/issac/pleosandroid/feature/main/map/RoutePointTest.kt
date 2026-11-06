package io.issac.pleosandroid.feature.main.map

import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class RoutePointTest {
    private val gwangalli = RoutePoint(35.15089, 129.11910, "광안리 해수욕장")

    @Test
    fun zeroCoordinateReadbackMatchesOnlyTheExactTrimmedName() {
        val readback = RoutePoint(0.0, 0.0, " 광안리 해수욕장 ")

        assertTrue(readback.valid)
        assertFalse(readback.hasCoordinates)
        assertTrue(readback.matches(gwangalli))
        assertTrue(gwangalli.matches(readback))
        assertTrue(readback.matches(RoutePoint(0.0, 0.0, "광안리 해수욕장")))
        assertFalse(readback.matches(gwangalli.copy(name = "광안리")))
        assertFalse(readback.matches(gwangalli.copy(name = "")))
    }

    @Test
    fun reliableCoordinatesMatchByDistanceEvenWithDifferentNames() {
        val near = gwangalli.copy(latitude = gwangalli.latitude + 0.0002, name = "광안리")
        val far = gwangalli.copy(latitude = gwangalli.latitude + 0.0004)

        assertTrue(gwangalli.hasCoordinates)
        assertTrue(gwangalli.matches(near))
        assertFalse(gwangalli.matches(far))
        assertTrue(RoutePoint(0.0, 0.001).hasCoordinates)
    }

    @Test
    fun invalidCoordinatesCannotUseNameFallback() {
        val invalidPoints = listOf(
            RoutePoint(0.0, 0.0, " \t"),
            gwangalli.copy(latitude = Double.NaN),
            gwangalli.copy(longitude = Double.POSITIVE_INFINITY),
            gwangalli.copy(latitude = 91.0),
            gwangalli.copy(longitude = -181.0),
        )

        for (invalid in invalidPoints) {
            assertFalse(invalid.hasCoordinates)
            assertFalse(invalid.valid)
            assertFalse(invalid.matches(gwangalli))
            assertFalse(gwangalli.matches(invalid))
        }
    }
}
