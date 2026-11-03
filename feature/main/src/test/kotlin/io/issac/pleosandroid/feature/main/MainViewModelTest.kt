package io.issac.pleosandroid.feature.main

import androidx.lifecycle.SavedStateHandle
import io.issac.pleosandroid.core.domain.policy.CarUxPolicy
import io.issac.pleosandroid.core.domain.model.MotionAvailability
import io.issac.pleosandroid.core.domain.model.VehicleGear
import io.issac.pleosandroid.core.domain.model.VehicleMotion
import io.issac.pleosandroid.core.domain.model.VehicleMotionState
import io.issac.pleosandroid.core.domain.repository.VehicleRepository
import io.issac.pleosandroid.core.domain.model.VehicleState
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.launch
import kotlinx.coroutines.test.StandardTestDispatcher
import kotlinx.coroutines.test.TestScope
import kotlinx.coroutines.test.UnconfinedTestDispatcher
import kotlinx.coroutines.test.resetMain
import kotlinx.coroutines.test.runCurrent
import kotlinx.coroutines.test.runTest
import kotlinx.coroutines.test.setMain
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test

@OptIn(ExperimentalCoroutinesApi::class)
class MainViewModelTest {
    private val dispatcher = StandardTestDispatcher()

    @Before
    fun setUp() {
        Dispatchers.setMain(dispatcher)
    }

    @After
    fun tearDown() {
        Dispatchers.resetMain()
    }

    @Test
    fun `departure starts automatically and parking after stopping finishes the journey`() = runTest {
        val fixture = fixture()
        assertEquals(MainPhase.BeforeDriving, fixture.ready.phase)

        fixture.repository.emit(motion = VehicleMotion.Moving, gear = VehicleGear.Drive)
        runCurrent()
        assertEquals(MainPhase.DrivingMoving, fixture.ready.phase)

        fixture.repository.emit(motion = VehicleMotion.Stopped)
        runCurrent()
        assertEquals(MainPhase.DrivingStopped, fixture.ready.phase)
        assertFalse(fixture.ready.canEndJourney)

        fixture.repository.emit(gear = VehicleGear.Park)
        runCurrent()
        assertEquals(MainPhase.Finished, fixture.ready.phase)
    }

    @Test
    fun `missing motion keeps an active journey pending instead of treating it as stopped`() = runTest {
        val fixture = fixture()
        fixture.viewModel.startJourney()
        fixture.repository.emit(motion = VehicleMotion.Unknown, availability = MotionAvailability.Error)
        runCurrent()

        assertNull(fixture.ready.phase)
        assertFalse(fixture.ready.canEndJourney)
        assertFalse(fixture.viewModel.endJourney())

        fixture.repository.emit(motion = VehicleMotion.Moving)
        runCurrent()
        assertEquals(MainPhase.DrivingMoving, fixture.ready.phase)
    }

    @Test
    fun `a new departure after finishing automatically starts another journey`() = runTest {
        val fixture = fixture()
        fixture.viewModel.startJourney()
        assertTrue(fixture.viewModel.endJourney())
        runCurrent()
        assertEquals(MainPhase.Finished, fixture.ready.phase)

        fixture.repository.emit(motion = VehicleMotion.Moving, gear = VehicleGear.Drive)
        runCurrent()
        assertEquals(MainPhase.DrivingMoving, fixture.ready.phase)
        assertFalse(fixture.viewModel.startJourney())

        fixture.repository.emit(motion = VehicleMotion.Stopped)
        runCurrent()
        assertEquals(MainPhase.DrivingStopped, fixture.ready.phase)
        assertFalse(fixture.viewModel.endJourney())
    }

    @Test
    fun `moving or unavailable vehicles cannot start or finish a journey`() = runTest {
        val fixture = fixture()
        fixture.repository.emit(motion = VehicleMotion.Moving)
        assertFalse(fixture.viewModel.startJourney())

        fixture.repository.emit(motion = VehicleMotion.Stopped)
        assertTrue(fixture.viewModel.startJourney())
        fixture.repository.emit(motion = VehicleMotion.Moving)
        assertFalse(fixture.viewModel.endJourney())

        fixture.repository.emit(motion = VehicleMotion.Unknown, availability = MotionAvailability.Connecting)
        assertFalse(fixture.viewModel.endJourney())
    }

    @Test
    fun `commands check the latest restrictions before the UI has collected them`() = runTest {
        val fixture = fixture()
        assertTrue(fixture.ready.canStartJourney)

        fixture.repository.emit(policy = CarUxPolicy())

        assertFalse(fixture.viewModel.startJourney())
        assertFalse(fixture.viewModel.canOpenBadges())
        runCurrent()
        assertFalse(fixture.ready.canOpenBadges)
    }

    @Test
    fun `finish command checks the latest speed before the UI has collected it`() = runTest {
        val fixture = fixture()
        fixture.viewModel.startJourney()
        runCurrent()
        assertTrue(fixture.ready.canEndJourney)

        fixture.repository.emit(motion = VehicleMotion.Moving)

        assertFalse(fixture.viewModel.endJourney())
    }

    @Test
    fun `restored active progress uses current vehicle motion`() = runTest {
        val fixture = fixture(
            initial = vehicle(motion = VehicleMotion.Moving, gear = VehicleGear.Drive),
            savedState = SavedStateHandle(mapOf("journey_progress" to "Active")),
        )

        assertEquals(MainPhase.DrivingMoving, fixture.ready.phase)
        fixture.repository.emit(motion = VehicleMotion.Stopped)
        runCurrent()
        assertEquals(MainPhase.DrivingStopped, fixture.ready.phase)
    }

    @Test
    fun `restored finished progress remains finished`() = runTest {
        val fixture = fixture(savedState = SavedStateHandle(mapOf("journey_progress" to "Finished")))

        assertEquals(MainPhase.Finished, fixture.ready.phase)
    }

    @Test
    fun `launching while moving detects active journey even when controls are restricted`() = runTest {
        val savedState = SavedStateHandle()
        val fixture = fixture(
            initial = vehicle(motion = VehicleMotion.Moving, gear = VehicleGear.Drive,
                policy = CarUxPolicy(connected = true, known = true)),
            savedState = savedState,
        )

        assertEquals(MainPhase.DrivingMoving, fixture.ready.phase)
        assertEquals("Active", savedState.get<String>("journey_progress"))
        assertFalse(fixture.ready.canOpenBadges)
        assertFalse(fixture.ready.canEndJourney)
    }

    @Test
    fun `manual start while parked waits for departure instead of immediately finishing`() = runTest {
        val fixture = fixture()
        assertTrue(fixture.viewModel.startJourney())
        runCurrent()
        fixture.repository.emit()
        runCurrent()

        assertEquals(MainPhase.DrivingStopped, fixture.ready.phase)
        assertTrue(fixture.ready.canEndJourney)
        fixture.repository.emit(motion = VehicleMotion.Moving, gear = VehicleGear.Drive)
        runCurrent()
        fixture.repository.emit(motion = VehicleMotion.Stopped, gear = VehicleGear.Park)
        runCurrent()
        assertEquals(MainPhase.Finished, fixture.ready.phase)
    }

    @Test
    fun `stops in drive neutral or unknown gear do not finish a trip`() = runTest {
        val fixture = fixture(initial = vehicle(motion = VehicleMotion.Moving, gear = VehicleGear.Drive))
        for (gear in listOf(VehicleGear.Drive, VehicleGear.Neutral, VehicleGear.Unknown)) {
            fixture.repository.emit(motion = VehicleMotion.Stopped, gear = gear)
            runCurrent()
            assertEquals(MainPhase.DrivingStopped, fixture.ready.phase)
            assertFalse(fixture.viewModel.endJourney())
        }
    }

    @Test
    fun `parking with unknown speed does not finish until valid stop is received`() = runTest {
        val fixture = fixture(initial = vehicle(motion = VehicleMotion.Moving, gear = VehicleGear.Drive))
        fixture.repository.emit(motion = VehicleMotion.Unknown, availability = MotionAvailability.Error,
            gear = VehicleGear.Park)
        runCurrent()
        assertNull(fixture.ready.phase)

        fixture.repository.emit(motion = VehicleMotion.Stopped)
        runCurrent()
        assertEquals(MainPhase.Finished, fixture.ready.phase)
    }

    @Test
    fun `restored preparation can wait in park and finish explicitly`() = runTest {
        val fixture = fixture(savedState = SavedStateHandle(mapOf("journey_progress" to "Prepared")))
        assertEquals(MainPhase.DrivingStopped, fixture.ready.phase)
        assertTrue(fixture.viewModel.endJourney())
    }

    @Test
    fun `retry requests a fresh repository connection`() = runTest {
        val fixture = fixture()

        fixture.viewModel.retryVehicle()

        assertEquals(1, fixture.repository.retryCount)
    }

    private fun TestScope.fixture(
        initial: VehicleState = vehicle(),
        savedState: SavedStateHandle = SavedStateHandle(),
    ): Fixture {
        val repository = FakeVehicleRepository(initial)
        val viewModel = MainViewModel(repository, savedState)
        backgroundScope.launch(UnconfinedTestDispatcher(testScheduler)) { viewModel.uiState.collect { } }
        runCurrent()
        return Fixture(repository, viewModel)
    }

    private class Fixture(val repository: FakeVehicleRepository, val viewModel: MainViewModel) {
        val ready: MainUiState.Ready get() = viewModel.uiState.value as MainUiState.Ready
    }

    private class FakeVehicleRepository(initial: VehicleState) : VehicleRepository {
        override val state = MutableStateFlow(initial)
        var retryCount = 0
            private set

        override fun retry() {
            retryCount++
        }

        fun emit(
            motion: VehicleMotion = state.value.motion.motion,
            availability: MotionAvailability = MotionAvailability.Available,
            policy: CarUxPolicy = state.value.uxPolicy,
            gear: VehicleGear = state.value.gear,
        ) {
            state.value = vehicle(motion, availability, policy, gear)
        }
    }
}

private fun vehicle(
    motion: VehicleMotion = VehicleMotion.Stopped,
    availability: MotionAvailability = MotionAvailability.Available,
    policy: CarUxPolicy = CarUxPolicy(
        connected = true,
        known = true,
        requiresDistractionOptimization = false,
        activeRestrictions = 0,
    ),
    gear: VehicleGear = VehicleGear.Park,
) = VehicleState(
    motion = VehicleMotionState(availability = availability, motion = motion, message = "차량 상태"),
    uxPolicy = policy,
    gear = gear,
)
