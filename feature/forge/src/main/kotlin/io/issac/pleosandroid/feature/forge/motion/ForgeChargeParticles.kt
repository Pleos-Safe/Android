package io.issac.pleosandroid.feature.forge.motion

import android.graphics.Bitmap
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.Paint
import android.graphics.Path
import android.graphics.RectF
import kotlin.math.PI
import kotlin.math.exp
import kotlin.math.sin
import kotlin.math.sqrt
import kotlin.random.Random

internal const val CHARGE_LEFT_Y = .39f
internal const val CHARGE_RIGHT_Y = .60f

private val dustColors = intArrayOf(0xFF87D5EE.toInt(), 0xFFC8B1F0.toInt(), 0xFFFFDCAC.toInt(), 0xFFC9EBF5.toInt())

// A white core and a coloured penumbra from one filtered sprite, including at subpixel sizes.
private val dustSprites by lazy {
    dustColors.map { color ->
        val n = 48
        val pixels = IntArray(n * n) { i ->
            val x = (2f * (i % n + .5f) / n) - 1f
            val y = (2f * (i / n + .5f) / n) - 1f
            val r2 = x * x + y * y
            val core = exp(-74f * r2)
            val alpha = (core + .67f * exp(-21f * r2) + .34f * exp(-4.8f * r2)).coerceAtMost(1f)
            fun channel(c: Int) = (c + (255 - c) * core).toInt()
            Color.argb(
                (255 * alpha).toInt(),
                channel(Color.red(color)),
                channel(Color.green(color)),
                channel(Color.blue(color)),
            )
        }
        Bitmap.createBitmap(pixels, n, n, Bitmap.Config.ARGB_8888)
    }
}

private class Mote(
    val x: FloatArray,
    val y: FloatArray,
    val birth: Float,
    val lifetime: Float,
    val depth: Float,
    val radius: Float,
    val hue: Int,
    val focused: Boolean,
)

/** Paths are sampled once in card-width coordinates. Playback and restoration sample the same light. */
internal class ForgeChargeParticles(extentX: Float, extentY: Float, sequence: ForgeSequence) {
    private val random = Random(0x516F11 xor sequence.before)
    private val motes = List(if (sequence.bonus) 28 else 84 + 7 * sequence.chargeDepth) { i ->
        val side = if (i % 2 == 0) -1f else 1f
        val focused = i < (if (sequence.bonus) 2 else 8)
        val depth = if (focused) .80f + .20f * random.nextFloat() else random.nextFloat()
        val fromTop = i % 5 == 0
        val reach = if (sequence.bonus) .32f else 1f
        val startX = if (fromTop) {
            side * extentX * (.12f + .79f * random.nextFloat())
        } else {
            side * (165f + (extentX - 140f) * (.23f + .93f * random.nextFloat()) * reach)
        }
        val startY = if (fromTop) {
            (if (random.nextBoolean()) -1f else 1f) * extentY * (.89f + .19f * random.nextFloat())
        } else {
            (2f * random.nextFloat() - 1f) * extentY
        }
        val endX = side * (124f + 12f * random.nextFloat())
        val port = if (side < 0f) CHARGE_LEFT_Y else CHARGE_RIGHT_Y
        val endY = (port - .5f) * 450f - 10f + (random.nextFloat() - .5f) * 34f
        val bend = (random.nextFloat() - .32f) * side * 200f * reach
        val xs = curve(startX, startX + (endX - startX) * .28f, endX + side * (34f + 72f * depth) * reach, endX)
        val ys = curve(startY, startY + bend, endY - .53f * bend + .14f * (startY - endY), endY)
        val arrival = if (focused) {
            (if (sequence.bonus) .79f else .43f + .15f * (i / 2)) + .07f * random.nextFloat()
        } else {
            .16f + .84f * sqrt(random.nextFloat())
        }
        val lifetime = .30f + random.nextFloat() * .38f
        val radius = 3.7f + depth * depth * 9.5f
        Mote(xs, ys, arrival - lifetime, lifetime, depth, radius, random.nextInt(dustColors.size), focused)
    }
    private val trail = Path()
    private val dst = RectF()
    private val paint = Paint(Paint.ANTI_ALIAS_FLAG or Paint.FILTER_BITMAP_FLAG)
    private val sprites = dustSprites
    private val wakeX = FloatArray(17)
    private val wakeY = FloatArray(wakeX.size)
    private val taper = FloatArray(wakeX.size) { i ->
        val t = i.toFloat() / wakeX.lastIndex
        sin(PI.toFloat() * t).coerceAtLeast(0f) * (.34f + .66f * t)
    }
    private val wake = ForgeRibbon(wakeX.size)

    fun draw(canvas: Canvas, progress: Float, energy: Float) {
        val light = easeBetween(0f, .14f, progress) * (.64f + .36f * energy)
        for (mote in motes) {
            val age = (progress - mote.birth) / mote.lifetime
            if (mote.focused && age > .79f && age < 1.22f) {
                val contact = pulse(age, .79f, 1f, 1.22f) * energy
                val edge = if (mote.x.last() < 0f) -143f else 143f
                val y = mote.y.last()
                dst.set(edge - 38f, y - 59f, edge + 38f, y + 59f)
                paint.color = Color.WHITE
                paint.alpha = (230f * contact).toInt()
                canvas.drawBitmap(sprites[mote.hue], null, dst, paint)
            }
            if (age <= 0f || age >= 1f) continue
            val alpha = light * easeBetween(0f, .16f, age) * (1f - easeBetween(.94f, 1f, age))
            val head = age * (.30f + .70f * age)
            if (mote.focused) {
                val start = (head - .13f - .20f * age).coerceAtLeast(0f)
                for (i in wakeX.indices) {
                    val point = start + (head - start) * i / wakeX.lastIndex
                    wakeX[i] = sample(mote.x, point)
                    wakeY[i] = sample(mote.y, point)
                }
                wake.draw(canvas, wakeX, wakeY, taper, dustColors[mote.hue], alpha * .56f, 8f + 5f * age)
            }
            val tail = (head - (.010f + .085f * age * age) * (.35f + mote.depth)).coerceAtLeast(0f)
            val x = sample(mote.x, head)
            val y = sample(mote.y, head)
            val tailX = sample(mote.x, tail)
            val tailY = sample(mote.y, tail)
            val dx = x - tailX
            val dy = y - tailY
            val radius = mote.radius * (.74f + .38f * age)
            val normal = radius * .105f / sqrt(dx * dx + dy * dy).coerceAtLeast(.01f)
            val nx = -dy * normal
            val ny = dx * normal
            val midX = sample(mote.x, (head + tail) * .5f)
            val midY = sample(mote.y, (head + tail) * .5f)
            trail.rewind()
            trail.moveTo(tailX, tailY)
            trail.quadTo(midX + nx * .48f, midY + ny * .48f, x + nx, y + ny)
            trail.lineTo(x - nx, y - ny)
            trail.quadTo(midX - nx * .48f, midY - ny * .48f, tailX, tailY)
            trail.close()
            paint.color = dustColors[mote.hue]
            paint.alpha = (alpha * (80f + 115f * mote.depth)).toInt()
            canvas.drawPath(trail, paint)
            paint.color = Color.WHITE
            paint.alpha = (alpha * (165f + 90f * mote.depth)).toInt()
            dst.set(x - radius, y - radius, x + radius, y + radius)
            canvas.drawBitmap(sprites[mote.hue], null, dst, paint)
        }
    }

    private fun curve(a: Float, b: Float, c: Float, d: Float) = FloatArray(33) { index ->
        val t = index / 32f
        val q = 1f - t
        q * q * q * a + 3f * q * q * t * b + 3f * q * t * t * c + t * t * t * d
    }

    private fun sample(points: FloatArray, position: Float): Float {
        val at = position * points.lastIndex
        val i = at.toInt().coerceAtMost(points.lastIndex - 1)
        return points[i] + (points[i + 1] - points[i]) * (at - i)
    }
}
