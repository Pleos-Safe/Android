package io.issac.pleosandroid.feature.records.report

import androidx.lifecycle.viewModelScope
import dagger.hilt.android.lifecycle.HiltViewModel
import io.issac.pleosandroid.core.domain.collection.repository.HistoryRepository
import io.issac.pleosandroid.core.domain.points.PointRepository
import io.issac.pleosandroid.core.ui.flow.collectUiState
import io.issac.pleosandroid.core.ui.mvi.BaseViewModel
import io.issac.pleosandroid.feature.records.points.observeRecordPoints
import javax.inject.Inject
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.collectLatest
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.filterNotNull
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch

@HiltViewModel
internal class DrivingReportViewModel @Inject constructor(
    private val history: HistoryRepository,
    private val points: PointRepository,
) : BaseViewModel<DrivingReportState, ReportIntent, ReportEffect>(DrivingReportState()) {
    private val request = MutableStateFlow<ReportRequest?>(null)

    init {
        viewModelScope.launch {
            request.filterNotNull().collectLatest { observe(it.journeyId) }
        }
    }

    override fun processIntent(intent: ReportIntent) {
        when (intent) {
            is ReportIntent.Load -> request.update { current ->
                if (current?.journeyId == intent.id) current else ReportRequest(intent.id)
            }

            ReportIntent.Retry -> request.update { current -> current?.copy(refresh = current.refresh + 1) }
        }
    }

    private suspend fun observe(id: String) {
        combine(history.observeJourney(id), points.observeRecordPoints()) { journey, wallet ->
            DrivingReportState(loading = false, journey = journey, points = wallet)
        }.collectUiState(
            onLoading = { updateState { DrivingReportState() } },
            onError = { updateState { copy(loading = false, error = true) } },
            onState = { value -> updateState { value } },
        )
    }
}

private data class ReportRequest(val journeyId: String, val refresh: Int = 0)
