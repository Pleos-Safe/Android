package io.issac.pleosandroid.feature.forge.motion

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.State
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.drawWithContent
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.BlendMode
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.CompositingStrategy
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.Shadow
import androidx.compose.ui.graphics.drawscope.DrawScope
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.semantics.LiveRegionMode
import androidx.compose.ui.semantics.clearAndSetSemantics
import androidx.compose.ui.semantics.liveRegion
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.ExperimentalTextApi
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.Font
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontVariation
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import io.issac.pleosandroid.core.cardui.component.label
import io.issac.pleosandroid.core.designsystem.R as DesignR
import io.issac.pleosandroid.core.designsystem.theme.PleosFontFamily
import io.issac.pleosandroid.core.domain.cards.ForgeOutcome

@OptIn(ExperimentalTextApi::class)
private val titleFace = FontFamily(
    Font(
        DesignR.font.noto_sans_kr,
        weight = FontWeight.ExtraBold,
        variationSettings = FontVariation.Settings(FontVariation.weight(780)),
    ),
)

@Composable
internal fun ForgeResultMark(
    sequence: ForgeSequence,
    modifier: Modifier,
    compact: Boolean = false,
    clock: State<Float>? = null,
) {
    val accent = sequence.accent
    val ink = Color(0xFF070A15)
    val semantics = if (compact) {
        Modifier.clearAndSetSemantics {}
    } else {
        Modifier.semantics(mergeDescendants = true) {
            liveRegion = LiveRegionMode.Polite
        }.testTag("forge-result-mark")
    }
    BoxWithConstraints(
        modifier.then(
            if (clock ==
                null
            ) {
                Modifier
            } else {
                Modifier.stamp(sequence, clock)
            },
        ).then(semantics),
        contentAlignment = Alignment.Center,
    ) {
        val font = (maxWidth * if (compact) .084f else .088f).coerceIn(
            if (compact) 13.dp else 26.dp,
            if (compact) 32.dp else 68.dp,
        )
        val titleSize = with(LocalDensity.current) { font.toSp() }
        val subSize = with(LocalDensity.current) { (font * .38f).coerceAtLeast(if (compact) 10.dp else 14.dp).toSp() }
        val paper = Brush.verticalGradient(listOf(Color.White, Color(0xFFFFFAEE), accent))
        Canvas(Modifier.matchParentSize()) {
            val w = size.width
            val h = size.height
            val glass = Brush.horizontalGradient(
                0f to ink.copy(alpha = 0f),
                .15f to ink.copy(alpha = .67f),
                .31f to ink.copy(alpha = .95f),
                .69f to ink.copy(alpha = .95f),
                .85f to ink.copy(alpha = .67f),
                1f to ink.copy(alpha = 0f),
            )
            val banner = Path().apply {
                moveTo(0f, h * .27f)
                lineTo(w * .14f, h * .11f)
                lineTo(w * .50f, 0f)
                lineTo(w * .86f, h * .11f)
                lineTo(w, h * .27f)
                lineTo(w, h * .73f)
                lineTo(w * .78f, h * .88f)
                lineTo(w * .50f, h)
                lineTo(w * .22f, h * .88f)
                lineTo(0f, h * .73f)
                close()
            }
            drawPath(banner, glass)
            val metal = Brush.horizontalGradient(
                0f to Color.Transparent,
                .23f to accent.copy(alpha = .75f),
                .5f to Color.White,
                .77f to accent.copy(alpha = .75f),
                1f to Color.Transparent,
            )
            drawPath(banner, metal, style = Stroke((w * .0013f).coerceAtLeast(1f)))
            if (!compact) {
                val lineY = h * .47f
                for (side in listOf(-1f, 1f)) {
                    val x = w * (.5f + side * .285f)
                    drawLine(metal, Offset(x + side * w * .20f, lineY), Offset(x + side * w * .032f, lineY), w * .0018f)
                    drawSigil(sequence.outcome, Offset(x, lineY), w * .013f, accent)
                }
            }
        }
        Column(
            Modifier.fillMaxWidth().padding(vertical = if (compact) 4.dp else font * .36f),
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.spacedBy(if (compact) 0.dp else 3.dp),
        ) {
            Text(
                sequence.outcome.label,
                modifier = if (clock == null) Modifier else Modifier.letterReflection(sequence, clock),
                maxLines = 1,
                textAlign = TextAlign.Center,
                style = TextStyle(
                    brush = paper,
                    fontFamily = titleFace,
                    fontWeight = FontWeight.ExtraBold,
                    fontSize = titleSize,
                    lineHeight = titleSize * 1.15f,
                    letterSpacing = titleSize * -.025f,
                    shadow = Shadow(Color.Black.copy(alpha = .75f), Offset(0f, 2f), 5f),
                ),
            )
            if (!compact) {
                Text(
                    sequence.levelLine,
                    fontFamily = PleosFontFamily,
                    fontWeight = FontWeight.Medium,
                    fontSize = subSize,
                    lineHeight = subSize * 1.2f,
                    letterSpacing = 0.sp,
                    color = accent,
                    textAlign = TextAlign.Center,
                )
            }
        }
    }
}

private fun Modifier.stamp(sequence: ForgeSequence, clock: State<Float>) = graphicsLayer {
    val t = clock.value
    val entry = sequence.title(t)
    val remaining = 1f - entry
    alpha = entry * (1f - sequence.exit(t))
    when (sequence.outcome) {
        ForgeOutcome.Hold -> scaleX = 1f - .10f * remaining

        ForgeOutcome.Drop -> {
            translationY = -size.height * .33f * remaining
            rotationZ = -1.5f * remaining
        }

        ForgeOutcome.Destroyed -> {
            translationX = size.width * .018f * kotlin.math.sin(11f * entry) * remaining
            rotationZ = 2.5f * remaining
        }

        ForgeOutcome.GreatSuccess -> {
            scaleX = 1f + .14f * remaining
            scaleY = scaleX
        }

        else -> {
            translationY = size.height * .23f * remaining
            scaleX = 1f - .04f * remaining
            scaleY = scaleX
        }
    }
}

private fun Modifier.letterReflection(sequence: ForgeSequence, clock: State<Float>) =
    graphicsLayer { compositingStrategy = CompositingStrategy.Offscreen }.drawWithContent {
        drawContent()
        val sheen = sequence.letterGlint(clock.value)
        if (sheen <= 0f || sheen >= 1f) return@drawWithContent
        val x = size.width * (-.42f + 1.84f * sheen)
        val beam = Brush.linearGradient(
            listOf(
                Color.Transparent,
                Color.White.copy(alpha = .85f),
                sequence.secondary.copy(alpha = .36f),
                Color.Transparent,
            ),
            Offset(x - size.width * .14f, 0f),
            Offset(x + size.width * .14f, size.height * .30f),
        )
        drawRect(beam, blendMode = BlendMode.SrcAtop)
    }

private fun DrawScope.drawSigil(outcome: ForgeOutcome, at: Offset, r: Float, color: Color) {
    val weight = (r * .18f).coerceAtLeast(1f)
    fun chevron(y: Float, up: Float) {
        val tip = Offset(at.x, at.y + y - up * r * .67f)
        drawLine(color, Offset(at.x - r * .72f, at.y + y), tip, weight)
        drawLine(color, tip, Offset(at.x + r * .72f, at.y + y), weight)
    }
    when (outcome) {
        ForgeOutcome.Hold -> for (dy in listOf(-.28f, .28f)) {
            drawLine(color, at + Offset(-r * .77f, r * dy), at + Offset(r * .77f, r * dy), weight)
        }

        ForgeOutcome.Drop -> chevron(-r * .32f, -1f)

        ForgeOutcome.Destroyed -> {
            drawLine(color, at + Offset(r * .13f, -r), at + Offset(-r * .45f, r * .1f), weight)
            drawLine(color, at + Offset(-r * .45f, r * .1f), at + Offset(r * .33f, -r * .08f), weight)
            drawLine(color, at + Offset(r * .33f, -r * .08f), at + Offset(-r * .19f, r), weight)
        }

        ForgeOutcome.GreatSuccess -> {
            chevron(-r * .04f, 1f)
            chevron(r * .66f, 1f)
        }

        else -> chevron(r * .36f, 1f)
    }
}
