package io.issac.pleosandroid.navigation

import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.ui.Modifier
import io.issac.pleosandroid.core.navigation.AppRoute
import io.issac.pleosandroid.core.navigation.NavEntryFactory
import io.issac.pleosandroid.core.navigation.NavEntryResult
import io.issac.pleosandroid.feature.nav.PleosNavHost
import kotlinx.serialization.Serializable

@Serializable
internal data object SetupRoute : AppRoute

/** Preserve the current vehicle lifecycle while bootstrapping the factory-based host. */
@Composable
internal fun PleosAppNavigation(
    contentPadding: PaddingValues,
    setupContent: @Composable (PaddingValues) -> Unit,
) {
    val currentContent by rememberUpdatedState(setupContent)
    val factories = remember {
        setOf(
            NavEntryFactory { route ->
                when (route) {
                    SetupRoute -> NavEntryResult { padding -> currentContent(padding) }
                    else -> null
                }
            },
        )
    }
    PleosNavHost(
        factories = factories,
        startRoute = SetupRoute,
        contentPadding = contentPadding,
        modifier = Modifier.fillMaxSize(),
    )
}
