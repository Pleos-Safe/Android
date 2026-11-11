package io.issac.pleosandroid.feature.badges.album

import io.issac.pleosandroid.core.navigation.AppRoute
import kotlinx.serialization.Serializable

// Saved Android back stacks identify these keys by JVM class name.
@Serializable data object PackOddsRoute : AppRoute

@Serializable data class AlbumDetailRoute(val copyId: String) : AppRoute

@Serializable data class WorkshopRoute(val copyId: String) : AppRoute
