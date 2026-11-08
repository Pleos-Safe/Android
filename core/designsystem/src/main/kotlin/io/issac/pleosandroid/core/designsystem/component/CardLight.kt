package io.issac.pleosandroid.core.designsystem.component

import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.animation.core.withInfiniteAnimationFrameNanos
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.State
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.ui.MotionDurationScale
import androidx.compose.ui.util.lerp
import kotlinx.coroutines.isActive

/** Share this light across a card's waiting, reveal and inspection states without restarting its reflection. */
@Composable
fun rememberMilestoneCardLight(ambient: Boolean, initialAngle: Float = -4f): State<Float> {
    val angle = remember { mutableFloatStateOf(initialAngle) }
    val motion = rememberCoroutineScope().coroutineContext[MotionDurationScale]
    val durationScale = motion?.scaleFactor ?: 1f
    LaunchedEffect(ambient, durationScale) {
        if (!ambient || durationScale == 0f) return@LaunchedEffect
        val durationNanos = 4_250_000_000.0 * durationScale
        var startTime: Long? = null
        var origin = angle.floatValue
        var target = 17.5f
        while (isActive) {
            withInfiniteAnimationFrameNanos { now ->
                val start = startTime ?: now.also { startTime = it }
                val fraction = ((now - start) / durationNanos).toFloat().coerceIn(0f, 1f)
                angle.floatValue = lerp(origin, target, FastOutSlowInEasing.transform(fraction))
                if (fraction == 1f) {
                    origin = target
                    target = -target
                    startTime = now
                }
            }
        }
    }
    return angle
}
