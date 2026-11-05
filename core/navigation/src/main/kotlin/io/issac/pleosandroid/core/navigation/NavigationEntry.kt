package io.issac.pleosandroid.core.navigation

import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.runtime.Composable

/** Return null for a route owned by another feature. */
fun interface NavEntryFactory {
    fun create(route: AppRoute): NavEntryResult?
}

class NavEntryResult(val metadata: Map<String, Any> = emptyMap(), val content: @Composable (PaddingValues) -> Unit)
