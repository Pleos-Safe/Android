package io.issac.pleosandroid.core.vehicle.policy

import io.issac.pleosandroid.core.domain.model.VehicleGear
import org.junit.Assert.assertEquals
import org.junit.Test

class VehicleGearClassifierTest {
    @Test
    fun mapsSelectedTransmissionGears() {
        assertEquals(VehicleGear.Park, classifyVehicleGear(4))
        assertEquals(VehicleGear.Reverse, classifyVehicleGear(2))
        assertEquals(VehicleGear.Neutral, classifyVehicleGear(1))
        assertEquals(VehicleGear.Drive, classifyVehicleGear(8))
    }

    @Test
    fun numberedForwardGearsAreDriving() {
        listOf(16, 32, 64, 128, 256, 512, 1024, 2048, 4096).forEach { raw ->
            assertEquals(VehicleGear.Drive, classifyVehicleGear(raw))
        }
    }

    @Test
    fun missingOrInvalidValueNeverBecomesPark() {
        listOf(null, 0, -1, 3, 8192).forEach { raw ->
            assertEquals(VehicleGear.Unknown, classifyVehicleGear(raw))
        }
    }
}
