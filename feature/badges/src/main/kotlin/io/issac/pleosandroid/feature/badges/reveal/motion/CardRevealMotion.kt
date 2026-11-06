package io.issac.pleosandroid.feature.badges.reveal.motion

import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.runtime.Composable
import androidx.compose.runtime.State
import androidx.compose.runtime.derivedStateOf
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.draw.drawWithCache
import androidx.compose.ui.draw.drawWithContent
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Rect
import androidx.compose.ui.geometry.RoundRect
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.GraphicsLayerScope
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.PathMeasure
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.drawscope.DrawScope
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.drawscope.clipPath
import androidx.compose.ui.graphics.drawscope.withTransform
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.semantics.clearAndSetSemantics
import androidx.compose.ui.unit.dp
import androidx.compose.ui.util.lerp
import io.issac.pleosandroid.core.designsystem.component.MILESTONE_CARD_ASPECT_RATIO
import io.issac.pleosandroid.core.designsystem.component.MilestoneCard
import io.issac.pleosandroid.core.designsystem.theme.PleosTheme
import io.issac.pleosandroid.feature.badges.model.CollectionCard
import kotlin.math.PI
import kotlin.math.abs
import kotlin.math.exp
import kotlin.math.max
import kotlin.math.sin

internal enum class CardRevealMotion(val durationMillis: Int, val revealStart: Float = 0f, val revealEnd: Float = 1f) {
    Flip(1440),
    Foil(2080, .43f, .78f),
    Tide(2800, .50f, .81f),
    Opal(3600, .56f, .82f),
    Gold(4600, .61f, .85f),
    ;

    companion object {
        fun forCard(card: CollectionCard): CardRevealMotion = when {
            card.revealedLevel == null -> Flip
            card.earnedLevel == 5 -> Gold
            card.earnedLevel == 4 -> Opal
            card.earnedLevel == 3 -> Tide
            else -> Foil
        }
    }
}

// All layers read the same clock. Only the edge-on face swap needs recomposition.
@Composable
internal fun RevealCardPrint(
    card: CollectionCard,
    motion: CardRevealMotion,
    progress: State<Float>,
    lightAngle: () -> Float,
    modifier: Modifier = Modifier,
) {
    val colors = PleosTheme.colors
    val evolution = motion != CardRevealMotion.Flip
    val advanced = evolution && motion != CardRevealMotion.Foil
    val mint = colors.actionPrimary
    val lilac = colors.accentLilac
    val light = when (motion) {
        CardRevealMotion.Opal -> lilac
        CardRevealMotion.Gold -> colors.statusPending
        else -> mint
    }
    val accent = when (motion) {
        CardRevealMotion.Opal -> mint
        CardRevealMotion.Gold -> Color(0xFFCE884B)
        else -> lilac
    }
    val surround = if (advanced) {
        Modifier.foilBacklight(motion, progress, light, accent)
    } else {
        Modifier.drawBehind { drawRevealLight(progress.value, evolution, mint, lilac) }
    }
    Box(modifier.aspectRatio(MILESTONE_CARD_ASPECT_RATIO).clearAndSetSemantics { }.then(surround)) {
        Box(
            Modifier.matchParentSize().graphicsLayer {
                val t = progress.value
                if (advanced) {
                    rockFoil(motion, t)
                    return@graphicsLayer
                }
                val lift = envelope(t, 0f, if (evolution) .40f else .30f, 1f)
                val punch = if (evolution) envelope(t, .54f, .71f, .91f) else 0f
                scaleX = 1f + .035f * lift + .035f * punch
                scaleY = scaleX
                translationY = -12.dp.toPx() * lift
                rotationZ = -2.5f * lift + 2f * punch
                val angle = if (evolution) 0f else flipDegrees(t)
                // The opposite print faces the viewer after the edge passes 90°.
                rotationY = if (angle >= 90f) angle - 180f else angle
                cameraDistance = size.width.coerceAtLeast(1f) * 2.5f
            }.clip(RoundedCornerShape(percent = 5)),
        ) {
            if (advanced) {
                MilestoneCard(card.face(), Modifier.matchParentSize(), lightAngle)
                MilestoneCard(
                    card.face(card.earnedLevel),
                    Modifier.matchParentSize().foilExposure(motion, progress, light, accent),
                    lightAngle,
                )
            } else if (evolution) {
                MilestoneCard(card.face(), Modifier.matchParentSize().chargeFoil(progress, mint, lilac), lightAngle)
                MilestoneCard(
                    card.face(card.earnedLevel),
                    Modifier.matchParentSize().evolutionWipe(progress, mint, lilac),
                    lightAngle,
                )
            } else {
                val front by remember(progress) { derivedStateOf { flipDegrees(progress.value) >= 90f } }
                MilestoneCard(
                    card.face(if (front) card.earnedLevel else null),
                    Modifier.matchParentSize().drawWithContent {
                        drawContent()
                        val t = progress.value
                        val edge = abs(sin(flipDegrees(t) * (PI.toFloat() / 180f)))
                        drawRect(colors.background.copy(alpha = .30f * edge))
                        val sweep = phase(t, .66f, .98f)
                        if (sweep > 0f && sweep < 1f) {
                            val middle = Offset(lerp(-size.width * .55f, size.width * 1.55f, sweep), center.y)
                            drawFoilBand(middle, Offset(size.width * .17f, -size.width * .06f), mint, lilac, .55f)
                        }
                    },
                    lightAngle,
                )
            }
        }
    }
}

private fun GraphicsLayerScope.rockFoil(motion: CardRevealMotion, t: Float) {
    val impact = motion.revealStart
    val lift = envelope(t, 0f, impact, 1f)
    val lean = envelope(t, 0f, impact - .06f, impact + .10f)
    val rebound = envelope(t, impact - .012f, impact + .075f, .97f)
    val weight = when (motion) {
        CardRevealMotion.Opal -> 1f
        CardRevealMotion.Gold -> 1.85f
        else -> 0f
    }
    val seconds = (t - impact) * motion.durationMillis / 1000f
    // A small knock anticipates the release. The recoil reverses direction and loses energy on each swing.
    val recoil = when (motion) {
        CardRevealMotion.Opal -> .52f * dampedSwing(seconds + .36f, 3.25f) - dampedSwing(seconds, 3.65f)
        CardRevealMotion.Gold -> .69f * dampedSwing(seconds + .48f, 2.9f) - dampedSwing(seconds, 3.05f, decay = 3.9f)
        else -> 0f
    }
    val swing = weight * recoil * (1f - phase(t, .91f, 1f))
    val compression = weight * envelope(t, impact - .09f, impact - .012f, impact + .045f)
    val width = size.width
    translationX = width * .027f * swing
    translationY = width * (-.018f * lift - .017f * weight * rebound + .006f * swing + .014f * compression)
    rotationX = 3.5f * lean - (2f + weight) * rebound
    rotationY = (7f + weight * 1.8f) * lean - (5f + weight * 2f) * rebound + 2.1f * swing
    rotationZ = -1.9f * lean + 1.7f * rebound + 3.4f * swing
    scaleX = 1f + .021f * lift + (.012f + .012f * weight) * rebound
    scaleY = scaleX - .009f * compression
    cameraDistance = width.coerceAtLeast(1f) * 3.5f
}

private fun dampedSwing(seconds: Float, frequency: Float, decay: Float = 5.4f): Float =
    if (seconds <= 0f) 0f else sin(seconds * frequency * (2 * PI).toFloat()) * exp(-decay * seconds)

private fun flipDegrees(t: Float): Float {
    val anticipation = -11f * phase(t, 0f, .22f)
    return lerp(anticipation, 180f, phase(t, .28f, .77f))
}

private fun Modifier.chargeFoil(progress: State<Float>, mint: Color, lilac: Color): Modifier = drawWithCache {
    val inset = 2.dp.toPx()
    val frame = Rect(inset, inset, size.width - inset, size.height - inset)
    val outline = Path().apply { addRoundRect(RoundRect(frame, CornerRadius(size.width * .05f))) }
    val measure = PathMeasure().apply { setPath(outline, true) }
    val trace = Path()
    val foil = Brush.linearGradient(listOf(lilac, mint, lilac))
    val glow = Stroke(6.dp.toPx(), cap = StrokeCap.Round)
    val edge = Stroke(2.dp.toPx(), cap = StrokeCap.Round)
    onDrawWithContent {
        drawContent()
        val t = progress.value
        val strength = envelope(t, 0f, .30f, .67f)
        if (strength > 0f) {
            val tip = measure.length * phase(t, .02f, .46f)
            trace.rewind()
            measure.getSegment(0f, tip, trace)
            drawPath(trace, foil, alpha = .33f * strength, style = glow)
            drawPath(trace, foil, alpha = strength, style = edge)
            drawCircle(mint, 3.dp.toPx(), measure.getPosition(tip), alpha = strength)
        }
    }
}

/** The grown print develops from the bottom left up the face of the existing print. */
private fun Modifier.evolutionWipe(progress: State<Float>, mint: Color, lilac: Color): Modifier = drawWithContent {
    val sweep = phase(progress.value, CardRevealMotion.Foil.revealStart, CardRevealMotion.Foil.revealEnd)
    if (sweep <= 0f) return@drawWithContent
    if (sweep >= 1f) {
        drawContent()
        return@drawWithContent
    }
    val slope = .32f
    val rise = size.width * slope
    val leftY = lerp(size.height, -rise, sweep)
    val revealed = Path().apply {
        moveTo(0f, leftY)
        lineTo(size.width, leftY + rise)
        lineTo(size.width, size.height)
        lineTo(0f, size.height)
        close()
    }
    clipPath(revealed) { this@drawWithContent.drawContent() }
    val middle = Offset(center.x, leftY + rise / 2)
    val band = size.height * .085f
    drawFoilBand(middle, Offset(-slope * band, band), mint, lilac, .86f)
}

private fun DrawScope.drawFoilBand(middle: Offset, halfWidth: Offset, mint: Color, lilac: Color, opacity: Float) {
    drawRect(
        Brush.linearGradient(
            0f to Color.Transparent,
            .35f to lilac.copy(alpha = .38f),
            .49f to mint.copy(alpha = .92f),
            .51f to Color.White,
            .64f to mint.copy(alpha = .36f),
            1f to Color.Transparent,
            start = middle - halfWidth,
            end = middle + halfWidth,
        ),
        alpha = opacity,
    )
}

private fun DrawScope.drawRevealLight(t: Float, evolution: Boolean, mint: Color, lilac: Color) {
    if (t <= 0f || t >= 1f) return
    val charge = if (evolution) envelope(t, .02f, .40f, .68f) else 0f
    val burst = if (evolution) envelope(t, .51f, .72f, 1f) else envelope(t, .40f, .68f, 1f)
    val glow = max(.78f * charge, burst)
    if (glow <= 0f) return

    val glowColor = if (evolution) lilac else mint
    withTransform({ scale(1f, size.height / size.width, center) }) {
        drawCircle(
            Brush.radialGradient(
                0f to glowColor.copy(alpha = .35f),
                .68f to glowColor.copy(alpha = .24f),
                1f to Color.Transparent,
                center = center,
                radius = size.width * .72f,
            ),
            radius = size.width * .72f,
            alpha = glow,
        )
    }

    // Small glints converge during the charge and travel away from the edges on release.
    val travel = if (evolution) phase(t, .49f, .99f) else phase(t, .44f, .98f)
    val distance = if (evolution) 1.22f - .24f * phase(t, .03f, .48f) + .25f * travel else 1f + .22f * travel
    for ((index, direction) in rimGlints.withIndex()) {
        val position = center + Offset(direction.x * size.width, direction.y * size.height) * distance
        val strength = max(charge * .8f, burst)
        val length = (3 + index % 3).dp.toPx() * strength
        val color = (if (index % 2 == 0) mint else lilac).copy(alpha = .88f * strength)
        val horizontal = Offset(length * .65f, 0f)
        val vertical = Offset(0f, length)
        drawLine(color, position - horizontal, position + horizontal, 1.dp.toPx())
        drawLine(color, position - vertical, position + vertical, 1.dp.toPx())
        drawCircle(color, radius = length * .30f, center = position)
    }
}

private val rimGlints = listOf(
    Offset(-.43f, -.48f),
    Offset(.25f, -.50f),
    Offset(.50f, -.31f),
    Offset(.50f, .18f),
    Offset(.41f, .48f),
    Offset(-.30f, .50f),
    Offset(-.50f, .27f),
    Offset(-.50f, -.18f),
)

internal fun phase(t: Float, start: Float, end: Float): Float =
    FastOutSlowInEasing.transform(((t - start) / (end - start)).coerceIn(0f, 1f))

internal fun envelope(t: Float, start: Float, peak: Float, end: Float): Float =
    phase(t, start, peak) * (1f - phase(t, peak, end))
