package io.issac.pleosandroid.core.navigation

import kotlinx.serialization.Serializable

/** Roots of the main tabs. Other routes open inside the current tab. */
sealed interface MainTabRoute : AppRoute

@Serializable
data object MainRoute : MainTabRoute

@Serializable
data object ExploreRoute : MainTabRoute

@Serializable
data object EnhancementRoute : MainTabRoute

@Serializable
data object BadgesRoute : MainTabRoute

@Serializable
data object RecordsRoute : MainTabRoute

@Serializable
data object TravelMapRoute : AppRoute

@Serializable
data class DrivingReportRoute(val journeyId: String) : AppRoute

@Serializable
data class DrivingDataStatusRoute(val journeyId: String) : AppRoute

@Serializable
data object DrivingPointsWeekRoute : AppRoute

@Serializable
data object RecordingSettingsRoute : AppRoute

@Serializable
data object SettingsRoute : AppRoute

@Serializable
data object ShopRoute : MainTabRoute

@Serializable
data object MembershipSettingsRoute : AppRoute

@Serializable
data object PackShelfRoute : AppRoute

@Serializable
data class OpenPackRoute(val packId: String) : AppRoute
