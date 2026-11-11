package io.issac.pleosandroid.feature.forge.motion

import android.graphics.Color as Pixel
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.requiredSize
import androidx.compose.foundation.layout.size
import androidx.compose.runtime.mutableStateOf
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.unit.dp
import io.issac.pleosandroid.core.cardui.testing.PrintSurfaceTest
import io.issac.pleosandroid.core.domain.cards.ForgeOutcome
import kotlin.math.abs
import kotlin.math.roundToInt
import org.junit.Assert.assertTrue
import org.junit.Test

class ForgeMaterialTest : PrintSurfaceTest() {
    @Test fun collectedLightBrightensTheStockWithoutStretchingItAcrossTheStage() {
        val sequence = ForgeSequence(9, 10, ForgeOutcome.Success)
        val clock = mutableStateOf(0f)
        val stage = Color(0xFF0A1320)
        val stock = Color(0xFF29485C)
        fun marker() = frameMarker((clock.value * 25).roundToInt(), 1f)
        compose.setContent {
            Box(Modifier.requiredSize(420.dp, 300.dp).captureBounds().background(stage), Alignment.Center) {
                Box(Modifier.size(144.dp, 216.dp).chargeSurface(sequence, clock).background(stock))
                Box(Modifier.align(Alignment.BottomEnd).size(8.dp).background(Color(marker())))
            }
        }
        val peak = (0..1000).first { sequence.gathering(it / 1000f) >= .88f } / 1000f
        for (time in listOf(0f, peak, 1f)) {
            compose.runOnIdle { clock.value = time }
            compose.waitForIdle()
            val image = captureLatched(marker())
            try {
                fun red(x: Float, y: Float) =
                    Pixel.red(image.getPixel((image.width * x).toInt(), (image.height * y).toInt()))
                assertTrue("Paper must not repeat past the rim", red(.08f, .5f) < 16 && red(.92f, .5f) < 16)
                assertTrue("Paper must not repeat vertically", red(.5f, .015f) < 16 && red(.5f, .985f) < 16)
                if (time == peak) {
                    assertTrue("Light reaches the middle of the actual print", red(.5f, .5f) > 62)
                    assertTrue("Light also spills just beyond the left metal edge", red(.315f, .5f) > 20)
                } else {
                    assertTrue("Uncharged paper retains its ink", abs(red(.5f, .5f) - 41) <= 3)
                }
            } finally {
                image.recycle()
            }
        }
    }

    @Test fun theEtchingRevealsTheNewStockOnlyBehindTheHotEdgeAndInsideTheCard() {
        val scan = ForgeSequence(4, 5, ForgeOutcome.Success)
        val clock = mutableStateOf(0f)
        fun marker(t: Float) = Pixel.rgb(75, (32 + 190 * t).roundToInt(), 138)
        compose.setContent {
            Box(
                Modifier.requiredSize(480.dp, 360.dp).background(Color(0xFF101824)).captureBounds().testTag("etch"),
                contentAlignment = Alignment.Center,
            ) {
                Box(Modifier.requiredSize(160.dp, 240.dp).background(Color(0xFF234A6C))) {
                    Box(Modifier.fillMaxSize().annealPrint(scan, clock).background(Color(0xFFF3D7A8)))
                }
                Box(Modifier.align(Alignment.BottomEnd).size(8.dp).background(Color(marker(clock.value))))
            }
        }
        compose.onNodeWithTag("etch").assertIsDisplayed()
        for (target in listOf(0f, .48f, 1f)) {
            val sample = (0..1000).first { scan.exposure(it / 1000f) >= target } / 1000f
            compose.runOnIdle { clock.value = sample }
            compose.waitForIdle()
            val image = captureLatched(marker(sample))
            try {
                for (side in listOf(.12f, .86f)) {
                    for (height in listOf(.08f, .53f, .83f)) {
                        val pixel = image.getPixel((side * image.width).toInt(), (height * image.height).toInt())
                        assertTrue(
                            "A hot edge must not extend the paper into the stage",
                            Pixel.red(pixel) < 32 && Pixel.blue(pixel) < 52,
                        )
                    }
                }
                val upper = Pixel.red(image.getPixel(image.width / 2, image.height / 3))
                val lower = Pixel.red(image.getPixel(image.width / 2, image.height * 2 / 3))
                when (target) {
                    0f -> assertTrue(upper < 80 && lower < 80)
                    1f -> assertTrue(upper > 190 && lower > 190)
                    else -> assertTrue("The old and new stock meet at the advancing edge", upper < 100 && lower > 160)
                }
            } finally {
                image.recycle()
            }
        }
    }
}
