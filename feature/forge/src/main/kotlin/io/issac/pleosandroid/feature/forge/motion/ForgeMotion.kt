package io.issac.pleosandroid.feature.forge.motion

import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.AnimationVector1D
import androidx.compose.animation.core.LinearEasing
import androidx.compose.animation.core.tween
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.derivedStateOf
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.saveable.listSaver
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.MotionDurationScale
import androidx.compose.ui.draw.clipToBounds
import androidx.compose.ui.geometry.Rect
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.layout.onGloballyPositioned
import androidx.compose.ui.layout.positionInRoot
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.semantics.clearAndSetSemantics
import androidx.compose.ui.unit.dp
import androidx.compose.ui.util.lerp
import io.issac.pleosandroid.core.cardui.component.CollectionBack
import io.issac.pleosandroid.core.cardui.print.CardPrintView
import io.issac.pleosandroid.core.designsystem.component.PleosTextLink
import io.issac.pleosandroid.core.designsystem.component.rememberCardLight
import io.issac.pleosandroid.core.domain.cards.CardCopy
import io.issac.pleosandroid.core.domain.cards.ForgeReceipt
import kotlin.math.roundToInt

/** One receipt drives the original print, new print, light and lettering. Playback never writes the inventory. */
@Composable
internal fun ForgePresentation(
    card: CardCopy,
    receipt: ForgeReceipt?,
    playbackId: String?,
    onFinished: (String) -> Unit,
    onBack: () -> Unit,
    modifier: Modifier,
    workshop: @Composable (playing: Boolean, art: @Composable (Modifier) -> Unit) -> Unit,
) {
    val playback = remember(receipt) { receipt?.let(::ForgePlayback) }
    val id = receipt?.id
    val ownsPlayback = id != null && id == playbackId
    // Sample the animation only when Android saves the window. A different receipt always starts at zero.
    val saver = remember(id) {
        listSaver<Animatable<Float, AnimationVector1D>, Any>(
            save = { listOf(id.orEmpty(), it.value) },
            restore = { data -> if (data[0] == id.orEmpty()) Animatable(data[1] as Float) else null },
        )
    }
    val progress = rememberSaveable(card.id, id, saver = saver) { Animatable(if (ownsPlayback) 0f else 1f) }
    var skip by remember(id) { mutableStateOf(false) }
    val reduced = rememberCoroutineScope().coroutineContext[MotionDurationScale]?.scaleFactor == 0f
    LaunchedEffect(id, ownsPlayback, skip, reduced) {
        if (playback == null || id == null || !ownsPlayback) return@LaunchedEffect
        if (skip || reduced) {
            progress.snapTo(1f)
        } else if (progress.value < 1f) {
            val remaining = ((1f - progress.value) * playback.durationMs).roundToInt().coerceAtLeast(1)
            progress.animateTo(1f, tween(remaining, easing = LinearEasing))
        }
        onFinished(id)
    }
    val playing by remember(progress, ownsPlayback) { derivedStateOf { ownsPlayback && progress.value < 1f } }
    val displayLevel = if (receipt != null && playing) receipt.before else card.level
    val light = rememberCardLight(displayLevel >= 10 || card.grade.ordinal > 0)
    val solar by rememberEclipseCorona(!reduced && maxOf(card.level, receipt?.before ?: 0) >= 25)
    var origin by remember { mutableStateOf(androidx.compose.ui.geometry.Offset.Zero) }
    var source by remember { mutableStateOf(Rect.Zero) }
    BoxWithConstraints(modifier.fillMaxSize().clipToBounds().onGloballyPositioned { origin = it.positionInRoot() }) {
        // Hide the background from accessibility and hit testing until the result is actually visible.
        Box(if (playing) Modifier.clearAndSetSemantics {} else Modifier) {
            workshop(playing) { art ->
                Box(
                    art.onGloballyPositioned {
                        val point = it.positionInRoot()
                        source = Rect(point.x, point.y, point.x + it.size.width, point.y + it.size.height)
                    },
                    contentAlignment = Alignment.Center,
                ) {
                    if (!playing) {
                        CardPrintView(card, Modifier.fillMaxSize(), inspectable = true, illumination = light)
                        if (playback != null && (skip || reduced)) {
                            ForgeResultMark(
                                playback.last,
                                Modifier.fillMaxWidth().align(Alignment.Center),
                                compact = true,
                            )
                        }
                    }
                }
            }
        }
        if (playing && playback != null) {
            val relative = source.translate(-origin.x, -origin.y)
            val sceneWidth = constraints.maxWidth.toFloat()
            val sceneHeight = constraints.maxHeight.toFloat()
            val width = minOf(maxWidth * .62f, maxHeight * .505f)
            val titleWidth = minOf(maxWidth * .90f, maxHeight * 1.36f)
            val printWidth = with(LocalDensity.current) { width.toPx().coerceAtLeast(1f) }
            val overlap = relative.intersect(Rect(0f, 0f, sceneWidth, sceneHeight))
            val visible =
                relative.width > 0f && overlap.width > relative.width * .55f && overlap.height > relative.height * .55f
            val scale = if (visible) (relative.width / printWidth).coerceIn(.15f, 1.8f) else .78f
            val dx = if (visible) relative.center.x - sceneWidth / 2f else 0f
            val dy = if (visible) relative.center.y - sceneHeight / 2f else 0f
            val sequence = remember(playback, progress) { derivedStateOf { playback.sequenceAt(progress.value) } }.value
            val time = remember(playback, sequence, progress) {
                derivedStateOf { playback.localTime(sequence, progress.value) }
            }
            Box(
                Modifier.fillMaxSize().testTag("forge-theatre"),
                contentAlignment = Alignment.Center,
            ) {
                Box(
                    Modifier.matchParentSize().pointerInput(Unit) {
                        awaitPointerEventScope { while (true) awaitPointerEvent().changes.forEach { it.consume() } }
                    },
                )
                ForgeField(
                    sequence,
                    time,
                    printWidth,
                    solar,
                    Modifier.fillMaxSize().graphicsLayer {
                        alpha = playback.enter(progress.value) * (1f - playback.exit(progress.value))
                    },
                )
                ForgeMovingPrint(
                    card,
                    sequence,
                    time,
                    light,
                    Modifier.width(width).aspectRatio(2f / 3f).graphicsLayer {
                        val follow = 1f - playback.enter(progress.value) + playback.exit(progress.value)
                        scaleX = lerp(1f, scale, follow)
                        scaleY = scaleX
                        translationX = dx * follow
                        translationY = dy * follow
                        alpha = if (visible) 1f else 1f - follow
                    },
                )
                val hasTitle by remember(sequence, time) { derivedStateOf { time.value >= sequence.titleAt } }
                if (hasTitle) {
                    ForgeResultMark(sequence, Modifier.width(titleWidth), clock = time)
                }
                Row(
                    Modifier.align(Alignment.TopCenter).fillMaxWidth().padding(horizontal = 16.dp),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    CollectionBack("뒤로", onBack)
                    PleosTextLink("연출 건너뛰기", { skip = true }, Modifier.testTag("forge-skip"))
                }
            }
        }
    }
}
