package io.issac.pleosandroid.core.designsystem.component

import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.animation.core.animate
import androidx.compose.animation.core.keyframes
import androidx.compose.animation.core.withInfiniteAnimationFrameNanos
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.Orientation
import androidx.compose.foundation.gestures.draggable
import androidx.compose.foundation.gestures.rememberDraggableState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.State
import androidx.compose.runtime.getValue
import androidx.compose.runtime.key
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.MotionDurationScale
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.layout.onSizeChanged
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.util.lerp
import io.issac.pleosandroid.core.designsystem.R
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch

/** Share this light across a card's waiting, reveal and inspection states without restarting its reflection. */
@Composable
fun rememberMilestoneCardLight(ambient: Boolean, initialAngle: Float = RESTING_LIGHT_ANGLE): State<Float> {
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

/** A parked exhibit can tilt its published print. Shelf and reveal-animation callers use [MilestoneCard]. */
@Composable
fun InspectableMilestoneCard(
    face: MilestoneCardFace,
    modifier: Modifier = Modifier,
    lightAngle: (() -> Float)? = null,
) {
    if (face !is MilestoneCardFace.Printed || face.level < 3) {
        MilestoneCard(face, modifier, lightAngle)
        return
    }
    key(face.seriesLabel, face.level) {
        var angle by remember { mutableFloatStateOf(RESTING_LIGHT_ANGLE) }
        var restingAngle by remember { mutableFloatStateOf(RESTING_LIGHT_ANGLE) }
        var width by remember { mutableIntStateOf(1) }
        val scope = rememberCoroutineScope()
        val durationScale = scope.coroutineContext[MotionDurationScale]
        var manual by remember { mutableStateOf(false) }
        val ambient = if (lightAngle == null && face.level >= 4) {
            rememberMilestoneCardLight(!manual, initialAngle = -17.5f)
        } else {
            null
        }
        fun takeLight() {
            if (!manual) {
                val incoming = lightAngle?.invoke() ?: ambient?.value
                if (incoming != null) {
                    angle = incoming
                    restingAngle = incoming
                }
                manual = true
            }
        }
        val tilt = rememberDraggableState { delta ->
            takeLight()
            angle = (angle + delta / width * (2f * MAX_LIGHT_ANGLE / .75f)).coerceIn(-MAX_LIGHT_ANGLE, MAX_LIGHT_ANGLE)
        }
        suspend fun sweep(tapped: Boolean) {
            // UserInput priority takes this same drag state away from a running sweep.
            tilt.drag {
                if (durationScale?.scaleFactor == 0f) {
                    if (tapped) angle = if (angle > 0f) -12f else 12f
                    return@drag
                }
                val origin = angle
                animate(
                    initialValue = origin,
                    targetValue = origin,
                    animationSpec = keyframes {
                        durationMillis = 2200
                        origin at 0 using FastOutSlowInEasing
                        -MAX_LIGHT_ANGLE at 440 using FastOutSlowInEasing
                        MAX_LIGHT_ANGLE at 1694 using FastOutSlowInEasing
                    },
                ) { value, _ -> angle = value }
            }
        }
        LaunchedEffect(Unit) { if (face.level == 3 && lightAngle == null) sweep(tapped = false) }
        MilestoneCardPrint(
            face,
            modifier.onSizeChanged { width = it.width.coerceAtLeast(1) }
                .draggable(tilt, Orientation.Horizontal)
                .clickable(
                    interactionSource = null,
                    indication = null,
                    role = Role.Button,
                    onClickLabel = stringResource(R.string.milestone_card_play_light),
                ) {
                    takeLight()
                    scope.launch { sweep(tapped = true) }
                }
                .graphicsLayer {
                    val lean = if (durationScale?.scaleFactor == 0f) 0f else angle - restingAngle
                    rotationY = lean * .15f
                    rotationX = -lean * .032f
                    cameraDistance = size.width.coerceAtLeast(1f) * 3f
                },
        ) { if (manual) angle else lightAngle?.invoke() ?: ambient?.value ?: angle }
    }
}
