package io.issac.pleosandroid.core.designsystem.component

import androidx.compose.foundation.ScrollState
import androidx.compose.foundation.background
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.interaction.PressInteraction
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.requiredWidth
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusProperties
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.toPixelMap
import androidx.compose.ui.input.InputMode
import androidx.compose.ui.input.InputModeManager
import androidx.compose.ui.input.key.Key
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.LocalInputModeManager
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.semantics.SemanticsActions
import androidx.compose.ui.test.ExperimentalTestApi
import androidx.compose.ui.test.assertCountEquals
import androidx.compose.ui.test.assertHeightIsEqualTo
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.assertIsNotEnabled
import androidx.compose.ui.test.assertIsOff
import androidx.compose.ui.test.assertIsOn
import androidx.compose.ui.test.assertIsSelected
import androidx.compose.ui.test.captureToImage
import androidx.compose.ui.test.click
import androidx.compose.ui.test.hasAnyAncestor
import androidx.compose.ui.test.hasTestTag
import androidx.compose.ui.test.hasText
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onAllNodesWithText
import androidx.compose.ui.test.onNodeWithContentDescription
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performImeAction
import androidx.compose.ui.test.performKeyInput
import androidx.compose.ui.test.performSemanticsAction
import androidx.compose.ui.test.performTextInput
import androidx.compose.ui.test.performTouchInput
import androidx.compose.ui.test.pressKey
import androidx.compose.ui.text.TextLayoutResult
import androidx.compose.ui.unit.Density
import androidx.compose.ui.unit.dp
import io.issac.pleosandroid.core.designsystem.modifier.customClickable
import io.issac.pleosandroid.core.designsystem.theme.PleosTheme
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertSame
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test

class PleosComponentsTest {
    @get:Rule val compose = createComposeRule()

    @Test
    fun scrollableActionStartsOneRippleOnDownAndCancelsItWhenScrolling() {
        val source = MutableInteractionSource()
        val presses = mutableListOf<PressInteraction>()
        val scroll = ScrollState(0)
        var clicks = 0
        compose.setContent {
            LaunchedEffect(source) {
                source.interactions.collect { if (it is PressInteraction) presses += it }
            }
            PleosTheme {
                Column(Modifier.size(240.dp, 160.dp).verticalScroll(scroll)) {
                    Box(
                        Modifier.size(240.dp, 120.dp).testTag("action").customClickable(interactionSource = source) {
                            clicks++
                        },
                    )
                    Spacer(Modifier.size(240.dp, 480.dp))
                }
            }
        }
        val action = compose.onNodeWithTag("action")
        val touch = Offset(32f, 25f)
        // Neither the framework tap timeout nor a release can start the ripple for this assertion.
        compose.mainClock.autoAdvance = false
        action.performTouchInput { down(touch) }
        compose.runOnIdle {
            assertTrue("Expected the ripple press on DOWN: $presses", presses.singleOrNull() is PressInteraction.Press)
            assertEquals(touch, (presses.single() as PressInteraction.Press).pressPosition)
            assertEquals(0, clicks)
        }
        compose.mainClock.advanceTimeBy(250)
        action.performTouchInput { up() }
        compose.runOnIdle {
            assertEquals(1, clicks)
            assertEquals(2, presses.size)
            assertSame(presses[0], (presses[1] as PressInteraction.Release).press)
        }

        action.performTouchInput {
            down(center)
            moveBy(Offset(0f, -24.dp.toPx()))
            moveBy(Offset(0f, -16.dp.toPx()))
            up()
        }
        compose.mainClock.advanceTimeByFrame()
        compose.runOnIdle {
            assertTrue(scroll.value > 0)
            assertEquals(1, clicks)
            assertEquals(4, presses.size)
            assertSame(presses[2], (presses[3] as PressInteraction.Cancel).press)
        }
    }

    @Test
    @OptIn(ExperimentalTestApi::class)
    fun buttonAndLinkFireOnceOnReleaseAndGiveDragToParent() {
        val scroll = ScrollState(0)
        val focus = FocusRequester()
        lateinit var inputMode: InputModeManager
        var buttonClicks = 0
        var linkClicks = 0
        var parentClicks = 0
        compose.setContent {
            inputMode = LocalInputModeManager.current
            PleosTheme {
                Column(Modifier.size(240.dp, 220.dp).verticalScroll(scroll)) {
                    Spacer(Modifier.size(240.dp, 56.dp))
                    Box(Modifier.customClickable { parentClicks++ }) {
                        PleosButton("여정 시작", { buttonClicks++ }, Modifier.focusRequester(focus))
                    }
                    PleosTextLink("돌아가기", { linkClicks++ })
                    Spacer(Modifier.size(240.dp, 480.dp))
                }
            }
        }
        val button = compose.onNodeWithText("여정 시작")
        button.performTouchInput { down(center) }
        compose.runOnIdle { assertEquals(0, buttonClicks) }
        button.performTouchInput { up() }
        compose.onNodeWithText("돌아가기").performTouchInput { click() }
        button.performTouchInput {
            down(center)
            moveBy(Offset(0f, -40.dp.toPx()))
            moveBy(Offset(0f, -24.dp.toPx()))
            up()
        }
        compose.runOnIdle {
            assertTrue(scroll.value > 0)
            assertEquals(1, buttonClicks)
            assertEquals(1, linkClicks)
            assertEquals(0, parentClicks)
            inputMode.requestInputMode(InputMode.Keyboard)
            focus.requestFocus()
        }
        button.performKeyInput { pressKey(Key.Enter) }
        compose.runOnIdle { assertEquals(2, buttonClicks) }
    }

    @Test
    fun roundedPressShrinksOnlyTheDrawingAndClipsTheRipple() {
        var clicks = 0
        var enabled by mutableStateOf(true)
        compose.setContent {
            PleosTheme {
                Box(Modifier.size(240.dp, 120.dp).background(Color.Black).testTag("capture")) {
                    Box(
                        Modifier.fillMaxSize().testTag("action")
                            .focusProperties { canFocus = false }
                            .customClickable(
                                shape = RoundedCornerShape(32.dp),
                                enabled = enabled,
                                rippleColor = Color.White,
                            ) {
                                clicks++
                            }
                            .background(Color.Blue),
                    )
                }
            }
        }
        val action = compose.onNodeWithTag("action")
        val capture = compose.onNodeWithTag("capture")
        val idle = capture.captureToImage().toPixelMap()
        val middle = idle.height / 2
        assertTrue(idle[0, middle].blue > .99f)

        action.performTouchInput { down(center) }
        compose.waitUntil(timeoutMillis = 5_000) {
            val pressed = capture.captureToImage().toPixelMap()
            // The white ripple tints the blue center, while the scaled side and rounded corner expose the backing.
            pressed[pressed.width / 2, middle].red > idle[idle.width / 2, middle].red + .01f &&
                pressed[0, middle] == Color.Black && pressed[3, 3] == Color.Black
        }
        action.performTouchInput { cancel() }
        // The blue edge reappears when the scale returns, even while the native ripple fades.
        compose.waitUntil(timeoutMillis = 5_000) {
            capture.captureToImage().toPixelMap()[0, middle].blue > .99f
        }
        compose.runOnIdle { assertEquals(0, clicks) }

        action.performTouchInput { click() }
        compose.runOnIdle { enabled = false }
        action.assertIsNotEnabled().performTouchInput { click() }
        compose.runOnIdle { assertEquals(1, clicks) }
    }

    @Test
    fun singleTabStripMovesWithWindowWidthAndDisappearsUnderRestriction() {
        var width by mutableStateOf(1216.dp)
        var restricted by mutableStateOf(false)
        var selected by mutableStateOf(PleosTab.Travel)
        compose.setContent {
            PleosTheme {
                Column(Modifier.requiredWidth(width)) {
                    PleosHeader(selected, { selected = it }, {}, restricted, Modifier.testTag("header"))
                    PleosBottomTabs(selected, { selected = it }, restricted, Modifier.testTag("footer"))
                }
            }
        }

        val headerTab = compose.onNode(hasText("컬렉션") and hasAnyAncestor(hasTestTag("header")))
        headerTab.performTouchInput { down(center) }
        compose.runOnIdle { assertEquals(PleosTab.Travel, selected) }
        headerTab.performTouchInput { up() }
        compose.onAllNodesWithText("컬렉션").assertCountEquals(1)
        compose.runOnIdle { width = 400.dp }
        compose.onNode(hasText("컬렉션") and hasAnyAncestor(hasTestTag("footer"))).assertIsSelected()
        compose.onAllNodesWithText("컬렉션").assertCountEquals(1)

        val footerTab = compose.onNode(hasText("기록") and hasAnyAncestor(hasTestTag("footer")))
        footerTab.performTouchInput { down(center) }
        compose.runOnIdle { assertEquals(PleosTab.Collection, selected) }
        footerTab.performTouchInput { up() }
        footerTab.assertIsSelected()

        compose.runOnIdle { restricted = true }
        compose.onNodeWithText("조작 제한").assertExists()
        compose.onAllNodesWithText("컬렉션").assertCountEquals(0)
        compose.onNodeWithContentDescription("설정").assertDoesNotExist()
    }

    @Test
    fun chagokWordmarkStaysOnOneLineInFullAndFiveTwentyDpHeaders() {
        var width by mutableStateOf(1216.dp)
        var fontScale by mutableStateOf(1f)
        var restricted by mutableStateOf(false)
        compose.setContent {
            val density = LocalDensity.current
            CompositionLocalProvider(LocalDensity provides Density(density.density, fontScale)) {
                PleosTheme {
                    Box(Modifier.requiredWidth(width)) {
                        PleosHeader(PleosTab.Travel, {}, {}, restricted)
                    }
                }
            }
        }
        val cases = listOf(
            Triple(1216.dp, 1f, false),
            Triple(520.dp, 1f, false),
            Triple(520.dp, 1f, true),
            Triple(520.dp, 1.5f, false),
            Triple(520.dp, 1.5f, true),
        )
        for ((targetWidth, targetFontScale, targetRestricted) in cases) {
            compose.runOnIdle {
                width = targetWidth
                fontScale = targetFontScale
                restricted = targetRestricted
            }
            val wordmark = compose.onNodeWithTag("brand-wordmark").assertIsDisplayed()
            val layouts = mutableListOf<TextLayoutResult>()
            wordmark.performSemanticsAction(SemanticsActions.GetTextLayoutResult) { it(layouts) }
            assertTrue(layouts.isNotEmpty())
            layouts.forEach { layout ->
                assertEquals("차곡차곡", layout.layoutInput.text.text)
                assertEquals(1, layout.lineCount)
                assertFalse("Brand clipped at $targetWidth / font scale $targetFontScale", layout.hasVisualOverflow)
            }
        }
    }

    @Test
    fun disabledActionIgnoresTouchAndFilterReportsSelection() {
        var clicks = 0
        var selected by mutableStateOf(false)
        compose.setContent {
            PleosTheme {
                Column {
                    PleosButton("위치 확인 중", { clicks++ }, enabled = false, showArrow = true)
                    PleosChip("장소", selected, { selected = it })
                }
            }
        }
        compose.onNodeWithText("위치 확인 중").assertHeightIsEqualTo(56.dp).assertIsNotEnabled().performTouchInput {
            click()
        }
        compose.onNodeWithText("장소").assertHeightIsEqualTo(40.dp).assertIsOff().performClick()
        compose.onNodeWithText("장소").assertIsOn()
        compose.runOnIdle { assertEquals(0, clicks) }
    }

    @Test
    fun searchSubmitsEditedTextAndClearResetsTheField() {
        var value by mutableStateOf("")
        var submitted = ""
        compose.setContent {
            PleosTheme { PleosSearchField(value, { value = it }, { submitted = it }) }
        }
        val field = compose.onNodeWithContentDescription("장소 검색")
        field.performTextInput("광안리")
        field.performImeAction()
        compose.runOnIdle { assertEquals("광안리", submitted) }
        compose.onNodeWithContentDescription("검색어 지우기").performClick()
        compose.runOnIdle { assertEquals("", value) }
        compose.onNodeWithContentDescription("검색어 지우기").assertDoesNotExist()
    }

    @Test
    fun mapEmitsMarkerAndClusterClicksWithLocationControlDisabled() {
        var selected by mutableStateOf(false)
        var pinClicks = 0
        var clusters = 0
        compose.setContent {
            PleosTheme {
                PleosPlaceMap(Modifier.size(400.dp, 280.dp), onZoomIn = {}, onZoomOut = {}) {
                    PleosMapPin(
                        label = "광안리",
                        selected = selected,
                        onClick = {
                            pinClicks++
                            selected = true
                        },
                        modifier = Modifier.align(Alignment.Center),
                        showLabel = selected,
                    )
                    PleosMapCluster(3, { clusters++ }, Modifier.align(Alignment.CenterStart))
                }
            }
        }
        compose.onNodeWithContentDescription("내 위치로 이동").assertIsNotEnabled()
        compose.onNodeWithContentDescription("광안리").performTouchInput { click() }
        compose.onNodeWithText("광안리").assertIsSelected().performTouchInput {
            click(bottomCenter - Offset(0f, 6.dp.toPx()))
        }
        compose.onNodeWithContentDescription("장소 3곳").performClick()
        compose.runOnIdle {
            assertEquals(2, pinClicks)
            assertEquals(1, clusters)
        }
    }

    @Test
    fun dialogConfirmationAndSheetDismissalAreOwnedByCaller() {
        var showDialog by mutableStateOf(true)
        var showSheet by mutableStateOf(false)
        compose.setContent {
            PleosTheme {
                if (showDialog) {
                    PleosDialog("방문 확인", "방문을 남길까요?", "방문했어요", onConfirm = {
                        showDialog = false
                        showSheet = true
                    }, onDismissRequest = { showDialog = false }, dismissLabel = "취소")
                }
                if (showSheet) {
                    PleosBottomSheet("방문 정보", { showSheet = false }) {
                        PleosButton("닫기", { showSheet = false })
                    }
                }
            }
        }
        compose.onNodeWithText("방문했어요").performClick()
        compose.onNodeWithText("방문 확인").assertDoesNotExist()
        compose.onNodeWithText("방문 정보").assertExists()
        compose.onNodeWithText("닫기").performClick()
        compose.onNodeWithText("방문 정보").assertDoesNotExist()
    }
}
