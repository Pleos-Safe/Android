package io.issac.pleosandroid.feature.main

import androidx.lifecycle.SavedStateHandle
import androidx.lifecycle.viewModelScope
import io.issac.pleosandroid.core.domain.collection.model.AssessmentStatus
import io.issac.pleosandroid.core.domain.collection.model.ConfirmedVisit
import io.issac.pleosandroid.core.domain.collection.model.HistoryQuery
import io.issac.pleosandroid.core.domain.collection.model.JourneyRecord
import io.issac.pleosandroid.core.domain.collection.model.MutationResult
import io.issac.pleosandroid.core.domain.collection.repository.HistoryRepository
import io.issac.pleosandroid.core.domain.driving.repository.DrivingRecorder
import io.issac.pleosandroid.core.domain.driving.repository.JourneyRepository
import io.issac.pleosandroid.core.domain.driving.repository.JourneyState
import io.issac.pleosandroid.core.domain.driving.repository.RecordingPhase
import io.issac.pleosandroid.core.domain.driving.repository.RecordingState
import io.issac.pleosandroid.core.domain.driving.repository.withVehicle
import io.issac.pleosandroid.core.domain.model.MotionAvailability
import io.issac.pleosandroid.core.domain.model.VehicleGear
import io.issac.pleosandroid.core.domain.model.VehicleMotion
import io.issac.pleosandroid.core.domain.model.VehicleMotionState
import io.issac.pleosandroid.core.domain.model.VehicleState
import io.issac.pleosandroid.core.domain.policy.CarUxPolicy
import io.issac.pleosandroid.core.domain.repository.VehicleRepository
import java.time.Instant
import java.util.UUID
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.cancel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.launch
import kotlinx.coroutines.test.StandardTestDispatcher
import kotlinx.coroutines.test.TestScope
import kotlinx.coroutines.test.advanceTimeBy
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
    private val viewModels = mutableListOf<MainViewModel>()

    @Before
    fun setUp() {
        Dispatchers.setMain(dispatcher)
    }

    @After
    fun tearDown() {
        viewModels.forEach { it.viewModelScope.cancel() }
        Dispatchers.resetMain()
    }

    @Test
    fun `departure starts automatically and parking after stopping finishes the journey`() = runTest {
        val fixture = fixture()
        assertEquals(MainPhase.BeforeDriving, fixture.ready.phase)

        fixture.repository.emit(motion = VehicleMotion.Moving, gear = VehicleGear.Drive)
        runCurrent()
        assertEquals(MainPhase.DrivingMoving, fixture.ready.phase)
        val startedAt = checkNotNull(fixture.ready.journeyStartedAtMillis)

        fixture.repository.emit(motion = VehicleMotion.Stopped)
        runCurrent()
        assertEquals(MainPhase.DrivingStopped, fixture.ready.phase)
        assertFalse(fixture.ready.canReturnHome)
        assertEquals(
            fixture.journeys.state.value.session?.startedAt?.toEpochMilli(),
            fixture.ready.journeyStartedAtMillis,
        )

        fixture.repository.emit(gear = VehicleGear.Park)
        runCurrent()
        assertEquals(MainPhase.Finished, fixture.ready.phase)
        assertNull(fixture.ready.journeyStartedAtMillis)
    }

    @Test
    fun `missing motion keeps an active journey pending instead of treating it as stopped`() = runTest {
        val fixture = fixture(initial = vehicle(motion = VehicleMotion.Moving, gear = VehicleGear.Drive))
        fixture.repository.emit(
            motion = VehicleMotion.Unknown,
            availability = MotionAvailability.Error,
        )
        runCurrent()

        assertNull(fixture.ready.phase)
        assertFalse(fixture.ready.canReturnHome)

        fixture.repository.emit(motion = VehicleMotion.Moving)
        runCurrent()
        assertEquals(MainPhase.DrivingMoving, fixture.ready.phase)
    }

    @Test
    fun `a new departure after finishing automatically starts another journey`() = runTest {
        val fixture = fixture(initial = vehicle(motion = VehicleMotion.Moving, gear = VehicleGear.Drive))
        fixture.repository.emit(motion = VehicleMotion.Stopped, gear = VehicleGear.Park)
        runCurrent()
        assertEquals(MainPhase.Finished, fixture.ready.phase)

        fixture.repository.emit(motion = VehicleMotion.Moving, gear = VehicleGear.Drive)
        runCurrent()
        assertEquals(MainPhase.DrivingMoving, fixture.ready.phase)
        assertNull(fixture.ready.summary)

        fixture.repository.emit(motion = VehicleMotion.Stopped)
        runCurrent()
        assertEquals(MainPhase.DrivingStopped, fixture.ready.phase)
    }

    @Test
    fun `unavailable motion cannot start a journey`() = runTest {
        val fixture = fixture()
        fixture.repository.emit(
            motion = VehicleMotion.Moving,
            availability = MotionAvailability.Error,
            gear = VehicleGear.Drive,
        )
        runCurrent()
        assertEquals(MainPhase.BeforeDriving, fixture.ready.phase)
        assertFalse(fixture.ready.canUseTabs)
    }

    @Test
    fun `commands check the latest restrictions before the UI has collected them`() = runTest {
        val fixture = fixture()
        assertTrue(fixture.ready.canOpenBadges)

        fixture.repository.emit(policy = CarUxPolicy())

        assertFalse(fixture.viewModel.canOpenBadges())
        runCurrent()
        assertFalse(fixture.ready.canOpenBadges)
    }

    @Test
    fun `tabs require an allowed stationary vehicle in park even when the UX policy alone is permissive`() = runTest {
        val fixture = fixture()
        assertTrue(fixture.viewModel.canUseTabs())
        for (gear in listOf(VehicleGear.Drive, VehicleGear.Reverse, VehicleGear.Neutral, VehicleGear.Unknown)) {
            fixture.repository.emit(gear = gear)
            assertFalse(fixture.viewModel.canUseTabs())
            runCurrent()
            assertFalse(fixture.ready.canUseTabs)
        }
        fixture.repository.emit(gear = VehicleGear.Park, motion = VehicleMotion.Moving)
        assertFalse(fixture.viewModel.canUseTabs())
        fixture.repository.emit(motion = VehicleMotion.Unknown, availability = MotionAvailability.PermissionRequired)
        assertFalse(fixture.viewModel.canUseTabs())
        assertTrue(fixture.viewModel.canRecoverVehicle())
        fixture.repository.emit(policy = CarUxPolicy())
        assertFalse(fixture.viewModel.canRecoverVehicle())
    }

    @Test
    fun `vehicle transitions update the journey when no UI observes it`() = runTest {
        val savedState = SavedStateHandle()
        val fixture = fixture(savedState = savedState)
        advanceTimeBy(6_000)
        fixture.repository.emit(motion = VehicleMotion.Moving, gear = VehicleGear.Drive)
        runCurrent()
        assertFalse(checkNotNull(fixture.journeys.state.value.session).finished)

        advanceTimeBy(6_000)
        fixture.repository.emit(motion = VehicleMotion.Stopped, gear = VehicleGear.Park)
        runCurrent()
        assertTrue(checkNotNull(fixture.journeys.state.value.session).finished)
        assertTrue(fixture.ready.canReturnHome)
        assertTrue(checkNotNull(fixture.ready.summary).durationMillis >= 0)
    }

    @Test
    fun `legacy UI state cannot replace the shared current journey`() = runTest {
        val startedAt = 3_000L
        val fixture = fixture(
            initial = vehicle(motion = VehicleMotion.Moving, gear = VehicleGear.Drive),
            savedState = SavedStateHandle(
                mapOf("journey_progress" to "Active", "journey_start_millis" to startedAt),
            ),
        )

        assertEquals(MainPhase.DrivingMoving, fixture.ready.phase)
        assertEquals(
            fixture.journeys.state.value.session?.startedAt?.toEpochMilli(),
            fixture.ready.journeyStartedAtMillis,
        )
        fixture.repository.emit(motion = VehicleMotion.Stopped)
        runCurrent()
        assertEquals(MainPhase.DrivingStopped, fixture.ready.phase)
    }

    @Test
    fun `legacy finished progress cannot create a phantom journey`() = runTest {
        val fixture =
            fixture(savedState = SavedStateHandle(mapOf("journey_progress" to "Finished")))

        assertEquals(MainPhase.BeforeDriving, fixture.ready.phase)
    }

    @Test
    fun `returning home dismisses the summary across recreation until the next journey ends`() = runTest {
        val savedState = SavedStateHandle()
        val first = fixture(
            initial = vehicle(motion = VehicleMotion.Moving, gear = VehicleGear.Drive),
            savedState = savedState,
        )
        first.repository.emit(motion = VehicleMotion.Stopped, gear = VehicleGear.Park)
        runCurrent()

        assertEquals(MainPhase.Finished, first.ready.phase)
        assertTrue(first.ready.canReturnHome)
        assertTrue(first.viewModel.returnHome())
        runCurrent()
        assertEquals(MainPhase.BeforeDriving, first.ready.phase)
        assertFalse(first.viewModel.returnHome())

        val dismissedProgress = savedState.get<String>("journey_progress")
        assertNull(dismissedProgress)
        val restored = fixture(savedState = SavedStateHandle(mapOf("journey_progress" to dismissedProgress)))
        assertEquals(MainPhase.BeforeDriving, restored.ready.phase)
        assertFalse(restored.ready.canReturnHome)

        restored.repository.emit(motion = VehicleMotion.Moving, gear = VehicleGear.Drive)
        runCurrent()
        restored.repository.emit(motion = VehicleMotion.Stopped, gear = VehicleGear.Park)
        runCurrent()

        assertEquals(MainPhase.Finished, restored.ready.phase)
        assertTrue(restored.ready.canReturnHome)
    }

    @Test
    fun `an active journey records a summary and clears the arrival signal when driving resumes`() = runTest {
        val savedState = SavedStateHandle()
        val fixture = fixture(
            initial = vehicle(motion = VehicleMotion.Moving, gear = VehicleGear.Drive),
            savedState = savedState,
        )

        fixture.repository.emit(motion = VehicleMotion.Stopped, gear = VehicleGear.Drive)
        savedState["arrived_journey_id"] = fixture.journeys.state.value.session?.id
        runCurrent()
        assertTrue(fixture.ready.arrivedBeforeParking)

        fixture.repository.emit(motion = VehicleMotion.Moving, gear = VehicleGear.Drive)
        runCurrent()
        fixture.repository.emit(motion = VehicleMotion.Stopped, gear = VehicleGear.Drive)
        runCurrent()
        assertFalse(fixture.ready.arrivedBeforeParking)

        fixture.repository.emit(gear = VehicleGear.Park)
        runCurrent()
        assertEquals(MainPhase.Finished, fixture.ready.phase)
        assertTrue(checkNotNull(fixture.ready.summary).durationMillis >= 0)
        assertTrue(fixture.viewModel.returnHome())
        runCurrent()
        assertEquals(null, fixture.ready.summary)
    }

    @Test
    fun `returning home checks the latest park and UX restrictions before the UI updates`() = runTest {
        val fixture = fixture(initial = vehicle(motion = VehicleMotion.Moving, gear = VehicleGear.Drive))
        fixture.repository.emit(motion = VehicleMotion.Stopped, gear = VehicleGear.Park)
        runCurrent()
        assertTrue(fixture.ready.canReturnHome)

        fixture.repository.emit(gear = VehicleGear.Drive)
        assertFalse(fixture.viewModel.returnHome())
        runCurrent()
        assertFalse(fixture.ready.canReturnHome)

        fixture.repository.emit(gear = VehicleGear.Park)
        runCurrent()
        assertTrue(fixture.ready.canReturnHome)

        fixture.repository.emit(policy = CarUxPolicy())
        assertFalse(fixture.viewModel.returnHome())
        runCurrent()
        assertFalse(fixture.ready.canReturnHome)
        assertEquals(MainPhase.Finished, fixture.ready.phase)
    }

    @Test
    fun `launching while moving detects active journey even when controls are restricted`() = runTest {
        val savedState = SavedStateHandle()
        val fixture = fixture(
            initial = vehicle(
                motion = VehicleMotion.Moving,
                gear = VehicleGear.Drive,
                policy = CarUxPolicy(connected = true, known = true),
            ),
            savedState = savedState,
        )

        assertEquals(MainPhase.DrivingMoving, fixture.ready.phase)
        assertFalse(checkNotNull(fixture.journeys.state.value.session).finished)
        assertFalse(fixture.ready.canOpenBadges)
    }

    @Test
    fun `shifting into drive at standstill does not start a journey`() = runTest {
        val fixture = fixture()
        fixture.repository.emit(gear = VehicleGear.Drive)
        runCurrent()
        assertEquals(MainPhase.BeforeDriving, fixture.ready.phase)
        assertFalse(fixture.ready.canUseTabs)

        fixture.repository.emit(gear = VehicleGear.Park)
        runCurrent()
        assertEquals(MainPhase.BeforeDriving, fixture.ready.phase)
        assertNull(fixture.ready.summary)
    }

    @Test
    fun `stops in drive neutral or unknown gear do not finish a trip`() = runTest {
        val fixture =
            fixture(initial = vehicle(motion = VehicleMotion.Moving, gear = VehicleGear.Drive))
        for (gear in listOf(VehicleGear.Drive, VehicleGear.Neutral, VehicleGear.Unknown)) {
            fixture.repository.emit(motion = VehicleMotion.Stopped, gear = gear)
            runCurrent()
            assertEquals(MainPhase.DrivingStopped, fixture.ready.phase)
            assertFalse(fixture.ready.canReturnHome)
        }
    }

    @Test
    fun `parking with unknown speed does not finish until valid stop is received`() = runTest {
        val fixture =
            fixture(initial = vehicle(motion = VehicleMotion.Moving, gear = VehicleGear.Drive))
        fixture.repository.emit(
            motion = VehicleMotion.Unknown,
            availability = MotionAvailability.Error,
            gear = VehicleGear.Park,
        )
        runCurrent()
        assertNull(fixture.ready.phase)

        fixture.repository.emit(motion = VehicleMotion.Stopped)
        runCurrent()
        assertEquals(MainPhase.Finished, fixture.ready.phase)
    }

    @Test
    fun `restored manual preparation is migrated to before driving`() = runTest {
        val savedState = SavedStateHandle(mapOf("journey_progress" to "Prepared"))
        val fixture = fixture(savedState = savedState)
        assertEquals(MainPhase.BeforeDriving, fixture.ready.phase)
        assertNull(savedState.get<String>("journey_progress"))
        assertNull(fixture.ready.summary)
    }

    @Test
    fun `retry requests a fresh repository connection`() = runTest {
        val fixture = fixture()

        fixture.viewModel.retryVehicle()

        assertEquals(1, fixture.repository.retryCount)
    }

    @Test
    fun `summary opens only the record saved for this shared journey`() = runTest {
        val fixture = fixture()
        fixture.recorder.enabled.value = true
        fixture.repository.emit(motion = VehicleMotion.Moving, gear = VehicleGear.Drive)
        runCurrent()
        val session = checkNotNull(fixture.journeys.state.value.session)
        fixture.repository.emit(motion = VehicleMotion.Stopped, gear = VehicleGear.Park)
        fixture.recorder.state.value = RecordingState(RecordingPhase.Saving, session.id)
        runCurrent()
        assertNull(fixture.ready.summary?.reportId)
        assertEquals(RecordingPhase.Saving, fixture.ready.summary?.recordingPhase)
        fixture.history.record.value =
            JourneyRecord(
                "previous",
                session.startedAt,
                session.startedAt.plusSeconds(1),
                "Asia/Seoul",
                0,
                AssessmentStatus.Insufficient,
            )
        runCurrent()
        assertNull(fixture.ready.summary?.reportId)
        fixture.history.record.value = fixture.history.record.value!!.copy(id = session.id)
        runCurrent()
        assertEquals(session.id, fixture.ready.summary?.reportId)
    }

    @Test
    fun `recreated home shares the session and dismissal keeps completion`() = runTest {
        val fixture = fixture()
        fixture.repository.emit(motion = VehicleMotion.Moving, gear = VehicleGear.Drive)
        runCurrent()
        val id = fixture.journeys.state.value.session?.id
        val recreated =
            MainViewModel(fixture.repository, SavedStateHandle(), fixture.journeys, fixture.recorder, fixture.history)
        viewModels += recreated
        runCurrent()
        assertEquals(fixture.ready, recreated.uiState.value)
        fixture.repository.emit(motion = VehicleMotion.Stopped, gear = VehicleGear.Park)
        runCurrent()
        assertTrue(recreated.returnHome())
        runCurrent()
        assertEquals(id, fixture.journeys.state.value.lastFinished?.id)
        assertEquals(MainPhase.BeforeDriving, fixture.ready.phase)
    }

    private fun TestScope.fixture(
        initial: VehicleState = vehicle(),
        savedState: SavedStateHandle = SavedStateHandle(),
    ): Fixture {
        val repository = FakeVehicleRepository(initial)
        val journeys = FakeJourneys(repository)
        backgroundScope.launch { repository.state.collect { journeys.update(it) } }
        val recorder = FakeRecorder()
        val history = FakeHistory()
        val viewModel = MainViewModel(repository, savedState, journeys, recorder, history)
        viewModels += viewModel
        runCurrent()
        return Fixture(repository, viewModel, journeys, recorder, history)
    }

    private class Fixture(
        val repository: FakeVehicleRepository,
        val viewModel: MainViewModel,
        val journeys: FakeJourneys,
        val recorder: FakeRecorder,
        val history: FakeHistory,
    ) {
        val ready: MainUiState.Ready get() = viewModel.uiState.value as MainUiState.Ready
    }

    private class FakeJourneys(private val vehicle: FakeVehicleRepository) : JourneyRepository {
        override val state = MutableStateFlow(JourneyState())
        private var tick = 1_000_000_000L
        fun update(value: VehicleState) {
            tick += 1_000_000_000L
            state.value =
                state.value.withVehicle(value, Instant.ofEpochMilli(tick / 1_000_000), tick) {
                    UUID.randomUUID().toString()
                }
        }
        override fun returnHome(): Boolean {
            if (!vehicle.state.value.allowsTabNavigation || state.value.session?.finished != true) return false
            state.value = state.value.copy(session = null)
            return true
        }
    }

    private class FakeRecorder : DrivingRecorder {
        override val enabled = MutableStateFlow(false)
        override val state = MutableStateFlow(RecordingState())
        override suspend fun setEnabled(enabled: Boolean) = MutationResult.Success
        override suspend fun monitor() = Unit
        override suspend fun finish() = MutationResult.Success
        override suspend fun retrySave() = MutationResult.Success
        override fun monitoringUnavailable() = Unit
    }
    private class FakeHistory : HistoryRepository {
        val record = MutableStateFlow<JourneyRecord?>(null)
        override fun observeJourney(journeyId: String) = record.map { it?.takeIf { it.id == journeyId } }
        override fun observeJourneys() = record.map { listOfNotNull(it) }
        override fun observeVisits(query: HistoryQuery) = flowOf(emptyList<ConfirmedVisit>())
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
