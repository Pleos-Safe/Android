package io.issac.pleosandroid.feature.forge

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
import androidx.compose.ui.test.assertIsNotEnabled
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
import java.io.File
import java.time.Instant
import kotlin.math.roundToInt
import org.junit.Assert.assertEquals
import org.junit.Assume.assumeTrue
import org.junit.Rule
import org.junit.Test

/** Deterministic real-UI states, not random attempts or writes to the user's wallet. */
class ForgeFlowCaptureTest {
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
    private var entry by mutableStateOf(0)
    private val now = Instant.parse("2026-11-12T03:00:00Z")
    private val pack = ReceivedPack("fixture-visit-busan", PackType.Busan, "방문 보상", now, "해운대 해변", "fixture-trip")
    private val print = CardPrint(
        "busan-bukangi", "북항의 부캉이", PackType.Busan, PrintGroup.Specialty, CardGrade.Rare,
        "print_busan_bukangi_base", "print_busan_bukangi_variant", "부캉이", "BUSAN / 009", "KR-26",
    )
    private var fixture by mutableStateOf(ready(0))
    private var restoredWith: String? = null

    @Test
    fun captureForgeLevelsAndOutcomes() {
        assumeTrue(InstrumentationRegistry.getArguments().getString("captureForgeFlow") == "true")
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
                    ).testTag("forge-flow-capture").onGloballyPositioned {
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
                    PleosHeader(PleosTab.Enhance, {}, {}, restricted = false)
                    key(entry) {
                        CardWorkshop(fixture.card, fixture.album, false, ::send, {}, {}, Modifier.weight(1f))
                    }
                    PleosBottomTabs(PleosTab.Enhance, {}, restricted = false)
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
            compose.runOnIdle { width = captureWidth }
            for (level in listOf(1, 10, 25)) {
                show(ready(level - 1))
                compose.onNodeWithTag("forge-attempt").assertIsEnabled()
                capture("01-before-plus$level")
                captureAction("01-before-plus$level", "forge-attempt")

                show(result(level - 1, ForgeOutcome.Success))
                assertEquals(CardGrade.Rare, fixture.card.grade)
                compose.onNodeWithText("강화 성공").assertIsDisplayed()
                capture("02-success-plus$level")
                captureAction("02-success-plus$level", "forge-next")
            }

            show(result(8, ForgeOutcome.GreatSuccess))
            compose.onNodeWithText("대성공").assertIsDisplayed()
            capture("03-great-success-plus8-to10-example")
            captureAction("03-great-success-plus8-to10-example", "forge-next")

            show(result(10, ForgeOutcome.Drop))
            compose.onNodeWithText("강화 하락").assertIsDisplayed()
            capture("04-drop-plus10-to9-example")
            captureAction("04-drop-plus10-to9-example", "forge-next")

            show(ready(25, balance = 0L))
            compose.onNodeWithTag("forge-attempt").assertIsNotEnabled()
            capture("05-insufficient-points-example")
            captureAction("05-insufficient-points-example", "forge-attempt")

            show(legacyTrace())
            compose.onNodeWithText("흔적 복구").assertIsDisplayed()
            capture("06-legacy-trace-restoration")
            compose.onNodeWithText("복구 재료 선택 · 1장").performScrollTo().assertIsEnabled()
            capture("06-legacy-trace-restoration-actions")
            compose.onNodeWithText("복구 재료 선택 · 1장").performClick()
            val restore = compose.onNodeWithText("이 사본으로 복구")
            restore.assertIsNotEnabled()
            compose.onNodeWithText("북항의 부캉이 +0", substring = true).performClick()
            restore.assertIsEnabled()
            capture("07-legacy-restoration-material-selected")
            restore.performClick()
            assertEquals("fixture-material-plus0", restoredWith)
            compose.onNodeWithText("복구 완료").assertIsDisplayed()
            capture("08-legacy-restoration-complete")
            captureAction("08-legacy-restoration-complete", "forge-next")
        }
    }

    private fun show(value: Fixture) {
        compose.runOnIdle {
            fixture = value
            entry++
        }
        compose.onNodeWithContentDescription("북항의 부캉이, Rare, +${value.card.level}").assertExists()
    }

    private fun ready(level: Int, balance: Long = balanceAfter(level)): Fixture {
        val card = card(level)
        return Fixture(card, AlbumSnapshot(balance, null, listOf(pack), listOf(card), historyTo(level)))
    }

    private fun result(before: Int, outcome: ForgeOutcome): Fixture {
        val after = CardRules.nextLevel(before, outcome)
        val receipt = ForgeReceipt(
            "fixture-${outcome.name}-$before", "fixture-bukangi-copy", before, after, outcome,
            CardRules.cost(CardGrade.Rare, before), now.plusSeconds(120), false, null,
        )
        val card = card(after).copy(highest = maxOf(before, after), revision = before + 1L)
        val album = AlbumSnapshot(
            balanceAfter(before) - receipt.costHalfPoints,
            null,
            listOf(pack),
            listOf(card),
            listOf(receipt) + historyTo(before),
        )
        return Fixture(card, album)
    }

    private fun legacyTrace(): Fixture {
        val trace = card(10).copy(status = CopyStatus.Trace, highest = 15)
        val material = card(0).copy(id = "fixture-material-plus0", pack = pack.copy(id = "fixture-material-pack"))
        return Fixture(
            trace,
            AlbumSnapshot(3_500L, null, listOf(pack, material.pack), listOf(trace, material), emptyList()),
        )
    }

    private fun send(intent: ForgeIntent) {
        check(intent is ForgeIntent.Restore) { "Only the legacy restoration fixture accepts an action" }
        restoredWith = intent.material
        val receipt = ForgeReceipt(
            intent.requestId, fixture.card.id, 10, 10, ForgeOutcome.Restored, 0L,
            now.plusSeconds(
                180,
            ),
            false, intent.material,
        )
        val restored = fixture.card.copy(status = CopyStatus.Owned, revision = fixture.card.revision + 1)
        val copies = fixture.album.copies.map {
            when (it.id) {
                restored.id -> restored
                intent.material -> it.copy(status = CopyStatus.Spent)
                else -> it
            }
        }
        fixture = Fixture(restored, fixture.album.copy(copies = copies, history = listOf(receipt)))
        entry++
    }

    private fun card(level: Int) = CardCopy(
        "fixture-bukangi-copy", print, false, level, level, CopyStatus.Owned, level.toLong(), pack, now, true,
    )

    private fun historyTo(level: Int) = (level downTo 1).map { after ->
        ForgeReceipt(
            "fixture-success-$after", "fixture-bukangi-copy", after - 1, after, ForgeOutcome.Success,
            CardRules.cost(CardGrade.Rare, after - 1), now.plusSeconds(after.toLong()), true, null,
        )
    }

    private fun balanceAfter(level: Int) = 20_000L - (0 until level).sumOf { CardRules.cost(CardGrade.Rare, it) }

    private fun captureAction(name: String, tag: String) {
        compose.onNodeWithTag(tag).performScrollTo().assertIsDisplayed()
        if (width == 520.dp) capture("$name-actions")
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
                File(InstrumentationRegistry.getInstrumentation().targetContext.filesDir, "screenshots/forge-flow")
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

    private data class Fixture(val card: CardCopy, val album: AlbumSnapshot)
}
