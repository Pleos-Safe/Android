package io.issac.pleosandroid.core.designsystem.component

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.Image
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.absoluteOffset
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.text.BasicText
import androidx.compose.foundation.text.TextAutoSize
import androidx.compose.runtime.Composable
import androidx.compose.ui.AbsoluteAlignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clipToBounds
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Rect
import androidx.compose.ui.geometry.RoundRect
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.drawscope.clipPath
import androidx.compose.ui.graphics.drawscope.translate
import androidx.compose.ui.graphics.painter.Painter
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import io.issac.pleosandroid.core.designsystem.R

enum class MilestoneCardAppearance(
    internal val level: Int,
    internal val isSafety: Boolean,
    internal val base: Int?,
    internal val overlay: Int,
) {
    PlaceLevel1(1, false, R.drawable.milestone_place_1_base, R.drawable.milestone_place_1_overlay),
    PlaceLevel2(2, false, R.drawable.milestone_place_2_base, R.drawable.milestone_place_2_overlay),
    PlaceLevel3(3, false, R.drawable.milestone_place_4_base, R.drawable.milestone_place_2_overlay),
    PlaceLevel4(4, false, R.drawable.milestone_place_4_base, R.drawable.milestone_place_4_overlay),
    PlaceLevel5(5, false, R.drawable.milestone_place_5_base, R.drawable.milestone_place_5_overlay),
    SafetyLevel1(1, true, null, R.drawable.milestone_safety_detail_overlay),
    SafetyLevel2(2, true, R.drawable.milestone_safety_5_base, R.drawable.milestone_safety_detail_overlay),
    SafetyLevel3(3, true, R.drawable.milestone_safety_5_base, R.drawable.milestone_safety_detail_overlay),
    SafetyLevel4(4, true, R.drawable.milestone_safety_5_base, R.drawable.milestone_safety_detail_overlay),
    SafetyLevel5(5, true, R.drawable.milestone_safety_5_base, R.drawable.milestone_safety_5_overlay),
}

sealed interface MilestoneCardFace {
    data class Front(
        val appearance: MilestoneCardAppearance,
        val artwork: Painter,
        val contextLabel: String,
        val title: String,
        val subtitle: String,
        val seriesLabel: String,
        val categoryLabel: String? = null,
    ) : MilestoneCardFace

    data object CommonBack : MilestoneCardFace
}

/** Draws a face at its design aspect ratio. Size and interaction are supplied by the caller. */
@Composable
fun MilestoneCard(
    face: MilestoneCardFace,
    modifier: Modifier = Modifier,
) {
    BoxWithConstraints(
        modifier = modifier
            .aspectRatio(CardWidth / CardHeight)
            .clipToBounds()
            .semantics(mergeDescendants = true) {},
        contentAlignment = AbsoluteAlignment.TopLeft,
    ) {
        val scale = maxWidth.value / CardWidth
        when (face) {
            is MilestoneCardFace.Front -> FrontFace(face, scale)
            MilestoneCardFace.CommonBack -> CommonBackFace(scale)
        }
    }
}

@Composable
private fun FrontFace(face: MilestoneCardFace.Front, scale: Float) {
    val appearance = face.appearance
    val base = appearance.base
    if (base != null) CardPlane(base) else SafetyBase()

    if (appearance.isSafety) {
        SafetyArtwork(face.artwork, appearance)
    } else {
        PlaceArtwork(face.artwork, appearance == MilestoneCardAppearance.PlaceLevel3)
    }

    CardPlane(appearance.overlay)
    if (appearance.isSafety) SafetyLabels(face, scale) else PlaceLabels(face, scale)
}

@Composable
private fun PlaceArtwork(artwork: Painter, partialReflection: Boolean) {
    Canvas(modifier = Modifier.fillMaxSize()) {
        val scale = size.width / CardWidth
        val mask = Path().apply {
            addRoundRect(
                RoundRect(
                    rect = Rect(30f * scale, 76f * scale, 330f * scale, 384f * scale),
                    cornerRadius = CornerRadius(6f * scale),
                ),
            )
        }
        clipPath(mask) {
            translate(left = 24f * scale, top = 62f * scale) {
                with(artwork) {
                    draw(size = Size(312f * scale, 448.5f * scale))
                }
            }
            // The atelier reference places the Level 3 reflection on the water.
            if (partialReflection) {
                val water = Path().apply {
                    moveTo(30f * scale, 274f * scale)
                    quadraticTo(138f * scale, 250f * scale, 330f * scale, 268f * scale)
                    lineTo(330f * scale, 384f * scale)
                    lineTo(30f * scale, 384f * scale)
                    close()
                }
                drawPath(
                    path = water,
                    brush = Brush.horizontalGradient(
                        colors = listOf(Color(0xA6BB56E6), Color(0x5FF6C879), Color(0x8065EBD0)),
                        startX = 30f * scale,
                        endX = 330f * scale,
                    ),
                )
            }
        }
    }
}

@Composable
private fun SafetyBase() {
    Canvas(modifier = Modifier.fillMaxSize()) {
        val scale = size.width / CardWidth
        drawRoundRect(
            color = Color(0xFF7D9DAC),
            cornerRadius = CornerRadius(20f * scale),
        )
        val rim = 5f * scale
        drawRoundRect(
            color = Color(0xFFE6EFF0),
            topLeft = Offset(rim, rim),
            size = Size(size.width - rim * 2, size.height - rim * 2),
            cornerRadius = CornerRadius(15f * scale),
        )
    }
}

@Composable
private fun SafetyArtwork(artwork: Painter, appearance: MilestoneCardAppearance) {
    Canvas(modifier = Modifier.fillMaxSize()) {
        val scale = size.width / CardWidth
        val corner = CornerRadius(10f * scale)
        val mask = Path().apply {
            addRoundRect(
                RoundRect(
                    rect = Rect(20f * scale, 62f * scale, 340f * scale, 362f * scale),
                    topLeft = corner,
                    topRight = corner,
                    bottomRight = CornerRadius.Zero,
                    bottomLeft = CornerRadius.Zero,
                ),
            )
        }
        clipPath(mask) {
            translate(left = 20f * scale, top = 62f * scale) {
                with(artwork) {
                    draw(size = Size(320f * scale, 300f * scale))
                }
            }
            // The atelier references show a narrow reflection at Level 3 and a wider sheen at Level 4.
            if (appearance == MilestoneCardAppearance.SafetyLevel4) {
                drawRect(
                    brush = Brush.linearGradient(
                        colors = listOf(Color(0x233D6B9C), Color(0x2F8A72B8), Color(0x274DB9A2)),
                        start = Offset(20f * scale, 62f * scale),
                        end = Offset(340f * scale, 362f * scale),
                    ),
                    topLeft = Offset(20f * scale, 62f * scale),
                    size = Size(320f * scale, 300f * scale),
                )
            }
            if (appearance == MilestoneCardAppearance.SafetyLevel3 ||
                appearance == MilestoneCardAppearance.SafetyLevel4
            ) {
                val reflection = Path().apply {
                    moveTo(216f * scale, 62f * scale)
                    lineTo(249f * scale, 62f * scale)
                    lineTo(147f * scale, 362f * scale)
                    lineTo(115f * scale, 362f * scale)
                    close()
                }
                drawPath(
                    path = reflection,
                    brush = Brush.verticalGradient(
                        colors = listOf(Color(0x4AA9A0CF), Color(0x369ED6D0)),
                        startY = 62f * scale,
                        endY = 362f * scale,
                    ),
                )
            }
        }
    }
}

@Composable
private fun PlaceLabels(face: MilestoneCardFace.Front, scale: Float) {
    CardText(face.contextLabel, scale, 28, 30, 127, 20, 12, Color(0xFF65778A))
    face.categoryLabel?.let {
        CardText(it, scale, 205, 30, 127, 20, 12, Color(0xFF65778A), alignment = TextAlign.Right)
    }
    CardText(face.title, scale, 28, 414, 304, 37, 28, Color(0xFF132C40), weight = FontWeight.Bold)
    CardText(face.subtitle, scale, 29, 453, 303, 26, 14, Color(0xFF687B8B))
    CardText(
        text = stringResource(R.string.milestone_card_level, face.appearance.level),
        scale = scale,
        x = 28,
        y = 490,
        width = 100,
        height = 25,
        fontSize = 12,
        color = Color(0xFF425F73),
    )
    CardText(
        text = face.seriesLabel,
        scale = scale,
        x = 138,
        y = 492,
        width = 194,
        height = 24,
        fontSize = 10,
        color = Color(0xFF7A8D9A),
        alignment = TextAlign.Right,
    )
}

@Composable
private fun SafetyLabels(face: MilestoneCardFace.Front, scale: Float) {
    CardText(face.contextLabel, scale, 22, 20, 250, 26, 15, Color(0xFF466778))
    CardText(face.title, scale, 22, 384, 316, 38, 27, Color(0xFF17384A), weight = FontWeight.Bold)
    CardText(face.subtitle, scale, 22, 424, 316, 37, 16, Color(0xFF527084))
    CardText(
        text = stringResource(R.string.milestone_card_level, face.appearance.level),
        scale = scale,
        x = 22,
        y = 476,
        width = 110,
        height = 31,
        fontSize = 16,
        color = Color(0xFF527084),
    )
    CardText(
        text = face.seriesLabel,
        scale = scale,
        x = 148,
        y = 492,
        width = 190,
        height = 24,
        fontSize = 10,
        color = Color(0xFF708E9C),
        alignment = TextAlign.Right,
    )
}

@Composable
private fun CommonBackFace(scale: Float) {
    CardPlane(R.drawable.milestone_common_back)
    CardText(
        text = stringResource(R.string.milestone_card_back_title),
        scale = scale,
        x = 32,
        y = 328,
        width = 296,
        height = 35,
        fontSize = 18,
        color = Color(0xFFB3CED2),
        alignment = TextAlign.Center,
    )
    CardText(
        text = stringResource(R.string.milestone_card_back_subtitle),
        scale = scale,
        x = 32,
        y = 366,
        width = 296,
        height = 31,
        fontSize = 16,
        color = Color(0xFF7396A6),
        alignment = TextAlign.Center,
    )
}

@Composable
private fun CardPlane(drawable: Int) {
    Image(
        painter = painterResource(drawable),
        contentDescription = null,
        modifier = Modifier.fillMaxSize(),
        contentScale = ContentScale.FillBounds,
    )
}

@Composable
private fun CardText(
    text: String,
    scale: Float,
    x: Int,
    y: Int,
    width: Int,
    height: Int,
    fontSize: Int,
    color: Color,
    weight: FontWeight = FontWeight.Normal,
    alignment: TextAlign = TextAlign.Left,
) {
    val preferredSize = (fontSize * scale).sp
    BasicText(
        text = text,
        modifier = Modifier
            .absoluteOffset(x = (x * scale).dp, y = (y * scale).dp)
            .width((width * scale).dp)
            .heightIn(max = (height * scale).dp)
            .clipToBounds(),
        style = TextStyle(
            color = color,
            fontSize = preferredSize,
            fontWeight = weight,
            textAlign = alignment,
        ),
        autoSize = TextAutoSize.StepBased(
            minFontSize = (fontSize * scale * 0.8f).sp,
            maxFontSize = preferredSize,
            stepSize = 0.25.sp,
        ),
        maxLines = 1,
        softWrap = false,
        overflow = TextOverflow.Ellipsis,
    )
}

private const val CardWidth = 360f
private const val CardHeight = 520f
