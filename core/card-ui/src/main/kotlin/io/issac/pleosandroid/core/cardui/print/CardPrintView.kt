package io.issac.pleosandroid.core.cardui.print

import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.gestures.Orientation
import androidx.compose.foundation.gestures.draggable
import androidx.compose.foundation.gestures.rememberDraggableState
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.TextAutoSize
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.State
import androidx.compose.runtime.derivedStateOf
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.clipToBounds
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.ColorFilter
import androidx.compose.ui.graphics.ColorMatrix
import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.graphics.asAndroidBitmap
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.graphics.painter.BitmapPainter
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.res.imageResource
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.semantics.clearAndSetSemantics
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.em
import androidx.compose.ui.unit.sp
import io.issac.pleosandroid.core.cardui.R
import io.issac.pleosandroid.core.designsystem.component.rememberCardLight
import io.issac.pleosandroid.core.designsystem.theme.PleosFontFamily
import io.issac.pleosandroid.core.domain.cards.CardCopy
import io.issac.pleosandroid.core.domain.cards.CardGrade
import io.issac.pleosandroid.core.domain.cards.CopyStatus

private val plates = listOf(
    listOf(
        R.drawable.plate_common_0,
        R.drawable.plate_common_1,
        R.drawable.plate_common_2,
        R.drawable.plate_common_3,
        R.drawable.plate_common_4,
        R.drawable.plate_common_5,
    ),
    listOf(
        R.drawable.plate_rare_0,
        R.drawable.plate_rare_1,
        R.drawable.plate_rare_2,
        R.drawable.plate_rare_3,
        R.drawable.plate_rare_4,
        R.drawable.plate_rare_5,
    ),
    listOf(
        R.drawable.plate_epic_0,
        R.drawable.plate_epic_1,
        R.drawable.plate_epic_2,
        R.drawable.plate_epic_3,
        R.drawable.plate_epic_4,
        R.drawable.plate_epic_5,
    ),
)
private data class CardStock(val plateIndex: Int, val paper: Color, val ink: Color, val tint: ColorFilter? = null)

private fun metallicTint(shadow: Color, highlight: Color): ColorFilter {
    val rows = FloatArray(20)
    val low = listOf(shadow.red, shadow.green, shadow.blue)
    val high = listOf(highlight.red, highlight.green, highlight.blue)
    for (i in 0..2) {
        val gain = high[i] - low[i]
        rows[i * 5] = .213f * gain
        rows[i * 5 + 1] = .715f * gain
        rows[i * 5 + 2] = .072f * gain
        rows[i * 5 + 4] = 255f * low[i]
    }
    rows[18] = 1f
    return ColorFilter.colorMatrix(ColorMatrix(rows))
}

private val hyundaiBlue = Color(0xFF002C5F)
private val epitMint = Color(0xFF2CD5C4)
private val stocks = mapOf(
    CardGrade.Common to CardStock(0, Color(0xFFF6F3F2), Color(0xFF24313B)),
    CardGrade.Rare to CardStock(1, Color(0xFFE4DCD3), Color(0xFF3F3830)),
    CardGrade.Epic to CardStock(2, Color(0xFFA36B4F), Color(0xFFFFF2D4)),
    CardGrade.H to CardStock(1, epitMint, Color(0xFF072F37), metallicTint(Color(0xFF063F46), Color(0xFF60EBDA))),
    CardGrade.HH to CardStock(2, hyundaiBlue, Color(0xFFF1FAFF), metallicTint(Color(0xFF000F29), Color(0xFF3075A8))),
)

private val FullWindow = { 1f }
private val SquareWindow = { 0f }

internal val CardCopy.plateResource: Int get() = plates[stocks.getValue(grade).plateIndex][(level / 5).coerceAtMost(5)]
internal val CardCopy.artworkResource: Int get() =
    printArt.getValue(if (variant) requireNotNull(print.variantArtwork) else print.artwork)

class PrintLevelChange(val before: Int, val after: Int, val progress: () -> Float)

@Composable
fun CardPrintView(
    card: CardCopy,
    modifier: Modifier = Modifier,
    inspectable: Boolean = false,
    illumination: State<Float>? = null,
    surfaceRotation: State<Offset>? = null,
    artExpansion: (() -> Float)? = null,
    levelChange: PrintLevelChange? = null,
    flipAngle: State<Float>? = null,
    prepared: PreparedCardPrint? = null,
) {
    val stockStyle = stocks.getValue(card.grade)
    val stage = (card.level / 5).coerceAtMost(5)
    val expansion = artExpansion ?: if (stage >= 3) FullWindow else SquareWindow
    val plate = prepared?.plate ?: ImageBitmap.imageResource(card.plateResource)
    val trace = card.status == CopyStatus.Trace
    val frontAngle = flipAngle?.let { angle ->
        remember(angle) { derivedStateOf { angle.value.coerceAtLeast(90f) } }
    }
    val light = illumination ?: rememberCardLight(inspectable && (stage >= 2 || stockStyle.plateIndex > 0))
    var tilt by remember(card.id) { mutableFloatStateOf(0f) }
    var dragging by remember(card.id) { mutableStateOf(false) }
    val resting by androidx.compose.animation.core.animateFloatAsState(if (dragging) tilt else 0f, label = "카드 각도")
    BoxWithConstraints(
        modifier.aspectRatio(2f / 3f).clearAndSetSemantics {
            contentDescription = "${card.print.title}, ${card.grade}, +${card.level}" + if (card.variant) ", 변종" else ""
        },
    ) {
        val unit = maxWidth / 400
        val px = constraints.maxWidth.coerceAtLeast(1)
        val drag = if (inspectable) {
            Modifier.draggable(
                rememberDraggableState { delta -> tilt = (tilt + 42f * delta / px).coerceIn(-21f, 21f) },
                Orientation.Horizontal,
                onDragStarted = {
                    tilt = 0f
                    dragging = true
                },
                onDragStopped = { dragging = false },
            )
        } else {
            Modifier
        }
        Box(
            Modifier.fillMaxSize().then(drag).graphicsLayer {
                rotationY = resting
                cameraDistance = 12 * density
                alpha = if (trace) .48f else 1f
            }.clip(RoundedCornerShape(unit * 21)),
        ) {
            val stock = if (trace) {
                Modifier
            } else {
                val bitmap = plate.asAndroidBitmap()
                val flipFoil = if (frontAngle == null) {
                    Modifier
                } else {
                    Modifier.packFlipFoil(card.grade, bitmap, stage, expansion, frontAngle)
                }
                flipFoil.printCoating(
                    stage,
                    card.grade,
                    card.variant,
                    bitmap,
                    light = { light.value },
                    tilt = {
                        val rotation = surfaceRotation?.value ?: Offset.Zero
                        val yaw = (frontAngle?.value ?: 180f) - 180f
                        Offset(rotation.x + resting + yaw, rotation.y)
                    },
                    fullArt = expansion,
                )
            }
            Box(Modifier.fillMaxSize().clipPrintStock(stage, expansion).then(stock).background(stockStyle.paper)) {
                val artwork = if (prepared == null) {
                    painterResource(card.artworkResource)
                } else {
                    remember(prepared) { BitmapPainter(prepared.artwork) }
                }
                FramedPrintArtwork(artwork, stage, expansion)
                Image(
                    plate,
                    null,
                    Modifier.fillMaxSize(),
                    contentScale = ContentScale.FillBounds,
                    colorFilter = stockStyle.tint,
                )
            }
            val ink = stockStyle.ink
            val titleInk = if (stage >= 4 &&
                card.grade in setOf(CardGrade.H, CardGrade.HH)
            ) {
                Color.White
            } else if (stage >=
                4
            ) {
                Color(0xFFFFF4D7)
            } else {
                ink
            }
            PrintLabel(
                if (card.grade ==
                    CardGrade.H
                ) {
                    "E-pit"
                } else {
                    card.print.packType?.title ?: "기억"
                },
                unit,
                14,
                ink,
                unit.field(30, 36, 90),
            )
            PrintLabel(card.grade.name, unit, 15, ink, unit.field(274, 34, 95), TextAlign.End)
            if (card.variant) PrintLabel("변종", unit, 11, ink, unit.field(295, 58, 73), TextAlign.End)
            PrintCaption(card, unit, titleInk, levelChange)
            PrintLabel("차곡차곡", unit, 10, ink, unit.field(230, 543, 138), TextAlign.End)
            PrintLabel(card.print.serial, unit, 10, ink, unit.field(230, 559, 138), TextAlign.End)
            if (card.level % 5 != 0 && stage < 5) {
                Row(Modifier.offset(unit * 120, unit * 561), horizontalArrangement = Arrangement.spacedBy(unit * 4)) {
                    repeat(card.level % 5) { Spacer(Modifier.size(unit * 7, unit * 2).background(ink)) }
                }
            }
        }
    }
}

@Composable
private fun PrintCaption(card: CardCopy, unit: Dp, ink: Color, change: PrintLevelChange?) {
    // The nameplate and number badge share the same left edge on every plate from +5 onward.
    val startInset = if ((change?.before ?: card.level) >= 5) 7 else 0
    val endInset = if ((change?.after ?: card.level) >= 5) 7 else 0
    val block = Modifier.offset(unit * (29 + startInset), unit * 452).size(unit * 340, unit * 132)
    val position = if (change == null || startInset == endInset) {
        block
    } else {
        block.graphicsLayer {
            translationX = (unit * (endInset - startInset)).toPx() * change.progress()
        }
    }
    Box(position) {
        PrintLabel(card.print.title, unit, 26, ink, unit.field(2, 7, 324))
        PrintLabel(card.print.place, unit, 15, ink, unit.field(2, 42, 324))
        Box(
            Modifier.offset(y = unit * 90).size(unit * 56, unit * 38).clipToBounds(),
            contentAlignment = Alignment.CenterStart,
        ) {
            val label = Modifier.fillMaxWidth()
            if (change == null) {
                PrintLabel("+${card.level}", unit, 28, ink, label, minSize = 7)
            } else {
                PrintLabel("+${change.before}", unit, 28, ink, label.rollLevel(change, entering = false), minSize = 7)
                PrintLabel("+${change.after}", unit, 28, ink, label.rollLevel(change, entering = true), minSize = 7)
            }
        }
    }
}

private fun Modifier.rollLevel(change: PrintLevelChange, entering: Boolean): Modifier = graphicsLayer {
    val progress = change.progress()
    val travel = if (change.after > change.before) -size.height else size.height
    translationY = travel * if (entering) progress - 1f else progress
    alpha = if (entering) progress else 1f - progress
}

private fun Dp.field(x: Int, y: Int, width: Int): Modifier = Modifier.offset(this * x, this * y).width(this * width)

@Composable
private fun PrintLabel(
    value: String,
    unit: Dp,
    size: Int,
    ink: Color,
    modifier: Modifier,
    alignment: TextAlign = TextAlign.Start,
    minSize: Int = size,
) {
    val density = LocalDensity.current
    val font = with(density) { (unit * size).toSp() }
    val autoSize = if (minSize < size && unit > 0.dp) {
        with(density) { TextAutoSize.StepBased((unit * minSize).toSp(), font, stepSize = 0.25.sp) }
    } else {
        null
    }
    Text(
        value, modifier, color = ink, autoSize = autoSize, fontFamily = PleosFontFamily,
        fontWeight = FontWeight.SemiBold, fontSize = font, lineHeight = 1.13.em,
        letterSpacing = 0.sp, textAlign = alignment,
        maxLines = 1, softWrap = false, overflow = if (autoSize != null) TextOverflow.Clip else TextOverflow.Ellipsis,
    )
}
