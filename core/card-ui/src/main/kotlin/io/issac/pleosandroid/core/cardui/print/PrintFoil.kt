package io.issac.pleosandroid.core.cardui.print

import android.graphics.Bitmap
import android.graphics.BitmapShader
import android.graphics.Canvas
import android.graphics.ComposeShader
import android.graphics.LinearGradient
import android.graphics.Matrix
import android.graphics.Paint
import android.graphics.Path
import android.graphics.PorterDuff
import android.graphics.RectF
import android.graphics.RenderEffect
import android.graphics.RuntimeShader
import android.graphics.Shader
import android.os.Build
import androidx.annotation.RequiresApi
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.drawWithCache
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.asComposeRenderEffect
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.graphics.nativeCanvas
import androidx.core.graphics.withSave
import io.issac.pleosandroid.core.domain.cards.CardGrade
import kotlin.math.sin

/** Apply to the printed stock only; labels remain crisp and the plate alpha registers the metal in both renderers. */
@Composable
internal fun Modifier.printCoating(
    stage: Int,
    grade: CardGrade,
    variant: Boolean,
    plate: Bitmap,
    light: () -> Float,
    tilt: () -> Offset,
    fullArt: () -> Float,
): Modifier {
    if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
        val finish = remember(stage, grade, variant, plate) { RegionalFinish(stage, grade, variant, plate) }
        return graphicsLayer {
            if (size.width > 0f && size.height > 0f) renderEffect = finish.effect(size, light(), tilt(), fullArt())
        }
    }
    return drawWithCache {
        val finish = CanvasFinish(stage, grade, variant, plate)
        onDrawWithContent {
            drawContent()
            drawContext.canvas.nativeCanvas.withSave {
                scale(size.width / 400f, size.height / 600f)
                finish.draw(this, light(), tilt())
            }
        }
    }
}

@RequiresApi(Build.VERSION_CODES.TIRAMISU)
private class RegionalFinish(private val stage: Int, grade: CardGrade, variant: Boolean, plate: Bitmap) {
    private val shader = RuntimeShader(REGIONAL_FOIL_AGSL).apply {
        setFloatUniform("tier", stage.toFloat())
        setFloatUniform(
            "grade",
            when (grade) {
                CardGrade.Epic -> 2f
                CardGrade.Rare -> 1f
                else -> 0f
            },
        )
        setFloatUniform("alternate", if (variant) 1f else 0f)
        setFloatUniform("plateSize", plate.width.toFloat(), plate.height.toFloat())
        setInputShader("plate", BitmapShader(plate, Shader.TileMode.CLAMP, Shader.TileMode.CLAMP))
    }
    private var lastExpansion = Float.NaN

    fun effect(size: Size, light: Float, tilt: Offset, fullArt: Float): androidx.compose.ui.graphics.RenderEffect {
        shader.setFloatUniform("viewport", size.width, size.height)
        shader.setFloatUniform("rotation", tilt.x, tilt.y)
        shader.setFloatUniform("lamp", light)
        if (lastExpansion != fullArt) {
            val aperture = PrintAperture(stage, fullArt)
            val rect = aperture.bounds
            shader.setFloatUniform("artWindow", rect.left, rect.top, rect.right, rect.bottom)
            shader.setFloatUniform("artCorners", aperture.radius, aperture.cut)
            lastExpansion = fullArt
        }
        return RenderEffect.createRuntimeShaderEffect(shader, "face").asComposeRenderEffect()
    }
}

/** Older Android versions use the same mirror direction, registered foil, and crossing engraved bands. */
private class CanvasFinish(private val stage: Int, grade: CardGrade, variant: Boolean, plate: Bitmap) {
    private val mask = plate.foilMask()
    private val silver = 0xffe9f8ff.toInt()
    private val gold = 0xffffcf80.toInt()
    private val pearl = when (grade) {
        CardGrade.Epic -> gold
        CardGrade.H -> 0xffb4fff2.toInt()
        CardGrade.HH -> 0xffa8d9ff.toInt()
        else -> silver
    }
    private val teal = if (variant) 0xff93e3ce.toInt() else 0xff99d7ed.toInt()
    private val iris = 0xffcfb3ff.toInt()
    private val satin = FoilBand(150f, intArrayOf(0, 0x220b2134, 0x39eff7ff, 0), false)
    private val metal = FoilBand(92f, intArrayOf(0x00203142, 0x733e4858, pearl, silver, 0x00efd4ac), false, mask)
    private val nacre = FoilBand(98f, intArrayOf(teal and 0xffffff, teal, pearl, iris, iris and 0xffffff), false)
    private val gilding = FoilBand(83f, intArrayOf(0x00945638, gold, silver, 0x00ffebbf), true)
    private val reverse = FoilBand(90f, intArrayOf(0x00affae6, iris, teal, pearl, 0x00d2a0cc), true)
    private val grooves = Path().apply {
        for (i in -20..66) {
            val y = i * 12f
            moveTo(-8f, y)
            cubicTo(92f, y - 76f, 244f, y + 74f, 410f, y - 59f)
        }
    }
    private val contours = Path().apply {
        for (i in 1..46) {
            val rx = i * 9.5f
            addOval(157f - rx, 346f - rx * .79f, 157f + rx, 346f + rx * .79f, Path.Direction.CW)
        }
    }
    private val bounds = RectF(0f, 0f, 400f, 600f)

    fun draw(canvas: Canvas, light: Float, tilt: Offset) {
        val center = 200f + 400f * (
            2.35f * sin(Math.toRadians(tilt.x.toDouble())).toFloat() +
                1.08f * sin(Math.toRadians(light.toDouble())).toFloat() -
                .68f * sin(Math.toRadians(tilt.y.toDouble())).toFloat()
            )
        canvas.drawRect(bounds, satin.at(center, 103 + stage * 12))
        canvas.withSave {
            clipOutRect(29f, 454f, 363f, 521f)
            clipOutRect(29f, 539f, 108f, 577f)
            if (stage >= 1) drawRect(bounds, metal.at(center, 170))
            if (stage >= 2) drawRect(bounds, nacre.at(center + 32f, 65 + stage * 8))
            if (stage >= 3) drawPath(grooves, nacre.at(center, 159, 1.9f))
            if (stage >= 4) {
                drawRect(bounds, gilding.at(400f - center, 55))
                drawPath(grooves, gilding.at(400f - center, 187, 2.5f))
            }
            if (stage >= 5) drawPath(contours, reverse.at(454f - center, 211, 2.1f))
        }
    }
}

internal fun Bitmap.foilMask(): Shader = BitmapShader(this, Shader.TileMode.CLAMP, Shader.TileMode.CLAMP).apply {
    setLocalMatrix(Matrix().apply { setScale(400f / width, 600f / height) })
}

internal class FoilBand(span: Float, colors: IntArray, crossed: Boolean = false, mask: Shader? = null) {
    private val vertical = if (crossed) -.46f else .48f
    private val gradient =
        LinearGradient(-span, -span * vertical, span, span * vertical, colors, null, Shader.TileMode.CLAMP)
    private val matrix = Matrix()
    private val paint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        shader = if (mask == null) gradient else ComposeShader(mask, gradient, PorterDuff.Mode.SRC_IN)
    }

    fun at(x: Float, alpha: Int, line: Float = 0f): Paint {
        matrix.setTranslate(x, 300f)
        gradient.setLocalMatrix(matrix)
        paint.alpha = alpha
        paint.style = if (line > 0f) Paint.Style.STROKE else Paint.Style.FILL
        paint.strokeWidth = line
        return paint
    }
}
