package io.issac.pleosandroid.core.cardui.print

import android.graphics.Bitmap
import android.graphics.Matrix
import android.graphics.Path
import androidx.compose.runtime.State
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.drawWithCache
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.nativeCanvas
import androidx.compose.ui.util.lerp
import androidx.core.graphics.withSave
import io.issac.pleosandroid.core.cardui.motion.smoothStep
import io.issac.pleosandroid.core.domain.cards.CardGrade
import kotlin.math.PI
import kotlin.math.abs
import kotlin.math.cos
import kotlin.math.roundToInt
import kotlin.math.sin

private enum class FlipFinish(val tint: Int, val engraved: Boolean = false) {
    Paper(0xfff7eee1.toInt()),
    Champagne(0xffecc89a.toInt()),
    Gold(0xffffbd63.toInt(), engraved = true),
    Mint(0xff74ecdb.toInt()),
    Blue(0xff81bdff.toInt(), engraved = true),
}

private val CardGrade.flipFinish: FlipFinish get() = when (this) {
    CardGrade.Common -> FlipFinish.Paper
    CardGrade.Rare -> FlipFinish.Champagne
    CardGrade.Epic -> FlipFinish.Gold
    CardGrade.H -> FlipFinish.Mint
    CardGrade.HH -> FlipFinish.Blue
}

// One engraved die is stamped into opposite corners of the illustration. The sweep stays in card coordinates.
private val foilRosette by lazy {
    Path().apply {
        for (ring in 1..17) {
            val radius = ring / 17f
            for (step in 0 until 96) {
                val turn = 2 * PI * step / 96
                val flute = .90 + .092 * cos(7 * turn - 2.4 * radius) + .018 * cos(14 * turn + radius)
                val x = (radius * flute * cos(turn)).toFloat()
                val y = (radius * flute * sin(turn)).toFloat()
                if (step == 0) moveTo(x, y) else lineTo(x, y)
            }
            close()
        }
    }
}

/** A paper edge stays visible at 90 degrees. Only the exposed front casts its foil colour onto the stage. */
fun Modifier.packFlipEdge(grade: CardGrade, angle: State<Float>): Modifier = drawWithCache {
    val finish = grade.flipFinish
    val colour = Color(finish.tint)
    val center = Offset(size.width * .50f, size.height * .44f)
    val glow = Brush.radialGradient(
        listOf(colour, colour.copy(alpha = .45f), Color.Transparent),
        center = center,
        radius = size.minDimension.coerceAtLeast(1f) * .69f,
    )
    val edge = Brush.verticalGradient(
        listOf(Color.Transparent, Color(0xffe5d8c9), Color.White, Color(0xffbcb1a8), Color.Transparent),
    )
    onDrawBehind {
        val degrees = angle.value
        if (degrees <= 0f || degrees >= 180f) return@onDrawBehind
        val front = (degrees - 90f) / 90f
        if (front > 0f && finish != FlipFinish.Paper) {
            val spill = smoothStep(0f, .32f, front) * (1f - smoothStep(.42f, .96f, front))
            drawRect(glow, alpha = spill * if (finish.engraved) .46f else .19f)
        }
        val grazing = 1f - smoothStep(0f, 12f, abs(degrees - 90f))
        if (grazing > 0f) {
            drawLine(
                edge,
                Offset(center.x, -size.height * .037f),
                Offset(center.x, size.height * .988f),
                strokeWidth = (size.width * .007f).coerceAtLeast(1f),
                cap = StrokeCap.Round,
                alpha = grazing,
            )
        }
    }
}

/** Drawn in stock coordinates beneath the type, using the same printed plate mask as the inspection light. */
internal fun Modifier.packFlipFoil(
    grade: CardGrade,
    plate: Bitmap,
    stage: Int,
    expansion: () -> Float,
    angle: State<Float>,
): Modifier = drawWithCache {
    val finish = grade.flipFinish
    val tint = finish.tint
    val clear = tint and 0xffffff
    val pearl = 0xfffff6e8.toInt()
    val highlight = android.graphics.Color.WHITE
    val satin = FoilBand(137f, intArrayOf(clear, tint, pearl, clear))
    val metal = if (finish == FlipFinish.Paper) {
        null
    } else {
        FoilBand(104f, intArrayOf(clear, 0xff52606a.toInt(), tint, highlight, tint, clear), mask = plate.foilMask())
    }
    val satinAlpha = if (metal == null) 37 else 65
    val aperture = PrintAperture(stage, expansion())
    val bounds = aperture.bounds
    val window = Path().apply {
        addRoundRect(
            bounds.left,
            bounds.top,
            bounds.right,
            bounds.bottom,
            aperture.radius,
            aperture.radius,
            Path.Direction.CW,
        )
    }
    val engraving = if (finish.engraved) {
        Path().apply {
            val stamp = Matrix()
            stamp.setScale(bounds.width * .52f, bounds.height * .41f)
            stamp.postTranslate(bounds.right - 8f, bounds.top + 11f)
            addPath(foilRosette, stamp)
            stamp.setScale(-bounds.width * .44f, -bounds.height * .37f)
            stamp.postTranslate(bounds.left + 6f, bounds.bottom - 9f)
            addPath(foilRosette, stamp)
        }
    } else {
        null
    }
    val counterfoil = if (finish.engraved) {
        FoilBand(104f, intArrayOf(clear, tint, pearl, highlight, clear), crossed = true)
    } else {
        null
    }
    onDrawWithContent {
        drawContent()
        val front = (angle.value - 90f) / 90f
        if (front <= 0f || front >= 1f) return@onDrawWithContent
        val exposure = smoothStep(0f, .25f, front) * (1f - smoothStep(.76f, 1f, front))
        val sweep = lerp(-185f, 585f, front)
        drawContext.canvas.nativeCanvas.withSave {
            scale(size.width / 400f, size.height / 600f)
            drawRect(0f, 0f, 400f, 600f, satin.at(sweep, (satinAlpha * exposure).roundToInt()))
            if (metal != null) drawRect(0f, 0f, 400f, 600f, metal.at(sweep, (248 * exposure).roundToInt()))
            if (engraving != null && counterfoil != null) {
                clipPath(window)
                drawPath(engraving, satin.at(sweep + 23f, (198 * exposure).roundToInt(), 1.85f))
                drawPath(engraving, counterfoil.at(429f - sweep, (165 * exposure).roundToInt(), 1.05f))
            }
        }
    }
}
