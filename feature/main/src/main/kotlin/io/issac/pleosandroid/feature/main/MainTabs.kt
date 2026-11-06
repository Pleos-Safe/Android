package io.issac.pleosandroid.feature.main

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.key
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.staticCompositionLocalOf
import androidx.compose.ui.Modifier
import androidx.hilt.lifecycle.viewmodel.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import io.issac.pleosandroid.core.designsystem.component.PleosBottomTabs
import io.issac.pleosandroid.core.designsystem.component.PleosHeader
import io.issac.pleosandroid.core.designsystem.component.PleosTab
import io.issac.pleosandroid.core.designsystem.component.pleosShellBackground
import io.issac.pleosandroid.core.navigation.NavEntryFactory
import io.issac.pleosandroid.core.navigation.NavigationRouter
import io.issac.pleosandroid.core.navigation.SettingsRoute
import io.issac.pleosandroid.feature.main.map.LocalPlaceMapViewModel
import io.issac.pleosandroid.feature.main.map.PlaceMapViewModel
import io.issac.pleosandroid.feature.nav.PleosNavDisplay
import io.issac.pleosandroid.feature.nav.rememberPleosNavEntries

// Keep one journey running while the driver switches tabs or the vehicle enters a restricted state.
internal val LocalMainViewModel = staticCompositionLocalOf<MainViewModel?> { null }

@Composable
fun PleosMain(
    factories: Set<NavEntryFactory>,
    modifier: Modifier = Modifier,
    viewModel: MainViewModel = hiltViewModel(),
) {
    val uiState by viewModel.uiState.collectAsStateWithLifecycle()
    val placeMapViewModel = hiltViewModel<PlaceMapViewModel>()
    CompositionLocalProvider(
        LocalMainViewModel provides viewModel,
        LocalPlaceMapViewModel provides placeMapViewModel,
    ) {
        MainTabs(
            factories = factories,
            restricted = (uiState as? MainUiState.Ready)?.canUseTabs != true,
            canNavigate = viewModel::canUseTabs,
            modifier = modifier,
            showJourneyResult = (uiState as? MainUiState.Ready)?.canReturnHome == true,
            restrictedContent = { JourneyContent(uiState, viewModel, onOpenBadges = {}) },
        )
    }
}

@Composable
internal fun MainTabs(
    factories: Set<NavEntryFactory>,
    restricted: Boolean,
    canNavigate: () -> Boolean,
    modifier: Modifier = Modifier,
    showJourneyResult: Boolean = false,
    restrictedContent: @Composable () -> Unit,
) {
    val navigation = rememberMainTabNavigation()
    LaunchedEffect(showJourneyResult) {
        if (showJourneyResult) navigation.returnToTravelHome()
    }
    val selected = navigation.selectedTab
    val allowed = rememberUpdatedState { !restricted && canNavigate() }
    val routers = remember(navigation) {
        PleosTab.entries.associateWith { tab ->
            NavigationRouter(
                backStack = navigation.stackFor(tab),
                selectTab = navigation::select,
                onBackAtRoot = if (tab == PleosTab.Travel) {
                    null
                } else {
                    { navigation.select(PleosTab.Travel) }
                },
                canNavigate = { navigation.selectedTab == tab && allowed.value() },
            )
        }
    }
    val entries = PleosTab.entries.associateWith { tab ->
        key(tab) {
            rememberPleosNavEntries(navigation.stackFor(tab), routers.getValue(tab), factories, tab = tab)
        }
    }
    val router = routers.getValue(selected)
    val selectTab: (PleosTab) -> Unit = { tab -> router.navigate(tab.route) }

    Column(modifier.fillMaxSize().pleosShellBackground()) {
        PleosHeader(
            selectedTab = selected,
            onTabSelected = selectTab,
            onSettingsClick = { router.navigate(SettingsRoute) },
            restricted = restricted,
        )
        Box(Modifier.weight(1f)) {
            if (restricted) {
                restrictedContent()
            } else {
                // At another tab's root, predictive Back previews the travel destination we will return to.
                val home = entries.getValue(PleosTab.Travel)
                val visibleEntries = if (selected == PleosTab.Travel) home else home + entries.getValue(selected)
                PleosNavDisplay(entries = visibleEntries, onBack = { router.pop() }, modifier = Modifier.fillMaxSize())
            }
        }
        PleosBottomTabs(selectedTab = selected, onTabSelected = selectTab, restricted = restricted)
    }
}
