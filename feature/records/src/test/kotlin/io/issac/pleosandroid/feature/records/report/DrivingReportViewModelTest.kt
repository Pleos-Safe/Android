package io.issac.pleosandroid.feature.records.report

import androidx.lifecycle.ViewModelStore
import io.issac.pleosandroid.core.domain.collection.model.ConfirmedVisit
import io.issac.pleosandroid.core.domain.collection.model.HistoryQuery
import io.issac.pleosandroid.core.domain.collection.model.JourneyRecord
import io.issac.pleosandroid.core.domain.collection.repository.HistoryRepository
import io.issac.pleosandroid.core.domain.points.PointRepository
import io.issac.pleosandroid.core.domain.points.PointSpendResult
import io.issac.pleosandroid.core.domain.points.PointWallet
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.awaitCancellation
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.flow
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.test.StandardTestDispatcher
import kotlinx.coroutines.test.resetMain
import kotlinx.coroutines.test.runCurrent
import kotlinx.coroutines.test.runTest
import kotlinx.coroutines.test.setMain
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test

@OptIn(ExperimentalCoroutinesApi::class)
class DrivingReportViewModelTest {
    private val stores = mutableListOf<ViewModelStore>()

    @Before
    fun setUp() = Dispatchers.setMain(StandardTestDispatcher())

    @After
    fun tearDown() {
        stores.forEach(ViewModelStore::clear)
        Dispatchers.resetMain()
    }

    @Test
    fun `retry and changing the report replace collection without overlapping subscriptions`() = runTest {
        val history = TrackingHistory()
        val viewModel = viewModel(history)
        viewModel.handleIntent(ReportIntent.Load("first"))
        runCurrent()
        assertEquals(listOf("first"), history.requests)
        assertEquals(1, history.activeCollectors)

        viewModel.handleIntent(ReportIntent.Load("first"))
        runCurrent()
        assertEquals(listOf("first"), history.requests)

        viewModel.handleIntent(ReportIntent.Retry)
        runCurrent()
        viewModel.handleIntent(ReportIntent.Load("second"))
        runCurrent()
        assertEquals(listOf("first", "first", "second"), history.requests)
        assertEquals(1, history.activeCollectors)
        assertEquals(1, history.maximumCollectors)
        assertFalse(viewModel.state.value.error)

        stores.single().clear()
        runCurrent()
        assertEquals(0, history.activeCollectors)
    }

    @Test
    fun `retry recovers a failed subscription and preserves one active collector`() = runTest {
        val history = TrackingHistory().apply { failNextCollection = true }
        val viewModel = viewModel(history)
        viewModel.handleIntent(ReportIntent.Load("journey"))
        runCurrent()
        assertTrue(viewModel.state.value.error)
        assertFalse(viewModel.state.value.loading)
        assertEquals(0, history.activeCollectors)

        viewModel.handleIntent(ReportIntent.Retry)
        runCurrent()
        assertFalse(viewModel.state.value.error)
        assertFalse(viewModel.state.value.loading)
        assertEquals(1, history.activeCollectors)

        assertEquals(listOf("journey", "journey"), history.requests)
        assertEquals(1, history.maximumCollectors)
    }

    private fun viewModel(history: TrackingHistory): DrivingReportViewModel {
        val viewModel = DrivingReportViewModel(history, FakePoints())
        stores += ViewModelStore().apply { put("report", viewModel) }
        return viewModel
    }
}

private class TrackingHistory : HistoryRepository {
    val requests = mutableListOf<String>()
    var activeCollectors = 0
        private set
    var maximumCollectors = 0
        private set
    var failNextCollection = false

    override fun observeJourney(journeyId: String): Flow<JourneyRecord?> = flow {
        requests += journeyId
        activeCollectors++
        maximumCollectors = maxOf(maximumCollectors, activeCollectors)
        try {
            if (failNextCollection) {
                failNextCollection = false
                throw IllegalStateException("History unavailable")
            }
            emit(null)
            awaitCancellation()
        } finally {
            activeCollectors--
        }
    }

    override fun observeJourneys() = flowOf(emptyList<JourneyRecord>())
    override fun observeVisits(query: HistoryQuery) = flowOf(emptyList<ConfirmedVisit>())
}

private class FakePoints : PointRepository {
    override fun observeWallet() = flowOf(PointWallet())
    override suspend fun spendForEnhancement(operationId: String, amount: Long, dataGeneration: String) =
        PointSpendResult.InvalidInput
}
