package io.issac.pleosandroid.feature.collection

import androidx.lifecycle.ViewModelStore
import io.issac.pleosandroid.core.domain.testing.TestCardAlbumRepository
import io.issac.pleosandroid.core.domain.testing.TestCardVehicle
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
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
class CollectionViewModelTest {
    private val store = ViewModelStore()
    private val repository = TestCardAlbumRepository()
    private val vehicle = TestCardVehicle()

    @Before fun setUp() = Dispatchers.setMain(StandardTestDispatcher())

    @After fun tearDown() {
        store.clear()
        Dispatchers.resetMain()
    }

    private fun viewModel() = CollectionViewModel(repository, vehicle).also { store.put("cards", it) }

    @Test fun retryReplacesTheObserverAndReleasesItWhenLeaving() = runTest {
        repository.failNextLoad = true
        val model = viewModel()
        runCurrent()
        assertTrue(model.state.value.loadFailed)

        model.handleIntent(CollectionIntent.Retry)
        runCurrent()
        model.handleIntent(CollectionIntent.Retry)
        runCurrent()
        assertFalse(model.state.value.loadFailed)
        assertEquals(repository.album.value, model.state.value.album)
        assertEquals(3, repository.subscriptions)
        assertEquals(1, repository.peakActive)
        store.clear()
        runCurrent()
        assertEquals(0, repository.active)
    }

    @Test fun repeatedOpenWhileSavingUsesTheShownProfileOnce() = runTest {
        val finish = CompletableDeferred<Unit>()
        val requests = mutableListOf<Pair<String, String?>>()
        repository.onOpen = { id, generation ->
            requests += id to generation
            finish.await()
            "copy"
        }
        val model = viewModel()
        runCurrent()
        repeat(2) { model.handleIntent(CollectionIntent.Open("pack")) }
        runCurrent()
        assertTrue(model.state.value.busy)
        assertEquals(listOf("pack" to "real"), requests)
        finish.complete(Unit)
        runCurrent()
        assertFalse(model.state.value.busy)
    }
}
