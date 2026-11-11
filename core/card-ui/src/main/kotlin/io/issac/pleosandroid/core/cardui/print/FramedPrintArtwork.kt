package io.issac.pleosandroid.core.cardui.print

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.drawWithCache
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Rect
import androidx.compose.ui.geometry.RoundRect
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.drawscope.clipPath
import androidx.compose.ui.graphics.drawscope.scale
import androidx.compose.ui.graphics.drawscope.translate
import androidx.compose.ui.graphics.painter.Painter
import androidx.compose.ui.util.lerp

// Coordinates of the stamped 400 × 600 plates. The outer rail starts at 4 and is at least 13 units wide.
private const val OUTER_RAIL = 4f
private const val UNDER_RAIL = 10f

private fun bevel(stage: Int) = if (stage >= 4) 31f else 23f

/** Both the ink and its foil shader use this opening. The illustration keeps its original center crop underneath. */
internal class PrintAperture(stage: Int, progress: Float) {
    val bounds = Rect(
        lerp(29f, UNDER_RAIL, progress),
        lerp(94f, UNDER_RAIL, progress),
        lerp(371f, 400f - UNDER_RAIL, progress),
        lerp(436f, 600f - UNDER_RAIL, progress),
    )
    val radius = 9f * (1f - progress)
    val cut = bevel(stage) * progress
}

private fun Path.chamfer(rect: Rect, cutX: Float, cutY: Float = cutX) {
    reset()
    moveTo(rect.left + cutX, rect.top)
    lineTo(rect.right - cutX, rect.top)
    lineTo(rect.right, rect.top + cutY)
    lineTo(rect.right, rect.bottom - cutY)
    lineTo(rect.right - cutX, rect.bottom)
    lineTo(rect.left + cutX, rect.bottom)
    lineTo(rect.left, rect.bottom - cutY)
    lineTo(rect.left, rect.top + cutY)
    close()
}

fun Path.printStockEdge(size: Size, stage: Int, expansion: Float) {
    val sx = size.width / 400f
    val sy = size.height / 600f
    val inset = OUTER_RAIL * expansion
    val cut = bevel(stage) * expansion
    chamfer(Rect(inset * sx, inset * sy, size.width - inset * sx, size.height - inset * sy), cut * sx, cut * sy)
}

internal fun Modifier.clipPrintStock(stage: Int, expansion: () -> Float): Modifier = drawWithCache {
    val edge = Path()
    onDrawWithContent {
        val p = expansion()
        if (p == 0f) {
            drawContent()
        } else {
            edge.printStockEdge(size, stage, p)
            clipPath(edge) { this@onDrawWithContent.drawContent() }
        }
    }
}

@Composable
internal fun FramedPrintArtwork(painter: Painter, stage: Int, expansion: () -> Float) {
    Box(
        Modifier.fillMaxSize().drawWithCache {
            val corners = Path()
            val opening = Path()
            val image = painter.intrinsicSize
            val sx = size.width / 400f
            val sy = size.height / 600f
            onDrawBehind {
                val p = expansion()
                val aperture = PrintAperture(stage, p)
                val viewWidth = lerp(342f, 400f, p)
                val viewHeight = lerp(342f, 600f, p)
                val zoom = maxOf(viewWidth / image.width, viewHeight / image.height)
                val scaled = Size(image.width * zoom, image.height * zoom)
                val x = (400f - scaled.width) / 2f
                val y = lerp(265f, 300f, p) - scaled.height / 2f
                opening.chamfer(aperture.bounds, aperture.cut)
                corners.reset()
                corners.addRoundRect(RoundRect(aperture.bounds, CornerRadius(aperture.radius)))
                scale(sx, sy, Offset.Zero) {
                    clipPath(opening) {
                        clipPath(corners) {
                            translate(x, y) { with(painter) { draw(scaled) } }
                        }
                    }
                }
            }
        },
    )
}
