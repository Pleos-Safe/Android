package io.issac.pleosandroid.navigation

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import io.issac.pleosandroid.core.navigation.NavEntryFactory
import io.issac.pleosandroid.feature.badges.home.CollectionHomeCard
import io.issac.pleosandroid.feature.badges.notice.CollectionRewardNoticeHost
import io.issac.pleosandroid.feature.main.PleosMain
@Composable
internal fun PleosAppNavigation(factories: Set<NavEntryFactory>, contentPadding: PaddingValues) {
    Box(Modifier.fillMaxSize()) {
        PleosMain(factories = factories, modifier = Modifier.padding(contentPadding), homeCard = {
            CollectionHomeCard()
        })
        CollectionRewardNoticeHost(Modifier.align(Alignment.TopCenter).padding(contentPadding).padding(16.dp))
    }
}
