package io.issac.pleosandroid.feature.main

import androidx.compose.foundation.layout.padding
import androidx.compose.ui.Modifier
import io.issac.pleosandroid.core.designsystem.component.PleosScreen
import io.issac.pleosandroid.core.designsystem.component.PleosTextLink
import io.issac.pleosandroid.core.navigation.AppRoute
import io.issac.pleosandroid.core.navigation.LocalNavigationRouter
import io.issac.pleosandroid.core.navigation.MainRoute
import io.issac.pleosandroid.core.navigation.NavEntryFactory
import io.issac.pleosandroid.core.navigation.NavEntryResult
import io.issac.pleosandroid.core.navigation.SettingsRoute
import io.issac.pleosandroid.core.navigation.TravelMapRoute
import io.issac.pleosandroid.feature.main.map.PlaceMapRouteContent
import javax.inject.Inject

class MainNavEntryFactory @Inject constructor() : NavEntryFactory {
    override fun create(route: AppRoute): NavEntryResult? = when (route) {
        MainRoute -> NavEntryResult { padding -> MainRouteContent(contentPadding = padding) }

        TravelMapRoute -> NavEntryResult { padding -> PlaceMapRouteContent(Modifier.padding(padding)) }

        SettingsRoute -> NavEntryResult { padding ->
            val router = LocalNavigationRouter.current
            PleosScreen(title = "설정", modifier = Modifier.padding(padding)) {
                PleosTextLink("돌아가기", onClick = { router.pop() })
            }
        }

        else -> null
    }
}
