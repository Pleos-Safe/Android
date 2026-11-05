package io.issac.pleosandroid.core.designsystem.component

import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.requiredWidth
import androidx.compose.foundation.layout.size
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.test.assertCountEquals
import androidx.compose.ui.test.assertHeightIsEqualTo
import androidx.compose.ui.test.assertIsNotEnabled
import androidx.compose.ui.test.assertIsOff
import androidx.compose.ui.test.assertIsOn
import androidx.compose.ui.test.assertIsSelected
import androidx.compose.ui.test.click
import androidx.compose.ui.test.hasAnyAncestor
import androidx.compose.ui.test.hasTestTag
import androidx.compose.ui.test.hasText
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onAllNodesWithText
import androidx.compose.ui.test.onNodeWithContentDescription
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performImeAction
import androidx.compose.ui.test.performTextInput
import androidx.compose.ui.test.performTouchInput
import androidx.compose.ui.unit.dp
import io.issac.pleosandroid.core.designsystem.theme.PleosTheme
import org.junit.Assert.assertEquals
import org.junit.Rule
import org.junit.Test

class PleosComponentsTest {
    @get:Rule val compose = createComposeRule()

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

        compose.onNode(hasText("컬렉션") and hasAnyAncestor(hasTestTag("header"))).performClick()
        compose.onAllNodesWithText("컬렉션").assertCountEquals(1)
        compose.runOnIdle { width = 400.dp }
        compose.onNode(hasText("컬렉션") and hasAnyAncestor(hasTestTag("footer"))).assertIsSelected()
        compose.onAllNodesWithText("컬렉션").assertCountEquals(1)

        compose.runOnIdle { restricted = true }
        compose.onNodeWithText("조작 제한").assertExists()
        compose.onAllNodesWithText("컬렉션").assertCountEquals(0)
        compose.onNodeWithContentDescription("설정").assertDoesNotExist()
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
        var clusters = 0
        compose.setContent {
            PleosTheme {
                PleosPlaceMap(Modifier.size(400.dp, 280.dp), onZoomIn = {}, onZoomOut = {}) {
                    PleosMapPin("광안리", selected, { selected = true }, Modifier.align(Alignment.Center))
                    PleosMapCluster(3, { clusters++ }, Modifier.align(Alignment.CenterStart))
                }
            }
        }
        compose.onNodeWithContentDescription("내 위치로 이동").assertIsNotEnabled()
        compose.onNodeWithContentDescription("광안리").performClick().assertIsSelected()
        compose.onNodeWithContentDescription("장소 3곳").performClick()
        compose.runOnIdle { assertEquals(1, clusters) }
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
