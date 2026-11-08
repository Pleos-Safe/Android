package io.issac.pleosandroid.core.domain.points

import java.math.BigDecimal
import java.text.NumberFormat
import java.util.Locale

fun formatHalfPoints(halfPoints: Long): String {
    val points = BigDecimal.valueOf(halfPoints).divide(BigDecimal.valueOf(2))
    return NumberFormat.getNumberInstance(Locale.KOREA).format(points) + " P"
}
