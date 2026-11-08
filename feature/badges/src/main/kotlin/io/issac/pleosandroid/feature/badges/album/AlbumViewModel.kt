package io.issac.pleosandroid.feature.badges.album

import androidx.lifecycle.viewModelScope
import dagger.hilt.android.lifecycle.HiltViewModel
import io.issac.pleosandroid.core.domain.cards.AlbumSnapshot
import io.issac.pleosandroid.core.domain.cards.CardActionException
import io.issac.pleosandroid.core.domain.cards.CardAlbumRepository
import io.issac.pleosandroid.core.domain.cards.CardCopy
import io.issac.pleosandroid.core.domain.cards.CardProblem
import io.issac.pleosandroid.core.domain.collection.repository.CollectionDemoRepository
import io.issac.pleosandroid.core.domain.repository.VehicleRepository
import io.issac.pleosandroid.core.ui.mvi.BaseViewModel
import io.issac.pleosandroid.core.ui.mvi.UiEffect
import io.issac.pleosandroid.core.ui.mvi.UiIntent
import io.issac.pleosandroid.core.ui.mvi.UiState
import javax.inject.Inject
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Job
import kotlinx.coroutines.flow.catch
import kotlinx.coroutines.launch

internal data class AlbumState(
    val album: AlbumSnapshot? = null,
    val busy: Boolean = false,
    val message: String? = null,
) : UiState

internal sealed interface AlbumIntent : UiIntent {
    data class Open(val id: String) : AlbumIntent
    data class Reveal(val id: String) : AlbumIntent
    data class Feature(val id: String) : AlbumIntent
    data class Enhance(val card: CardCopy, val requestId: String) : AlbumIntent
    data class Restore(val trace: CardCopy, val material: String, val requestId: String) : AlbumIntent
    data class Acknowledge(val id: String) : AlbumIntent
    data class Demo(val enabled: Boolean) : AlbumIntent
    data object Retry : AlbumIntent
    data object Dismiss : AlbumIntent
}

@HiltViewModel
internal class AlbumViewModel @Inject constructor(
    private val repository: CardAlbumRepository,
    private val vehicle: VehicleRepository,
    private val demo: CollectionDemoRepository,
) : BaseViewModel<AlbumState, AlbumIntent, UiEffect>(AlbumState()) {
    val demoAvailable get() = demo.available
    val demoEnabled get() = demo.enabled
    private var observation: Job? = null

    init {
        observe()
    }

    private fun observe() {
        observation?.cancel()
        observation = viewModelScope.launch {
            repository.observe().catch {
                updateState { copy(album = null, message = "보관함을 불러오지 못했어요.") }
            }.collect { snapshot -> updateState { copy(album = snapshot) } }
        }
    }

    override fun processIntent(intent: AlbumIntent) {
        if (intent == AlbumIntent.Dismiss || intent == AlbumIntent.Retry) {
            updateState { copy(message = null) }
            if (intent == AlbumIntent.Retry) observe()
            return
        }
        if (state.value.busy || !vehicle.state.value.allowsTabNavigation) return
        val generation = state.value.album?.generation ?: return
        updateState { copy(busy = true, message = null) }
        viewModelScope.launch {
            try {
                when (intent) {
                    is AlbumIntent.Open -> repository.open(intent.id, generation)

                    is AlbumIntent.Reveal -> repository.reveal(intent.id, generation)

                    is AlbumIntent.Feature -> repository.feature(intent.id, generation)

                    is AlbumIntent.Enhance -> repository.enhance(
                        intent.card.id,
                        intent.card.revision,
                        intent.requestId,
                        generation,
                    )

                    is AlbumIntent.Restore -> repository.restore(
                        intent.trace.id,
                        intent.material,
                        intent.trace.revision,
                        intent.requestId,
                        generation,
                    )

                    is AlbumIntent.Acknowledge -> repository.acknowledge(intent.id, generation)

                    is AlbumIntent.Demo -> demo.setEnabled(intent.enabled)

                    AlbumIntent.Dismiss, AlbumIntent.Retry -> Unit
                }
            } catch (error: CardActionException) {
                updateState {
                    copy(
                        message = when (error.problem) {
                            CardProblem.InsufficientPoints -> "강화 포인트가 부족해요."
                            CardProblem.InvalidMaterial -> "같은 카드·같은 색상의 +0 사본을 골라 주세요."
                            CardProblem.Changed -> "카드 상태가 바뀌었어요. 최신 결과를 확인해 주세요."
                            CardProblem.Missing, CardProblem.Unavailable -> "이 카드는 지금 사용할 수 없어요."
                        },
                    )
                }
            } catch (cancel: CancellationException) {
                throw cancel
            } catch (_: Exception) {
                updateState { copy(message = "카드를 저장하지 못했어요. 다시 시도해 주세요.") }
            } finally {
                updateState { copy(busy = false) }
            }
        }
    }
}
