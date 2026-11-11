package io.issac.pleosandroid.feature.forge.motion

import androidx.compose.runtime.State
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.drawWithCache
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Rect
import androidx.compose.ui.geometry.RoundRect
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.CompositingStrategy
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.PathMeasure
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.drawscope.clipPath
import androidx.compose.ui.graphics.drawscope.withTransform
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.util.lerp
import io.issac.pleosandroid.core.cardui.print.printStockEdge
import kotlin.math.PI
import kotlin.math.sin

private data class Shard(val outline: Path, val center: Offset, val flight: Offset, val spin: Float, val delay: Float)

/** Adjacent fragments share their exact crack. The inner graphics layer replays one real print into each shard. */
internal fun Modifier.breakPrint(sequence: ForgeSequence, clock: State<Float>) = graphicsLayer {
    compositingStrategy = CompositingStrategy.ModulateAlpha
    alpha = 1f - easeBetween(.53f, .95f, sequence.release(clock.value))
}.drawWithCache {
    val w = size.width
    val h = size.height
    val strike = Offset(w * FRACTURE_X, h * FRACTURE_Y)
    val printEdge = Path().apply {
        if (sequence.before >= 15) {
            printStockEdge(size, sequence.before / 5, 1f)
        } else {
            addRoundRect(RoundRect(Rect(Offset.Zero, size), CornerRadius(w * 21f / 400f)))
        }
    }
    val edges = listOf(
        Offset(0f, 0f),
        Offset(w * .47f, 0f),
        Offset(w, 0f),
        Offset(w, h * .55f),
        Offset(w, h),
        Offset(w * .58f, h),
        Offset(0f, h),
        Offset(0f, h * .36f),
    )
    val rays = edges.mapIndexed { index, end ->
        val direction = end - strike
        val kink =
            Offset(-direction.y, direction.x) / direction.getDistance() * w * (if (index % 2 == 0) .045f else -.054f)
        listOf(
            strike,
            strike + direction * .29f + kink,
            strike + direction * .55f - kink * .75f,
            strike + direction * .72f + kink * .36f,
            end,
        )
    }
    val cracks = rays.map { points ->
        PathMeasure().apply { setPath(pathOf(points, closed = false), false) }
    }
    val branches = rays.mapIndexedNotNull { i, ray ->
        if (i % 2 != 0) return@mapIndexedNotNull null
        val branch = ray[2]
        val direction = ray.last() - strike
        val turn = Offset(-direction.y, direction.x) * .12f
        val fork =
            pathOf(listOf(branch, branch + direction * .09f + turn, branch + direction * .19f + turn * .82f), false)
        PathMeasure().apply { setPath(fork, false) }
    }
    val shards = rays.indices.map { i ->
        val next = (i + 1) % rays.size
        val center = (strike + edges[i] + edges[next]) / 3f
        val direction = center - strike
        Shard(
            pathOf(rays[i] + rays[next].asReversed(), closed = true),
            center,
            Offset(direction.x * .75f, direction.y * .62f),
            if (i % 2 == 0) 21f + i else -18f - i,
            .16f + .018f * (i * 3 % 5),
        )
    }
    val forkScars = Path().also { path -> branches.forEach { it.getSegment(0f, it.length, path, true) } }
    val line = Path()
    val knife = Stroke((w * .0038f).coerceAtLeast(1f))
    val ember = Stroke(w * .019f)
    val rim = Stroke(w * .006f)
    val pearl = Color(0xFFFFEAD1)
    val red = Color(0xFFE85A70)
    val flash = Brush.radialGradient(
        0f to pearl,
        .23f to red.copy(alpha = .73f),
        1f to Color.Transparent,
        center = strike,
        radius = w * .29f,
    )
    onDrawWithContent {
        val u = sequence.release(clock.value)
        if (u <= 0f) {
            drawContent()
            return@onDrawWithContent
        }
        if (u <= .16f) {
            drawContent()
            line.reset()
            cracks.forEachIndexed { i, crack ->
                crack.getSegment(0f, crack.length * easeBetween(.018f * (i % 3), .155f, u), line, true)
            }
            branches.forEach { branch ->
                branch.getSegment(0f, branch.length * easeBetween(.087f, .16f, u), line, true)
            }
            clipPath(printEdge) {
                drawPath(line, red.copy(alpha = .58f), style = ember)
                drawPath(line, pearl, style = knife)
                drawCircle(flash, w * .29f, strike, alpha = .85f * pulse(u, .02f, .08f, .16f))
            }
        } else {
            for (shard in shards) {
                val t = ((u - shard.delay) / (.95f - shard.delay)).coerceIn(0f, 1f)
                val impulse = 1f - (1f - t) * (1f - t)
                val turn = sin(PI.toFloat() * t)
                withTransform({
                    translate(shard.flight.x * impulse, shard.flight.y * impulse - h * .055f * turn + h * .29f * t * t)
                    rotate(shard.spin * impulse, shard.center)
                    scale(1f - .26f * turn - .13f * t, lerp(1f, .88f, t), shard.center)
                }) {
                    clipPath(printEdge) {
                        clipPath(shard.outline) {
                            this@onDrawWithContent.drawContent()
                            drawPath(shard.outline, Color(0xFF201429).copy(alpha = .66f * t))
                            val heat = 1f - easeBetween(.02f, .46f, t)
                            if (heat > 0f) drawPath(forkScars, pearl.copy(alpha = heat), style = knife)
                            drawPath(shard.outline, red.copy(alpha = .82f * (1f - t)), style = rim)
                            drawPath(shard.outline, pearl.copy(alpha = 1f - t), style = knife)
                        }
                    }
                }
            }
        }
    }
}.graphicsLayer()

private fun pathOf(points: List<Offset>, closed: Boolean) = Path().apply {
    moveTo(points.first().x, points.first().y)
    for (i in 1 until points.size) lineTo(points[i].x, points[i].y)
    if (closed) close()
}
