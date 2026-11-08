package io.issac.pleosandroid.feature.records

import androidx.compose.foundation.layout.padding
import androidx.compose.ui.Modifier
import dagger.Binds
import dagger.Module
import dagger.hilt.InstallIn
import dagger.hilt.components.SingletonComponent
import dagger.multibindings.IntoSet
import io.issac.pleosandroid.core.navigation.AppRoute
import io.issac.pleosandroid.core.navigation.DrivingDataStatusRoute
import io.issac.pleosandroid.core.navigation.DrivingPointsWeekRoute
import io.issac.pleosandroid.core.navigation.DrivingReportRoute
import io.issac.pleosandroid.core.navigation.NavEntryFactory
import io.issac.pleosandroid.core.navigation.NavEntryResult
import io.issac.pleosandroid.core.navigation.RecordingSettingsRoute
import io.issac.pleosandroid.core.navigation.RecordsRoute
import io.issac.pleosandroid.feature.records.navigation.RecordsRouteContent
import javax.inject.Inject

internal class RecordsNavEntryFactory @Inject constructor() : NavEntryFactory {
    override fun create(route: AppRoute): NavEntryResult? = when (route) {
        RecordsRoute, RecordingSettingsRoute, DrivingPointsWeekRoute,
        is DrivingReportRoute, is DrivingDataStatusRoute,
        ->
            NavEntryResult { padding ->
                RecordsRouteContent(route, Modifier.padding(padding))
            }

        else -> null
    }
}

@Module
@InstallIn(SingletonComponent::class)
internal abstract class RecordsNavigationModule {
    @Binds
    @IntoSet
    abstract fun bindRecordsEntry(factory: RecordsNavEntryFactory): NavEntryFactory
}
