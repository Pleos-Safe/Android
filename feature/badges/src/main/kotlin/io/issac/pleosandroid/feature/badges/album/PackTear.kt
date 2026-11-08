package io.issac.pleosandroid.feature.badges.album

import android.graphics.Bitmap
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.LinearGradient
import android.graphics.Paint
import android.graphics.Path
import android.graphics.Rect
import android.graphics.Shader
import androidx.compose.foundation.Canvas as Drawing
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.drawscope.drawIntoCanvas
import androidx.compose.ui.graphics.nativeCanvas
import androidx.core.graphics.withSave
import io.issac.pleosandroid.core.domain.cards.ReceivedPack
import kotlin.math.abs
import kotlin.math.sin

internal fun packStep(start: Float, end: Float, t: Float): Float {
    val x = ((t - start) / (end - start)).coerceIn(0f, 1f)
    return x * x * (3f - 2f * x)
}

/** Drawn in front of the card so the front of the pouch actually occludes the emerging print. */
@Composable
internal fun PackTear(pack: ReceivedPack, modifier: Modifier = Modifier, progress: () -> Float) {
    val face = rememberPackFace(pack)
    val foil = remember(face) { FoilPouch(face) }
    Drawing(modifier) {
        drawIntoCanvas { canvas -> foil.draw(canvas.nativeCanvas, size.width, size.height, progress()) }
    }
}

private const val COLUMNS = 32
private const val BODY_ROWS = 12
private const val STRIP_ROWS = 4
private val pi = Math.PI.toFloat()

/** The two meshes share a cut in texture space. Each column stays attached until the tear reaches it. */
private class FoilPouch(private val source: Bitmap) {
    private val w = source.width.toFloat()
    private val h = source.height.toFloat()
    private val originalBounds = Rect(0, 0, source.width, source.height)
    private val stripBottom = (h * .147f).toInt()
    private val bodyTop = (h * .099f).toInt()
    private val cut = FloatArray(COLUMNS + 1) { i ->
        val u = i.toFloat() / COLUMNS
        h * (.130f - .017f * u + .003f * sin(8.7f * u) + .0022f * sin(2.6f * i) + .0015f * sin(5.3f * i))
    }
    private val paint = Paint(Paint.ANTI_ALIAS_FLAG or Paint.FILTER_BITMAP_FLAG)
    private val edgePaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        strokeJoin = Paint.Join.ROUND
        strokeCap =
            Paint.Cap.ROUND
    }
    private val path = Path()
    private val liner = LinearGradient(
        0f,
        0f,
        w,
        -h * .035f,
        intArrayOf(0xff606975.toInt(), 0xffd4d9dc.toInt(), 0xff7c8491.toInt(), 0xfff2eee3.toInt(), 0xff7b7780.toInt()),
        floatArrayOf(0f, .31f, .62f, .83f, 1f),
        Shader.TileMode.CLAMP,
    )
    private val strip = crop(source, 0, stripBottom, true)
    private val body = crop(source, bodyTop, source.height, false)
    private val bodyVerts = FloatArray((COLUMNS + 1) * (BODY_ROWS + 1) * 2)
    private val stripVerts = FloatArray((COLUMNS + 1) * (STRIP_ROWS + 1) * 2)
    private val bodyColor = IntArray(bodyVerts.size / 2)
    private val stripColor = IntArray(stripVerts.size / 2)
    private val bodyEdge = FloatArray((COLUMNS + 1) * 2)
    private val stripEdge = FloatArray(bodyEdge.size)
    private val opening = FloatArray(COLUMNS + 1)

    private fun crop(source: Bitmap, top: Int, bottom: Int, upper: Boolean): Bitmap {
        val bmp = Bitmap.createBitmap(source.width, bottom - top, Bitmap.Config.ARGB_8888)
        val c = Canvas(bmp)
        c.translate(0f, -top.toFloat())
        path.rewind()
        path.moveTo(0f, if (upper) 0f else h)
        for (i in cut.indices) path.lineTo(w * i / COLUMNS, cut[i])
        path.lineTo(w, if (upper) 0f else h)
        path.close()
        c.clipPath(path)
        c.drawBitmap(source, null, Rect(0, 0, source.width, source.height), paint)
        return bmp
    }

    fun draw(canvas: Canvas, width: Float, height: Float, t: Float) {
        if (t >= 1f) return
        if (t <= 0f) {
            canvas.withSave {
                scale(width / w, height / h)
                paint.alpha = 255
                drawBitmap(source, null, originalBounds, paint)
            }
            return
        }
        val tension = packStep(0f, .13f, t) * (1f - packStep(.38f, .56f, t))
        val tear = packStep(.115f, .48f, t)
        val detach = packStep(.44f, .74f, t)
        val bend = packStep(.105f, .30f, t)
        val purse = packStep(.26f, .56f, t)
        val slide = packStep(.48f, .94f, t)
        val drop = h * 1.31f * slide
        for (i in cut.indices) {
            val u = i.toFloat() / COLUMNS
            val lever = u * u
            val wave = sin(pi * u)
            val release = packStep(1f - u, 1f - u + .18f, tear * 1.18f)
            val drag = w * .042f * lever * tension
            val pull = -h * .015f * lever * tension
            val swell = release * (wave * purse + lever * bend * .50f)
            opening[i] = release
            for (j in 0..BODY_ROWS) {
                val v = j.toFloat() / BODY_ROWS
                val y = bodyTop + body.height * v
                val near = (1f - (y - cut[i]).coerceAtLeast(0f) / (h * .46f)).coerceAtLeast(0f)
                val n = near * near
                val x1 = w * u + drag * n - w * .045f * slide - w * .036f * wave * swell * n + w * .035f * v * slide
                val y1 = y + pull * n + h * .031f * swell * n + drop + h * .032f * (u - .5f) * slide
                val k = j * (COLUMNS + 1) + i
                bodyVerts[2 * k] = x1
                bodyVerts[2 * k + 1] = y1
                val crease = sin(u * 21f + v * 6f - tear * 2f)
                bodyColor[k] = shade(1f - (.085f + .085f * crease) * n * (tension * lever + .52f * swell))
            }
            bodyEdge[2 * i] = w * u + drag - w * .045f * slide - w * .036f * wave * swell
            bodyEdge[2 * i + 1] = cut[i] + pull + h * .031f * swell + drop + h * .032f * (u - .5f) * slide

            val curl = release * (.28f + .72f * u) * bend
            val across = w * (u + .14f * curl + (.42f + .27f * u) * detach)
            val along = cut[i] + pull - h * ((.025f + .14f * u) * curl + (.25f + .14f * u) * detach)
            val roll = .88f * curl + .62f * detach
            for (j in 0..STRIP_ROWS) {
                val y = stripBottom * j.toFloat() / STRIP_ROWS
                val d = y - cut[i]
                val k = j * (COLUMNS + 1) + i
                stripVerts[2 * k] = across + drag + d * roll * .83f
                stripVerts[2 * k + 1] = along + d * (1f - .72f * curl - .34f * detach)
                stripColor[k] = shade(1f - (.14f + .10f * sin(u * 10f + j * .9f)) * curl)
            }
            stripEdge[2 * i] = across + drag
            stripEdge[2 * i + 1] = along
        }
        canvas.withSave {
            canvas.scale(width / w, height / h)
            if (slide < 1f) {
                paint.alpha = 255
                canvas.drawBitmapMesh(body, COLUMNS, BODY_ROWS, bodyVerts, 0, bodyColor, 0, paint)
                lip(canvas, bodyEdge, upper = false, strength = purse)
            }
            if (detach < 1f) {
                paint.alpha = (255f * (1f - packStep(.58f, .74f, t))).toInt()
                canvas.drawBitmapMesh(strip, COLUMNS, STRIP_ROWS, stripVerts, 0, stripColor, 0, paint)
                lip(canvas, stripEdge, upper = true, strength = bend * paint.alpha / 255f)
            }
        }
    }

    private fun shade(value: Float): Int {
        val x = (255 * value).toInt().coerceIn(0, 255)
        return Color.rgb(x, x, x)
    }

    /** Thin folded silver lining, confined to the printed packet's opaque width. */
    private fun lip(canvas: Canvas, edge: FloatArray, upper: Boolean, strength: Float) {
        if (strength < .001f) return
        val first = 3
        val last = COLUMNS - 3
        path.rewind()
        path.moveTo(edge[2 * first], edge[2 * first + 1])
        for (i in first + 1..last) path.lineTo(edge[2 * i], edge[2 * i + 1])
        for (i in last downTo first) {
            val u = (i - first).toFloat() / (last - first)
            val roll = opening[i] * (.24f + .76f * u) * sin(pi * u)
            val depth = if (upper) 9.5f else -7.5f
            path.lineTo(edge[2 * i], edge[2 * i + 1] + depth * h / 960f * strength * roll)
        }
        path.close()
        edgePaint.style = Paint.Style.FILL
        edgePaint.shader = liner
        edgePaint.alpha = (228 * strength).toInt()
        canvas.drawPath(path, edgePaint)
        edgePaint.shader = null
        // A grazing reflection follows the rip, then settles back into the silver edge.
        edgePaint.style = Paint.Style.STROKE
        edgePaint.strokeWidth = w * .0025f
        for (i in first + 1..last) {
            val release = opening[i]
            if (release <= 0f) continue
            val catch = 1f - abs(2f * release - 1f)
            edgePaint.color = if (upper) Color.WHITE else 0xff383946.toInt()
            edgePaint.alpha = ((80 + 140 * catch) * release * strength).toInt()
            canvas.drawLine(edge[2 * i - 2], edge[2 * i - 1], edge[2 * i], edge[2 * i + 1], edgePaint)
        }
    }
}
