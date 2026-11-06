package io.issac.pleosandroid.core.designsystem.component

import android.graphics.Bitmap
import android.graphics.BitmapShader
import android.graphics.BlendMode
import android.graphics.Canvas
import android.graphics.ComposeShader
import android.graphics.LinearGradient
import android.graphics.Matrix
import android.graphics.Paint
import android.graphics.Path
import android.graphics.PorterDuff
import android.graphics.PorterDuffColorFilter
import android.graphics.PorterDuffXfermode
import android.graphics.RectF
import android.graphics.Shader
import android.os.Build
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.drawWithCache
import androidx.compose.ui.graphics.asAndroidPath
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.graphics.nativeCanvas
import kotlin.math.cos
import kotlin.math.sin

internal const val MAX_LIGHT_ANGLE = 24f
internal const val RESTING_LIGHT_ANGLE = -4f
internal const val FOIL_REFLECTION_STRENGTH = 1.25f

@Composable
internal fun Modifier.blackLabelFinish(
    material: BlackLabel,
    stock: CardStock,
    relief: Bitmap?,
    spot: Bitmap?,
    lightAngle: () -> Float,
): Modifier {
    if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
        val finish = remember(stock, material, spot) { FoilMaterialShader(stock, material, spot) }
        return graphicsLayer {
            if (size.width > 0f && size.height > 0f) {
                renderEffect = finish.effect(size, lightAngle())
            }
        }
    }
    return drawWithCache {
        val finish = CardSurfaceFinish(stock, material, relief, spot)
        onDrawWithContent {
            drawContent()
            val canvas = drawContext.canvas.nativeCanvas
            val saveCount = canvas.save()
            canvas.scale(size.width / CARD_WIDTH, size.height / CARD_HEIGHT)
            finish.draw(canvas, lightAngle())
            canvas.restoreToCount(saveCount)
        }
    }
}

/** All layers share an incident angle, so reflected light crosses the artwork and the paper in one pass. */
private class CardSurfaceFinish(stock: CardStock, material: BlackLabel, relief: Bitmap?, spot: Bitmap?) {
    private val paper = Path().apply {
        val edge = 1.3f
        val radius = stock.corner - edge
        addRoundRect(edge, edge, CARD_WIDTH - edge, CARD_HEIGHT - edge, radius, radius, Path.Direction.CW)
    }
    private val picture = androidx.compose.ui.graphics.Path().apply { addRoundRect(stock.artwork) }.asAndroidPath()
    private val foilInk = if (material == BlackLabel.Sapphire) Hologram else Gold
    private val onyx = material == BlackLabel.Onyx
    private val fullCoat = if (material == BlackLabel.Silver) {
        null
    } else {
        LightBand(foilInk, 195f, if (onyx) 150 else 181).apply {
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
                paint.blendMode = BlendMode.SOFT_LIGHT
            } else {
                paint.xfermode = PorterDuffXfermode(PorterDuff.Mode.OVERLAY)
            }
        }
    }
    private val reflection = if (material == BlackLabel.Silver) null else LightBand(foilInk, 139f, if (onyx) 38 else 51)
    private val etching = relief?.let {
        LightBand(foilInk, 133f, 199, coverage(it, RectF(0f, 0f, CARD_WIDTH, CARD_HEIGHT)))
    }
    private val artMask = spot?.let { coverage(it, with(stock.artwork) { RectF(left, top, right, bottom) }) }
    private val spotLight = artMask?.let { LightBand(if (onyx) Gold else Silver, 113f, 255, it) }
    private val goldInk = if (onyx && artMask != null) {
        Paint(Paint.ANTI_ALIAS_FLAG or Paint.FILTER_BITMAP_FLAG).apply {
            shader = artMask
            colorFilter = PorterDuffColorFilter(0xFFE4B75D.toInt(), PorterDuff.Mode.SRC_IN)
            alpha = 117
        }
    } else {
        null
    }

    fun draw(canvas: Canvas, angle: Float) {
        val center = angle * 10.3f - 23f
        fullCoat?.draw(canvas, paper, center)
        reflection?.draw(canvas, paper, center)
        etching?.draw(canvas, paper, center)
        goldInk?.let { canvas.drawPath(picture, it) }
        spotLight?.draw(canvas, picture, if (onyx) center - 13f else center)
    }
}

private fun coverage(bitmap: Bitmap, rect: RectF) =
    BitmapShader(bitmap, Shader.TileMode.CLAMP, Shader.TileMode.CLAMP).apply {
        val source = RectF(0f, 0f, bitmap.width.toFloat(), bitmap.height.toFloat())
        setLocalMatrix(Matrix().apply { setRectToRect(source, rect, Matrix.ScaleToFit.FILL) })
    }

private val lightX = cos(-.34f)
private val lightY = sin(-.34f)
private val screen = PorterDuffXfermode(PorterDuff.Mode.SCREEN)

private class LightBand(ink: FoilInk, span: Float, alpha: Int, mask: Shader? = null) {
    private val matrix = Matrix()
    private val gradient = LinearGradient(
        -lightX * span,
        -lightY * span,
        lightX * span,
        lightY * span,
        ink.colors,
        ink.stops,
        Shader.TileMode.CLAMP,
    )
    val paint = Paint(Paint.ANTI_ALIAS_FLAG or Paint.FILTER_BITMAP_FLAG).apply {
        shader = if (mask == null) gradient else ComposeShader(mask, gradient, PorterDuff.Mode.SRC_IN)
        xfermode = screen
        this.alpha = alpha
    }

    fun draw(canvas: Canvas, path: Path, center: Float) {
        matrix.setTranslate(CARD_WIDTH / 2 + lightX * center, CARD_HEIGHT / 2 + lightY * center)
        gradient.setLocalMatrix(matrix)
        canvas.drawPath(path, paint)
    }
}

private class FoilInk(val stops: FloatArray, val colors: IntArray)

private val Silver = FoilInk(
    floatArrayOf(0f, 0.34f, 0.51f, 0.69f, 1f),
    intArrayOf(
        0x00D3E7EA,
        0x4AB4D9E3,
        0xD0F1FFF4.toInt(),
        0x61A7D5E4,
        0x00B6DBED,
    ),
)

private val Hologram = FoilInk(
    floatArrayOf(0f, 0.15f, 0.29f, 0.39f, 0.49f, 0.56f, 0.71f, 0.86f, 1f),
    intArrayOf(
        0x00A9BBF0, 0x33519CCE, 0x9E6BD3CD.toInt(), 0x98B7EEE8.toInt(),
        0xC4F7ECC9.toInt(), 0xAFE6AAEC.toInt(), 0x9A9C96E9.toInt(), 0x5068B8C6,
        0x006BA8D7,
    ),
)

private val Gold = FoilInk(
    floatArrayOf(0f, 0.16f, 0.33f, 0.46f, 0.53f, 0.6f, 0.76f, 1f),
    intArrayOf(
        0x00E8C994,
        0x2CD8AC69,
        0x7FECC06B,
        0xB9FFE5AD.toInt(),
        0xF5FFF6DA.toInt(),
        0xB9EACF87.toInt(),
        0x5ECA9781,
        0x00D7B78B,
    ),
)
