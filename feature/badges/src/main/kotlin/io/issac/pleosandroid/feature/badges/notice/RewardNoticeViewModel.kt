package io.issac.pleosandroid.feature.badges.notice

import androidx.lifecycle.viewModelScope
import dagger.hilt.android.lifecycle.HiltViewModel
import io.issac.pleosandroid.core.domain.collection.model.MutationResult
import io.issac.pleosandroid.core.domain.collection.model.RewardNotice
import io.issac.pleosandroid.core.domain.collection.repository.CollectionRepository
import io.issac.pleosandroid.core.domain.repository.VehicleRepository
import io.issac.pleosandroid.core.ui.mvi.BaseViewModel
import io.issac.pleosandroid.core.ui.mvi.UiIntent
import io.issac.pleosandroid.core.ui.mvi.UiState
import io.issac.pleosandroid.feature.badges.navigation.CollectionEffect
import javax.inject.Inject
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.catch
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.retryWhen
import kotlinx.coroutines.launch

internal data class RewardNoticeUiState(
    val notice: RewardNotice? = null,
    val allowed: Boolean = false,
    val failedAttempt: Int = 0,
) : UiState
internal data class AcknowledgeNotice(val id: String) : UiIntent

@HiltViewModel
internal class RewardNoticeViewModel @Inject constructor(
    private val repository: CollectionRepository,
    private val vehicle: VehicleRepository,
) : BaseViewModel<RewardNoticeUiState, AcknowledgeNotice, CollectionEffect>(RewardNoticeUiState()) {
    init {
        viewModelScope.launch {
            combine(repository.observeRewardNotices(), vehicle.state) { notices, vehicle ->
                RewardNoticeUiState(notices.firstOrNull(), vehicle.allowsTabNavigation)
            }.retryWhen { _, attempt ->
                updateState { RewardNoticeUiState() }
                if (attempt < 2) {
                    delay(1_000)
                    true
                } else {
                    false
                }
            }.catch { updateState { RewardNoticeUiState() } }
                .collect { noticeState ->
                    updateState {
                        noticeState.copy(
                            failedAttempt =
                                failedAttempt.takeIf { notice?.id == noticeState.notice?.id } ?: 0,
                        )
                    }
                }
        }
    }
    override fun processIntent(intent: AcknowledgeNotice) {
        if (!vehicle.state.value.allowsTabNavigation) return
        viewModelScope.launch {
            try {
                when (repository.acknowledgeRewardNotice(intent.id)) {
                    MutationResult.Success, MutationResult.AlreadyApplied -> Unit
                    else -> acknowledgeFailed(intent.id)
                }
            } catch (
                cancelled: CancellationException,
            ) {
                throw cancelled
            } catch (
                _: Exception,
            ) {
                acknowledgeFailed(intent.id)
            }
        }
    }

    private fun acknowledgeFailed(id: String) = updateState {
        if (notice?.id == id) copy(failedAttempt = failedAttempt + 1) else this
    }
}
