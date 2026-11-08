package io.issac.pleosandroid.feature.badges.album.forge

import android.graphics.Bitmap
import android.graphics.Color
import androidx.compose.runtime.Composable
import androidx.compose.runtime.State
import androidx.compose.runtime.produceState
import kotlin.math.PI
import kotlin.math.abs
import kotlin.math.atan2
import kotlin.math.cos
import kotlin.math.exp
import kotlin.math.sin
import kotlin.math.sqrt
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

@Composable
internal fun rememberEclipseCorona(eligible: Boolean): State<Bitmap?> = produceState<Bitmap?>(null, eligible) {
    if (eligible) value = withContext(Dispatchers.Default) { eclipseCorona }
}

/** Luminous rim and uneven, outward-flowing plumes. Baked once; playback only transforms the bitmap. */
private val eclipseCorona by lazy {
    val size = 384
    val sectors = 1024
    val rim = FloatArray(sectors)
    val plume = FloatArray(sectors)
    val tint = FloatArray(sectors)
    for (i in 0 until sectors) {
        val a = i.toFloat() * 2f * PI.toFloat() / sectors
        rim[i] = .603f + .008f * sin(a * 9f) + .004f * sin(a * 19f + .7f)
        val flare = (.5f + .5f * cos(a * 3f + 1.6f))
        val streams = (.5f + .5f * sin(a * 17f + 2.8f * sin(4f * a)))
        plume[i] = .17f + .58f * flare * flare + .45f * streams * streams
        tint[i] = .5f + .5f * sin(a * 2f - .7f)
    }
    val pixels = IntArray(size * size)
    for (y in 0 until size) {
        for (x in 0 until size) {
            val px = (2f * x + 1f) / size - 1f
            val py = (2f * y + 1f) / size - 1f
            val radius = sqrt(px * px + py * py)
            val angle = atan2(py, px)
            val sector = ((angle + PI.toFloat()) * sectors / (2f * PI.toFloat())).toInt() % sectors
            val d = radius - rim[sector]
            val core = exp(-abs(d) / .012f)
            val sheath = exp(-abs(d) / .047f) * .52f
            val streak = if (d > 0f) {
                val curl = .68f + .32f * sin(33f * angle + 3.2f * sin(5f * angle) + 17f * d)
                exp(-d / (.023f + .145f * plume[sector])) * plume[sector] * curl
            } else {
                0f
            }
            val glow = ((core + sheath + streak * .76f) * (1f - easeBetween(.94f, 1f, radius))).coerceIn(0f, 1f)
            val heat = (.20f + .80f * core).coerceAtMost(1f)
            val tone = tint[sector]
            pixels[y * size + x] =
                Color.argb(
                    (255f * glow).toInt(),
                    255,
                    (113f + 73f * tone + (91f - 32f * tone) * heat).toInt(),
                    (92f + 44f * (1f - tone) + 100f * heat).toInt(),
                )
        }
    }
    Bitmap.createBitmap(pixels, size, size, Bitmap.Config.ARGB_8888)
}
