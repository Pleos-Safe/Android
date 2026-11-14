package io.issac.pleosandroid.feature.records.report

import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.ui.Modifier
import androidx.hilt.lifecycle.viewmodel.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import io.issac.pleosandroid.core.designsystem.component.PleosButton
import io.issac.pleosandroid.core.navigation.BadgesRoute
import io.issac.pleosandroid.core.navigation.DrivingDataStatusRoute
import io.issac.pleosandroid.core.navigation.DrivingReportRoute
import io.issac.pleosandroid.core.navigation.LocalNavigationRouter
import io.issac.pleosandroid.feature.records.component.RecordBody
import io.issac.pleosandroid.feature.records.component.RecordsPage

@Composable
internal fun DrivingReportDestination(
    journeyId: String,
    showDataStatus: Boolean,
    modifier: Modifier = Modifier,
    viewModel: DrivingReportViewModel = hiltViewModel(),
) {
    val router = LocalNavigationRouter.current
    LaunchedEffect(viewModel, journeyId) { viewModel.handleIntent(ReportIntent.Load(journeyId)) }
    val state by viewModel.state.collectAsStateWithLifecycle()
    val journey = state.journey
    val onBack: () -> Unit = { router.pop() }
    val onRetry: () -> Unit = { viewModel.handleIntent(ReportIntent.Retry) }
    when {
        state.loading || state.error || journey == null -> ReportLoadStatus(state, onBack, onRetry, modifier)

        showDataStatus -> DrivingDataStatusScreen(
            journey,
            onBack,
            { router.navigate(DrivingReportRoute(journeyId)) },
            modifier,
            state.points,
            onRetry,
        )

        else -> DrivingReportScreen(
            journey,
            onBack,
            { router.navigate(DrivingDataStatusRoute(journeyId)) },
            { router.replaceAll(BadgesRoute) },
            modifier,
            state.points,
            onRetry,
        )
    }
}

@Composable
private fun ReportLoadStatus(state: DrivingReportState, onBack: () -> Unit, onRetry: () -> Unit, modifier: Modifier) {
    RecordsPage("운전습관 기록", modifier, onBack = onBack) {
        RecordBody(
            when {
                state.loading -> "기록을 불러오고 있어요."
                state.error -> "기록을 불러오지 못했어요."
                else -> "이 여정의 기록이 없어요."
            },
        )
        if (state.error) PleosButton("다시 시도", onRetry)
    }
}
