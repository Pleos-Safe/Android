package io.issac.pleosandroid.feature.badges.album

import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.graphics.Color as Pixel
import android.graphics.Rect
import androidx.activity.ComponentActivity
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
import androidx.compose.ui.layout.onGloballyPositioned
import androidx.compose.ui.layout.positionInWindow
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.junit4.createAndroidComposeRule
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.unit.dp
import androidx.test.platform.app.InstrumentationRegistry
import io.issac.pleosandroid.core.designsystem.theme.PleosTextStyles
import io.issac.pleosandroid.core.designsystem.theme.PleosTheme
import io.issac.pleosandroid.core.domain.cards.CardCopy
import io.issac.pleosandroid.core.domain.cards.CardGrade
import io.issac.pleosandroid.core.domain.cards.CardPrint
import io.issac.pleosandroid.core.domain.cards.CopyStatus
import io.issac.pleosandroid.core.domain.cards.ForgeOutcome
import io.issac.pleosandroid.core.domain.cards.PackType
import io.issac.pleosandroid.core.domain.cards.PrintGroup
import io.issac.pleosandroid.core.domain.cards.ReceivedPack
import io.issac.pleosandroid.feature.badges.R
import io.issac.pleosandroid.feature.badges.album.forge.ForgeSequence
import io.issac.pleosandroid.feature.badges.album.forge.annealPrint
import io.issac.pleosandroid.feature.badges.album.forge.chargeSurface
import java.io.File
import java.time.Instant
import kotlin.math.PI
import kotlin.math.abs
import kotlin.math.roundToInt
import kotlin.math.sin
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test

class RegionalPrintCoatingTest {
    @get:Rule val compose = createAndroidComposeRule<ComponentActivity>()
    private val screen = Rect()
    private val lamp = mutableStateOf(-4f)
    private val tilt = mutableStateOf(Offset.Zero)
    private val pack = ReceivedPack("pack", PackType.Busan, "방문 보상", Instant.EPOCH)
    private val art = CardPrint(
        "busan-bukangi", "북항의 부캉이", PackType.Busan, PrintGroup.Specialty, CardGrade.Rare,
        "print_busan_bukangi_base", "print_busan_bukangi_variant", "부캉이", "BUSAN / 009",
    )
    private fun print(level: Int) =
        CardCopy("copy", art, false, level, level, CopyStatus.Owned, 0, pack, Instant.EPOCH, true)

    private fun Modifier.captureBounds() = onGloballyPositioned {
        val pt = it.positionInWindow()
        screen.set(
            pt.x.roundToInt(),
            pt.y.roundToInt(),
            pt.x.roundToInt() + it.size.width,
            pt.y.roundToInt() + it.size.height,
        )
    }

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

    @Test fun hyundaiEmblemIsPrintedOnTheSameSurfaceShownAtTheStartOfTheTear() {
        val context = InstrumentationRegistry.getInstrumentation().targetContext
        val special = pack.copy(packType = PackType.Hyundai)
        var tearing by mutableStateOf(false)
        lateinit var printed: Bitmap
        compose.setContent {
            printed = rememberPackFace(special)
            Box(Modifier.requiredSize(240.dp, 360.dp).captureBounds().background(Color.Black)) {
                if (tearing) {
                    PackTear(special, Modifier.fillMaxSize()) { 0f }
                } else {
                    PackArtwork(special, Modifier.fillMaxSize())
                }
                Box(
                    Modifier.align(
                        Alignment.BottomEnd,
                    ).size(8.dp).background(Color(frameMarker(0, if (tearing) 1f else -1f))),
                )
            }
        }
        val still = captureLatched(frameMarker(0, -1f))
        compose.runOnIdle { tearing = true }
        val tear = captureLatched(frameMarker(0, 1f))
        val original = BitmapFactory.decodeResource(context.resources, R.drawable.pack_hyundai)
        try {
            var total = 0L
            var stamped = 0
            for (y in 0 until printed.height step 2) {
                for (x in 0 until printed.width step 2) {
                    if (Pixel.red(printed.getPixel(x, y)) - Pixel.red(original.getPixel(x, y)) < 60) continue
                    stamped++
                    val px = x * still.width / printed.width
                    val py = y * still.height / printed.height
                    total += abs(Pixel.red(still.getPixel(px, py)) - Pixel.red(tear.getPixel(px, py)))
                }
            }
            assertTrue("The symbol must cover visible ink on the wrapper", stamped > 160)
            assertTrue("The printed symbol must stay in place when the mesh takes over", total < stamped * 6L)
            if (InstrumentationRegistry.getArguments().getString("packCapture") == "true") {
                File(context.filesDir, "hyundai-printed-pack.png").outputStream().use {
                    printed.compress(Bitmap.CompressFormat.PNG, 100, it)
                }
            }
        } finally {
            original.recycle()
            still.recycle()
            tear.recycle()
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

    private fun frameMarker(level: Int, yaw: Float) = Pixel.rgb(45 + level * 4, if (yaw < 0f) 52 else 212, 138)

    private fun captureLatched(expected: Int): Bitmap {
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
