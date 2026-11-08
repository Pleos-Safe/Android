package io.issac.pleosandroid.feature.badges.album.forge

import android.graphics.Bitmap
import android.graphics.Paint
import android.graphics.RectF
import android.graphics.RenderEffect
import android.graphics.RuntimeShader
import android.os.Build
import androidx.annotation.RequiresApi
import androidx.compose.runtime.Composable
import androidx.compose.runtime.State
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.drawWithCache
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Rect
import androidx.compose.ui.geometry.RoundRect
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.BlendMode
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.asComposeRenderEffect
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.drawscope.clipPath
import androidx.compose.ui.graphics.drawscope.withTransform
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.graphics.nativeCanvas
import kotlin.math.abs
import kotlin.math.exp
import kotlin.math.sqrt

// Soft light outside the rounded print, with a narrower white core at the metal edge.
private const val HALO_MARGIN = 56f
private val haloBitmap by lazy {
    val width = 206
    val height = 281
    val pixels = IntArray(width * height) { i ->
        val x = (i % width + .5f) * 2f - width
        val y = (i / width + .5f) * 2f - height
        val dx = abs(x) - (150f - 16f)
        val dy = abs(y) - (225f - 16f)
        val outsideX = dx.coerceAtLeast(0f)
        val outsideY = dy.coerceAtLeast(0f)
        val distance = sqrt(outsideX * outsideX + outsideY * outsideY) + maxOf(dx, dy).coerceAtMost(0f) - 16f
        val d2 = distance * distance
        val core = exp(-d2 / 40f)
        val portY = ((if (x < 0f) CHARGE_LEFT_Y else CHARGE_RIGHT_Y) - .5f) * 450f
        val along = (y - portY) / 108f
        val contact = exp(-along * along)
        val penumbra = (.34f + .31f * contact) * exp(-d2 / 360f) + .20f * exp(-d2 / 990f)
        val alpha = ((.76f + .24f * contact) * core + penumbra).coerceAtMost(1f)
        val warm = (.5f + x / 410f + y / 1350f).coerceIn(0f, 1f)
        val red = 110f + 145f * warm
        val green = 211f + 8f * warm
        val blue = 255f - 66f * warm
        fun hot(channel: Float) = (channel + (255f - channel) * core * .82f).toInt()
        android.graphics.Color.argb((255f * alpha).toInt(), hot(red), hot(green), hot(blue))
    }
    Bitmap.createBitmap(pixels, width, height, Bitmap.Config.ARGB_8888)
}

/** The halo and the ink share the print's transform, including its last small recoil. */
@Composable
internal fun Modifier.chargeSurface(seq: ForgeSequence, clock: State<Float>): Modifier {
    val halo = drawWithCache {
        val spread = size.width * (HALO_MARGIN / 300f)
        val dst = RectF(-spread, -spread, size.width + spread, size.height + spread)
        val paint = Paint(Paint.ANTI_ALIAS_FLAG or Paint.FILTER_BITMAP_FLAG)
        val bitmap = haloBitmap
        onDrawBehind {
            val time = clock.value
            val alpha = seq.pressure(time) * (.20f + .80f * easeBetween(.26f, .88f, seq.gathering(time)))
            if (alpha > .002f) {
                paint.alpha = (255f * alpha).toInt()
                drawContext.canvas.nativeCanvas.drawBitmap(bitmap, null, dst, paint)
            }
        }
    }
    if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
        val material = remember { ChargeEmission() }
        return halo.graphicsLayer {
            val time = clock.value
            val energy = seq.pressure(time)
            val lit = energy > .001f && size.width > 0f && size.height > 0f
            clip = lit
            renderEffect = if (lit) {
                material.effect(size, seq.gathering(time), energy)
            } else {
                null
            }
        }
    }
    return halo.drawWithCache {
        val bounds = Path().apply { addRoundRect(RoundRect(Rect(Offset.Zero, size), CornerRadius(size.width * .052f))) }
        fun front(color: Color) = Brush.radialGradient(
            0f to color.copy(alpha = .30f),
            .63f to color.copy(alpha = .24f),
            .83f to color.copy(alpha = .68f),
            .91f to Color.White.copy(alpha = .62f),
            1f to Color.Transparent,
            center = Offset.Zero,
            radius = 1f,
        )
        val frost = front(Color(0xFF9DE8FA))
        val gold = front(Color(0xFFFFDCAB))
        val rim = Stroke(size.width * .011f)
        onDrawWithContent {
            drawContent()
            val time = clock.value
            val energy = seq.pressure(time)
            if (energy <= .001f) return@onDrawWithContent
            val phase = seq.gathering(time)
            clipPath(bounds) {
                for (i in 0..1) {
                    val radius = size.width * (.07f + 1.1f * easeBetween(.06f + .10f * i, .98f, phase))
                    withTransform({
                        translate(size.width * i, size.height * if (i == 0) CHARGE_LEFT_Y else CHARGE_RIGHT_Y)
                        scale(radius, radius * 1.17f, Offset.Zero)
                    }) {
                        drawCircle(if (i == 0) frost else gold, 1f, Offset.Zero, energy, blendMode = BlendMode.Screen)
                    }
                }
                drawPath(bounds, Color(0xFFFFF2DA), energy * phase, style = rim)
            }
        }
    }
}

@RequiresApi(Build.VERSION_CODES.TIRAMISU)
private class ChargeEmission {
    private val shader = RuntimeShader(CHARGE_EMISSION_AGSL)
    fun effect(size: Size, phase: Float, energy: Float): androidx.compose.ui.graphics.RenderEffect {
        shader.setFloatUniform("viewport", size.width, size.height)
        shader.setFloatUniform("charge", phase, energy)
        return RenderEffect.createRuntimeShaderEffect(shader, "print").asComposeRenderEffect()
    }
}

private const val CHARGE_EMISSION_AGSL = """
uniform shader print;
uniform float2 viewport;
uniform float2 charge;
float bell(float d) { return exp(-d*d); }
half4 main(float2 p) {
    if (p.x < 0. || p.y < 0. || p.x >= viewport.x || p.y >= viewport.y) return half4(0.);
    half4 ink = print.eval(p);
    if (ink.a < .002) return ink;
    float3 pigment = float3(ink.rgb) / float(ink.a);
    float brightness = dot(pigment, float3(.23,.66,.11));
    float2 uv = p / viewport;
    float2 paper = uv * float2(1.,1.5);
    float phase = charge.x;
    // The foil grain and real edges in the illustration disturb the light as it travels across the paper.
    float2 tap = max(1.,viewport.x/260.) * float2(1.,.65);
    float3 neighbour = float3(print.eval(clamp(p + tap, float2(.5), viewport-.5)).rgb);
    float3 scattered = float3(print.eval(clamp(p - 2.8*tap, float2(.5), viewport-.5)).rgb);
    float relief = clamp(2.8*length(neighbour - pigment),0.,1.);
    float highlight = max(dot(neighbour,float3(.23,.66,.11)),dot(scattered,float3(.23,.66,.11)));
    float bleed = max(0.,highlight-brightness)*smoothstep(.45,.86,highlight);
    float grain = .025*sin(13.*paper.y + 4.5*paper.x) + .012*sin(24.*paper.x - 6.*paper.y) + .035*(brightness-.5);
    float dl = length((paper-float2(.022,1.5*$CHARGE_LEFT_Y))*float2(1.,.84)) + grain;
    float dr = length((paper-float2(.978,1.5*$CHARGE_RIGHT_Y))*float2(1.,.88)) - grain;
    float left = mix(-.08,1.18,smoothstep(.06,.94,phase)) - dl;
    float right = mix(-.08,1.17,smoothstep(.17,.99,phase)) - dr;
    float flooded = .53*smoothstep(-.08,.18,left) + .47*smoothstep(-.08,.18,right);
    float3 ice = float3(.41,.85,1.);
    float3 gold = float3(1.,.78,.49);
    float3 pearl = float3(1.,.98,.89);
    float3 tint = mix(ice,gold,smoothstep(.13,.93,uv.x+.12*(uv.y-.5)));
    float catchLight = .38 + .33*smoothstep(.24,.84,brightness) + .46*relief;
    float3 front = ice*(.40*bell(left/.15) + .25*bell(left/.034))
                 + gold*(.45*bell(right/.15) + .26*bell(right/.036));
    float fold = paper.y + .23*uv.x + .055*sin(6.*uv.x) - mix(1.47,.63,phase);
    float burnish = bell(fold/.14)*(.18+.62*relief) + .23*bell(fold/.042)*smoothstep(.38,.80,brightness);
    float labels = max(1.-smoothstep(.10,.17,uv.y),smoothstep(.735,.79,uv.y));
    float3 light = (front*catchLight + tint*flooded*(.065+.42*brightness*brightness)
                    + pearl*flooded*(burnish + 1.08*bleed)) * (1.-.69*labels);
    float2 corner = abs(paper-float2(.5,.75)) - float2(.448,.698);
    float inside = .052 - length(max(corner,float2(0.))) - min(max(corner.x,corner.y),0.);
    float rim = exp(-max(0.,inside)/.018) + .30*bell((inside-.023)/.006);
    light += mix(tint,pearl,.49)*rim*(.19+.81*flooded);
    float3 rgb = pigment + (1.-pigment)*clamp(light*charge.y,0.,.90);
    return half4(rgb*float(ink.a),ink.a);
}
"""
