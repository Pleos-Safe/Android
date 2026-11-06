package io.issac.pleosandroid.feature.badges.reveal.motion

import android.graphics.Bitmap
import android.graphics.Color as AndroidColor
import android.graphics.Paint
import androidx.compose.runtime.State
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.drawWithCache
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.drawscope.DrawScope
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.drawscope.clipPath
import androidx.compose.ui.graphics.drawscope.drawIntoCanvas
import androidx.compose.ui.graphics.drawscope.withTransform
import androidx.compose.ui.graphics.lerp as mixColor
import androidx.compose.ui.graphics.nativeCanvas
import androidx.compose.ui.graphics.toArgb
import androidx.compose.ui.util.lerp
import kotlin.math.exp
import kotlin.math.roundToInt
import kotlin.math.sin

/** Both the exposure and its reflection follow the same crease in the foil. */
internal fun Modifier.foilExposure(
    motion: CardRevealMotion,
    progress: State<Float>,
    light: Color,
    accent: Color,
): Modifier = drawWithCache {
    val film = FoilSurface(size, motion, reflectionTexture(light, accent))
    val shade = Brush.linearGradient(listOf(Color.Transparent, Color.Transparent, Color(0x63302639)))
    val coating = Brush.linearGradient(
        0f to accent.copy(alpha = .40f),
        .46f to Color.Transparent,
        1f to light.copy(alpha = .34f),
        start = Offset(0f, size.height * .74f),
        end = Offset(size.width, size.height * .12f),
    )
    val flare = Brush.radialGradient(
        0f to mixColor(light, Color.White, .65f),
        .22f to light.copy(alpha = .78f),
        .57f to accent.copy(alpha = .30f),
        1f to Color.Transparent,
        center = Offset.Zero,
        radius = 1f,
    )
    val rim = Brush.linearGradient(
        0f to light,
        .25f to accent,
        .45f to Color.Transparent,
        .62f to Color.Transparent,
        1f to light,
        start = Offset(0f, size.height),
        end = Offset(size.width, 0f),
    )
    val rimStroke = Stroke(size.width * .009f)
    val corner = CornerRadius(size.width * .05f)
    onDrawWithContent {
        val t = progress.value
        if (t <= 0f) return@onDrawWithContent
        if (t >= 1f) {
            drawContent()
            return@onDrawWithContent
        }
        val start = motion.revealStart
        val open = phase(t, start, motion.revealEnd)
        val gather = lerp((t / start).coerceIn(0f, 1f), phase(t, .015f, start - .035f), .38f)
        val lean = envelope(t, 0f, start, motion.revealEnd)
        val crest = envelope(t, start - .07f, start + .07f, motion.revealEnd + .035f)
        film.bend(gather - 1.55f * open)

        val gilded = motion == CardRevealMotion.Gold
        val seam = .49f
        val edge = lerp(-.33f, 1.38f, open)
        val spread = if (t < start) lerp(.97f, .018f, gather) else lerp(.018f, .99f, open)
        if (open >= 1f) {
            drawContent()
        } else if (open > 0f) {
            val mask = if (gilded) film.mask(seam - spread, seam + spread) else film.mask(-1f, edge)
            clipPath(mask) { this@onDrawWithContent.drawContent() }
        }

        drawRect(shade, alpha = .82f * lean)
        drawRect(coating, alpha = .52f * lean)
        val brightness = maxOf(.86f * lean, crest)
        val front = if (t < start) lerp(1.30f, -.33f, gather) else edge
        when (motion) {
            CardRevealMotion.Tide -> {
                film.reflect(this, front, .16f, brightness)
                film.reflect(this, front + .14f, .075f, .38f * brightness, curvature = .72f)
            }

            CardRevealMotion.Opal -> {
                // Two differently inclined faces of the lacquer catch the same moving light.
                film.reflect(this, lerp(-.60f, 1.48f, gather) - .57f * open, .20f, .60f * lean, curvature = -.75f)
                film.reflect(this, front, .25f, brightness)
                film.reflect(this, front - .105f, .085f, .50f * crest, curvature = .86f)
            }

            CardRevealMotion.Gold -> {
                film.reflect(this, seam - spread, .30f, brightness)
                film.reflect(this, seam + spread, .22f, .86f * brightness, reverse = true)
                film.reflect(this, seam + spread + .10f, .09f, .52f * crest)
            }

            else -> Unit
        }
        if (motion != CardRevealMotion.Tide) {
            // A grazing highlight blooms across the bend for one beat as the print springs back.
            val contact = envelope(t, start - .065f, start + .024f, start + .19f)
            if (contact > 0f) {
                val x = if (gilded) seam + open * .23f else .16f + open * .74f
                withTransform({
                    translate(size.width * x, size.height * (.41f - .09f * open))
                    rotate(if (gilded) -24f else 33f, Offset.Zero)
                    scale(size.width * (.56f + .57f * contact), size.height * (.10f + .14f * contact), Offset.Zero)
                }) {
                    drawCircle(flare, radius = 1f, center = Offset.Zero, alpha = contact * if (gilded) .95f else .68f)
                }
            }
        }
        val finish = envelope(t, motion.revealEnd - .06f, motion.revealEnd + .025f, 1f)
        if (finish > 0f) {
            val travel = phase(t, motion.revealEnd - .06f, 1f)
            film.reflect(this, lerp(-.36f, 1.36f, travel), .18f, finish * if (gilded) .90f else .49f)
        }
        drawRoundRect(rim, cornerRadius = corner, alpha = maxOf(.54f * lean, crest), style = rimStroke)
    }
}

// Each strip is a soft continuous reflection. Only its mesh moves; the spectral texture is cached.
private class FoilSurface(private val size: Size, private val motion: CardRevealMotion, private val texture: Bitmap) {
    private val along = FloatArray(FOIL_COLUMNS + 1) { -.10f + 1.20f * it / FOIL_COLUMNS }
    private val curl = FloatArray(along.size)
    private val breadth = FloatArray(along.size) { .83f + .18f * sin(along[it] * 4.2f + .15f) }
    private val mesh = FloatArray(along.size * 4)
    private val path = Path()
    private val paint = Paint(Paint.ANTI_ALIAS_FLAG or Paint.FILTER_BITMAP_FLAG)

    fun bend(tilt: Float) {
        for (i in along.indices) {
            val u = along[i]
            curl[i] = when (motion) {
                CardRevealMotion.Tide -> .16f * (u - .5f) + .089f * sin(4.5f * u + tilt)
                CardRevealMotion.Opal -> -.43f * (u - .5f) + .15f * sin(4.1f * u + 1.13f * tilt)
                else -> .26f * (u - .5f) + .145f * sin(4.75f * u - .8f + .93f * tilt)
            }
        }
    }

    private fun point(i: Int, cross: Float): Offset = if (motion == CardRevealMotion.Tide) {
        Offset(along[i] * size.width, (1f - cross) * size.height)
    } else {
        Offset(cross * size.width, along[i] * size.height)
    }

    fun mask(from: Float, to: Float): Path {
        path.rewind()
        for (i in along.indices) {
            val p = point(i, from + curl[i])
            if (i == 0) path.moveTo(p.x, p.y) else path.lineTo(p.x, p.y)
        }
        for (i in along.lastIndex downTo 0) {
            val p = point(i, to + curl[i])
            path.lineTo(p.x, p.y)
        }
        path.close()
        return path
    }

    fun reflect(
        scope: DrawScope,
        center: Float,
        halfWidth: Float,
        opacity: Float,
        curvature: Float = 1f,
        reverse: Boolean = false,
    ) {
        if (opacity <= .001f) return
        for (i in along.indices) {
            val middle = center + curl[i] * curvature
            val width = halfWidth * breadth[i] * if (reverse) -1f else 1f
            val a = point(i, middle - width)
            val b = point(i, middle + width)
            val top = i * 2
            val bottom = (along.size + i) * 2
            mesh[top] = a.x
            mesh[top + 1] = a.y
            mesh[bottom] = b.x
            mesh[bottom + 1] = b.y
        }
        paint.alpha = (255 * opacity).roundToInt()
        scope.drawIntoCanvas { it.nativeCanvas.drawBitmapMesh(texture, FOIL_COLUMNS, 1, mesh, 0, null, 0, paint) }
    }
}

private const val FOIL_COLUMNS = 28

/** A feathered reflection with a narrow white ridge and a coloured shoulder, like thin film under a strip light. */
private fun reflectionTexture(light: Color, accent: Color): Bitmap {
    val width = 96
    val height = 112
    val palette = IntArray(128) { mixColor(light, accent, it / 127f).toArgb() }
    val pixels = IntArray(width * height)
    for (x in 0 until width) {
        val u = x.toFloat() / (width - 1)
        val ridge = .09f * sin(6.2f * u + .25f) + .035f * sin(9.7f * u)
        for (y in 0 until height) {
            val v = 2f * y / (height - 1) - 1f
            val d = v - ridge
            val broad = exp(-4.4f * d * d)
            val shoulder = exp(-21f * (d + .21f) * (d + .21f))
            val fine = exp(-260f * d * d)
            val taper = 1f - v * v
            val alpha = (.28f * broad + .37f * shoulder + .47f * fine) * taper
            val hue = .50f + .48f * sin(4.5f * v + 3.6f * u - .7f)
            val tint = palette[(hue * palette.lastIndex).roundToInt()]
            val pearl = (.84f * fine + .12f * broad) * (.76f + .22f * sin(3.4f * u))
            pixels[y * width + x] = AndroidColor.argb(
                (255 * alpha.coerceIn(0f, 1f)).roundToInt(),
                lerp(AndroidColor.red(tint), 255, pearl),
                lerp(AndroidColor.green(tint), 255, pearl),
                lerp(AndroidColor.blue(tint), 255, pearl),
            )
        }
    }
    return Bitmap.createBitmap(pixels, width, height, Bitmap.Config.ARGB_8888)
}

internal fun Modifier.foilBacklight(
    motion: CardRevealMotion,
    progress: State<Float>,
    light: Color,
    accent: Color,
): Modifier = drawWithCache {
    fun lamp(color: Color) = Brush.radialGradient(listOf(color, Color.Transparent), center = Offset.Zero, radius = 1f)
    val leading = lamp(light)
    val trailing = lamp(accent)
    onDrawBehind {
        val t = progress.value
        if (t <= 0f || t >= 1f) return@onDrawBehind
        val start = motion.revealStart
        val charge = envelope(t, 0f, start, .92f)
        val release = envelope(t, start - .025f, start + .065f, 1f)
        val travel = phase(t, start, motion.revealEnd)
        val w = size.width
        val h = size.height
        val strength = if (motion == CardRevealMotion.Gold) .88f else .58f
        withTransform({
            translate(w * lerp(.12f, .77f, travel), h * .29f)
            scale(w * (.45f + .35f * release), h * .61f, Offset.Zero)
        }) { drawCircle(leading, 1f, Offset.Zero, alpha = strength * maxOf(.60f * charge, release)) }
        withTransform({
            translate(w * lerp(.82f, .21f, travel), h * .78f)
            scale(w * .63f, h * .48f, Offset.Zero)
        }) { drawCircle(trailing, 1f, Offset.Zero, alpha = .47f * maxOf(charge, release)) }
    }
}
