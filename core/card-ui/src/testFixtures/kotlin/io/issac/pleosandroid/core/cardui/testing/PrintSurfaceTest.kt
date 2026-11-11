package io.issac.pleosandroid.core.cardui.testing

import android.graphics.Bitmap
import android.graphics.Color as Pixel
import android.graphics.Rect
import androidx.activity.ComponentActivity
import androidx.compose.ui.Modifier
import androidx.compose.ui.layout.onGloballyPositioned
import androidx.compose.ui.layout.positionInWindow
import androidx.compose.ui.test.junit4.createAndroidComposeRule
import kotlin.math.abs
import kotlin.math.roundToInt
import org.junit.Rule

abstract class PrintSurfaceTest {
    @get:Rule val compose = createAndroidComposeRule<ComponentActivity>()
    protected val screen = Rect()
    protected fun Modifier.captureBounds() = onGloballyPositioned {
        val pt = it.positionInWindow()
        screen.set(
            pt.x.roundToInt(),
            pt.y.roundToInt(),
            pt.x.roundToInt() + it.size.width,
            pt.y.roundToInt() + it.size.height,
        )
    }

    protected fun frameMarker(level: Int, yaw: Float) = Pixel.rgb(45 + level * 4, if (yaw < 0f) 52 else 212, 138)

    protected fun captureLatched(expected: Int): Bitmap {
        var result: Bitmap? = null
        // Composition being idle does not guarantee that SurfaceFlinger has latched that buffer yet.
        compose.waitUntil(5000) {
            val image = captureForgePrint(compose.activity.window, screen)
            val marker = image.getPixel(image.width - 3, image.height - 3)
            if (abs(Pixel.red(marker) - Pixel.red(expected)) <= 2 &&
                abs(Pixel.green(marker) - Pixel.green(expected)) <= 2
            ) {
                result = image
                true
            } else {
                image.recycle()
                false
            }
        }
        return checkNotNull(result)
    }
}
