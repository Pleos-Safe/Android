package io.issac.pleosandroid.core.designsystem.component

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.Image
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.absoluteOffset
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.width
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.AbsoluteAlignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clipToBounds
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Rect
import androidx.compose.ui.geometry.RoundRect
import androidx.compose.ui.geometry.Size
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
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import io.issac.pleosandroid.core.designsystem.R

enum class MilestoneCardAppearance {
    PlaceLevel1,
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
    when (face.appearance) {
        MilestoneCardAppearance.PlaceLevel1 -> {
            CardPlane(R.drawable.milestone_place_1_base)
            PlaceArtwork(face.artwork)
            CardPlane(R.drawable.milestone_place_1_overlay)
            PlaceLabels(face, scale)
        }
    }
}

@Composable
private fun PlaceArtwork(artwork: Painter) {
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
        }
    }
}

@Composable
private fun PlaceLabels(face: MilestoneCardFace.Front, scale: Float) {
    CardText(face.contextLabel, scale, 28, 30, 127, 12, Color(0xFF65778A))
    face.categoryLabel?.let {
        CardText(it, scale, 205, 30, 127, 12, Color(0xFF65778A), alignment = TextAlign.Right)
    }
    CardText(face.title, scale, 28, 414, 304, 28, Color(0xFF132C40), weight = FontWeight.Bold)
    CardText(face.subtitle, scale, 29, 453, 303, 14, Color(0xFF687B8B))
    CardText(
        text = stringResource(R.string.milestone_card_level, 1),
        scale = scale,
        x = 28,
        y = 490,
        width = 100,
        fontSize = 12,
        color = Color(0xFF425F73),
    )
    CardText(
        text = face.seriesLabel,
        scale = scale,
        x = 138,
        y = 492,
        width = 194,
        fontSize = 10,
        color = Color(0xFF7A8D9A),
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
    fontSize: Int,
    color: Color,
    weight: FontWeight = FontWeight.Normal,
    alignment: TextAlign = TextAlign.Left,
) {
    Text(
        text = text,
        modifier = Modifier
            .absoluteOffset(x = (x * scale).dp, y = (y * scale).dp)
            .width((width * scale).dp),
        style = TextStyle(
            color = color,
            fontFamily = FontFamily.Default,
            fontSize = (fontSize * scale).sp,
            fontWeight = weight,
            textAlign = alignment,
        ),
        maxLines = 1,
        softWrap = false,
        overflow = TextOverflow.Ellipsis,
    )
}

private const val CardWidth = 360f
private const val CardHeight = 520f
