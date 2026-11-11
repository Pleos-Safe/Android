package io.issac.pleosandroid.feature.badges.album.gallery

import io.issac.pleosandroid.core.domain.cards.CardGrade
import io.issac.pleosandroid.core.domain.cards.ForgeOutcome
import io.issac.pleosandroid.core.domain.cards.PackType
import io.issac.pleosandroid.core.navigation.AppRoute
import kotlinx.serialization.Serializable

// Keep the JVM names used by saved pack and forge previews.
@Serializable data class MotionGalleryRoute(val startOnForge: Boolean = false) : AppRoute

@Serializable
sealed interface CardMotion : AppRoute {
    @Serializable
    data class Pack(val region: PackType, val flipOnly: Boolean = false, val grade: CardGrade? = null) : CardMotion

    @Serializable data class Forge(val before: Int, val after: Int, val outcome: ForgeOutcome) : CardMotion
}
