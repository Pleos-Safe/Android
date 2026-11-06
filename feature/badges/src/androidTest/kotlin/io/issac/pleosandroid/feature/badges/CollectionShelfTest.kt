package io.issac.pleosandroid.feature.badges

import android.graphics.Bitmap
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.requiredSize
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.asAndroidBitmap
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.test.captureToImage
import androidx.compose.ui.test.getUnclippedBoundsInRoot
import androidx.compose.ui.test.junit4.StateRestorationTester
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.compose.ui.unit.dp
import androidx.test.platform.app.InstrumentationRegistry
import io.issac.pleosandroid.core.designsystem.component.PleosBottomTabs
import io.issac.pleosandroid.core.designsystem.component.PleosHeader
import io.issac.pleosandroid.core.designsystem.component.PleosTab
import io.issac.pleosandroid.core.designsystem.component.pleosShellBackground
import io.issac.pleosandroid.core.designsystem.theme.PleosTheme
import io.issac.pleosandroid.core.designsystem.theme.PleosWindowTheme
import io.issac.pleosandroid.core.navigation.AppRoute
import io.issac.pleosandroid.feature.badges.collection.CollectionScreen
import io.issac.pleosandroid.feature.badges.model.CardKind
import io.issac.pleosandroid.feature.badges.model.CollectionCardDesign
import io.issac.pleosandroid.feature.badges.model.CollectionSeed
import io.issac.pleosandroid.feature.badges.model.GentleStart
import io.issac.pleosandroid.feature.badges.model.Gwangalli
import io.issac.pleosandroid.feature.badges.model.Songdo
import io.issac.pleosandroid.feature.badges.navigation.CollectionGuideRoute
import java.io.File
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test

class CollectionShelfTest {
    @get:Rule val compose = createComposeRule()

    @Test
    fun tallSplitShelfGrowsTheCardAndKeepsThePagerInView() {
        var height by mutableStateOf(616.dp)
        compose.setContent {
            PleosWindowTheme(Modifier.requiredSize(841.dp, height)) {
                Box(Modifier.fillMaxSize().testTag("collection-viewport")) {
                    CollectionScreen(CollectionSeed.cards, CollectionSeed.representativeId, {})
                }
            }
        }
        val card = compose.onNodeWithTag("collection-card-${CollectionCardDesign.Gwangalli.id}")
        val title = compose.onNodeWithText("나의 컬렉션")
        val initialTitle = title.getUnclippedBoundsInRoot()
        val design = card.getUnclippedBoundsInRoot()
        compose.runOnIdle { height = 1082.dp }
        val enlargedTitle = title.getUnclippedBoundsInRoot()
        assertTrue(enlargedTitle.bottom - enlargedTitle.top > (initialTitle.bottom - initialTitle.top) * 1.3f)
        val enlarged = card.getUnclippedBoundsInRoot()
        assertTrue(enlarged.bottom - enlarged.top > (design.bottom - design.top) * 1.5f)
        val viewport = compose.onNodeWithTag("collection-viewport").getUnclippedBoundsInRoot()
        val pager = compose.onNodeWithText("다음").getUnclippedBoundsInRoot()
        assertTrue(pager.bottom <= viewport.bottom && viewport.bottom - pager.bottom < 64.dp)
    }

    @Test
    fun fullAndSplitShelvesKeepTheCardAnchorAcrossResizeAndRestoration() {
        var width by mutableStateOf(1216.dp)
        val restoration = StateRestorationTester(compose)
        restoration.setContent {
            PleosWindowTheme(Modifier.requiredSize(width, 760.dp)) {
                Column(Modifier.fillMaxSize().testTag("collection-window").pleosShellBackground()) {
                    PleosHeader(PleosTab.Collection, {}, {}, false)
                    Box(Modifier.weight(1f)) {
                        CollectionScreen(CollectionSeed.cards, CollectionSeed.representativeId, {})
                    }
                    PleosBottomTabs(PleosTab.Collection, {}, false)
                }
            }
        }
        compose.onNodeWithText("1–4 / 8").assertExists()
        captureIfRequested("collection-full")
        compose.runOnIdle { width = 520.dp }
        compose.onNodeWithText("1–2 / 8").assertExists()
        captureIfRequested("collection-split")
        repeat(3) { compose.onNodeWithText("다음").performClick() }
        compose.onNodeWithText("7–8 / 8").assertExists()
        compose.onNodeWithText("송도의 푸른 길").assertDoesNotExist()
        compose.runOnIdle { width = 1216.dp }
        compose.onNodeWithText("5–8 / 8").assertExists()
        restoration.emulateSavedInstanceStateRestore()
        compose.onNodeWithTag("collection-card-place-songdo-001").assertExists()
        compose.runOnIdle { width = 520.dp }
        compose.onNodeWithText("7–8 / 8").assertExists()
    }

    @Test
    fun removingTheAnchorKeepsTheNearestSurvivingShelf() {
        var cards by mutableStateOf(CollectionSeed.cards)
        compose.setContent {
            PleosTheme {
                CollectionScreen(cards, CollectionSeed.representativeId, {}, Modifier.requiredSize(520.dp, 760.dp))
            }
        }
        repeat(3) { compose.onNodeWithText("다음").performClick() }
        compose.onNodeWithText("7–8 / 8").assertExists()
        compose.runOnIdle { cards = cards.filterNot { it.design == CollectionCardDesign.GentleStart } }
        compose.onNodeWithText("7–7 / 7").assertExists()
        compose.onNodeWithTag("collection-card-place-songdo-001").assertExists()
        compose.runOnIdle { cards = cards.filterNot { it.design == CollectionCardDesign.Songdo } }
        compose.onNodeWithText("5–6 / 6").assertExists()
    }

    @Test
    fun emptyCollectionOpensGuidesWithoutCreatingOwnership() {
        var destination: AppRoute? = null
        compose.setContent {
            PleosTheme { CollectionScreen(emptyList(), null, { destination = it }) }
        }
        compose.onNodeWithText("첫 장을 기다리는 자리").assertExists()
        compose.onNodeWithText("장소 둘러보기").performClick()
        compose.runOnIdle { assertEquals(CollectionGuideRoute(CardKind.Place), destination) }
        compose.onNodeWithText("운전습관 기록 보기").performClick()
        compose.runOnIdle { assertEquals(CollectionGuideRoute(CardKind.Safety), destination) }
        compose.onNodeWithText("해운대의 파도").assertDoesNotExist()
    }

    private fun captureIfRequested(name: String) {
        if (InstrumentationRegistry.getArguments().getString("captureCollection") != "true") return
        val directory = File(InstrumentationRegistry.getInstrumentation().targetContext.filesDir, "screenshots")
        directory.mkdirs()
        val image = compose.onNodeWithTag("collection-window").captureToImage().asAndroidBitmap()
        File(directory, "$name.png").outputStream().use { check(image.compress(Bitmap.CompressFormat.PNG, 100, it)) }
    }
}
