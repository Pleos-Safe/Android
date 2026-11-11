package io.issac.pleosandroid.feature.explore

import androidx.lifecycle.SavedStateHandle
import androidx.lifecycle.ViewModelStore
import io.issac.pleosandroid.core.domain.collection.model.VisitPackClaimResult
import io.issac.pleosandroid.core.domain.place.ClaimableVisit
import io.issac.pleosandroid.core.domain.place.DrivingSafetyMessage
import io.issac.pleosandroid.core.domain.place.PlaceLocationState
import io.issac.pleosandroid.core.domain.place.PlaceMapPosition
import io.issac.pleosandroid.core.domain.place.PlaceNavigationOperation
import io.issac.pleosandroid.core.domain.place.PlaceNavigationState
import io.issac.pleosandroid.core.domain.place.PlaceSessionRepository
import io.issac.pleosandroid.core.domain.place.TravelPlace
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
import org.junit.Assert.assertNull
import org.junit.Before
import org.junit.Test

@OptIn(ExperimentalCoroutinesApi::class)
class ExploreViewModelTest {
    @Before
    fun setUp() = Dispatchers.setMain(StandardTestDispatcher())

    @After
    fun tearDown() = Dispatchers.resetMain()

    @Test
    fun `search filters and selected place survive ViewModel recreation`() = runTest {
        val repository = FakePlaces()
        val savedState = SavedStateHandle()
        val first = observe(repository, savedState)
        first.handleIntent(ExploreIntent.SetQuery("해변"))
        first.handleIntent(ExploreIntent.SelectRegion("KR-26"))
        first.handleIntent(ExploreIntent.SelectPlace("busan"))
        first.handleIntent(ExploreIntent.ToggleList)
        runCurrent()

        val restored = observe(repository, savedState)
        assertEquals("해변", restored.state.value.query)
        assertEquals("KR-26", restored.state.value.region)
        assertEquals(listOf("busan"), restored.state.value.places.map { it.id })
        assertEquals("busan", restored.state.value.selected?.id)
        assertEquals(true, restored.state.value.listOnly)
    }

    @Test
    fun `a removed destination cancels waypoint confirmation without changing the route`() = runTest {
        val repository = FakePlaces()
        repository.navigationState.value = route(hasDestination = true)
        val viewModel = observe(repository)
        viewModel.handleIntent(ExploreIntent.RequestNavigation("busan", hasDestination = true))
        runCurrent()
        assertEquals("busan", viewModel.state.value.waypoint?.id)
        assertEquals(emptyList<TravelPlace>(), repository.requests)

        repository.navigationState.value = route(hasDestination = false)
        viewModel.handleIntent(ExploreIntent.ConfirmWaypoint)
        runCurrent()
        assertNull(viewModel.state.value.waypoint)
        assertEquals(emptyList<TravelPlace>(), repository.requests)

        viewModel.handleIntent(ExploreIntent.RequestNavigation("busan", hasDestination = false))
        assertEquals(listOf("busan"), repository.requests.map { it.id })
    }

    @Test
    fun `latest vehicle restriction blocks confirmation before the UI observes the change`() = runTest {
        val repository = FakePlaces()
        repository.navigationState.value = route(hasDestination = true)
        val viewModel = observe(repository)
        viewModel.handleIntent(ExploreIntent.RequestNavigation("busan", hasDestination = true))
        runCurrent()
        repository.interactive = false
        viewModel.handleIntent(ExploreIntent.ConfirmWaypoint)
        assertEquals(emptyList<TravelPlace>(), repository.requests)

        repository.interactive = true
        viewModel.handleIntent(ExploreIntent.ConfirmWaypoint)
        assertEquals(listOf("busan"), repository.requests.map { it.id })
    }

    @Test
    fun `a stale add waypoint click cannot replace a destination that just disappeared`() = runTest {
        val repository = FakePlaces()
        repository.navigationState.value = route(hasDestination = true)
        val viewModel = observe(repository)
        repository.navigationState.value = route(hasDestination = false)
        viewModel.handleIntent(ExploreIntent.RequestNavigation("busan", hasDestination = true))
        assertEquals(emptyList<TravelPlace>(), repository.requests)
    }

    private fun TestScope.observe(
        repository: FakePlaces,
        savedState: SavedStateHandle = SavedStateHandle(),
    ): ExploreViewModel {
        val viewModel = ExploreViewModel(repository, savedState)
        val store = ViewModelStore().apply { put("explore", viewModel) }
        backgroundScope.launch(UnconfinedTestDispatcher(testScheduler)) {
            try {
                viewModel.state.collect {}
            } finally {
                store.clear()
            }
        }
        runCurrent()
        return viewModel
    }
}

private class FakePlaces : PlaceSessionRepository {
    override val places = MutableStateFlow(
        listOf(
            TravelPlace("busan", "광안리 해변", 35.15, 129.12, "부산", "해변", regionId = "KR-26"),
            TravelPlace("seoul", "경복궁", 37.57, 126.97, "서울", "고궁", regionId = "KR-11"),
        ),
    )
    override val catalogError = MutableStateFlow(false)
    override val locationState = MutableStateFlow<PlaceLocationState>(PlaceLocationState.Unavailable)
    override val navigationState = MutableStateFlow(route(hasDestination = false))
    override val badgeRegionIds = MutableStateFlow(emptySet<String>())
    override val drivingSafetyMessage = MutableStateFlow<DrivingSafetyMessage?>(null)
    override val claimableVisit = MutableStateFlow<ClaimableVisit?>(null)
    override val visitResult = MutableStateFlow<VisitPackClaimResult?>(null)
    override val drivingPosition = MutableStateFlow<PlaceMapPosition?>(null)
    override val drivingPlace = MutableStateFlow<TravelPlace?>(null)
    var interactive = true
    val requests = mutableListOf<TravelPlace>()

    override fun acquire(): AutoCloseable = AutoCloseable {}
    override fun canInteract() = interactive
    override fun retryCatalog() = Unit
    override fun refreshLocation(owner: String) = Unit
    override fun stopLocation(owner: String) = Unit
    override fun request(place: TravelPlace) {
        requests += place
    }
    override fun recheck() = Unit
    override fun refreshParkedVisit() = Unit
    override fun claimVisit(visit: ClaimableVisit) = Unit
    override fun startDrivingOverview(journeyId: Long, owner: String) = Unit
    override fun stopDrivingOverview(journeyId: Long, owner: String) = Unit
    override fun requestDrivingPlace(place: TravelPlace, hasDestination: Boolean) = Unit
    override fun recheckWhileDriving() = Unit
}

private fun route(hasDestination: Boolean) = PlaceNavigationState(
    hasDestination = hasDestination,
    operation = PlaceNavigationOperation.Idle,
)
