package io.issac.pleosandroid.feature.explore.navigation

import androidx.compose.foundation.layout.padding
import androidx.compose.ui.Modifier
import io.issac.pleosandroid.core.navigation.AppRoute
import io.issac.pleosandroid.core.navigation.ExploreRoute
import io.issac.pleosandroid.core.navigation.NavEntryFactory
import io.issac.pleosandroid.core.navigation.NavEntryResult
import io.issac.pleosandroid.core.navigation.TravelMapRoute
import io.issac.pleosandroid.feature.explore.ExploreRouteContent
import javax.inject.Inject

internal class ExploreNavEntryFactory @Inject constructor() : NavEntryFactory {
    override fun create(route: AppRoute): NavEntryResult? = when (route) {
        ExploreRoute, TravelMapRoute -> NavEntryResult { padding ->
            ExploreRouteContent(route, Modifier.padding(padding))
        }

        else -> null
    }
}
