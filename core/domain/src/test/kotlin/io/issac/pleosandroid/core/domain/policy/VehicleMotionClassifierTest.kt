package io.issac.pleosandroid.core.domain.policy

import io.issac.pleosandroid.core.domain.model.VehicleMotion
import org.junit.Assert.assertEquals
import org.junit.Test

class VehicleMotionClassifierTest {
    @Test
    fun `forward and reverse movement are both moving`() {
        assertEquals(VehicleMotion.Moving, classifyVehicleMotion(5f))
        assertEquals(VehicleMotion.Moving, classifyVehicleMotion(-5f))
    }

    @Test
    fun `stationary sensor jitter does not become a trip movement`() {
        listOf(0f, 0.05f, -0.05f, 0.1f, -0.1f).forEach {
            assertEquals(VehicleMotion.Stopped, classifyVehicleMotion(it))
        }
        assertEquals(VehicleMotion.Moving, classifyVehicleMotion(0.101f))
        assertEquals(VehicleMotion.Moving, classifyVehicleMotion(-0.101f))
    }

    @Test
    fun `missing and invalid samples stay unknown`() {
        listOf(null, Float.NaN, Float.POSITIVE_INFINITY, Float.NEGATIVE_INFINITY).forEach {
            assertEquals(VehicleMotion.Unknown, classifyVehicleMotion(it))
        }
    }
}
