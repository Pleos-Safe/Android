package io.issac.pleosandroid.feature.badges.reveal

import androidx.lifecycle.viewModelScope
import dagger.hilt.android.lifecycle.HiltViewModel
import io.issac.pleosandroid.core.domain.collection.model.BeginRevealResult
import io.issac.pleosandroid.core.domain.collection.model.MutationResult
import io.issac.pleosandroid.core.domain.collection.model.RevealToken
import io.issac.pleosandroid.core.domain.collection.repository.CollectionRepository
import io.issac.pleosandroid.core.domain.collection.usecase.BeginCardRevealUseCase
import io.issac.pleosandroid.core.domain.collection.usecase.CompleteCardRevealUseCase
import io.issac.pleosandroid.core.domain.repository.VehicleRepository
import io.issac.pleosandroid.core.ui.mvi.BaseViewModel
import io.issac.pleosandroid.feature.badges.model.CardUiMapper
import io.issac.pleosandroid.feature.badges.navigation.CollectionEffect
import io.issac.pleosandroid.feature.badges.navigation.RepresentativeCardRoute
import io.issac.pleosandroid.feature.badges.navigation.UnopenedCardsRoute
import javax.inject.Inject
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Job
import kotlinx.coroutines.flow.catch
import kotlinx.coroutines.launch

@HiltViewModel
internal class CardRevealViewModel @Inject constructor(
    private val begin: BeginCardRevealUseCase,
    private val complete: CompleteCardRevealUseCase,
    private val mapper: CardUiMapper,
    private val vehicle: VehicleRepository,
    private val repository: CollectionRepository,
) : BaseViewModel<CardRevealUiState, CardRevealIntent, CollectionEffect>(CardRevealUiState()) {
    private var token: RevealToken? = null
    private var loading: Job? = null
    private var observation: Job? = null

    override fun processIntent(intent: CardRevealIntent) {
        when (intent) {
            is CardRevealIntent.Load -> load(intent.cardId)

            is CardRevealIntent.AnimationCompleted -> finish(intent.level)

            CardRevealIntent.Back -> if (canAct()) sendEffect(CollectionEffect.Back)

            CardRevealIntent.Later -> if (canAct()) sendEffect(CollectionEffect.Navigate(UnopenedCardsRoute))

            CardRevealIntent.ViewCollection -> state.value.card?.let {
                if (canAct()) sendEffect(CollectionEffect.ShowOnShelf(it.id))
            }

            CardRevealIntent.SetRepresentative -> state.value.card?.let {
                if (canAct()) sendEffect(CollectionEffect.Navigate(RepresentativeCardRoute(it.id)))
            }
        }
    }

    private fun canAct() = vehicle.state.value.allowsTabNavigation

    private fun load(id: String) {
        if (state.value.card?.id == id && state.value.error == null) return
        loading?.cancel()
        observation?.cancel()
        token = null
        updateState { CardRevealUiState() }
        loading = viewModelScope.launch {
            try {
                when (val result = begin(id)) {
                    is BeginRevealResult.Ready -> {
                        token = result.session.token
                        updateState { copy(loading = false, card = mapper.card(result.session.card)) }
                        observeValidity(id, result.session.token.ownershipId)
                    }

                    is BeginRevealResult.AlreadyRevealed -> {
                        updateState { copy(loading = false, card = mapper.card(result.card)) }
                        observeValidity(id, result.card.ownershipId)
                    }

                    else -> updateState { copy(loading = false, error = "지금 이 카드를 열 수 없어요.") }
                }
            } catch (cancelled: CancellationException) {
                throw cancelled
            } catch (_: Exception) {
                updateState { copy(loading = false, error = "카드를 불러오지 못했어요.") }
            }
        }
    }

    private fun observeValidity(id: String, ownershipId: String) {
        observation = viewModelScope.launch {
            repository.observeCard(id).catch {
                token = null
                updateState { copy(card = null, error = "카드 상태를 확인하지 못했어요.") }
            }.collect { latest ->
                val pending = token
                if (latest == null || latest.ownershipId != ownershipId ||
                    (pending != null && latest.earnedLevel < pending.targetLevel)
                ) {
                    token = null
                    updateState { copy(card = null, saving = false, error = "카드 상태가 바뀌었어요. 다시 확인해 주세요.") }
                }
                // Higher earned levels never replace the print of an animation already in progress.
            }
        }
    }

    private fun finish(level: Int) {
        val pending = token ?: return
        if (state.value.saving || level != pending.targetLevel) return
        updateState { copy(saving = true, error = null) }
        viewModelScope.launch {
            try {
                when (complete(pending)) {
                    MutationResult.Success, MutationResult.AlreadyApplied -> {
                        token = null
                        updateState { copy(saving = false, card = card?.copy(revealedLevel = level)) }
                    }

                    else -> failed()
                }
            } catch (cancelled: CancellationException) {
                throw cancelled
            } catch (_: Exception) {
                failed()
            }
        }
    }

    private fun failed() = updateState {
        copy(
            saving = false,
            error = "공개 상태를 저장하지 못했어요. 다시 열어 주세요.",
            failedAttempt = failedAttempt + 1,
        )
    }
}
