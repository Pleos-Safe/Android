package io.issac.pleosandroid.feature.badges.album.forge

import android.graphics.Bitmap
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.LinearGradient
import android.graphics.Paint
import android.graphics.Path
import android.graphics.RadialGradient
import android.graphics.RectF
import android.graphics.Shader
import androidx.compose.foundation.layout.Box
import androidx.compose.runtime.Composable
import androidx.compose.runtime.State
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.drawWithCache
import androidx.compose.ui.graphics.drawscope.drawIntoCanvas
import androidx.compose.ui.graphics.nativeCanvas
import androidx.compose.ui.graphics.toArgb
import androidx.core.graphics.withSave
import io.issac.pleosandroid.core.domain.cards.ForgeOutcome
import kotlin.math.PI
import kotlin.math.cos
import kotlin.math.pow
import kotlin.math.sin
import kotlin.math.sqrt

@Composable
internal fun ForgeField(
    sequence: ForgeSequence,
    progress: State<Float>,
    printWidth: Float,
    solar: Bitmap?,
    modifier: Modifier,
) {
    Box(
        modifier.drawWithCache {
            val field = ForgeFieldPainter(sequence, size.width, size.height, printWidth, solar)
            onDrawBehind { drawIntoCanvas { field.draw(it.nativeCanvas, progress.value) } }
        },
    )
}

private const val TAU = (PI * 2).toFloat()
private val silver = Color.rgb(205, 231, 241)
private val sea = Color.rgb(91, 215, 217)
private val iris = Color.rgb(147, 139, 235)
private val amber = Color.rgb(255, 198, 111)
private val pearl = Color.rgb(255, 241, 211)
private val ruby = Color.rgb(244, 88, 106)

private val sides = floatArrayOf(-1f, 1f)
private val crownProfile = floatArrayOf(
    -94f, -25f, -68f, -10f, -47f, -39f, -23f, -16f, 0f, -53f,
    23f, -16f, 47f, -39f, 68f, -10f, 94f, -25f,
)
private val starMap = floatArrayOf(
    .40f, .64f, .59f, .32f, .84f, .46f, .73f, .065f, .92f, -.22f,
    .67f, -.46f, .48f, -.21f, .57f, -.63f,
)
private fun bezier(t: Float, a: Float, b: Float, c: Float, d: Float): Float {
    val q = 1f - t
    return q * q * q * a + 3f * q * q * t * b + 3f * q * t * t * c + t * t * t * d
}

/** The card is 300 x 450 in this coordinate system. Light is staged around its silhouette on any viewport. */
private class ForgeFieldPainter(
    private val sequence: ForgeSequence,
    private val width: Float,
    private val height: Float,
    printWidth: Float,
    private val solar: Bitmap?,
) {
    private val scale = printWidth / 300f
    private val extentX = width / (2f * scale)
    private val extentY = height / (2f * scale)
    private val particles = ForgeChargeParticles(extentX, extentY, sequence)
    private val accent = sequence.accent.toArgb()
    private val second = sequence.secondary.toArgb()
    private val paint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        strokeCap = Paint.Cap.ROUND
        strokeJoin = Paint.Join.ROUND
    }
    private val path = Path()
    private val x = FloatArray(27)
    private val y = FloatArray(x.size)
    private val film = ForgeRibbon(x.size)
    private val strength = FloatArray(x.size)
    private val matte = LinearGradient(
        -extentX,
        -extentY,
        extentX,
        extentY,
        intArrayOf(0xff111e32.toInt(), 0xff080d19.toInt(), 0xff111124.toInt()),
        null,
        Shader.TileMode.CLAMP,
    )
    private val glow = listOf(silver, sea, iris, amber, pearl, ruby, accent, second).associateWith { color ->
        RadialGradient(
            0f,
            0f,
            1f,
            intArrayOf(color, translucent(color, .46f), translucent(color, .10f), translucent(color, 0f)),
            floatArrayOf(0f, .23f, .62f, 1f),
            Shader.TileMode.CLAMP,
        )
    }
    private val leafMetal = LinearGradient(
        -5f,
        1f,
        21f,
        -24f,
        intArrayOf(0xff986139.toInt(), 0xffefbd70.toInt(), 0xfffff0c1.toInt(), 0xffb98352.toInt()),
        null,
        Shader.TileMode.CLAMP,
    )
    private val crownMetal = LinearGradient(
        -96f,
        -40f,
        88f,
        -10f,
        intArrayOf(0xffa27453.toInt(), 0xffffdf9b.toInt(), 0xffe4a967.toInt(), 0xffffebbb.toInt(), 0xffa97c65.toInt()),
        null,
        Shader.TileMode.CLAMP,
    )
    private val coronaBounds = RectF()
    private val neon = Paint(Paint.ANTI_ALIAS_FLAG or Paint.FILTER_BITMAP_FLAG)

    fun draw(canvas: Canvas, time: Float) = canvas.withSave {
        translate(width / 2f, height / 2f)
        scale(scale, scale)
        fill(matte, 1f)
        drawRect(-extentX, -extentY, extentX, extentY, paint)
        val release = sequence.release(time)
        val energy = sequence.pressure(time)
        val power = sequence.power
        bloom(this, -122f, -126f, 300f, 205f, sea, .12f + energy * .45f)
        bloom(this, 121f, 111f, 265f, 275f, iris, .13f + energy * .44f)
        if (energy > .001f) {
            val gathering = sequence.gathering(time)
            particles.draw(this, gathering, energy)
            val light = energy * easeBetween(.20f, .82f, gathering)
            val lift = -14f * energy
            bloom(this, -144f, (CHARGE_LEFT_Y - .5f) * 450f + lift, 101f, 125f, sea, .86f * light)
            bloom(this, 144f, (CHARGE_RIGHT_Y - .5f) * 450f + lift, 107f, 133f, pearl, .79f * light)
        }
        if (release <= 0f) return@withSave
        val strike = pulse(release, 0f, .11f, .70f)
        bloom(this, 0f, 0f, 235f + 165f * release, 280f, accent, strike * (.48f + power * .39f))
        when (sequence.outcome) {
            ForgeOutcome.Hold -> drawSeal(this, release)

            ForgeOutcome.Drop -> drawFall(this, release)

            ForgeOutcome.Destroyed -> drawRupture(this, release)

            ForgeOutcome.GreatSuccess -> {
                victoryCrescents(this, release, true)
                drawGildedFan(this, release)
                foilEjecta(this, ((release - .10f) / .90f).coerceIn(0f, 1f))
            }

            ForgeOutcome.Success -> victoryCrescents(this, release, false)

            ForgeOutcome.Restored -> wave(this, release, accent, .86f, -8f)
        }
        if (sequence.finish != ForgeFinish.Print) drawMilestone(this, release, 1f - sequence.exit(time))
    }

    private fun wave(canvas: Canvas, u: Float, color: Int, weight: Float, tilt: Float) {
        val visible = pulse(u, 0f, .15f, .89f) * weight
        if (visible <= .001f) return
        val expansion = easeBetween(0f, .93f, u)
        canvas.withSave {
            rotate(tilt)
            val rx = 108f + 183f * expansion
            val ry = 150f + 121f * expansion
            val cy = 33f - 95f * expansion
            arc(this, rx, ry, cy, 166f, 94f, color, visible, 2.7f)
            arc(this, rx, ry, cy, -33f, 80f, color, visible, 2.3f)
            arc(this, rx + 6f, ry + 4f, cy, 175f, 62f, pearl, visible * .46f, 1f)
        }
    }

    /** Broad pearlescent wave fronts overtake the shoulders of the card, then thin to reflected edges. */
    private fun victoryCrescents(canvas: Canvas, u: Float, gilded: Boolean) {
        if (u <= 0f || u >= 1f) return
        val travel = easeBetween(0f, .97f, u)
        val light = pulse(u, 0f, .20f, 1.02f)
        val reach = if (gilded) 245f else 191f + sequence.power * 27f
        val radius = 105f + reach * travel
        val top = 52f - travel * 151f
        val depth = 147f + 115f * travel
        for (side in sides) {
            for (fold in 0..1) {
                val phase = if (side < 0f) -.13f else .16f
                for (i in x.indices) {
                    val t = i.toFloat() / x.lastIndex
                    val theta = -1.24f + 2.59f * t + phase - .29f * travel + .08f * fold
                    val bow = sin(PI.toFloat() * t)
                    x[i] = side * (radius + 11f * fold + 26f * bow) * cos(theta)
                    y[i] = top + depth * sin(theta) + fold * 6f - bow * 14f
                    strength[i] = bow * (.48f + .52f * sin(1.72f * t + .24f))
                }
                val color = if (gilded) {
                    if (fold == 0) amber else pearl
                } else if (fold == 0) {
                    accent
                } else {
                    sea
                }
                ribbon(
                    canvas,
                    color,
                    light * if (fold == 0) 1f else .72f,
                    if (gilded) 41f - fold * 16f else 29f - fold * 12f,
                )
                bloom(canvas, x[13], y[13], 112f, 62f, color, .68f * light)
            }
        }
        val crest = pulse(u, .06f, .25f, .67f)
        bloom(canvas, -radius * .68f, top - depth * .62f, 124f, 51f, if (gilded) amber else accent, .75f * crest)
        wave(canvas, u, if (gilded) pearl else accent, .69f * light, if (gilded) 15f else -10f)
    }

    /** Small pieces of stamped foil turn edge-on and cool after the second impact. */
    private fun foilEjecta(canvas: Canvas, u: Float) {
        if (u <= 0f || u >= 1f) return
        val flight = easeBetween(0f, 1f, u)
        for (i in 0 until 18) {
            val a = i * 2.39996f + .27f
            val seed = (i * 13 % 17) / 17f
            val px = cos(a) * (147f + (95f + 97f * seed) * flight)
            val py = sin(a) * (177f + (86f + 82f * seed) * flight) - 63f * flight + 97f * flight * flight
            val flash = kotlin.math.abs(cos(a + flight * (5.8f + seed * 3f)))
            val hot = pulse(u, .015f, .23f + seed * .18f, 1.04f) * flash
            canvas.withSave {
                translate(px, py)
                rotate(a * 57.3f + flight * (75f + seed * 110f))
                val length = 4f + seed * 7f
                val width = .65f + 2.6f * flash
                path.rewind()
                path.moveTo(-length, 0f)
                path.lineTo(-1f, -width)
                path.lineTo(length, 0f)
                path.lineTo(1f, width)
                path.close()
                solid(if (i % 3 == 0) silver else amber, hot * .96f)
                drawPath(path, paint)
                line(-length, 0f, -1f, -width, pearl, hot, .85f, this)
            }
        }
    }

    private fun drawGildedFan(canvas: Canvas, u: Float) {
        val opening = easeBetween(.23f, .74f, u)
        val radiance = pulse(u, .22f, .48f, 1.08f)
        if (radiance <= 0f) return
        for (i in -3..3) {
            val side = i / 3f
            for (j in x.indices) {
                val along = j.toFloat() / x.lastIndex
                val bow = sin(along * PI.toFloat())
                x[j] = side * (43f + 337f * along * opening) + (37f - i * 8f) * bow
                y[j] = 224f - along * (501f - i * i * 13f) - bow * 51f * opening
                strength[j] = bow * (.46f + along)
            }
            val foil = if (i % 2 == 0) amber else pearl
            ribbon(canvas, foil, radiance * .96f, 38f + (3 - kotlin.math.abs(i)) * 4f)
            bloom(canvas, x[19], y[19], 99f, 68f, foil, radiance * .50f)
        }
    }

    /** Two sheets of cold light press against the shoulders, then reflect away from the same contact points. */
    private fun drawSeal(canvas: Canvas, u: Float) {
        val press = holdCompression(u)
        val rebound = holdRebound(u)
        val arrival = easeBetween(0f, .27f, u)
        val escape = easeBetween(.27f, .94f, u)
        val radius = 241f - 86f * arrival + 138f * escape
        for (side in sides) {
            val tint = if (side < 0f) sea else silver
            for (fold in 0..1) {
                for (i in x.indices) {
                    val t = i.toFloat() / x.lastIndex
                    val a = (t - .5f) * 2.62f + side * (.19f - .27f * escape) + .08f * fold
                    val bow = sin(PI.toFloat() * t)
                    x[i] = side * ((radius + fold * 11f) * cos(a) + 8f * bow * press)
                    y[i] = (208f - 30f * press + 31f * escape) * sin(a) + side * (19f - 32f * escape)
                    strength[i] = bow * (.58f + .42f * cos(a))
                }
                val light = if (fold == 0) .88f * press + rebound else .37f * press + .57f * rebound
                ribbon(canvas, if (fold == 0) tint else iris, light, (31f - fold * 13f) * (1f - .36f * escape))
            }
            bloom(canvas, side * 156f, side * 18f, 86f, 119f, tint, press * .88f)
            bloom(canvas, side * radius, -side * 11f, 112f, 54f, tint, rebound * .66f)
        }
    }

    /** The foil peels down from each shoulder. The landing sends one shallow reflection along the floor. */
    private fun drawFall(canvas: Canvas, u: Float) {
        val fall = fallDisplacement(u)
        val head = easeBetween(.045f, .77f, u)
        val tail = easeBetween(.29f, 1f, u)
        val light = pulse(u, .025f, .26f, .97f)
        for (side in sides) {
            for (fold in 0..1) {
                for (i in x.indices) {
                    val v = i.toFloat() / x.lastIndex
                    val t = tail + (head - tail) * v
                    val twist = sin((t * 2.2f + fold * .19f) * PI.toFloat())
                    x[i] = side * (bezier(t, 128f, 270f, 96f, 241f) + twist * (18f + fold * 9f)) - 16f * fall
                    y[i] = bezier(t, -204f, -90f, 207f, 260f) + side * 23f + 27f * fall
                    strength[i] = sin(v * PI.toFloat()) * (.44f + .56f * kotlin.math.abs(twist))
                }
                ribbon(canvas, if (fold == 0) accent else second, light * (1f - fold * .34f), 32f - fold * 11f)
            }
        }
        val hit = landingShock(u)
        val spread = easeBetween(.46f, .96f, u)
        val afterglow = pulse(u, .46f, .58f, .99f)
        bloom(canvas, -14f, 248f, 89f + 178f * spread, 42f, amber, hit * .94f)
        arc(canvas, 65f + 264f * spread, 7f + 39f * spread, 248f, 4f, 171f, pearl, afterglow * .86f, 2.2f)
        arc(canvas, 76f + 285f * spread, 9f + 44f * spread, 248f, 12f, 151f, second, afterglow * .51f, 1.4f)
    }

    private fun drawRupture(canvas: Canvas, u: Float) {
        val shock = fractureShock(u)
        val originX = (FRACTURE_X - .5f) * 300f
        val originY = (FRACTURE_Y - .5f) * 450f
        canvas.withSave {
            translate(originX, originY)
            rotate(-17f)
            for (echo in 0..1) {
                val start = .10f + echo * .12f
                val out = easeBetween(start, .87f + .10f * echo, u)
                val light = pulse(u, start, start + .15f, .89f + .10f * echo)
                if (light <= .001f) continue
                for (side in sides) {
                    for (i in x.indices) {
                        val v = i.toFloat() / x.lastIndex
                        val a = (v - .5f) * 2.94f + side * (.18f + .21f * echo)
                        val ripple = sin(3.1f * a + side) * 12f * out
                        x[i] = side * (36f + 298f * out + ripple) * cos(a)
                        y[i] = (26f + 225f * out - ripple) * sin(a)
                        strength[i] = sin(PI.toFloat() * v) * (.80f + .20f * cos(2f * a))
                    }
                    ribbon(this, if (echo == 0) ruby else iris, light * (1f - .32f * echo), 39f - 18f * out)
                }
            }
            bloom(this, 0f, 0f, 306f, 83f, ruby, shock * .89f)
            bloom(this, 0f, 0f, 209f, 43f, pearl, shock * .72f)
        }
        // Slivers leave the fracture point on the same expanding front, then turn edge-on and cool.
        for (i in 0 until 12) {
            val seed = (i * 7 % 11) / 11f
            val a = i * 2.39996f + .51f
            val start = .14f + .075f * seed
            val flight = ((u - start) / (1f - start)).coerceIn(0f, 1f)
            val out = 1f - (1f - flight) * (1f - flight)
            val px = originX + cos(a) * (192f + seed * 118f) * out
            val py = originY + sin(a) * (171f + seed * 113f) * out - 46f * flight + 119f * flight * flight
            val hot = pulse(u, start, start + .16f, .97f)
            if (hot <= .001f) continue
            canvas.withSave {
                translate(px, py)
                rotate(a * 57.3f + (80f + 76f * seed) * out)
                val len = 6f + seed * 8f
                val edge = .65f + 2.2f * kotlin.math.abs(cos(a + out * 4.3f))
                path.rewind()
                path.moveTo(-len, 0f)
                path.lineTo(2f, -edge)
                path.lineTo(len * .76f, 0f)
                path.lineTo(-3f, edge * .67f)
                path.close()
                solid(if (i % 3 == 0) silver else ruby, hot * .91f)
                drawPath(path, paint)
                line(-len, 0f, 2f, -edge, pearl, hot * .87f, .9f, this)
            }
        }
        bloom(canvas, 0f, 52f, 176f, 256f, iris, .33f * pulse(u, .36f, .64f, 1f))
    }

    private fun drawMilestone(canvas: Canvas, u: Float, opacity: Float) {
        val tier = sequence.finish.ordinal
        val kindle = easeBetween(.12f, .65f, u)
        val light = pulse(u, .07f, .50f, 1.22f) * opacity
        if (light < .005f) return
        bloom(canvas, 0f, -134f, 278f, 154f, if (tier >= 4) amber else second, .50f * light)
        if (tier >= 2) drawAurora(canvas, u, light, tier)
        if (tier >= 4) crown(canvas, kindle, light)
        if (tier >= 5) laurels(canvas, kindle, light)
        when (sequence.sky) {
            SkyFinish.Eclipse -> corona(canvas, u, light)
            SkyFinish.Aurora -> auroralVeil(canvas, u, light)
            SkyFinish.Constellation -> constellation(canvas, u, light)
            null -> Unit
        }
    }

    private fun drawAurora(canvas: Canvas, u: Float, light: Float, tier: Int) {
        for (i in 0 until if (tier >= 3) 4 else 2) {
            val side = if (i % 2 == 0) -1f else 1f
            for (j in x.indices) {
                val v = j.toFloat() / x.lastIndex
                x[j] = side * (109f + 148f * sin(v * 2.7f + i * .19f + u * .8f) + 23f * v)
                y[j] = 274f - 545f * v - 86f * u + 29f * sin(v * 6f + i)
                strength[j] = sin(v * PI.toFloat()).coerceAtLeast(0f) * (.58f + .42f * sin(5f * v + u * 2f + i))
            }
            ribbon(canvas, if (i % 3 == 0) second else accent, light * (.54f - .06f * i), if (tier >= 3) 44f else 22f)
        }
    }

    private fun crown(canvas: Canvas, grow: Float, light: Float) = canvas.withSave {
        bloom(this, 0f, -232f, 129f, 76f, amber, .79f * light)
        translate(0f, -215f - 9f * grow)
        scale(.85f + .15f * grow, grow.coerceAtLeast(.01f))
        path.rewind()
        path.moveTo(-94f, 8f)
        for (i in crownProfile.indices step 2) path.lineTo(crownProfile[i], crownProfile[i + 1])
        path.lineTo(94f, 8f)
        path.close()
        fill(crownMetal, light * .95f)
        drawPath(path, paint)
        paint.style = Paint.Style.STROKE
        paint.strokeWidth = 1.45f
        drawPath(path, paint)
        for (i in -2..2) {
            val x = i * 47f
            val top = when (kotlin.math.abs(i)) {
                0 -> -53f
                1 -> -39f
                else -> -25f
            }
            line(x, top, x * .85f - 10f, -8f, pearl, light * .91f, 1.1f, this)
            bloom(this, x, top, 7.5f, 6f, pearl, light * .95f)
        }
        path.rewind()
        path.moveTo(0f, -38f)
        path.lineTo(6f, -28f)
        path.lineTo(0f, -18f)
        path.lineTo(-6f, -28f)
        path.close()
        solid(0xff617e83.toInt(), light)
        drawPath(path, paint)
        line(0f, -38f, -6f, -28f, silver, light, 1.2f, this)
        line(-91f, -4f, 91f, -4f, amber, light, 2f, this)
    }

    private fun laurels(canvas: Canvas, grow: Float, light: Float) {
        for (side in sides) {
            path.rewind()
            for (i in 0..22) {
                val t = grow * i / 22f
                val x = side * bezier(t, 72f, 185f, 225f, 190f)
                val y = bezier(t, 230f, 216f, 42f, -112f)
                if (i == 0) path.moveTo(x, y) else path.lineTo(x, y)
            }
            solid(amber, light * .84f)
            paint.style = Paint.Style.STROKE
            paint.strokeWidth = 1.65f
            canvas.drawPath(path, paint)
            for (i in 0 until 10) {
                val t = .065f + i * .097f
                val leaf = easeBetween(t * .69f, t * .69f + .24f, grow)
                if (leaf <= 0f) continue
                val x = side * bezier(t, 72f, 185f, 225f, 190f)
                val y = bezier(t, 230f, 216f, 42f, -112f)
                canvas.withSave {
                    translate(x, y)
                    scale(side * leaf, leaf)
                    rotate(21f - 36f * t)
                    leaf(this, 26f - 8f * t, -21f - 9f * t, light)
                    leaf(this, -7f - 9f * t, -25f, light * .89f)
                }
            }
        }
    }

    private fun leaf(canvas: Canvas, x: Float, y: Float, light: Float) {
        path.rewind()
        path.moveTo(0f, 0f)
        path.cubicTo(x * .18f - 6f, y * .61f, x * .72f, y * .82f, x, y)
        path.cubicTo(x * .78f + 5f, y * .22f, x * .37f, y * .11f, 0f, 0f)
        fill(leafMetal, light)
        canvas.drawPath(path, paint)
        line(0f, 0f, x * .83f, y * .81f, pearl, .77f * light, .78f, canvas)
    }

    private fun auroralVeil(canvas: Canvas, u: Float, light: Float) {
        val unfurl = easeBetween(.05f, .69f, u)
        for (i in 0 until 7) {
            for (j in x.indices) {
                val t = j.toFloat() / x.lastIndex
                x[j] = (t - .5f) * extentX.coerceIn(310f, 540f) * 2.2f
                val fold = sin(t * 8.5f + u * 2.6f + i * .14f + sequence.azimuth * .008f)
                y[j] = -102f + fold * (109f + 32f * sin(t * 5f)) - i * 9f - 40f * unfurl
                strength[j] = sin(PI.toFloat() * t) * (.58f + .42f * fold)
            }
            val color = when (i % 3) {
                0 -> sea
                1 -> iris
                else -> silver
            }
            ribbon(canvas, color, light * .86f, 22f + i * 6f)
        }
        bloom(canvas, 0f, -175f, 395f, 108f, sea, .52f * light)
    }

    private fun constellation(canvas: Canvas, u: Float, light: Float) {
        val width = extentX.coerceIn(213f, 435f)
        val height = extentY.coerceAtMost(310f)
        canvas.withSave {
            rotate(5f * sin(sequence.azimuth * PI.toFloat() / 180f))
            for (side in sides) {
                var lastX = 0f
                var lastY = 0f
                for (i in starMap.indices step 2) {
                    val n = i / 2
                    val ignition = .035f + .046f * n + if (side > 0f) .05f else 0f
                    val joined = easeBetween(ignition, ignition + .20f, u)
                    val x = side * width * starMap[i]
                    val y = height * (starMap[i + 1] * side + .10f * sin(n * 1.7f))
                    if (i != 0) {
                        val tipX = lastX + (x - lastX) * joined
                        val tipY = lastY + (y - lastY) * joined
                        line(lastX, lastY, tipX, tipY, iris, .22f * light, 4.0f, this)
                        line(lastX, lastY, tipX, tipY, silver, .85f * light, 1.1f, this)
                        bloom(this, tipX, tipY, 13f, 7f, pearl, pulse(u, ignition, ignition + .11f, ignition + .25f))
                    }
                    val r = (3.7f + n % 3 * 1.9f) * joined
                    bloom(this, x, y, 44f, 36f, iris, joined * light * .73f)
                    bloom(this, x, y, 18f, 8f, pearl, joined * light * .97f)
                    path.rewind()
                    path.moveTo(x, y - r * 1.8f)
                    path.lineTo(x + r * .59f, y)
                    path.lineTo(x, y + r * 1.8f)
                    path.lineTo(x - r * .59f, y)
                    path.close()
                    solid(pearl, joined * light)
                    drawPath(path, paint)
                    lastX = x
                    lastY = y
                }
            }
        }
    }

    private fun corona(canvas: Canvas, u: Float, light: Float) {
        val texture = solar ?: return
        val unfurl = easeBetween(.08f, .69f, u)
        val rad = 133f + 106f * unfurl
        val radius = rad / .603f
        canvas.withSave {
            rotate(sequence.azimuth + 21f * u)
            coronaBounds.set(-radius, -radius, radius, radius)
            neon.alpha = (250 * light).toInt()
            drawBitmap(texture, null, coronaBounds, neon)
            rotate(-28f)
            for (i in 0 until 2) {
                rotate(78f)
                arc(
                    this, rad * 1.39f, rad * .59f, 0f, 11f + u * 29f, 245f,
                    if (i ==
                        0
                    ) {
                        sea
                    } else {
                        pearl
                    },
                    .68f * light, 1.45f,
                )
            }
        }
        bloom(canvas, -rad * .80f, -rad * .56f, 104f, 57f, pearl, .82f * light)
        bloom(canvas, rad * .91f, rad * .38f, 112f, 69f, amber, .77f * light)
    }

    private fun ribbon(canvas: Canvas, color: Int, opacity: Float, breadth: Float) {
        if (opacity > .004f) film.draw(canvas, x, y, strength, color, opacity, breadth)
    }

    private fun arc(
        c: Canvas,
        x: Float,
        y: Float,
        cy: Float,
        start: Float,
        sweep: Float,
        color: Int,
        alpha: Float,
        stroke: Float,
    ) {
        solid(color, alpha)
        paint.style = Paint.Style.STROKE
        paint.strokeWidth = stroke
        c.drawArc(-x, cy - y, x, cy + y, start, sweep, false, paint)
    }

    private fun bloom(c: Canvas, x: Float, y: Float, rx: Float, ry: Float, color: Int, alpha: Float) {
        if (alpha < .005f) return
        c.withSave {
            translate(x, y)
            scale(rx, ry)
            fill(glow.getValue(color), alpha)
            drawCircle(0f, 0f, 1f, paint)
        }
    }

    private fun line(x1: Float, y1: Float, x2: Float, y2: Float, color: Int, alpha: Float, stroke: Float, c: Canvas) {
        solid(color, alpha)
        paint.strokeWidth = stroke
        c.drawLine(x1, y1, x2, y2, paint)
    }

    private fun fill(shader: Shader, opacity: Float) {
        paint.style = Paint.Style.FILL
        paint.shader = shader
        paint.color = Color.WHITE
        paint.alpha = (opacity.coerceIn(0f, 1f) * 255f).toInt()
    }

    private fun solid(color: Int, opacity: Float) {
        paint.shader = null
        paint.style = Paint.Style.FILL
        paint.color = translucent(color, opacity)
    }

    private fun translucent(color: Int, opacity: Float) =
        Color.argb((opacity.coerceIn(0f, 1f) * 255f).toInt(), Color.red(color), Color.green(color), Color.blue(color))
}
