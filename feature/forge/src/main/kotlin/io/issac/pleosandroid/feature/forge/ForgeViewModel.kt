package io.issac.pleosandroid.feature.forge

import androidx.lifecycle.viewModelScope
import dagger.hilt.android.lifecycle.HiltViewModel
import io.issac.pleosandroid.core.domain.cards.CardActionException
import io.issac.pleosandroid.core.domain.cards.CardAlbumRepository
import io.issac.pleosandroid.core.domain.cards.CardProblem
import io.issac.pleosandroid.core.domain.collection.repository.CollectionDemoRepository
import io.issac.pleosandroid.core.domain.repository.VehicleRepository
import io.issac.pleosandroid.core.ui.flow.collectUiState
import io.issac.pleosandroid.core.ui.mvi.BaseViewModel
import io.issac.pleosandroid.core.ui.mvi.UiEffect
import javax.inject.Inject
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.collectLatest
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch

@HiltViewModel
internal class ForgeViewModel @Inject constructor(
    private val repository: CardAlbumRepository,
    private val vehicle: VehicleRepository,
    private val demo: CollectionDemoRepository,
) : BaseViewModel<ForgeState, ForgeIntent, UiEffect>(ForgeState()) {
    private val refresh = MutableStateFlow(0)
    val demoAvailable get() = demo.available

    init {
        viewModelScope.launch {
            refresh.collectLatest {
                repository.observe().collectUiState(
                    onLoading = { updateState { copy(album = null, loadFailed = false) } },
                    onError = { updateState { copy(album = null, loadFailed = true) } },
                    onState = { snapshot ->
                        updateState {
                            copy(
                                album = snapshot,
                                loadFailed = false,
                                message = message.takeIf { album?.generation == snapshot.generation },
                            )
                        }
                    },
                )
            }
        }
    }

    override fun processIntent(intent: ForgeIntent) {
        when (intent) {
            is ForgeIntent.Enhance -> mutate { generation ->
                repository.enhance(intent.card.id, intent.card.revision, intent.requestId, generation)
            }

            is ForgeIntent.Restore -> mutate { generation ->
                repository.restore(
                    intent.trace.id,
                    intent.material,
                    intent.trace.revision,
                    intent.requestId,
                    generation,
                )
            }

            is ForgeIntent.Acknowledge -> mutate { repository.acknowledge(intent.id, it) }

            is ForgeIntent.SetDemo -> if (demo.available) mutate { demo.setEnabled(intent.enabled) }

            ForgeIntent.Retry -> refresh.update { it + 1 }

            ForgeIntent.Dismiss -> updateState { copy(message = null) }
        }
    }

    private fun mutate(action: suspend (String) -> Unit) {
        val current = state.value
        val generation = current.album?.generation ?: return
        if (current.busy || !vehicle.state.value.allowsTabNavigation) return
        updateState { copy(busy = true, message = null) }
        viewModelScope.launch {
            try {
                if (vehicle.state.value.allowsTabNavigation && state.value.album?.generation == generation) {
                    action(generation)
                }
            } catch (cancelled: CancellationException) {
                throw cancelled
            } catch (error: Exception) {
                val message = when ((error as? CardActionException)?.problem) {
                    CardProblem.InsufficientPoints -> "강화 포인트가 부족해요."
                    CardProblem.InvalidMaterial -> "같은 카드·같은 색상의 +0 사본을 골라 주세요."
                    CardProblem.Changed -> "카드 상태가 바뀌었어요. 최신 결과를 확인해 주세요."
                    CardProblem.Missing, CardProblem.Unavailable -> "이 카드는 지금 사용할 수 없어요."
                    null -> "카드를 저장하지 못했어요. 다시 시도해 주세요."
                }
                updateState { if (album?.generation == generation) copy(message = message) else this }
            } finally {
                updateState { copy(busy = false) }
            }
        }
    }
}
