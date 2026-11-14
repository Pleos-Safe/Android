package io.issac.pleosandroid.feature.forge

import androidx.lifecycle.ViewModelStore
import io.issac.pleosandroid.core.domain.cards.CardActionException
import io.issac.pleosandroid.core.domain.cards.CardCopy
import io.issac.pleosandroid.core.domain.cards.CardGrade
import io.issac.pleosandroid.core.domain.cards.CardPrint
import io.issac.pleosandroid.core.domain.cards.CardProblem
import io.issac.pleosandroid.core.domain.cards.CopyStatus
import io.issac.pleosandroid.core.domain.cards.PackType
import io.issac.pleosandroid.core.domain.cards.PrintGroup
import io.issac.pleosandroid.core.domain.cards.ReceivedPack
import io.issac.pleosandroid.core.domain.model.VehicleGear
import io.issac.pleosandroid.core.domain.testing.TestCardAlbumRepository
import io.issac.pleosandroid.core.domain.testing.TestCardVehicle
import java.time.Instant
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

    private fun viewModel() = ForgeViewModel(repository, vehicle).also { store.put("cards", it) }

    @Test fun aQueuedAttemptRechecksParkingAndKeepsTheShownRevisionAndRequest() = runTest {
        val pack = ReceivedPack("pack", PackType.Busan, "방문 보상", Instant.EPOCH)
        val print = CardPrint(
            "busan-haeundae", "해운대의 파도", PackType.Busan, PrintGroup.Beach, CardGrade.Common,
            "print_busan_haeundae_base", null, "해운대", "BUSAN / 001",
        )
        val card = CardCopy("copy", print, false, 0, 0, CopyStatus.Owned, 12, pack, Instant.EPOCH, true)
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
        repository.album.value = repository.album.value.copy(generation = "after-reset")
        runCurrent()
        assertEquals(null, model.state.value.message)
        assertEquals("after-reset", model.state.value.album!!.generation)
    }
}
