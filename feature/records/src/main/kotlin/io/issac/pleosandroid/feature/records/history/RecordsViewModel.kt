package io.issac.pleosandroid.feature.records.history

import androidx.lifecycle.viewModelScope
import dagger.hilt.android.lifecycle.HiltViewModel
import io.issac.pleosandroid.core.domain.collection.model.MutationResult
import io.issac.pleosandroid.core.domain.collection.repository.CollectionDemoRepository
import io.issac.pleosandroid.core.domain.collection.repository.HistoryRepository
import io.issac.pleosandroid.core.domain.collection.repository.VisitRecorder
import io.issac.pleosandroid.core.domain.driving.repository.DrivingRecorder
import io.issac.pleosandroid.core.domain.repository.VehicleRepository
import io.issac.pleosandroid.core.navigation.DrivingReportRoute
import io.issac.pleosandroid.core.navigation.RecordingSettingsRoute
import io.issac.pleosandroid.core.ui.mvi.BaseViewModel
import javax.inject.Inject
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Job
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.launch

@HiltViewModel
internal class RecordsViewModel @Inject constructor(
    private val history: HistoryRepository,
    private val recorder: DrivingRecorder,
    private val demo: CollectionDemoRepository,
    private val vehicle: VehicleRepository,
    visits: VisitRecorder,
) : BaseViewModel<RecordsState, RecordsIntent, RecordsEffect>(RecordsState()) {
    private var observation: Job? = null
    private val visits = visits.status

    init {
        observe()
    }

    private fun observe() {
        observation?.cancel()
        observation = viewModelScope.launch {
            updateState { copy(loading = true, error = false) }
            try {
                combine(history.observeJourneys(), recorder.enabled, recorder.state, demo.enabled, visits) {
                        journeys,
                        enabled,
                        recording,
                        demo,
                        visitStatus,
                    ->
                    RecordsState(
                        loading = false,
                        journeys = journeys.sortedByDescending { it.endedAt },
                        enabled = enabled,
                        recording = recording,
                        demo = demo,
                        visitStatus = visitStatus,
                    )
                }.collect { value -> updateState { value.copy(busy = busy, message = message) } }
            } catch (cancelled: CancellationException) {
                throw cancelled
            } catch (_: Exception) {
                updateState { copy(loading = false, error = true) }
            }
        }
    }

    override fun processIntent(intent: RecordsIntent) {
        if (!vehicle.state.value.allowsTabNavigation) return
        when (intent) {
            is RecordsIntent.OpenJourney -> if (state.value.journeys.any { it.id == intent.id }) {
                sendEffect(RecordsEffect.Navigate(DrivingReportRoute(intent.id)))
            }

            RecordsIntent.Settings -> sendEffect(RecordsEffect.Navigate(RecordingSettingsRoute))

            RecordsIntent.Retry -> observe()

            is RecordsIntent.SetEnabled -> mutate { recorder.setEnabled(intent.enabled) }

            RecordsIntent.RetrySave -> mutate { recorder.retrySave() }

            RecordsIntent.Finish -> mutate { recorder.finish() }
        }
    }

    private fun mutate(action: suspend () -> MutationResult) {
        if (state.value.busy) return
        updateState { copy(busy = true, message = null) }
        viewModelScope.launch {
            try {
                val result = action()
                updateState {
                    copy(
                        busy = false,
                        message = when (result) {
                            MutationResult.Success, MutationResult.AlreadyApplied -> null
                            MutationResult.Restricted -> "주차 상태와 데모 설정을 확인해 주세요."
                            MutationResult.Stale -> "기록이 변경됐어요. 다음 여정부터 새로 기록해요."
                            else -> "저장하지 못했어요. 다시 시도해 주세요."
                        },
                    )
                }
            } catch (cancelled: CancellationException) {
                throw cancelled
            } catch (_: Exception) {
                updateState { copy(busy = false, message = "저장하지 못했어요. 다시 시도해 주세요.") }
            }
        }
    }
}
