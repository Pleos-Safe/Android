package io.issac.pleosandroid.feature.collection

import android.accessibilityservice.AccessibilityServiceInfo
import android.graphics.Bitmap
import android.graphics.Rect
import android.os.ParcelFileDescriptor
import android.os.SystemClock
import androidx.activity.ComponentActivity
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.requiredSize
import androidx.compose.runtime.getValue
import androidx.compose.runtime.key
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.layout.onGloballyPositioned
import androidx.compose.ui.layout.positionInWindow
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.assertIsEnabled
import androidx.compose.ui.test.junit4.createAndroidComposeRule
import androidx.compose.ui.test.onNodeWithContentDescription
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performScrollTo
import androidx.compose.ui.unit.dp
import androidx.test.platform.app.InstrumentationRegistry
import io.issac.pleosandroid.core.cardui.testing.captureForgePrint
import io.issac.pleosandroid.core.designsystem.component.PleosBottomTabs
import io.issac.pleosandroid.core.designsystem.component.PleosHeader
import io.issac.pleosandroid.core.designsystem.component.PleosTab
import io.issac.pleosandroid.core.designsystem.component.pleosShellBackground
import io.issac.pleosandroid.core.designsystem.theme.PleosWindowTheme
import io.issac.pleosandroid.core.domain.cards.AlbumSnapshot
import io.issac.pleosandroid.core.domain.cards.CardCopy
import io.issac.pleosandroid.core.domain.cards.CardGrade
import io.issac.pleosandroid.core.domain.cards.CardPrint
import io.issac.pleosandroid.core.domain.cards.CardRules
import io.issac.pleosandroid.core.domain.cards.CopyStatus
import io.issac.pleosandroid.core.domain.cards.ForgeOutcome
import io.issac.pleosandroid.core.domain.cards.ForgeReceipt
import io.issac.pleosandroid.core.domain.cards.PackType
import io.issac.pleosandroid.core.domain.cards.PrintGroup
import io.issac.pleosandroid.core.domain.cards.ReceivedPack
import io.issac.pleosandroid.feature.collection.detail.CardDetails
import io.issac.pleosandroid.feature.collection.pack.PackOpening
import io.issac.pleosandroid.feature.collection.pack.PackShelf
import io.issac.pleosandroid.feature.collection.shelf.CollectionShelfScreen
import java.io.File
import java.time.Instant
import kotlin.math.roundToInt
import org.junit.Assert.assertEquals
import org.junit.Assume.assumeTrue
import org.junit.Rule
import org.junit.Test

/** Real collection UI with a declared Rare draw and successful-level fixtures; no user DB writes. */
class CollectionFlowCaptureTest {
    @get:Rule val compose = createAndroidComposeRule<ComponentActivity>()
    private val screen = Rect()
    private val adjacentDisplay by lazy {
        InstrumentationRegistry.getArguments().getString("captureAdjacentDisplay") == "true"
    }
    private val fullscreenDisplay by lazy {
        InstrumentationRegistry.getArguments().getString("captureFullscreenDisplay") == "true"
    }
    private val displayCapture get() = adjacentDisplay || fullscreenDisplay
    private val sceneDwellMillis by lazy {
        InstrumentationRegistry.getArguments().getString("captureSceneDwellMs")?.toLongOrNull()
            ?.coerceIn(200L, 1_500L) ?: if (fullscreenDisplay) 500L else 200L
    }
    private var width by mutableStateOf(1216.dp)
    private var scene by mutableStateOf(Scene.Inbox)
    private var entry by mutableStateOf(0)
    private var copy by mutableStateOf<CardCopy?>(null)
    private val now = Instant.parse("2026-11-12T03:00:00Z")
    private val pack = ReceivedPack("fixture-visit-busan", PackType.Busan, "방문 보상", now, "해운대 해변", "fixture-trip")
    private val print = CardPrint(
        "busan-bukangi", "북항의 부캉이", PackType.Busan, PrintGroup.Specialty, CardGrade.Rare,
        "print_busan_bukangi_base", "print_busan_bukangi_variant", "부캉이", "BUSAN / 009", "KR-26",
    )

    @Test
    fun captureVisitPackAndCardStages() {
        assumeTrue(InstrumentationRegistry.getArguments().getString("captureCollectionFlow") == "true")
        check(!(adjacentDisplay && fullscreenDisplay)) { "Choose one capture window arrangement." }
        compose.setContent {
            PleosWindowTheme(
                (
                    if (displayCapture) {
                        Modifier.fillMaxSize()
                    } else {
                        Modifier.requiredSize(
                            width,
                            760.dp,
                        )
                    }
                    ).testTag("collection-flow-capture").onGloballyPositioned {
                    val origin = it.positionInWindow()
                    screen.set(
                        origin.x.roundToInt(),
                        origin.y.roundToInt(),
                        origin.x.roundToInt() + it.size.width,
                        origin.y.roundToInt() + it.size.height,
                    )
                },
            ) {
                Column(Modifier.fillMaxSize().pleosShellBackground()) {
                    PleosHeader(PleosTab.Collection, {}, {}, restricted = false)
                    key(entry, scene) {
                        val album = album(copy)
                        val content = Modifier.weight(1f).fillMaxSize()
                        when (scene) {
                            Scene.Inbox -> PackShelf(
                                album,
                                {},
                                { scene = Scene.Pack },
                                {},
                                modifier = content,
                            )

                            Scene.Pack -> PackOpening(
                                pack,
                                copy,
                                false,
                                ::send,
                                {},
                                { scene = Scene.Collection },
                                content,
                            )

                            Scene.Collection -> CollectionShelfScreen(
                                album,
                                copy?.id,
                                {},
                                { scene = Scene.Detail },
                                { scene = Scene.Inbox },
                                {},
                                content,
                            )

                            Scene.Detail -> CardDetails(checkNotNull(copy), album, false, {}, {}, {}, {}, content)
                        }
                    }
                    PleosBottomTabs(PleosTab.Collection, {}, restricted = false)
                }
            }
        }

        if (displayCapture) awaitDisplayPlacement()
        val sizes = when {
            adjacentDisplay -> listOf(520.dp)
            fullscreenDisplay -> listOf(1216.dp)
            else -> listOf(1216.dp, 520.dp)
        }
        for (captureWidth in sizes) {
            compose.runOnIdle {
                width = captureWidth
                copy = null
                scene = Scene.Inbox
                entry++
            }
            compose.onNodeWithTag("album-pack-${pack.id}").assertIsDisplayed()
            capture("01-visit-pack-inbox")
            compose.onNodeWithTag("album-pack-${pack.id}").performClick()
            val seal = compose.onNodeWithTag("album-open-seal")
            capture("02-pack-sealed")
            seal.performScrollTo().assertIsEnabled()
            if (width == 520.dp) capture("02-pack-sealed-actions")

            compose.mainClock.autoAdvance = false
            seal.performClick()
            advanceMotion(960)
            compose.onNodeWithText("부산 컬렉션 팩").performScrollTo()
            capture("03-pack-opening")
            advanceMotion(2_300)
            val flip = compose.onNodeWithTag("album-flip")
            compose.onNodeWithContentDescription("카드 뒷면").assertExists()
            capture("04-card-back")
            flip.performScrollTo().assertIsEnabled()
            if (width == 520.dp) capture("04-card-back-actions")
            flip.performClick()
            if (fullscreenDisplay) advanceMotion(960)
            compose.waitUntil(15_000) {
                compose.mainClock.advanceTimeBy(160)
                runCatching { compose.onNodeWithTag("album-show-copy").assertIsEnabled() }.isSuccess
            }
            assertEquals(CardGrade.Rare, checkNotNull(copy).grade)
            assertEquals(0, checkNotNull(copy).level)
            compose.onNodeWithText("새로운 카드").performScrollTo()
            capture("05-bukangi-revealed-rare-plus0")
            compose.mainClock.autoAdvance = true
            compose.onNodeWithTag("album-show-copy").performScrollTo().performClick()
            compose.onNodeWithContentDescription("북항의 부캉이, Rare, +0").assertExists()
            capture("06-collection-plus0")

            for (level in listOf(0, 1, 10, 25)) {
                compose.runOnIdle {
                    copy = card(level)
                    scene = Scene.Detail
                    entry++
                }
                compose.onNodeWithText("북항의 부캉이  +$level").assertExists()
                assertEquals("Strengthening does not change the base rarity", CardGrade.Rare, checkNotNull(copy).grade)
                capture("07-card-detail-plus$level-top")
                compose.onNodeWithText("획득 장소   해운대 해변").performScrollTo().assertIsDisplayed()
                compose.onNodeWithText("획득 경로   방문 보상").assertExists()
                capture("08-card-detail-plus$level-provenance")
                if (level > 0) {
                    compose.onNodeWithText("+${level - 1} → +$level", substring = true)
                        .performScrollTo().assertIsDisplayed()
                    capture("09-card-detail-plus$level-history")
                }
            }
        }
    }

    private fun advanceMotion(millis: Long) {
        if (!fullscreenDisplay) {
            compose.mainClock.advanceTimeBy(millis)
            return
        }
        var remaining = millis
        while (remaining > 0) {
            val frame = minOf(16L, remaining)
            val started = SystemClock.uptimeMillis()
            compose.mainClock.advanceTimeBy(frame)
            compose.waitForIdle()
            val wait = frame - (SystemClock.uptimeMillis() - started)
            if (wait > 0) SystemClock.sleep(wait)
            remaining -= frame
        }
    }

    private fun send(intent: CollectionIntent) {
        when (intent) {
            is CollectionIntent.Open -> copy = card(0).copy(seen = false)
            is CollectionIntent.Reveal -> copy = copy?.copy(seen = true)
            else -> error("Unexpected capture action: $intent")
        }
    }

    private fun card(level: Int) = CardCopy(
        "fixture-bukangi-copy", print, false, level, level, CopyStatus.Owned, level.toLong(), pack, now, true,
    )

    private fun album(card: CardCopy?): AlbumSnapshot {
        val level = card?.level ?: 0
        val history = (level downTo 1).map { after ->
            ForgeReceipt(
                "fixture-success-$after", checkNotNull(card).id, after - 1, after, ForgeOutcome.Success,
                CardRules.cost(CardGrade.Rare, after - 1), now.plusSeconds(after.toLong()), true, null,
            )
        }
        return AlbumSnapshot(
            20_000L - history.sumOf {
                it.costHalfPoints
            },
            null,
            listOf(pack),
            listOfNotNull(card),
            history,
        )
    }

    private fun capture(name: String) {
        compose.mainClock.advanceTimeByFrame()
        compose.waitForIdle()
        val image = if (displayCapture) {
            checkDisplayPlacement()
            // Keep each scene visible in real time and let SurfaceFlinger present its committed buffer.
            SystemClock.sleep(sceneDwellMillis)
            checkNotNull(InstrumentationRegistry.getInstrumentation().uiAutomation.takeScreenshot())
        } else {
            captureForgePrint(compose.activity.window, screen)
        }
        try {
            val dir =
                File(InstrumentationRegistry.getInstrumentation().targetContext.filesDir, "screenshots/collection-flow")
            check(dir.isDirectory || dir.mkdirs())
            val suffix = when {
                adjacentDisplay -> "-adjacent-display"
                fullscreenDisplay -> "-fullscreen-display"
                else -> ""
            }
            File(dir, "$name-${width.value.toInt()}$suffix.png").outputStream().use {
                check(image.compress(Bitmap.CompressFormat.PNG, 100, it))
            }
        } finally {
            image.recycle()
        }
    }

    /** The controller arranges the real app pane, then creates the ready file. */
    private fun awaitDisplayPlacement() {
        val dir = InstrumentationRegistry.getInstrumentation().targetContext.filesDir
        val ready = File(dir, "adjacent-capture.ready")
        ready.delete()
        val waiting = File(dir, "adjacent-capture.waiting")
        waiting.writeText(
            if (fullscreenDisplay) {
                "Expand the fixture activity to the entire app area."
            } else {
                "Place the fixture activity on the right with built-in navigation on its left."
            },
        )
        try {
            val deadline = SystemClock.uptimeMillis() + 90_000L
            while (!ready.exists() && SystemClock.uptimeMillis() < deadline) SystemClock.sleep(100)
            check(ready.exists()) { "Window placement was not confirmed by the capture controller." }
            checkDisplayPlacement()
        } finally {
            ready.delete()
            waiting.delete()
        }
    }

    private fun checkDisplayPlacement() {
        if (adjacentDisplay) {
            checkAdjacentNavigation()
            return
        }
        val own = Rect()
        InstrumentationRegistry.getInstrumentation().runOnMainSync {
            val decor = compose.activity.window.decorView
            val location = IntArray(2)
            decor.getLocationOnScreen(location)
            own.set(location[0], location[1], location[0] + decor.width, location[1] + decor.height)
        }
        check(own.width() > own.height()) { "Fullscreen capture needs the expanded landscape app pane: $own" }
    }

    private fun checkAdjacentNavigation() {
        val instrumentation = InstrumentationRegistry.getInstrumentation()
        val automation = instrumentation.uiAutomation
        automation.serviceInfo = automation.serviceInfo.apply {
            flags = flags or AccessibilityServiceInfo.FLAG_RETRIEVE_INTERACTIVE_WINDOWS
        }
        val own = Rect()
        instrumentation.runOnMainSync {
            val decor = compose.activity.window.decorView
            val location = IntArray(2)
            decor.getLocationOnScreen(location)
            own.set(location[0], location[1], location[0] + decor.width, location[1] + decor.height)
        }
        val windows = automation.windows.map { window ->
            val bounds = Rect().also(window::getBoundsInScreen)
            window.root?.packageName?.toString() to bounds
        }
        fun isAdjacent(bounds: Rect) = !bounds.isEmpty && bounds.left > 0 && bounds.left < own.left &&
            bounds.right <= own.left + 8 && bounds.top < own.bottom && bounds.bottom > own.top
        if (windows.any { (name, bounds) ->
                name == "ai.umos.maps.android.navigation.app" && isAdjacent(bounds)
            }
        ) {
            return
        }
        // Embedded, unfocused task surfaces can be absent from the accessibility window list.
        val taskState = ParcelFileDescriptor.AutoCloseInputStream(automation.executeShellCommand("am stack list"))
            .bufferedReader().use { it.readText() }
        val boundsPattern = Regex("""bounds=\[(\d+),(\d+)]\[(\d+),(\d+)]""")
        val nativeBounds = taskState.lineSequence()
            .filter { "ai.umos.maps.android.navigation.app/" in it && "visible=true" in it }
            .mapNotNull { line ->
                boundsPattern.find(line)?.groupValues?.drop(1)?.map(String::toInt)?.let {
                    Rect(it[0], it[1], it[2], it[3])
                }
            }.toList()
        check(nativeBounds.any(::isAdjacent)) {
            "Built-in navigation must be visible to the left of the fixture. " +
                "Own=$own, windows=$windows, nativeTasks=$nativeBounds"
        }
    }

    private enum class Scene { Inbox, Pack, Collection, Detail }
}
