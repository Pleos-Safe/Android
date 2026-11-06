package io.issac.pleosandroid.feature.records.report

import androidx.lifecycle.viewModelScope
import dagger.hilt.android.lifecycle.HiltViewModel
import io.issac.pleosandroid.core.domain.collection.model.JourneyRecord
import io.issac.pleosandroid.core.domain.collection.repository.CollectionDemoRepository
import io.issac.pleosandroid.core.domain.collection.repository.HistoryRepository
import io.issac.pleosandroid.core.ui.mvi.BaseViewModel
import io.issac.pleosandroid.core.ui.mvi.UiEffect
import io.issac.pleosandroid.core.ui.mvi.UiIntent
import io.issac.pleosandroid.core.ui.mvi.UiState
import javax.inject.Inject
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Job
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.launch

internal data class DrivingReportState(
    val loading: Boolean = true,
    val journey: JourneyRecord? = null,
    val demo: Boolean = false,
    val error: Boolean = false,
) : UiState

internal sealed interface ReportIntent : UiIntent {
    data class Load(val id: String) : ReportIntent
    data object Retry : ReportIntent
}
internal sealed interface ReportEffect : UiEffect

@HiltViewModel
internal class DrivingReportViewModel @Inject constructor(
    private val history: HistoryRepository,
    private val demo: CollectionDemoRepository,
) : BaseViewModel<DrivingReportState, ReportIntent, ReportEffect>(DrivingReportState()) {
    private var id: String? = null
    private var observation: Job? = null

    override fun processIntent(intent: ReportIntent) {
        when (intent) {
            is ReportIntent.Load -> if (id != intent.id) {
                id = intent.id
                observe(intent.id)
            }

            ReportIntent.Retry -> id?.let(::observe)
        }
    }

    private fun observe(id: String) {
        observation?.cancel()
        observation = viewModelScope.launch {
            updateState { copy(loading = true, error = false) }
            try {
                combine(history.observeJourney(id), demo.enabled) { journey, demo ->
                    DrivingReportState(loading = false, journey = journey, demo = demo)
                }.collect { value -> updateState { value } }
            } catch (cancelled: CancellationException) {
                throw cancelled
            } catch (_: Exception) {
                updateState { copy(loading = false, error = true) }
            }
        }
    }
}
