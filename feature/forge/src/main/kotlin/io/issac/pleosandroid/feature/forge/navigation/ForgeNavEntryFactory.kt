package io.issac.pleosandroid.feature.forge.navigation

import androidx.compose.foundation.layout.padding
import androidx.compose.ui.Modifier
import io.issac.pleosandroid.core.navigation.AppRoute
import io.issac.pleosandroid.core.navigation.EnhancementRoute
import io.issac.pleosandroid.core.navigation.ForgeMotionRoute
import io.issac.pleosandroid.core.navigation.LocalNavigationRouter
import io.issac.pleosandroid.core.navigation.NavEntryFactory
import io.issac.pleosandroid.core.navigation.NavEntryResult
import io.issac.pleosandroid.core.navigation.WorkshopRoute
import io.issac.pleosandroid.feature.forge.ForgeDestination
import io.issac.pleosandroid.feature.forge.preview.ForgeMotionPlayer
import javax.inject.Inject

internal class ForgeNavEntryFactory @Inject constructor() : NavEntryFactory {
    override fun create(route: AppRoute): NavEntryResult? = when (route) {
        EnhancementRoute, is WorkshopRoute ->
            NavEntryResult { padding -> ForgeDestination(route, Modifier.padding(padding)) }

        is ForgeMotionRoute -> NavEntryResult { padding ->
            val router = LocalNavigationRouter.current
            ForgeMotionPlayer(route, { router.pop() }, Modifier.padding(padding))
        }

        else -> null
    }
}
