package io.issac.pleosandroid.feature.home.driving

import androidx.compose.ui.graphics.Color

/** Each three lifetime visit packs deepen a region, up to six visible levels. */
internal object VisitPackMapPalette {
    val colors = listOf(
        Color(0xFFD8C8F3),
        Color(0xFFBA9AE5),
        Color(0xFF9C70D5),
        Color(0xFF7F4CBD),
        Color(0xFF66379D),
        Color(0xFF50277D),
    )

    fun stepFor(count: Int): Int = if (count <= 0) 0 else ((count - 1) / 3 + 1).coerceAtMost(colors.size)

    fun colorFor(count: Int, unvisited: Color): Color = colors.getOrNull(stepFor(count) - 1) ?: unvisited
}
