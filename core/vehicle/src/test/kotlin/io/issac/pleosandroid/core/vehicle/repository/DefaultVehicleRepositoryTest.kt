package io.issac.pleosandroid.core.vehicle.repository

import io.issac.pleosandroid.core.domain.model.MotionAvailability
import io.issac.pleosandroid.core.domain.model.UxAvailability
import io.issac.pleosandroid.core.domain.model.VehicleGear
import io.issac.pleosandroid.core.domain.model.VehicleMotion
import io.issac.pleosandroid.core.domain.model.VehicleMotionState
import io.issac.pleosandroid.core.domain.model.VehicleState
import io.issac.pleosandroid.core.domain.policy.CarUxPolicy
import io.issac.pleosandroid.core.vehicle.datasource.CarUxDataSource
import io.issac.pleosandroid.core.vehicle.datasource.VehicleGearDataSource
import io.issac.pleosandroid.core.vehicle.datasource.VehicleMotionDataSource
import io.issac.pleosandroid.core.vehicle.model.CarUxState
import io.issac.pleosandroid.core.vehicle.repository.impl.DefaultVehicleRepository
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.collect
import kotlinx.coroutines.flow.emitAll
import kotlinx.coroutines.flow.flow
import kotlinx.coroutines.launch
import kotlinx.coroutines.test.UnconfinedTestDispatcher
import kotlinx.coroutines.test.runCurrent
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

@OptIn(ExperimentalCoroutinesApi::class)
class DefaultVehicleRepositoryTest {
    @Test
    fun sourcesStartOnlyWhenObservedAndStopAfterLastCollector() = runTest {
        val motion = FakeMotionSource()
        val ux = FakeUxSource()
        val gear = FakeGearSource()
        val repository = DefaultVehicleRepository(motion, ux, gear, backgroundScope)
        runCurrent()
        assertEquals(0, motion.starts)
        val first = backgroundScope.launch(UnconfinedTestDispatcher(testScheduler)) {
            repository.state.collect()
        }
        val second = backgroundScope.launch(UnconfinedTestDispatcher(testScheduler)) {
            repository.state.collect()
        }
        runCurrent()
        assertEquals(1, motion.starts)
        assertEquals(1, ux.starts)
        assertEquals(1, gear.starts)
        first.cancel()
        runCurrent()
        assertEquals(0, motion.stops)
        second.cancel()
        runCurrent()
        assertEquals(1, motion.stops)
        assertEquals(1, ux.stops)
        assertEquals(1, gear.stops)
        assertEquals(VehicleState(), repository.state.value)
    }

    @Test
    fun explicitRetryCancelsOldSubscriptionsBeforeStartingNewOnes() = runTest {
        val motion = FakeMotionSource()
        val ux = FakeUxSource()
        val gear = FakeGearSource()
        val repository = DefaultVehicleRepository(motion, ux, gear, backgroundScope)
        backgroundScope.launch(UnconfinedTestDispatcher(testScheduler)) {
            repository.state.collect()
        }
        runCurrent()
        repository.retry()
        runCurrent()
        assertEquals(2, motion.starts)
        assertEquals(1, motion.stops)
        assertEquals(2, ux.starts)
        assertEquals(1, ux.stops)
        assertEquals(1, motion.maxConcurrentSubscriptions)
        assertEquals(1, ux.maxConcurrentSubscriptions)
        assertEquals(2, gear.starts)
        assertEquals(1, gear.stops)
        assertEquals(1, gear.maxConcurrentSubscriptions)
    }

    @Test
    fun uxFailureClearsPriorPolicyAndCanRecoverWithoutNewScreenSubscription() = runTest {
        val motion = FakeMotionSource()
        val ux = FakeUxSource()
        val repository = DefaultVehicleRepository(motion, ux, FakeGearSource(), backgroundScope)
        backgroundScope.launch(UnconfinedTestDispatcher(testScheduler)) {
            repository.state.collect()
        }
        runCurrent()
        ux.updates.value = CarUxState(
            availability = UxAvailability.Available,
            policy = CarUxPolicy(
                connected = true,
                known = true,
                requiresDistractionOptimization = false,
            ),
        )
        motion.updates.value = VehicleMotionState(
            motion = VehicleMotion.Stopped,
            speedMetersPerSecond = 0f,
            availability = MotionAvailability.Available,
        )
        runCurrent()
        assertFalse(repository.state.value.uxPolicy.restrictionsRequired)
        ux.updates.value = CarUxState(availability = UxAvailability.Error)
        runCurrent()
        assertTrue(repository.state.value.uxPolicy.restrictionsRequired)
        assertEquals(UxAvailability.Error, repository.state.value.uxAvailability)
        ux.updates.value = CarUxState(
            availability = UxAvailability.Available,
            policy = CarUxPolicy(
                connected = true,
                known = true,
                requiresDistractionOptimization = false,
            ),
        )
        runCurrent()
        assertFalse(repository.state.value.uxPolicy.restrictionsRequired)
        assertEquals(1, ux.starts)
    }

    @Test
    fun unknownGearDoesNotInvalidateMotionOrUxAndCanRecoverIndependently() = runTest {
        val motion = FakeMotionSource()
        val ux = FakeUxSource()
        val gear = FakeGearSource()
        val repository = DefaultVehicleRepository(motion, ux, gear, backgroundScope)
        backgroundScope.launch(UnconfinedTestDispatcher(testScheduler)) {
            repository.state.collect()
        }
        motion.updates.value = VehicleMotionState(
            motion = VehicleMotion.Stopped,
            speedMetersPerSecond = 0f,
            availability = MotionAvailability.Available,
        )
        ux.updates.value = CarUxState(
            availability = UxAvailability.Available,
            policy = CarUxPolicy(
                connected = true,
                known = true,
                requiresDistractionOptimization = false,
            ),
        )
        gear.updates.value = VehicleGear.Park
        runCurrent()
        assertEquals(VehicleGear.Park, repository.state.value.gear)

        gear.updates.value = VehicleGear.Unknown
        runCurrent()
        assertEquals(VehicleGear.Unknown, repository.state.value.gear)
        assertEquals(MotionAvailability.Available, repository.state.value.motion.availability)
        assertFalse(repository.state.value.uxPolicy.restrictionsRequired)

        gear.updates.value = VehicleGear.Drive
        runCurrent()
        assertEquals(VehicleGear.Drive, repository.state.value.gear)
        assertEquals(1, motion.starts)
        assertEquals(1, ux.starts)
    }

    private class FakeMotionSource : VehicleMotionDataSource {
        val updates = MutableStateFlow(VehicleMotionState())
        var starts = 0
        var stops = 0
        var maxConcurrentSubscriptions = 0
        override fun observe(): Flow<VehicleMotionState> = flow {
            starts++
            maxConcurrentSubscriptions = maxOf(maxConcurrentSubscriptions, starts - stops)
            try {
                emitAll(updates)
            } finally {
                stops++
            }
        }
    }

    private class FakeUxSource : CarUxDataSource {
        val updates = MutableStateFlow(CarUxState())
        var starts = 0
        var stops = 0
        var maxConcurrentSubscriptions = 0
        override fun observe(): Flow<CarUxState> = flow {
            starts++
            maxConcurrentSubscriptions = maxOf(maxConcurrentSubscriptions, starts - stops)
            try {
                emitAll(updates)
            } finally {
                stops++
            }
        }
    }

    private class FakeGearSource : VehicleGearDataSource {
        val updates = MutableStateFlow(VehicleGear.Unknown)
        var starts = 0
        var stops = 0
        var maxConcurrentSubscriptions = 0
        override fun observe(): Flow<VehicleGear> = flow {
            starts++
            maxConcurrentSubscriptions = maxOf(maxConcurrentSubscriptions, starts - stops)
            try {
                emitAll(updates)
            } finally {
                stops++
            }
        }
    }
}
