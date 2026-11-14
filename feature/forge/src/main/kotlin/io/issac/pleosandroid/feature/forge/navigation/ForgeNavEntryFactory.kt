package io.issac.pleosandroid.feature.forge.navigation

import androidx.compose.foundation.layout.padding
import androidx.compose.ui.Modifier
import io.issac.pleosandroid.core.navigation.AppRoute
import io.issac.pleosandroid.core.navigation.EnhancementRoute
import io.issac.pleosandroid.core.navigation.NavEntryFactory
import io.issac.pleosandroid.core.navigation.NavEntryResult
import io.issac.pleosandroid.core.navigation.WorkshopRoute
import io.issac.pleosandroid.feature.forge.ForgeDestination
import javax.inject.Inject

internal class ForgeNavEntryFactory @Inject constructor() : NavEntryFactory {
    override fun create(route: AppRoute): NavEntryResult? = when (route) {
        EnhancementRoute, is WorkshopRoute ->
            NavEntryResult { padding -> ForgeDestination(route, Modifier.padding(padding)) }

        else -> null
    }
}
