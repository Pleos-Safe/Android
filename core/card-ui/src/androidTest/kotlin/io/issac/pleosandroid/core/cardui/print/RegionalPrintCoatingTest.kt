package io.issac.pleosandroid.core.cardui.print

import android.graphics.Bitmap
import android.graphics.Color as Pixel
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.requiredSize
import androidx.compose.foundation.layout.size
import androidx.compose.material3.Text
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.graphics.painter.BitmapPainter
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.unit.dp
import androidx.test.platform.app.InstrumentationRegistry
import io.issac.pleosandroid.core.cardui.testing.PrintSurfaceTest
import io.issac.pleosandroid.core.cardui.testing.writeForgeFrame
import io.issac.pleosandroid.core.designsystem.theme.PleosTextStyles
import io.issac.pleosandroid.core.designsystem.theme.PleosTheme
import io.issac.pleosandroid.core.domain.cards.CardCopy
import io.issac.pleosandroid.core.domain.cards.CardGrade
import io.issac.pleosandroid.core.domain.cards.CardPrint
import io.issac.pleosandroid.core.domain.cards.CopyStatus
import io.issac.pleosandroid.core.domain.cards.PackType
import io.issac.pleosandroid.core.domain.cards.PrintGroup
import io.issac.pleosandroid.core.domain.cards.ReceivedPack
import java.time.Instant
import kotlin.math.PI
import kotlin.math.abs
import kotlin.math.sin
import org.junit.Assert.assertTrue
import org.junit.Test

class RegionalPrintCoatingTest : PrintSurfaceTest() {
    private val lamp = mutableStateOf(-4f)
    private val tilt = mutableStateOf(Offset.Zero)
    private val pack = ReceivedPack("pack", PackType.Busan, "방문 보상", Instant.EPOCH)
    private val art = CardPrint(
        "busan-bukangi", "북항의 부캉이", PackType.Busan, PrintGroup.Specialty, CardGrade.Rare,
        "print_busan_bukangi_base", "print_busan_bukangi_variant", "부캉이", "BUSAN / 009",
    )
    private fun print(level: Int) =
        CardCopy("copy", art, false, level, level, CopyStatus.Owned, 0, pack, Instant.EPOCH, true)

    @Test fun fullCoverInkStaysUnderTheMetalOnAllFourSidesAndAcrossTheCutCorners() {
        val ink = Pixel.rgb(238, 18, 153)
        val paper = Pixel.rgb(53, 129, 97)
        val outside = Pixel.rgb(12, 20, 35)
        val bmp = Bitmap.createBitmap(32, 48, Bitmap.Config.ARGB_8888).apply { eraseColor(ink) }
        val painter = BitmapPainter(bmp.asImageBitmap())
        var stage by mutableIntStateOf(3)
        var expansion by mutableStateOf(0f)
        var tick by mutableIntStateOf(0)
        compose.setContent {
            Box(Modifier.requiredSize(240.dp, 360.dp).captureBounds().background(Color(outside))) {
                Box(Modifier.fillMaxSize().clipPrintStock(stage) { expansion }.background(Color(paper))) {
                    FramedPrintArtwork(painter, stage) { expansion }
                }
                Box(Modifier.align(Alignment.BottomEnd).size(8.dp).background(Color(frameMarker(tick, 1f))))
            }
        }
        for (tier in 3..5) {
            for (amount in listOf(0f, .5f, 1f, .5f, 0f)) {
                compose.runOnIdle {
                    stage = tier
                    expansion = amount
                    tick++
                }
                val image = captureLatched(frameMarker(tick, 1f))
                try {
                    fun pixel(x: Int, y: Int) = image.getPixel(image.width * x / 400, image.height * y / 600)
                    fun isInk(x: Int, y: Int) = Pixel.red(pixel(x, y)) > 180 && Pixel.green(pixel(x, y)) < 55
                    assertTrue(isInk(200, 280))
                    if (amount == 1f) {
                        for ((x, y) in listOf(2 to 300, 397 to 300, 200 to 2, 200 to 597, 10 to 10, 389 to 10)) {
                            assertTrue("Stock outside plate $tier at ($x,$y)", Pixel.green(pixel(x, y)) < 50)
                        }
                        for ((x, y) in listOf(8 to 260, 391 to 260, 200 to 7, 200 to 592, 20 to 20, 379 to 579)) {
                            assertTrue("Art beyond rim $tier at ($x,$y)", !isInk(x, y))
                        }
                        assertTrue("Art must extend under the rim", isInk(14, 300) && isInk(200, 14))
                    } else {
                        assertTrue(!isInk(17, 300))
                        assertTrue((amount > 0f) == isInk(24, 300))
                    }
                } finally {
                    image.recycle()
                }
            }
        }
    }

    @Test fun minorChangesOfSurfaceNormalMoveTheCoatingAcrossTheActualArtwork() {
        var level by mutableIntStateOf(0)
        compose.setContent {
            PleosTheme {
                Box(Modifier.requiredSize(276.dp, 414.dp).captureBounds().testTag("print")) {
                    // Hold geometry fixed to measure the optical change, independently of perspective displacement.
                    CardPrintView(print(level), Modifier.fillMaxSize(), illumination = lamp, surfaceRotation = tilt)
                    Box(
                        Modifier.align(
                            Alignment.BottomEnd,
                        ).size(8.dp).background(Color(frameMarker(level, tilt.value.x))),
                    )
                }
            }
        }
        compose.onNodeWithTag("print").assertIsDisplayed()
        val response = (0..25 step 5).map { stage ->
            compose.runOnIdle {
                level = stage
                tilt.value = Offset(-8f, 2f)
            }
            compose.waitForIdle()
            val left = captureLatched(frameMarker(stage, -8f))
            compose.runOnIdle { tilt.value = Offset(8f, -2f) }
            compose.waitForIdle()
            val right = captureLatched(frameMarker(stage, 8f))
            try {
                difference(left, right)
            } finally {
                left.recycle()
                right.recycle()
            }
        }
        android.util.Log.i("PrintOptics", "Artwork response at +0/+5/+10/+15/+20/+25 (luma, chroma) $response")
        assertTrue("Even the base satin should catch the change of angle", response.first().first > 1.0)
        assertTrue(
            "The prism should change hue as well as brightness",
            response.last().second > response.first().second * 1.4,
        )
        assertTrue(
            "The full-bleed finishes should respond more strongly than satin",
            response.drop(3).all {
                it.first >
                    response.first().first * 1.25
            },
        )
    }

    @Test fun freshPrintsShowIncreasingFoilDuringTheFlipAndSettleToTheirOriginalInk() {
        var grade by mutableStateOf(CardGrade.Common)
        var turning by mutableStateOf(false)
        val angle = mutableStateOf(138f)
        var frame by mutableIntStateOf(0)
        compose.setContent {
            PleosTheme {
                Box(Modifier.requiredSize(276.dp, 414.dp).captureBounds()) {
                    // Use the same artwork and surface normal on both exposures to isolate the temporary foil.
                    val card = print(0).copy(print = art.copy(baseGrade = grade))
                    CardPrintView(
                        card,
                        Modifier.fillMaxSize(),
                        illumination = lamp,
                        surfaceRotation = if (turning) null else tilt,
                        flipAngle = if (turning) angle else null,
                    )
                    Box(Modifier.align(Alignment.BottomEnd).size(8.dp).background(Color(frameMarker(frame, 1f))))
                }
            }
        }
        fun capture(stock: CardGrade, degrees: Float, flip: Boolean): Bitmap {
            compose.runOnIdle {
                grade = stock
                angle.value = degrees
                tilt.value = Offset(degrees - 180f, 0f)
                turning = flip
                frame++
            }
            return captureLatched(frameMarker(frame, 1f))
        }
        fun response(stock: CardGrade, degrees: Float): Double {
            val resting = capture(stock, degrees, false)
            val revealed = capture(stock, degrees, true)
            return try {
                difference(resting, revealed).first
            } finally {
                resting.recycle()
                revealed.recycle()
            }
        }
        val exposure = CardGrade.entries.associateWith { response(it, 138f) }
        val paper = exposure.getValue(CardGrade.Common)
        val metal = exposure.getValue(CardGrade.Rare)
        assertTrue(
            "Paper, metal and engraving must read differently: $exposure",
            paper > 1 && metal > paper && exposure.getValue(CardGrade.Epic) > metal,
        )
        assertTrue(
            "Blue engraving must outshine the mint rail: $exposure",
            exposure.getValue(CardGrade.HH) > exposure.getValue(CardGrade.H),
        )
        assertTrue("The settled print must match inspection", response(CardGrade.Epic, 180f) < .5)
    }

    private fun difference(left: Bitmap, right: Bitmap): Pair<Double, Double> {
        var delta = 0.0
        var chroma = 0.0
        var count = 0
        for (y in left.height * 24 / 100..left.height * 65 / 100 step 3) {
            for (x in left.width * 16 / 100..left.width * 84 / 100 step 3) {
                val a = left.getPixel(x, y)
                val b = right.getPixel(x, y)
                val red = Pixel.red(b) - Pixel.red(a)
                val green = Pixel.green(b) - Pixel.green(a)
                val blue = Pixel.blue(b) - Pixel.blue(a)
                delta += (abs(red) + abs(green) + abs(blue)) / 3.0
                chroma += maxOf(red, green, blue) - minOf(red, green, blue)
                count++
            }
        }
        return delta / count to chroma / count
    }

    @Test fun allSixPrintFinishesCanBeInspectedAtTheSameAngle() {
        val captions = listOf("새틴지", "헤어라인 금속", "펄 코팅", "홀로그램 포일", "금박 엠보싱", "프리즘 각인")
        compose.setContent {
            PleosTheme {
                Column(
                    Modifier.requiredSize(
                        1008.dp,
                        644.dp,
                    ).captureBounds().testTag("materials").background(Color(0xFF0B1222)).padding(8.dp),
                    verticalArrangement = Arrangement.spacedBy(8.dp),
                ) {
                    repeat(2) { row ->
                        Row(Modifier.fillMaxWidth().weight(1f), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                            repeat(3) { col ->
                                val tier = row * 3 + col
                                Column(
                                    Modifier.weight(1f).fillMaxHeight().background(Color(0xFF142032)).padding(7.dp),
                                    horizontalAlignment = Alignment.CenterHorizontally,
                                    verticalArrangement = Arrangement.spacedBy(5.dp),
                                ) {
                                    Text(
                                        "+${tier * 5}   ${captions[tier]}",
                                        color = Color(0xFFE4EAF3),
                                        style = PleosTextStyles.meta,
                                    )
                                    Box(Modifier.weight(1f).aspectRatio(2f / 3f), contentAlignment = Alignment.Center) {
                                        CardPrintView(
                                            print(tier * 5),
                                            Modifier.fillMaxSize().graphicsLayer {
                                                rotationY = tilt.value.x
                                                rotationX = tilt.value.y
                                                cameraDistance = 16f * density
                                            },
                                            illumination = lamp,
                                            surfaceRotation = tilt,
                                        )
                                    }
                                }
                            }
                        }
                    }
                }
            }
        }
        compose.onNodeWithTag("materials").assertIsDisplayed()
        if (InstrumentationRegistry.getArguments().getString("coatingFilm") == "true") {
            for (i in 0..94) {
                val t = i / 94f
                compose.runOnIdle {
                    tilt.value = Offset(10f * sin(2.0 * PI * t).toFloat(), 3.5f * sin(PI * t).toFloat())
                }
                compose.waitForIdle()
                writeForgeFrame(compose.activity.window, screen, "forge-optics/materials", i)
            }
        }
    }
}
