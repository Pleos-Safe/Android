package io.issac.pleosandroid.feature.records.navigation

import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import io.issac.pleosandroid.core.navigation.AppRoute
import io.issac.pleosandroid.core.navigation.DrivingDataStatusRoute
import io.issac.pleosandroid.core.navigation.DrivingPointsWeekRoute
import io.issac.pleosandroid.core.navigation.DrivingReportRoute
import io.issac.pleosandroid.core.navigation.RecordingSettingsRoute
import io.issac.pleosandroid.core.navigation.RecordsRoute
import io.issac.pleosandroid.feature.records.history.RecordsDestination
import io.issac.pleosandroid.feature.records.report.DrivingReportDestination

@Composable
internal fun RecordsRouteContent(route: AppRoute, modifier: Modifier) {
    when (route) {
        RecordsRoute, RecordingSettingsRoute, DrivingPointsWeekRoute -> RecordsDestination(route, modifier)

        is DrivingReportRoute -> DrivingReportDestination(route.journeyId, showDataStatus = false, modifier = modifier)

        is DrivingDataStatusRoute -> DrivingReportDestination(
            route.journeyId,
            showDataStatus = true,
            modifier = modifier,
        )

        else -> Unit
    }
}
