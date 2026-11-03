package io.issac.pleosandroid.feature.main

import io.issac.pleosandroid.core.navigation.AppRoute
import io.issac.pleosandroid.core.navigation.MainRoute
import io.issac.pleosandroid.core.navigation.NavEntryFactory
import io.issac.pleosandroid.core.navigation.NavEntryResult
import javax.inject.Inject

class MainNavEntryFactory @Inject constructor() : NavEntryFactory {
    override fun create(route: AppRoute): NavEntryResult? = when (route) {
        MainRoute -> NavEntryResult { padding -> MainRouteContent(contentPadding = padding) }
        else -> null
    }
}
