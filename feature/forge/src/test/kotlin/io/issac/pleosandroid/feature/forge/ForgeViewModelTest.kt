package io.issac.pleosandroid.feature.forge

import androidx.lifecycle.ViewModelStore
import io.issac.pleosandroid.core.cardui.preview.previewPackCard
import io.issac.pleosandroid.core.domain.cards.CardActionException
import io.issac.pleosandroid.core.domain.cards.CardProblem
import io.issac.pleosandroid.core.domain.cards.PackType
import io.issac.pleosandroid.core.domain.model.VehicleGear
import io.issac.pleosandroid.core.domain.testing.TestCardAlbumRepository
import io.issac.pleosandroid.core.domain.testing.TestCardDemo
import io.issac.pleosandroid.core.domain.testing.TestCardVehicle
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
class ForgeViewModelTest {
    private val store = ViewModelStore()
    private val repository = TestCardAlbumRepository()
    private val vehicle = TestCardVehicle()

    @Before fun setUp() = Dispatchers.setMain(StandardTestDispatcher())

    @After fun tearDown() {
        store.clear()
        Dispatchers.resetMain()
    }

    private fun viewModel() = ForgeViewModel(repository, vehicle, TestCardDemo()).also { store.put("cards", it) }

    @Test fun aQueuedAttemptRechecksParkingAndKeepsTheShownRevisionAndRequest() = runTest {
        val card = previewPackCard(PackType.Busan).copy(revision = 12)
        var attempts = 0
        repository.onEnhance = { id, revision, request, generation ->
            attempts++
            assertEquals(card.id, id)
            assertEquals(12L, revision)
            assertEquals("attempt", request)
            assertEquals("real", generation)
            throw CardActionException(CardProblem.InsufficientPoints)
        }
        val model = viewModel()
        runCurrent()
        val intent = ForgeIntent.Enhance(card, "attempt")
        model.handleIntent(intent)
        vehicle.state.value = vehicle.state.value.copy(gear = VehicleGear.Drive)
        runCurrent()
        model.handleIntent(intent)
        runCurrent()
        assertEquals(0, attempts)
        assertFalse(model.state.value.busy)

        vehicle.state.value = vehicle.state.value.copy(gear = VehicleGear.Park)
        repeat(2) { model.handleIntent(intent) }
        runCurrent()
        assertEquals(1, attempts)
        assertEquals("강화 포인트가 부족해요.", model.state.value.message)
        repository.album.value = repository.album.value.copy(generation = "demo", demo = true)
        runCurrent()
        assertEquals(null, model.state.value.message)
        assertTrue(model.state.value.album!!.demo)
    }
}
