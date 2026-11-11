package io.issac.pleosandroid.feature.collection.pack

import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.graphics.Color as Pixel
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.requiredSize
import androidx.compose.foundation.layout.size
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.dp
import androidx.test.platform.app.InstrumentationRegistry
import io.issac.pleosandroid.core.cardui.testing.PrintSurfaceTest
import io.issac.pleosandroid.core.domain.cards.PackType
import io.issac.pleosandroid.core.domain.cards.ReceivedPack
import io.issac.pleosandroid.feature.collection.R
import java.io.File
import java.time.Instant
import kotlin.math.abs
import org.junit.Assert.assertTrue
import org.junit.Test

class PrintedPackTest : PrintSurfaceTest() {
    private val pack = ReceivedPack("pack", PackType.Busan, "방문 보상", Instant.EPOCH)

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
}
