package io.issac.pleosandroid.feature.forge

import android.graphics.Bitmap
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.requiredSize
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.asAndroidBitmap
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.assertIsEnabled
import androidx.compose.ui.test.captureToImage
import androidx.compose.ui.test.getUnclippedBoundsInRoot
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performScrollTo
import androidx.compose.ui.unit.dp
import androidx.test.platform.app.InstrumentationRegistry
import io.issac.pleosandroid.core.designsystem.theme.PleosTheme
import io.issac.pleosandroid.core.domain.cards.AlbumSnapshot
import io.issac.pleosandroid.core.domain.cards.CardCopy
import io.issac.pleosandroid.core.domain.cards.CardGrade
import io.issac.pleosandroid.core.domain.cards.CardPrint
import io.issac.pleosandroid.core.domain.cards.CopyStatus
import io.issac.pleosandroid.core.domain.cards.PackType
import io.issac.pleosandroid.core.domain.cards.PrintGroup
import io.issac.pleosandroid.core.domain.cards.ReceivedPack
import java.io.File
import java.time.Instant
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test

class WorkshopWindowTest {
    @get:Rule val compose = createComposeRule()
    private val now = Instant.parse("2026-11-07T12:00:00Z")
    private val pack = ReceivedPack("pack", PackType.Busan, "방문 보상", now)
    private val print = CardPrint(
        "busan-bukangi", "북항의 부캉이", PackType.Busan, PrintGroup.Specialty, CardGrade.Rare,
        "print_busan_bukangi_base", "print_busan_bukangi_variant", "부캉이", "BUSAN / 009",
    )
    private val card = CardCopy("copy", print, true, 25, 25, CopyStatus.Owned, 0, pack, now, true)
    private val album = AlbumSnapshot(10_000L, null, listOf(pack), listOf(card), emptyList())

    @Test fun narrowWindowKeepsTheFullPrintAboveScrollableActionsAndWideWindowUsesTwoColumns() {
        var width by mutableStateOf(480.dp)
        var sent: ForgeIntent? = null
        compose.setContent {
            Box(Modifier.requiredSize(width, 630.dp).testTag("viewport")) {
                PleosTheme {
                    CardWorkshop(card, album, false, { sent = it }, {}, {}, Modifier.fillMaxSize())
                }
            }
        }
        val art = compose.onNodeWithText("카드 강화")
        art.assertIsDisplayed()
        capture("card-workshop-split.png")
        val button = compose.onNodeWithTag("forge-attempt")
        button.performScrollTo().assertIsDisplayed().assertIsEnabled().performClick()
        assertEquals(card, (sent as ForgeIntent.Enhance).card)
        val viewport = compose.onNodeWithTag("viewport")
        val narrow = viewport.getUnclippedBoundsInRoot()
        val action = button.getUnclippedBoundsInRoot()
        assertTrue(action.left >= narrow.left && action.right <= narrow.right)
        compose.runOnIdle { width = 1216.dp }
        button.performScrollTo().assertIsDisplayed()
        val wide = viewport.getUnclippedBoundsInRoot()
        assertTrue(button.getUnclippedBoundsInRoot().left > wide.left + (wide.right - wide.left) * .45f)
    }

    private fun capture(name: String) {
        val bitmap = compose.onNodeWithTag("viewport").captureToImage().asAndroidBitmap()
        val files = InstrumentationRegistry.getInstrumentation().targetContext.filesDir
        File(files, name).outputStream().use { bitmap.compress(Bitmap.CompressFormat.PNG, 100, it) }
    }
}
