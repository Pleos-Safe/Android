package io.issac.pleosandroid.navigation

import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.padding
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import io.issac.pleosandroid.core.navigation.NavEntryFactory
import io.issac.pleosandroid.feature.main.PleosMain

@Composable
internal fun PleosAppNavigation(factories: Set<NavEntryFactory>, contentPadding: PaddingValues) {
    PleosMain(factories = factories, modifier = Modifier.padding(contentPadding))
}
