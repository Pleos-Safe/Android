package io.issac.pleosandroid.core.cardui.motion

fun smoothStep(start: Float, end: Float, t: Float): Float {
    val x = ((t - start) / (end - start)).coerceIn(0f, 1f)
    return x * x * (3f - 2f * x)
}
