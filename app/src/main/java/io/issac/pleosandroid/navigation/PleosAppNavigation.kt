package io.issac.pleosandroid.navigation

import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.padding
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import io.issac.pleosandroid.core.navigation.NavEntryFactory
import io.issac.pleosandroid.core.navigation.ProvideCollectionFocus
import io.issac.pleosandroid.feature.collection.home.CollectionHomeCard
import io.issac.pleosandroid.feature.home.HomeFeatureProvider
import io.issac.pleosandroid.feature.home.HomeRouteContent
import io.issac.pleosandroid.feature.main.PleosMain

@Composable
internal fun PleosAppNavigation(factories: Set<NavEntryFactory>, contentPadding: PaddingValues) {
    ProvideCollectionFocus {
        HomeFeatureProvider(homeCard = { CollectionHomeCard() }) {
            PleosMain(
                factories = factories,
                modifier = Modifier.padding(contentPadding),
                restrictedContent = { HomeRouteContent() },
            )
        }
    }
}
