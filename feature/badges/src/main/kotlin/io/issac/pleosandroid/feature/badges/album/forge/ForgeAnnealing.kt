package io.issac.pleosandroid.feature.badges.album.forge

import android.graphics.RenderEffect
import android.graphics.RuntimeShader
import android.os.Build
import androidx.annotation.RequiresApi
import androidx.compose.runtime.Composable
import androidx.compose.runtime.State
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clipToBounds
import androidx.compose.ui.draw.drawWithCache
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Rect
import androidx.compose.ui.geometry.RoundRect
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.asComposeRenderEffect
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.drawscope.clipPath
import androidx.compose.ui.graphics.graphicsLayer
import kotlin.math.PI
import kotlin.math.cos
import kotlin.math.sin

/** Composite the new physical face over the old one, with the molten edge at the exact material boundary. */
@Composable
internal fun Modifier.annealPrint(sequence: ForgeSequence, clock: State<Float>): Modifier {
    if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
        val front = remember(sequence) { AnnealingShader(sequence.finish.ordinal) }
        return clipToBounds().graphicsLayer {
            clip = true
            if (size.width > 0f && size.height > 0f) renderEffect = front.effect(size, sequence.exposure(clock.value))
        }
    }
    return canvasAnnealing(sequence, clock)
}

@RequiresApi(Build.VERSION_CODES.TIRAMISU)
private class AnnealingShader(tier: Int) {
    private val shader = RuntimeShader(ANNEALING_AGSL).apply { setFloatUniform("tier", tier.toFloat()) }
    fun effect(size: Size, progress: Float): androidx.compose.ui.graphics.RenderEffect {
        shader.setFloatUniform("resolution", size.width, size.height)
        shader.setFloatUniform("progress", progress)
        return RenderEffect.createRuntimeShaderEffect(shader, "newPrint").asComposeRenderEffect()
    }
}

// Signed distance is shared by the print mask, incandescent core and the cooling interference trail.
// Both endpoints explicitly return the original compositions, even at transparent rounded corners.
private const val ANNEALING_AGSL = """
uniform shader newPrint;
uniform float2 resolution;
uniform float progress;
uniform float tier;

float noiseFront(float s) {
    return .010*sin(22.*s+progress*3.3)+.006*sin(49.*s-progress*3.8)+.0025*sin(107.*s);
}
float bell(float a) { return exp(-a*a); }
half4 main(float2 p) {
    if (p.x<0. || p.y<0. || p.x>=resolution.x || p.y>=resolution.y) return half4(0.);
    half4 incoming=newPrint.eval(p);
    if (progress<=0.) return half4(0.);
    if (progress>=1. || incoming.a<.001) return incoming;
    float2 uv=p/resolution;
    float2 paper=float2(uv.x,uv.y*1.5);
    float dist, along;
    if (tier>3.5) {
        // The upper crest stamps a spreading pool of metal into the plate.
        float2 origin=tier>4.5?float2(.51,.72):float2(.49,.22);
        float2 ray=paper-origin;
        along=atan(ray.y,ray.x);
        float flutes=.017*sin(5.*along+progress*2.)+.009*sin(11.*along-progress*3.);
        dist=mix(-.09,tier>4.5?1.13:1.57,progress)-length(ray)+flutes+noiseFront(along*.34);
    } else if (tier>1.5 && tier<2.5) {
        // Twin heat fronts separate along the prism's inclined grain.
        float groove=uv.x-.86+.67*uv.y+.032*sin(uv.y*9.);
        along=uv.y;
        dist=mix(-.095,1.07,progress)-abs(groove+noiseFront(uv.y));
    } else {
        along=uv.x;
        float slope=(uv.x-.5)*(.34+.085*tier);
        float ripple=.071*sin(5.7*uv.x+2.1*progress)+noiseFront(uv.x);
        dist=paper.y-mix(1.91,-.43,progress)-slope-ripple;
    }
    float aa=max(.0016,1.35/resolution.x);
    float exposed=smoothstep(-aa,aa,dist);
    float heat=pow(clamp(1.-abs(2.*progress-1.),0.,1.),.27);
    float turbulence=.70+.30*sin(19.*along-12.*progress)*sin(7.3*along+4.*progress);
    float hot=bell(dist/(.0070+.0014*tier+aa));
    float edge=bell((dist-.009)/(.023+.002*tier));
    float penumbra=bell((dist+.020)/(.039+.003*tier));
    float wake=max(dist,0.);
    float cooling=exp(-wake/(.062+.014*tier))*smoothstep(0.,.020,dist);
    float ribs=.5+.5*cos(105.*dist+3.*sin(along*12.)-progress*4.);
    float3 spectrum=.64+.34*cos(6.28318*(float3(.02,.68,.35)+dist*2.+along*.30-progress*.55));
    float3 hotInk=mix(float3(1.,.35,.075),float3(1.,.66,.22),turbulence);
    float3 skin=mix(hotInk,spectrum,.11*tier);
    float3 base=float3(incoming.rgb)/max(float(incoming.a),.001);
    half4 neighbour=newPrint.eval(clamp(p+float2(1.35,.85),float2(.5),resolution-.5));
    float3 nearby=float3(neighbour.rgb)/max(float(neighbour.a),.001);
    float relief=smoothstep(.012,.18,length(nearby-base));
    float solder=heat*cooling*relief*(.35+.075*tier);
    base*=1.-.29*cooling;
    base=1.-(1.-base)*(1.-skin*cooling*(.22+.31*ribs));
    base=1.-(1.-base)*(1.-mix(hotInk,float3(1.,.93,.69),.52)*solder);
    float3 rgb=base*exposed;
    float alpha=exposed;
    // A slim dark margin in front gives the contact point contrast on even the light-sand paper.
    float shade=penumbra*heat*.56*(1.-exposed);
    rgb=rgb*(1.-shade)+float3(.19,.063,.048)*shade;
    alpha=alpha+(1.-alpha)*shade;
    float amber=edge*heat*(.78+.18*turbulence);
    rgb=rgb*(1.-amber)+hotInk*amber;
    alpha=alpha+(1.-alpha)*amber;
    float white=hot*heat;
    rgb=rgb*(1.-white)+float3(1.,.99,.84)*white;
    alpha=alpha+(1.-alpha)*white;
    return half4(half3(clamp(rgb,0.,1.))*incoming.a, half(alpha)*incoming.a);
}
"""

/** Same travelling fronts on devices without RuntimeShader. */
private fun Modifier.canvasAnnealing(sequence: ForgeSequence, clock: State<Float>) = drawWithCache {
    val w = size.width
    val h = size.height
    val limit = Path().apply { addRoundRect(RoundRect(Rect(Offset.Zero, size), CornerRadius(w * .052f))) }
    val mask = Path()
    val edge = Path()
    val finish = sequence.finish
    onDrawWithContent {
        val p = sequence.exposure(clock.value)
        if (p <= 0f) return@onDrawWithContent
        if (p >= 1f) {
            drawContent()
            return@onDrawWithContent
        }
        mask.reset()
        edge.reset()
        when {
            finish >= ForgeFinish.Crown -> {
                val cy = if (finish == ForgeFinish.Laurel) h * .48f else h * .15f
                val range = if (finish == ForgeFinish.Laurel) 1.13f else 1.57f
                for (i in 0..80) {
                    val a = i * (2f * PI.toFloat() / 80f)
                    val radius = w * (-.09f + (range + .09f) * p + .017f * sin(5f * a + p * 2f)).coerceAtLeast(0f)
                    val x = w * .50f + cos(a) * radius
                    val y = cy + sin(a) * radius
                    if (i == 0) edge.moveTo(x, y) else edge.lineTo(x, y)
                }
                edge.close()
                mask.addPath(edge)
            }

            finish == ForgeFinish.Prism -> {
                val radius = (-.095f + 1.165f * p).coerceAtLeast(0f) * w
                for (side in 0..1) {
                    for (n in 0..40) {
                        val t = if (side == 0) n / 40f else 1f - n / 40f
                        val x = w * (.86f - .67f * t - .032f * sin(t * 9f)) + (if (side == 0) radius else -radius)
                        if (n == 0) edge.moveTo(x, h * t) else edge.lineTo(x, h * t)
                        if (side == 0 && n == 0) mask.moveTo(x, h * t) else mask.lineTo(x, h * t)
                    }
                }
                mask.close()
            }

            else -> {
                for (i in 0..54) {
                    val x = i / 54f
                    val y =
                        w *
                            (
                                1.91f - 2.34f * p + (x - .5f) * (.34f + .085f * finish.ordinal) + .071f * sin(
                                    5.7f * x + 2.1f * p,
                                ) +
                                    .010f * sin(22f * x + p * 3.3f)
                                )
                    if (i == 0) edge.moveTo(0f, y) else edge.lineTo(w * x, y)
                }
                mask.addPath(edge)
                mask.lineTo(w, h)
                mask.lineTo(0f, h)
                mask.close()
            }
        }
        clipPath(mask) { this@onDrawWithContent.drawContent() }
        val strength = pulse(p, 0f, .12f, 1.14f)
        clipPath(limit) {
            drawPath(edge, Color(0xFFAB603F).copy(alpha = .37f * strength), style = Stroke(w * .083f))
            drawPath(edge, sequence.accent.copy(alpha = .73f * strength), style = Stroke(w * .044f))
            drawPath(edge, Color(0xFFFFBE68).copy(alpha = strength), style = Stroke(w * .024f))
            drawPath(edge, Color(0xFFFFFFDE).copy(alpha = strength), style = Stroke(w * .011f))
        }
    }
}
