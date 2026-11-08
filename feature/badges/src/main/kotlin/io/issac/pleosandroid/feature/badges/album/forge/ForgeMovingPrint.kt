package io.issac.pleosandroid.feature.badges.album.forge

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.runtime.Composable
import androidx.compose.runtime.State
import androidx.compose.runtime.derivedStateOf
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.drawWithCache
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Rect
import androidx.compose.ui.geometry.RoundRect
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.PathMeasure
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.drawscope.clipPath
import androidx.compose.ui.graphics.drawscope.translate
import androidx.compose.ui.graphics.drawscope.withTransform
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.semantics.clearAndSetSemantics
import androidx.compose.ui.util.lerp
import io.issac.pleosandroid.core.domain.cards.CardCopy
import io.issac.pleosandroid.core.domain.cards.CopyStatus
import io.issac.pleosandroid.core.domain.cards.ForgeOutcome
import io.issac.pleosandroid.feature.badges.album.CardPrintView
import io.issac.pleosandroid.feature.badges.album.PrintLevelChange
import io.issac.pleosandroid.feature.badges.album.printStockEdge
import kotlin.math.sin

@Composable
internal fun ForgeMovingPrint(
    card: CardCopy,
    seq: ForgeSequence,
    clock: State<Float>,
    light: State<Float>,
    modifier: Modifier,
) {
    val tremor = remember(seq, clock) { derivedStateOf { seq.chargeShake(clock.value) } }
    val rotation = remember(seq, clock) { derivedStateOf { seq.cardRotation(clock.value, tremor.value) } }
    val before = remember(card, seq) {
        val status = if (seq.outcome == ForgeOutcome.Restored) CopyStatus.Trace else CopyStatus.Owned
        card.copy(level = seq.before, status = status)
    }
    val after = remember(card, seq) { card.copy(level = seq.after) }
    val expansion: (() -> Float)? = remember(seq, clock) {
        when {
            seq.expandsArt -> ({ seq.exposure(clock.value) })
            seq.contractsArt -> ({ 1f - seq.exposure(clock.value) })
            else -> null
        }
    }
    // Both faces carry the same moving ink when the reveal mask passes over their lettering.
    val levelChange = remember(seq, clock) {
        if (seq.before == seq.after) {
            null
        } else {
            PrintLevelChange(seq.before, seq.after) { seq.exposure(clock.value) }
        }
    }
    Box(modifier.clearAndSetSemantics {}) {
        Box(
            Modifier.fillMaxSize().graphicsLayer {
                val t = clock.value
                val energy = seq.pressure(t)
                val u = seq.release(t)
                val recoil = pulse(u, 0f, .14f, .48f)
                val shake = tremor.value
                translationX = size.width * .016f * shake.x
                translationY = size.height * (-.031f * energy + .006f * shake.y)
                rotationZ = -.85f * energy + 1.85f * shake.x
                rotationY = rotation.value.x
                rotationX = rotation.value.y
                cameraDistance = 16f * density
                var zoom = 1f - .034f * energy
                var compression = 0f
                when (seq.outcome) {
                    ForgeOutcome.Hold -> {
                        compression = .055f * holdCompression(u)
                        zoom += .064f * holdRebound(u)
                        translationY -= size.height * .018f * holdRebound(u)
                    }

                    ForgeOutcome.Drop -> {
                        val fall = fallDisplacement(u)
                        val lift = pulse(u, 0f, .115f, .29f)
                        translationY += size.height * (.16f * fall - .035f * lift)
                        translationX -= size.width * .055f * fall
                        rotationZ += 8.5f * fall - 2f * lift
                        compression = .045f * landingShock(u)
                        zoom -= .044f * fall
                    }

                    ForgeOutcome.Destroyed -> {
                        val impact = fractureShock(u)
                        val tension = pulse(u, 0f, .10f, .22f)
                        translationX += size.width * .048f * impact * sin(25f * u)
                        rotationZ -= 3.6f * impact
                        zoom += .078f * impact - .046f * tension
                    }

                    ForgeOutcome.GreatSuccess -> {
                        val lift = pulse(u, 0f, .24f, .67f)
                        zoom += .105f * lift
                        translationY -= size.height * .058f * lift
                    }

                    else -> {
                        zoom += (.044f + .054f * seq.power) * recoil
                        translationY -= size.height * .037f * recoil
                    }
                }
                scaleX = zoom + compression * .65f
                scaleY = zoom - compression
            }.chargeSurface(seq, clock),
        ) {
            val print = Modifier.fillMaxSize()
            if (seq.outcome == ForgeOutcome.Destroyed) {
                val keepOld by remember(seq, clock) { derivedStateOf { seq.release(clock.value) < .95f } }
                CardPrintView(
                    after,
                    print.graphicsLayer {
                        alpha = easeBetween(.21f, .69f, seq.release(clock.value))
                    },
                    illumination = light,
                    surfaceRotation = rotation,
                    artExpansion = expansion,
                    levelChange = levelChange,
                )
                if (keepOld) {
                    CardPrintView(
                        before,
                        print.breakPrint(seq, clock),
                        illumination = light,
                        surfaceRotation = rotation,
                    )
                }
            } else if (seq.outcome == ForgeOutcome.Hold) {
                CardPrintView(before, print, illumination = light, surfaceRotation = rotation)
            } else {
                val underneath by remember(seq, clock) { derivedStateOf { seq.exposure(clock.value) < 1f } }
                if (underneath) {
                    CardPrintView(
                        before,
                        print,
                        illumination = light,
                        surfaceRotation = rotation,
                        artExpansion = expansion,
                        levelChange = levelChange,
                    )
                }
                CardPrintView(
                    after,
                    if (seq.etchesFrame) print.annealPrint(seq, clock) else print.exposePrint(seq, clock),
                    illumination = light,
                    surfaceRotation = rotation,
                    artExpansion = expansion,
                    levelChange = levelChange,
                )
            }
            Box(print.incidentLight(seq, clock))
        }
    }
}

private fun Modifier.exposePrint(seq: ForgeSequence, clock: State<Float>): Modifier = drawWithCache {
    val mask = Path()
    val width = size.width
    val height = size.height
    val center = Offset(width * .52f, height * .44f)
    onDrawWithContent {
        val lift = seq.exposure(clock.value)
        if (lift <= 0f) return@onDrawWithContent
        if (lift >= 1f) {
            drawContent()
            return@onDrawWithContent
        }
        mask.reset()
        when {
            seq.finish == ForgeFinish.Beyond -> {
                val radius = height * .88f * lift
                mask.addOval(Rect(center.x - radius, center.y - radius, center.x + radius, center.y + radius))
            }

            seq.outcome == ForgeOutcome.GreatSuccess -> {
                val spread = width * 1.29f * lift
                mask.moveTo(width * .85f - spread, 0f)
                mask.lineTo(width * .85f + spread, 0f)
                mask.lineTo(width * .15f + spread, height)
                mask.lineTo(width * .15f - spread, height)
                mask.close()
            }

            else -> {
                val falling = seq.outcome == ForgeOutcome.Drop
                val seam = if (falling) height * (-.2f + lift * 1.41f) else height * (1.22f - lift * 1.47f)
                val end = if (falling) 0f else height
                mask.moveTo(0f, end)
                mask.lineTo(0f, seam)
                mask.cubicTo(
                    width * .32f,
                    seam - height * .14f,
                    width * .62f,
                    seam + height * .13f,
                    width,
                    seam - height * .08f,
                )
                mask.lineTo(width, end)
                mask.close()
            }
        }
        clipPath(mask) { this@onDrawWithContent.drawContent() }
    }
}

private fun Modifier.incidentLight(seq: ForgeSequence, clock: State<Float>): Modifier = drawWithCache {
    val w = size.width
    val h = size.height
    val round = CornerRadius(w * .052f)
    val aperture = Path().apply { addRoundRect(RoundRect(Rect(0f, 0f, w, h), round)) }
    val bevel = Path()
    val rail = Path().apply { addRoundRect(RoundRect(Rect(w * .012f, w * .012f, w * .988f, h - w * .012f), round)) }
    val measure = PathMeasure().apply { setPath(rail, true) }
    val stroke = Stroke((w * .007f).coerceAtLeast(1f), cap = StrokeCap.Round)
    val outline = Path()
    val neutral = Color(0xFFC6F8F5)
    val warm = Color(0xFFFFE9B3)
    val pearl = Brush.linearGradient(
        listOf(
            Color.Transparent,
            seq.accent.copy(alpha = .14f),
            Color.White,
            seq.secondary.copy(alpha = .65f),
            Color.Transparent,
        ),
        Offset(-w * .22f, 0f),
        Offset(w * .22f, h * .12f),
    )
    val seam = Path()
    val lens = Brush.radialGradient(
        listOf(Color.White, neutral.copy(alpha = .75f), Color.Transparent),
        Offset.Zero,
        w * .23f,
    )
    val releaseGlow = Brush.radialGradient(
        0f to Color.White,
        .15f to warm.copy(alpha = .92f),
        .40f to seq.accent.copy(alpha = .48f),
        1f to Color.Transparent,
        center = Offset.Zero,
        radius = 1f,
    )
    val ice = Brush.verticalGradient(
        0f to Color.Transparent,
        .42f to neutral.copy(alpha = .14f),
        .70f to neutral.copy(alpha = .54f),
        .76f to Color.White.copy(alpha = .77f),
        1f to Color.Transparent,
        startY = -h * .12f,
        endY = h * .12f,
    )
    val fallingFoil = Brush.verticalGradient(
        0f to Color.Transparent,
        .43f to seq.secondary.copy(alpha = .22f),
        .74f to seq.accent.copy(alpha = .60f),
        .87f to warm.copy(alpha = .85f),
        1f to Color.Transparent,
        startY = -h * .18f,
        endY = h * .035f,
    )
    onDrawBehind {
        val t = clock.value
        val u = seq.release(t)
        val reveal = seq.exposure(t)
        val expanded = when {
            seq.expandsArt -> reveal
            seq.contractsArt -> 1f - reveal
            seq.before >= 15 -> 1f
            else -> 0f
        }
        val edge = if (expanded > 0f) {
            bevel.printStockEdge(size, (if (reveal < .5f) seq.before else seq.after) / 5, expanded)
            bevel
        } else {
            aperture
        }
        val flare = pulse(u, 0f, .085f, .35f)
        if (flare > .002f) {
            val fracture = if (seq.outcome == ForgeOutcome.Destroyed) easeBetween(0f, .085f, u) else 0f
            val radius = w * (.10f + .75f * easeBetween(0f, .35f, u))
            val brightness = if (seq.outcome == ForgeOutcome.Hold) .65f else .91f
            clipPath(edge) {
                withTransform({
                    translate(w * lerp(.51f, FRACTURE_X, fracture), h * lerp(.49f, FRACTURE_Y, fracture))
                    scale(radius, radius * .76f, Offset.Zero)
                }) {
                    drawCircle(releaseGlow, 1f, Offset.Zero, alpha = brightness * flare)
                }
            }
        }
        if (seq.outcome == ForgeOutcome.Destroyed) {
            val ember = pulse(u, .43f, .67f, .97f)
            if (ember > .001f) {
                clipPath(edge) {
                    val drift = easeBetween(.43f, .94f, u)
                    val radius = w * (.29f - .16f * drift)
                    withTransform({
                        translate(w * lerp(FRACTURE_X, .48f, drift), h * lerp(FRACTURE_Y, .64f, drift))
                        scale(radius, radius * .68f, Offset.Zero)
                    }) { drawCircle(releaseGlow, 1f, Offset.Zero, alpha = .55f * ember) }
                }
            }
            return@onDrawBehind
        }
        if (u <= 0f) return@onDrawBehind
        val shine = pulse(u, .03f, .27f, .88f)
        if (shine <= 0f) return@onDrawBehind
        clipPath(edge) {
            when {
                seq.etchesFrame -> Unit

                // The hot seam is part of the actual reveal mask.
                seq.outcome == ForgeOutcome.Hold -> {
                    val press = holdCompression(u)
                    val rebound = holdRebound(u)
                    val gap = .39f - .37f * easeBetween(.01f, .27f, u) + .55f * easeBetween(.27f, .94f, u)
                    for (side in 0..1) {
                        val sign = if (side == 0) -1f else 1f
                        withTransform({
                            translate(w * .5f, h * (.49f + sign * gap))
                            rotate(-13f + 11f * press - 9f * rebound, Offset.Zero)
                            scale(1f, sign, Offset.Zero)
                        }) {
                            drawRect(
                                ice,
                                Offset(-w * .77f, -h * .12f),
                                androidx.compose.ui.geometry.Size(w * 1.54f, h * .24f),
                                alpha = .72f * press + .54f * rebound,
                            )
                        }
                    }
                }

                seq.outcome == ForgeOutcome.Drop -> {
                    val y = h * (-.20f + reveal * 1.41f)
                    translate(0f, y) {
                        seam.reset()
                        seam.moveTo(0f, 0f)
                        seam.cubicTo(w * .32f, -h * .14f, w * .62f, h * .13f, w, -h * .08f)
                        drawPath(seam, warm.copy(alpha = .78f * shine), style = stroke)
                        seam.lineTo(w, -h * .30f)
                        seam.lineTo(0f, -h * .30f)
                        seam.close()
                        drawPath(seam, fallingFoil, alpha = .87f * shine)
                    }
                    val contact = landingShock(u)
                    drawPath(rail, seq.accent.copy(alpha = .65f * contact), style = stroke)
                }

                seq.finish == ForgeFinish.Beyond -> {
                    val radius = h * .88f * reveal
                    val center = Offset(w * .52f, h * .44f)
                    drawCircle(seq.accent.copy(alpha = .12f * shine), radius, center, style = Stroke(w * .083f))
                    drawCircle(warm.copy(alpha = .87f * shine), radius, center, style = stroke)
                }

                seq.outcome == ForgeOutcome.GreatSuccess -> {
                    for (sign in listOf(-1f, 1f)) {
                        val x = w * (.85f + sign * 1.29f * reveal)
                        val a = Offset(x, 0f)
                        val b = Offset(x - w * .70f, h)
                        drawLine(seq.accent.copy(alpha = .18f * shine), a, b, w * .074f)
                        drawLine(warm.copy(alpha = .90f * shine), a, b, w * .008f)
                    }
                }

                else -> {
                    val y = h * (1.22f - reveal * 1.47f)
                    seam.reset()
                    seam.moveTo(0f, y)
                    seam.cubicTo(w * .32f, y - h * .14f, w * .62f, y + h * .13f, w, y - h * .08f)
                    drawPath(seam, seq.accent.copy(alpha = .17f * shine), style = Stroke(w * .068f))
                    drawPath(seam, Color.White.copy(alpha = .88f * shine), style = stroke)
                }
            }
            if (seq.finish != ForgeFinish.Print) {
                outline.reset()
                val metal = easeBetween(.04f, .76f, u)
                val length = measure.length
                measure.getSegment(0f, length * metal, outline, true)
                drawPath(outline, warm.copy(alpha = .25f * shine), style = Stroke(w * .022f))
                drawPath(outline, Color.White.copy(alpha = .94f * shine), style = stroke)
                val glint = measure.getPosition(length * metal)
                translate(glint.x, glint.y) {
                    drawCircle(lens, w * .23f, Offset.Zero, alpha = .76f * shine)
                }
            }
            if (seq.rising || seq.outcome == ForgeOutcome.Restored) {
                val pass = easeBetween(.20f, 1f, u)
                translate(lerp(-w * .64f, w * 1.50f, pass), 0f) {
                    drawRect(
                        pearl,
                        Offset(-w * .8f, 0f),
                        androidx.compose.ui.geometry.Size(w * 1.6f, h),
                        alpha =
                            shine * (.28f + .25f * seq.power),
                    )
                }
            }
        }
    }
}
