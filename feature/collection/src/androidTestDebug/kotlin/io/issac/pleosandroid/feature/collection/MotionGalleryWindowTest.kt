package io.issac.pleosandroid.feature.collection

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.requiredSize
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.getUnclippedBoundsInRoot
import androidx.compose.ui.test.hasText
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performScrollToNode
import androidx.compose.ui.unit.dp
import io.issac.pleosandroid.core.designsystem.theme.PleosTheme
import io.issac.pleosandroid.core.domain.cards.ForgeOutcome
import io.issac.pleosandroid.core.navigation.CardMotionRoute
import io.issac.pleosandroid.core.navigation.ForgeMotionRoute
import io.issac.pleosandroid.core.navigation.MotionGalleryRoute
import io.issac.pleosandroid.feature.collection.gallery.MotionGallery
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test

class MotionGalleryWindowTest {
    @get:Rule val compose = createComposeRule()

    @Test fun galleryChoicesFitSplitWindowsAndKeepTheirStartingLevelOnResize() {
        var width by mutableStateOf(460.dp)
        var selected: CardMotionRoute? = null
        compose.setContent {
            Box(Modifier.requiredSize(width, 590.dp).testTag("viewport")) {
                PleosTheme { MotionGallery(MotionGalleryRoute(true), { selected = it }, {}) }
            }
        }
        compose.onNodeWithText("+1").performClick()
        val grid = compose.onNodeWithTag("motion-gallery")
        grid.performScrollToNode(hasText("카드 파괴"))
        val destroy = compose.onNodeWithText("카드 파괴").assertIsDisplayed()
        val item = destroy.getUnclippedBoundsInRoot()
        val viewport = grid.getUnclippedBoundsInRoot()
        assertTrue(item.left >= viewport.left && item.right <= viewport.right)
        destroy.performClick()
        assertEquals(ForgeMotionRoute(15, 10, ForgeOutcome.Destroyed), selected)
        compose.runOnIdle { width = 1216.dp }
        grid.performScrollToNode(hasText("시작 강화  +15"))
        compose.onNodeWithText("시작 강화  +15").assertIsDisplayed()
    }
}
