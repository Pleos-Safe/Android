package io.issac.pleosandroid.feature.collection.gallery

import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.AnimationVector1D
import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.animation.core.LinearEasing
import androidx.compose.animation.core.tween
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.State
import androidx.compose.runtime.derivedStateOf
import androidx.compose.runtime.getValue
import androidx.compose.runtime.key
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.Saver
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import io.issac.pleosandroid.core.cardui.preview.PreviewStage
import io.issac.pleosandroid.core.cardui.preview.previewPackCard
import io.issac.pleosandroid.core.navigation.PackMotionRoute
import io.issac.pleosandroid.feature.collection.pack.PACK_FLIP_MS
import io.issac.pleosandroid.feature.collection.pack.PACK_TEAR_MS
import io.issac.pleosandroid.feature.collection.pack.PackOpeningPrint
import kotlin.math.roundToInt

private const val CARD_BACK_PAUSE_MS = 420
private val clockSaver = Saver<Animatable<Float, AnimationVector1D>, Float>(
    save = { it.value },
    restore = { Animatable(it) },
)

@Composable
internal fun PackMotionPlayer(scene: PackMotionRoute, onBack: () -> Unit, modifier: Modifier = Modifier) {
    var run by rememberSaveable(scene) { mutableIntStateOf(0) }
    val replay: () -> Unit = { run++ }
    key(scene, run) {
        val card = remember(scene) { previewPackCard(scene.region, scene.grade) }
        val flipAt = if (scene.flipOnly) 0 else PACK_TEAR_MS + CARD_BACK_PAUSE_MS
        val duration = flipAt + PACK_FLIP_MS
        TimedPreview(scene, duration, replay, onBack, modifier) { time, done, art ->
            val tear = remember(time) {
                derivedStateOf {
                    if (scene.flipOnly) 1f else (time.value * duration / PACK_TEAR_MS).coerceIn(0f, 1f)
                }
            }
            val angle = remember(time) {
                derivedStateOf {
                    val flip = ((time.value * duration - flipAt) / PACK_FLIP_MS).coerceIn(0f, 1f)
                    180f * FastOutSlowInEasing.transform(flip)
                }
            }
            PackOpeningPrint(card.pack, card, tear, angle, done, art)
        }
    }
}

@Composable
private fun TimedPreview(
    scene: PackMotionRoute,
    durationMs: Int,
    replay: () -> Unit,
    onBack: () -> Unit,
    modifier: Modifier,
    art: @Composable (State<Float>, Boolean, Modifier) -> Unit,
) {
    val time = rememberSaveable(saver = clockSaver) { Animatable(0f) }
    var skip by rememberSaveable { mutableStateOf(false) }
    LaunchedEffect(time, skip) {
        if (skip) {
            time.snapTo(1f)
        } else if (time.value < 1f) {
            val remaining = ((1f - time.value) * durationMs).roundToInt().coerceAtLeast(1)
            time.animateTo(1f, tween(remaining, easing = LinearEasing))
        }
    }
    val done by remember(time) { derivedStateOf { time.value >= 1f } }
    PreviewStage(scene.title, scene.detail, done, replay, onBack, modifier, onSkip = {
        skip = true
    }) { art(time.asState(), done, it) }
}
