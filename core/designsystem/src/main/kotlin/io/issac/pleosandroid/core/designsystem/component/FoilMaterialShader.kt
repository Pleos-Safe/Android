package io.issac.pleosandroid.core.designsystem.component

import android.graphics.Bitmap
import android.graphics.BitmapShader
import android.graphics.LinearGradient
import android.graphics.Matrix
import android.graphics.RectF
import android.graphics.RenderEffect
import android.graphics.RuntimeShader
import android.graphics.Shader
import android.os.Build
import androidx.annotation.RequiresApi
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.geometry.boundingRect
import androidx.compose.ui.graphics.asComposeRenderEffect

/** Shades the composed face in place, keeping its artwork, frame and native text as the source. */
@RequiresApi(Build.VERSION_CODES.TIRAMISU)
internal class FoilMaterialShader(stock: CardStock, material: BlackLabel, spot: Bitmap?) {
    private val shader = RuntimeShader(FOIL_MATERIAL_AGSL).apply {
        val art = stock.artwork
        val bezel = stock.picture ?: art.boundingRect
        setFloatUniform("uGrade", material.level.toFloat())
        setFloatUniform("uStrength", FOIL_REFLECTION_STRENGTH)
        setFloatUniform("uArt", art.left, art.top, art.right, art.bottom)
        setFloatUniform(
            "uArtCorners",
            art.topLeftCornerRadius.x,
            art.topRightCornerRadius.x,
            art.bottomRightCornerRadius.x,
            art.bottomLeftCornerRadius.x,
        )
        setFloatUniform("uBezel", bezel.left, bezel.top, bezel.right, bezel.bottom)
        if (stock == CardStock.Safety) {
            setFloatUniform("uCrest", 283f, 15f, 318f, 52f)
        } else {
            setFloatUniform("uCrest", 159f, 17f, 201f, 62f)
        }
        setFloatUniform(
            "uRuleY",
            when (stock) {
                CardStock.Place -> 481f
                CardStock.Postcard -> 480f
                CardStock.Safety -> 463f
            },
        )
        val mask = if (spot == null) {
            LinearGradient(0f, 0f, 1f, 1f, 0, 0, Shader.TileMode.CLAMP)
        } else {
            BitmapShader(spot, Shader.TileMode.CLAMP, Shader.TileMode.CLAMP).apply {
                setLocalMatrix(
                    Matrix().apply {
                        setRectToRect(
                            RectF(0f, 0f, spot.width.toFloat(), spot.height.toFloat()),
                            RectF(art.left, art.top, art.right, art.bottom),
                            Matrix.ScaleToFit.FILL,
                        )
                    },
                )
            }
        }
        setInputShader("uSpot", mask)
    }

    fun effect(size: Size, angle: Float): androidx.compose.ui.graphics.RenderEffect {
        shader.setFloatUniform("uSize", size.width, size.height)
        shader.setFloatUniform("uAngle", angle.coerceIn(-MAX_LIGHT_ANGLE, MAX_LIGHT_ANGLE))
        // RenderEffect snapshots the shader; refreshing it here updates uniforms without recomposing the print.
        return RenderEffect.createRuntimeShaderEffect(shader, "uBase").asComposeRenderEffect()
    }
}
