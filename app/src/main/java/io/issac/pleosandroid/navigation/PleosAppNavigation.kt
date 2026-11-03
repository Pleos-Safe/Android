package io.issac.pleosandroid.navigation

import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import io.issac.pleosandroid.core.navigation.MainRoute
import io.issac.pleosandroid.core.navigation.NavEntryFactory
import io.issac.pleosandroid.feature.nav.PleosNavHost

@Composable
internal fun PleosAppNavigation(
    factories: Set<NavEntryFactory>,
    contentPadding: PaddingValues,
) {
    PleosNavHost(
        factories = factories,
        startRoute = MainRoute,
        contentPadding = contentPadding,
        modifier = Modifier.fillMaxSize(),
    )
}
