package io.issac.pleosandroid.feature.badges.reveal

import androidx.lifecycle.viewModelScope
import androidx.test.core.app.ApplicationProvider
import androidx.test.ext.junit.runners.AndroidJUnit4
import io.issac.pleosandroid.core.domain.collection.model.BeginRevealResult
import io.issac.pleosandroid.core.domain.collection.model.CardKind
import io.issac.pleosandroid.core.domain.collection.model.CollectedCard
import io.issac.pleosandroid.core.domain.collection.model.CollectionSnapshot
import io.issac.pleosandroid.core.domain.collection.model.MutationResult
import io.issac.pleosandroid.core.domain.collection.model.RevealSession
import io.issac.pleosandroid.core.domain.collection.model.RevealToken
import io.issac.pleosandroid.core.domain.collection.repository.CollectionRepository
import io.issac.pleosandroid.core.domain.collection.usecase.BeginCardRevealUseCase
import io.issac.pleosandroid.core.domain.collection.usecase.CompleteCardRevealUseCase
import io.issac.pleosandroid.core.domain.model.MotionAvailability
import io.issac.pleosandroid.core.domain.model.VehicleGear
import io.issac.pleosandroid.core.domain.model.VehicleMotion
import io.issac.pleosandroid.core.domain.model.VehicleMotionState
import io.issac.pleosandroid.core.domain.model.VehicleState
import io.issac.pleosandroid.core.domain.policy.CarUxPolicy
import io.issac.pleosandroid.core.domain.repository.VehicleRepository
import io.issac.pleosandroid.feature.badges.model.CardUiMapper
import java.time.Instant
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.cancel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.test.StandardTestDispatcher
import kotlinx.coroutines.test.resetMain
import kotlinx.coroutines.test.runCurrent
import kotlinx.coroutines.test.runTest
import kotlinx.coroutines.test.setMain
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith

@OptIn(ExperimentalCoroutinesApi::class)
@RunWith(AndroidJUnit4::class)
class CardRevealViewModelTest {
    private val cards = FakeCards()
    private val vehicle = object : VehicleRepository {
        override val state = MutableStateFlow(
            VehicleState(
                motion = VehicleMotionState(VehicleMotion.Stopped, 0f, MotionAvailability.Available),
                gear = VehicleGear.Park,
                uxPolicy = CarUxPolicy(connected = true, known = true, requiresDistractionOptimization = false),
            ),
        )
        override fun retry() = Unit
    }
    private var viewModel: CardRevealViewModel? = null
    private fun create(): CardRevealViewModel = CardRevealViewModel(
        BeginCardRevealUseCase(cards, vehicle),
        CompleteCardRevealUseCase(cards, vehicle),
        CardUiMapper(ApplicationProvider.getApplicationContext()),
        vehicle,
        cards,
    ).also {
        viewModel = it
        it.handleIntent(CardRevealIntent.Load("card"))
    }

    @After
    fun clear() {
        viewModel?.viewModelScope?.cancel()
        Dispatchers.resetMain()
    }

    @Test
    fun additionalGrowthDoesNotReplaceTheAnimatingCard() = runTest {
        Dispatchers.setMain(StandardTestDispatcher(testScheduler))
        val vm = create()
        runCurrent()
        cards.current.value = checkNotNull(cards.current.value).copy(earnedLevel = 4)
        runCurrent()
        assertEquals(3, vm.state.value.card!!.earnedLevel)
        vm.handleIntent(CardRevealIntent.AnimationCompleted(3))
        runCurrent()
        assertEquals(3, vm.state.value.card!!.revealedLevel)
        assertEquals(3, cards.completed!!.targetLevel)
    }

    @Test
    fun revealStateChangesOnlyAfterTheWriteSucceeds() = runTest {
        Dispatchers.setMain(StandardTestDispatcher(testScheduler))
        val gate = CompletableDeferred<MutationResult>()
        cards.completion = { gate.await() }
        val vm = create()
        runCurrent()
        vm.handleIntent(CardRevealIntent.AnimationCompleted(3))
        runCurrent()
        assertTrue(vm.state.value.saving)
        assertEquals(2, vm.state.value.card!!.revealedLevel)
        vm.handleIntent(CardRevealIntent.AnimationCompleted(3))
        runCurrent()
        assertEquals(1, cards.completeCalls)
        gate.complete(MutationResult.Success)
        runCurrent()
        assertFalse(vm.state.value.saving)
        assertEquals(3, vm.state.value.card!!.revealedLevel)
    }

    @Test
    fun failedSaveKeepsTheTokenForRetry() = runTest {
        Dispatchers.setMain(StandardTestDispatcher(testScheduler))
        cards.completion = { MutationResult.StorageFailure }
        val vm = create()
        runCurrent()
        vm.handleIntent(CardRevealIntent.AnimationCompleted(3))
        runCurrent()
        assertEquals(2, vm.state.value.card!!.revealedLevel)
        assertEquals(1, vm.state.value.failedAttempt)
        assertNotNull(vm.state.value.error)
        cards.completion = { MutationResult.Success }
        vm.handleIntent(CardRevealIntent.AnimationCompleted(3))
        runCurrent()
        assertEquals(3, vm.state.value.card!!.revealedLevel)
        assertNull(vm.state.value.error)
    }

    @Test
    fun deletionInvalidatesTheVisibleCardAndCompletion() = runTest {
        Dispatchers.setMain(StandardTestDispatcher(testScheduler))
        val vm = create()
        runCurrent()
        cards.current.value = null
        runCurrent()
        assertNull(vm.state.value.card)
        assertNotNull(vm.state.value.error)
        vm.handleIntent(CardRevealIntent.AnimationCompleted(3))
        runCurrent()
        assertEquals(0, cards.completeCalls)
    }

    @Test
    fun vehicleRestrictionPreventsRevealAcknowledgement() = runTest {
        Dispatchers.setMain(StandardTestDispatcher(testScheduler))
        val vm = create()
        runCurrent()
        vehicle.state.value = VehicleState()
        vm.handleIntent(CardRevealIntent.AnimationCompleted(3))
        runCurrent()
        assertEquals(0, cards.completeCalls)
        assertEquals(2, vm.state.value.card!!.revealedLevel)
    }

    private class FakeCards : CollectionRepository {
        val current = MutableStateFlow<CollectedCard?>(
            CollectedCard("card", "owner", CardKind.Safety, null, null, 3, 2, Instant.EPOCH, emptyList()),
        )
        var completion: suspend () -> MutationResult = { MutationResult.Success }
        var completeCalls = 0
        var completed: RevealToken? = null
        override fun observeCard(cardId: String) = current
        override suspend fun beginReveal(cardId: String) = BeginRevealResult.Ready(
            RevealSession(RevealToken("generation", "card", "owner", "grant", 3), checkNotNull(current.value)),
        )
        override suspend fun completeReveal(token: RevealToken): MutationResult {
            completeCalls++
            completed = token
            return completion()
        }
        override fun observeCollection() = flowOf(CollectionSnapshot(emptyList(), null, 0))
        override fun observeCardProgress(cardId: String) = flowOf(null)
        override fun observePendingReveals() = flowOf(emptyList<CollectedCard>())
        override fun observeRepresentativeCard() = flowOf(null)
        override fun observeRewardNotices() =
            flowOf(emptyList<io.issac.pleosandroid.core.domain.collection.model.RewardNotice>())
        override suspend fun setRepresentativeCard(cardId: String?) = MutationResult.Success
        override suspend fun acknowledgeRewardNotice(noticeId: String) = MutationResult.Success
    }
}
