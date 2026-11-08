package io.issac.pleosandroid.feature.badges.album.forge

import android.graphics.Bitmap
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.Paint
import kotlin.math.PI
import kotlin.math.exp
import kotlin.math.sin
import kotlin.math.sqrt

/** A single translucent fold: soft scattering across its width, two refracted crests, feathered ends. */
private val foldTexture by lazy {
    val width = 192
    val height = 48
    val pixels = IntArray(width * height)
    for (x in 0 until width) {
        val u = x.toFloat() / (width - 1)
        val crest = .12f * sin(u * 6.2f) + .055f * sin(u * 14.8f)
        val end = sin(u * PI).coerceAtLeast(0.0).toFloat()
        for (y in 0 until height) {
            val cross = 2f * y / (height - 1) - 1f
            val core = gaussian((cross - crest) / .093f)
            val shoulder = gaussian((cross - crest + .19f) / .33f)
            val echo = gaussian((cross + .42f - crest * .57f) / .051f)
            val haze = gaussian(cross / .57f)
            val alpha = ((.19f * haze + .47f * shoulder + .81f * core + .34f * echo) * sqrt(end)).coerceIn(0f, 1f)
            pixels[y * width + x] = Color.argb((255 * alpha).toInt(), 255, 255, 255)
        }
    }
    Bitmap.createBitmap(pixels, width, height, Bitmap.Config.ARGB_8888)
}

private fun gaussian(x: Float) = exp(-x * x)

/** Skin the fold along a curved spine. All buffers are retained across frames. */
internal class ForgeRibbon(samples: Int) {
    private val cols = samples - 1
    private val rows = 4
    private val vertices = FloatArray(2 * samples * (rows + 1))
    private val colors = IntArray(samples * (rows + 1))
    private val ink = Paint(Paint.ANTI_ALIAS_FLAG or Paint.FILTER_BITMAP_FLAG)
    private val film = foldTexture

    fun draw(
        canvas: Canvas,
        x: FloatArray,
        y: FloatArray,
        taper: FloatArray,
        color: Int,
        opacity: Float,
        breadth: Float,
    ) {
        val red = Color.red(color)
        val green = Color.green(color)
        val blue = Color.blue(color)
        for (i in 0..cols) {
            val prev = (i - 1).coerceAtLeast(0)
            val next = (i + 1).coerceAtMost(cols)
            val dx = x[next] - x[prev]
            val dy = y[next] - y[prev]
            val length = sqrt(dx * dx + dy * dy).coerceAtLeast(.01f)
            val nx = -dy / length
            val ny = dx / length
            for (row in 0..rows) {
                val v = row * 2f / rows - 1f
                val offset = taper[i] * breadth * v
                val k = row * x.size + i
                vertices[2 * k] = x[i] + nx * offset
                vertices[2 * k + 1] = y[i] + ny * offset
                val white = if (row == 2) {
                    .41f
                } else if (row == 1) {
                    .14f
                } else {
                    0f
                }
                colors[k] = Color.rgb(
                    (red + (255 - red) * white).toInt(),
                    (green + (255 - green) * white).toInt(),
                    (blue + (255 - blue) * white).toInt(),
                )
            }
        }
        ink.alpha = (255f * opacity.coerceIn(0f, 1f)).toInt()
        canvas.drawBitmapMesh(film, cols, rows, vertices, 0, colors, 0, ink)
    }
}
