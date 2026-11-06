package io.issac.pleosandroid.core.designsystem.component

import androidx.annotation.DrawableRes
import androidx.compose.ui.graphics.Color
import io.issac.pleosandroid.core.designsystem.R

/** A text-free 360 × 520 printing plate. The artwork, lettering and moving varnish stay independent. */
internal class PrintPlate(@param:DrawableRes val paper: Int, @param:DrawableRes val relief: Int? = null)

internal enum class BlackLabel(
    val level: Int,
    val titleInk: Color,
    val secondaryInk: Color,
    private val place: PrintPlate,
    private val postcard: PrintPlate,
    private val safety: PrintPlate,
) {
    Silver(
        3,
        Color(0xFFEFF2ED),
        Color(0xFFB9C7CB),
        PrintPlate(R.drawable.milestone_place_silver),
        PrintPlate(R.drawable.milestone_postcard_silver),
        PrintPlate(R.drawable.milestone_safety_silver),
    ),
    Sapphire(
        4,
        Color(0xFFF2F7FC),
        Color(0xFFC7D2F2),
        PrintPlate(R.drawable.milestone_place_sapphire, R.drawable.milestone_place_sapphire_relief),
        PrintPlate(R.drawable.milestone_postcard_sapphire, R.drawable.milestone_postcard_sapphire_relief),
        PrintPlate(R.drawable.milestone_safety_sapphire, R.drawable.milestone_safety_sapphire_relief),
    ),
    Onyx(
        5,
        Color(0xFFF6D18B),
        Color(0xFFD2C4A6),
        PrintPlate(R.drawable.milestone_place_onyx, R.drawable.milestone_place_onyx_relief),
        PrintPlate(R.drawable.milestone_postcard_onyx, R.drawable.milestone_postcard_onyx_relief),
        PrintPlate(R.drawable.milestone_safety_onyx, R.drawable.milestone_safety_onyx_relief),
    ),
    ;

    fun plate(stock: CardStock): PrintPlate = when (stock) {
        CardStock.Place -> place
        CardStock.Postcard -> postcard
        CardStock.Safety -> safety
    }

    companion object {
        fun fromLevel(level: Int): BlackLabel? = when (level) {
            3 -> Silver
            4 -> Sapphire
            5 -> Onyx
            else -> null
        }
    }
}
