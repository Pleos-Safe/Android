package io.issac.pleosandroid.feature.records.report

import io.issac.pleosandroid.core.domain.collection.model.JourneyRecord
import io.issac.pleosandroid.core.ui.mvi.UiEffect
import io.issac.pleosandroid.core.ui.mvi.UiIntent
import io.issac.pleosandroid.core.ui.mvi.UiState
import io.issac.pleosandroid.feature.records.points.RecordPoints

internal data class DrivingReportState(
    val loading: Boolean = true,
    val journey: JourneyRecord? = null,
    val demo: Boolean = false,
    val error: Boolean = false,
    val points: RecordPoints = RecordPoints.Loading,
) : UiState

internal sealed interface ReportIntent : UiIntent {
    data class Load(val id: String) : ReportIntent
    data object Retry : ReportIntent
}

internal sealed interface ReportEffect : UiEffect
