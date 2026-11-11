package io.issac.pleosandroid.feature.records.report

import androidx.lifecycle.viewModelScope
import dagger.hilt.android.lifecycle.HiltViewModel
import io.issac.pleosandroid.core.domain.collection.repository.CollectionDemoRepository
import io.issac.pleosandroid.core.domain.collection.repository.HistoryRepository
import io.issac.pleosandroid.core.domain.points.PointRepository
import io.issac.pleosandroid.core.ui.mvi.BaseViewModel
import io.issac.pleosandroid.feature.records.points.observeRecordPoints
import javax.inject.Inject
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Job
import kotlinx.coroutines.flow.collectLatest
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.launch

@HiltViewModel
internal class DrivingReportViewModel @Inject constructor(
    private val history: HistoryRepository,
    private val demo: CollectionDemoRepository,
    private val points: PointRepository,
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
                demo.enabled.collectLatest { usingDemo ->
                    updateState { DrivingReportState(demo = usingDemo) }
                    combine(history.observeJourney(id), points.observeRecordPoints(usingDemo)) { journey, wallet ->
                        DrivingReportState(loading = false, journey = journey, demo = usingDemo, points = wallet)
                    }.collect { value -> updateState { value } }
                }
            } catch (cancelled: CancellationException) {
                throw cancelled
            } catch (_: Exception) {
                updateState { copy(loading = false, error = true) }
            }
        }
    }
}
