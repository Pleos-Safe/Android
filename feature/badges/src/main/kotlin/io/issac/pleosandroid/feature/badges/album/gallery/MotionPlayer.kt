package io.issac.pleosandroid.feature.badges.album.gallery

import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.AnimationVector1D
import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.animation.core.LinearEasing
import androidx.compose.animation.core.tween
import androidx.compose.foundation.layout.fillMaxWidth
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
import androidx.compose.ui.platform.testTag
import io.issac.pleosandroid.core.designsystem.component.PleosButton
import io.issac.pleosandroid.core.designsystem.component.PleosTextLink
import io.issac.pleosandroid.core.domain.cards.ForgeReceipt
import io.issac.pleosandroid.feature.badges.album.BodyText
import io.issac.pleosandroid.feature.badges.album.ExhibitScreen
import io.issac.pleosandroid.feature.badges.album.ForgePresentation
import io.issac.pleosandroid.feature.badges.album.PACK_FLIP_MS
import io.issac.pleosandroid.feature.badges.album.PACK_TEAR_MS
import io.issac.pleosandroid.feature.badges.album.PackOpeningPrint
import io.issac.pleosandroid.feature.badges.component.SectionTitle
import java.time.Instant
import kotlin.math.roundToInt

private const val CARD_BACK_PAUSE_MS = 420
private val clockSaver = Saver<Animatable<Float, AnimationVector1D>, Float>(
    save = { it.value },
    restore = { Animatable(it) },
)

@Composable
internal fun MotionPlayer(scene: CardMotion, onBack: () -> Unit, modifier: Modifier = Modifier) {
    var run by rememberSaveable(scene) { mutableIntStateOf(0) }
    val replay: () -> Unit = { run++ }
    key(scene, run) {
        when (scene) {
            is CardMotion.Forge -> {
                val card = remember(scene) { scene.sample() }
                val receipt = remember(scene, run) {
                    ForgeReceipt(
                        id = "preview-$run", copyId = card.id, before = scene.before, after = scene.after,
                        outcome = scene.outcome, costHalfPoints = 0, at = Instant.EPOCH,
                        acknowledged = false, materialId = null,
                    )
                }
                var finished by rememberSaveable { mutableStateOf(false) }
                ForgePresentation(
                    card,
                    receipt,
                    receipt.id.takeUnless { finished },
                    { finished = true },
                    onBack,
                    modifier,
                ) { playing, art ->
                    PreviewStage(scene, !playing, replay, onBack, art = art)
                }
            }

            is CardMotion.Pack -> {
                val card = remember(scene) { motionCard(scene.region) }
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
    }
}

@Composable
private fun TimedPreview(
    scene: CardMotion,
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
    PreviewStage(scene, done, replay, onBack, modifier, onSkip = { skip = true }) { art(time.asState(), done, it) }
}

@Composable
private fun PreviewStage(
    scene: CardMotion,
    done: Boolean,
    replay: () -> Unit,
    onBack: () -> Unit,
    modifier: Modifier = Modifier,
    onSkip: (() -> Unit)? = null,
    art: @Composable (Modifier) -> Unit,
) {
    ExhibitScreen("연출 미리보기", onBack, modifier, art = art) {
        SectionTitle(scene.title)
        BodyText(scene.detail)
        if (done) {
            PleosButton("다시 재생", replay, Modifier.fillMaxWidth().testTag("motion-replay"), showArrow = true)
        } else if (onSkip != null) {
            PleosButton("연출 건너뛰기", onSkip, Modifier.fillMaxWidth().testTag("motion-skip"))
        }
        PleosTextLink("연출 목록", onBack)
    }
}
