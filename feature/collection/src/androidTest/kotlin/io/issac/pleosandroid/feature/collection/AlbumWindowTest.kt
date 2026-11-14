package io.issac.pleosandroid.feature.collection

import android.graphics.Bitmap
import android.graphics.Rect
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.requiredSize
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.layout.onGloballyPositioned
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.assertIsEnabled
import androidx.compose.ui.test.assertIsNotEnabled
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onNodeWithContentDescription
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.compose.ui.unit.dp
import androidx.test.platform.app.InstrumentationRegistry
import io.issac.pleosandroid.core.designsystem.theme.PleosTheme
import io.issac.pleosandroid.core.domain.cards.CardCopy
import io.issac.pleosandroid.core.domain.cards.CardGrade
import io.issac.pleosandroid.core.domain.cards.CardPrint
import io.issac.pleosandroid.core.domain.cards.CopyStatus
import io.issac.pleosandroid.core.domain.cards.PackType
import io.issac.pleosandroid.core.domain.cards.PrintGroup
import io.issac.pleosandroid.core.domain.cards.ReceivedPack
import io.issac.pleosandroid.feature.collection.pack.PackOpening
import java.io.File
import java.time.Instant
import kotlin.math.roundToInt
import org.junit.Assert.assertEquals
import org.junit.Rule
import org.junit.Test

class AlbumWindowTest {
    @get:Rule val compose = createComposeRule()
    private val now = Instant.parse("2026-11-07T12:00:00Z")
    private val pack = ReceivedPack("pack", PackType.Busan, "방문 보상", now)
    private val print = CardPrint(
        "busan-bukangi", "북항의 부캉이", PackType.Busan, PrintGroup.Specialty, CardGrade.Rare,
        "print_busan_bukangi_base", "print_busan_bukangi_variant", "부캉이", "BUSAN / 009",
    )
    private val card = CardCopy("copy", print, true, 25, 25, CopyStatus.Owned, 0, pack, now, true)

    @Test fun sealedPackNeedsTwoDistinctActionsAndKeepsTheFrontHiddenDuringTheTear() {
        var copy by mutableStateOf<CardCopy?>(null)
        val actions = mutableListOf<CollectionIntent>()
        val screen = Rect()
        compose.setContent {
            Box(
                Modifier.requiredSize(1000.dp, 610.dp).testTag("viewport").onGloballyPositioned {
                    val origin = it.localToScreen(Offset.Zero)
                    screen.set(
                        origin.x.roundToInt(),
                        origin.y.roundToInt(),
                        origin.x.roundToInt() + it.size.width,
                        origin.y.roundToInt() + it.size.height,
                    )
                },
            ) {
                PleosTheme {
                    PackOpening(pack, copy, false, { intent ->
                        actions += intent
                        when (intent) {
                            is CollectionIntent.Open ->
                                copy =
                                    card.copy(seen = false, variant = false, level = 0, highest = 0)

                            is CollectionIntent.Reveal -> copy = copy?.copy(seen = true)

                            else -> error("Unexpected action")
                        }
                    }, {}, {})
                }
            }
        }
        compose.waitUntil(15_000) {
            runCatching { compose.onNodeWithTag("album-open-seal").assertIsDisplayed() }.isSuccess
        }
        compose.mainClock.autoAdvance = false
        val export = InstrumentationRegistry.getArguments().getString("capturePackMotion") == "true"
        var frame = 0
        fun advance(steps: Int) {
            if (export) {
                repeat(steps) {
                    compose.mainClock.advanceTimeBy(48)
                    val name = "pack-%03d.png".format(java.util.Locale.ROOT, frame++)
                    compose.waitForIdle()
                    captureScreen(name, screen)
                }
            } else {
                compose.mainClock.advanceTimeBy(48L * steps)
            }
        }
        val seal = compose.onNodeWithTag("album-open-seal")
        val flip = compose.onNodeWithTag("album-flip")
        val front = compose.onNodeWithContentDescription(card.print.title, substring = true)
        advance(9)
        assertEquals(emptyList<CollectionIntent>(), actions)
        flip.assertDoesNotExist()
        seal.performClick()
        advance(9)
        seal.assertIsNotEnabled()
        flip.assertDoesNotExist()
        compose.onNodeWithText(card.print.title).assertDoesNotExist()
        advance(44)
        flip.assertIsEnabled()
        front.assertDoesNotExist()
        assertEquals(listOf(CollectionIntent.Open(pack.id)), actions)
        flip.performClick()
        advance(5)
        front.assertDoesNotExist()
        assertEquals(listOf(CollectionIntent.Open(pack.id)), actions)
        advance(6)
        front.assertExists()
        advance(20)
        compose.onNodeWithTag("album-show-copy").assertIsEnabled()
        assertEquals(listOf(CollectionIntent.Open(pack.id), CollectionIntent.Reveal(card.id)), actions)
    }

    // Window PixelCopy depends on a fresh surface buffer. For a paused animation clock the
    // display screenshot also captures consecutive identical frames, including in car split screen.
    private fun captureScreen(name: String, region: Rect) {
        val instrumentation = InstrumentationRegistry.getInstrumentation()
        val display = checkNotNull(instrumentation.uiAutomation.takeScreenshot())
        try {
            val bitmap = Bitmap.createBitmap(display, region.left, region.top, region.width(), region.height())
            try {
                File(instrumentation.targetContext.filesDir, name).outputStream().use {
                    bitmap.compress(Bitmap.CompressFormat.PNG, 100, it)
                }
            } finally {
                if (bitmap !== display) bitmap.recycle()
            }
        } finally {
            display.recycle()
        }
    }
}
