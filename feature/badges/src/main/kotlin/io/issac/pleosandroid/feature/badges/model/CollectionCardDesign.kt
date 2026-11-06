package io.issac.pleosandroid.feature.badges.model

import androidx.annotation.DrawableRes
import androidx.compose.runtime.Composable
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import io.issac.pleosandroid.core.designsystem.R as DesignR
import io.issac.pleosandroid.core.designsystem.component.MilestoneCardAppearance
import io.issac.pleosandroid.core.designsystem.component.MilestoneCardFace
import io.issac.pleosandroid.feature.badges.R

internal data class CollectionCardDesign(
    val id: String,
    val title: String,
    val subtitle: String,
    val context: String,
    val series: String,
    val place: CollectionPlace?,
    @param:DrawableRes val artwork: Int,
    @param:DrawableRes private val glossMask: Int,
    @param:DrawableRes private val engravingMask: Int,
    val postcard: Boolean = true,
) {
    companion object

    @Composable
    fun front(level: Int): MilestoneCardFace {
        val image = painterResource(artwork)
        if (postcard) {
            return MilestoneCardFace.Postcard(
                artwork = image,
                glossMask = glossMask,
                engravingMask = engravingMask,
                title = title,
                subtitle = subtitle,
                contextLabel = context,
                categoryLabel = if (place == null) "여정의 기억" else stringResource(R.string.card_detail_place),
                seriesLabel = series,
                level = level,
            )
        }
        return MilestoneCardFace.Front(
            appearance = MilestoneCardAppearance.forLevel(level, safety = place == null),
            artwork = image,
            glossMask = glossMask,
            engravingMask = engravingMask,
            contextLabel = context,
            title = title,
            subtitle = subtitle,
            seriesLabel = series,
            categoryLabel = if (place != null) stringResource(R.string.card_detail_place) else null,
        )
    }
}
