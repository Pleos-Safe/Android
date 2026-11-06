package io.issac.pleosandroid.feature.records.history

import io.issac.pleosandroid.core.domain.collection.model.JourneyRecord
import io.issac.pleosandroid.core.domain.collection.repository.VisitRecordingStatus
import io.issac.pleosandroid.core.domain.driving.repository.RecordingState
import io.issac.pleosandroid.core.navigation.AppRoute
import io.issac.pleosandroid.core.ui.mvi.UiEffect
import io.issac.pleosandroid.core.ui.mvi.UiIntent
import io.issac.pleosandroid.core.ui.mvi.UiState

internal data class RecordsState(
    val loading: Boolean = true,
    val journeys: List<JourneyRecord> = emptyList(),
    val enabled: Boolean = false,
    val demo: Boolean = false,
    val recording: RecordingState = RecordingState(),
    val visitStatus: VisitRecordingStatus =
        VisitRecordingStatus.Waiting,
    val busy: Boolean = false,
    val error: Boolean = false,
    val message: String? = null,
) : UiState

internal sealed interface RecordsIntent : UiIntent {
    data class OpenJourney(val id: String) : RecordsIntent
    data class SetEnabled(val enabled: Boolean) : RecordsIntent
    data object Settings : RecordsIntent
    data object Retry : RecordsIntent
    data object RetrySave : RecordsIntent
    data object Finish : RecordsIntent
}

internal sealed interface RecordsEffect : UiEffect {
    data class Navigate(val route: AppRoute) : RecordsEffect
}
