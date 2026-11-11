package io.issac.pleosandroid.feature.home.navigation

import androidx.compose.foundation.layout.padding
import androidx.compose.ui.Modifier
import io.issac.pleosandroid.core.navigation.AppRoute
import io.issac.pleosandroid.core.navigation.BadgesRoute
import io.issac.pleosandroid.core.navigation.LocalNavigationRouter
import io.issac.pleosandroid.core.navigation.MainRoute
import io.issac.pleosandroid.core.navigation.NavEntryFactory
import io.issac.pleosandroid.core.navigation.NavEntryResult
import io.issac.pleosandroid.core.navigation.OpenPackRoute
import io.issac.pleosandroid.core.navigation.PackShelfRoute
import io.issac.pleosandroid.feature.home.HomeRouteContent
import javax.inject.Inject

internal class HomeNavEntryFactory @Inject constructor() : NavEntryFactory {
    override fun create(route: AppRoute): NavEntryResult? = when (route) {
        MainRoute -> NavEntryResult { padding ->
            val router = LocalNavigationRouter.current
            HomeRouteContent(
                modifier = Modifier.padding(padding),
                onNavigate = { if (router.isActive(MainRoute)) router.navigate(it) },
                onOpenPack = {
                    if (router.isActive(MainRoute)) router.replaceAll(BadgesRoute, PackShelfRoute, OpenPackRoute(it))
                },
            )
        }

        else -> null
    }
}
