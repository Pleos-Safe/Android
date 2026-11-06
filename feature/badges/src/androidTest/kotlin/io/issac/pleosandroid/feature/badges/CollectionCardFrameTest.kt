package io.issac.pleosandroid.feature.badges

import androidx.compose.foundation.layout.width
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.PixelMap
import androidx.compose.ui.graphics.toPixelMap
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.test.captureToImage
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.unit.dp
import io.issac.pleosandroid.core.designsystem.component.MilestoneCard
import io.issac.pleosandroid.core.designsystem.theme.PleosTheme
import io.issac.pleosandroid.feature.badges.model.CollectionCardDesign
import io.issac.pleosandroid.feature.badges.model.Haeundae
import io.issac.pleosandroid.feature.badges.model.Huinnyeoul
import io.issac.pleosandroid.feature.badges.model.SafeHome
import kotlin.math.roundToInt
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test

class CollectionCardFrameTest {
    @get:Rule val compose = createComposeRule()

    @Test
    fun allThreeStocksShowTheBlueSilverSapphireAndGoldRimsAtBothSizes() {
        compose.mainClock.autoAdvance = false
        var design by mutableStateOf(CollectionCardDesign.Haeundae)
        var level by mutableIntStateOf(2)
        var width by mutableIntStateOf(360)
        compose.setContent {
            PleosTheme {
                MilestoneCard(design.front(level), Modifier.width(width.dp).testTag("print"))
            }
        }
        val stocks =
            listOf(CollectionCardDesign.Haeundae, CollectionCardDesign.Huinnyeoul, CollectionCardDesign.SafeHome)
        for (cardWidth in listOf(180, 360)) {
            for (stock in stocks) {
                for (grade in 2..5) {
                    compose.runOnIdle {
                        design = stock
                        level = grade
                        width = cardWidth
                    }
                    compose.mainClock.advanceTimeByFrame()
                    val pixels = compose.onNodeWithTag("print").captureToImage().toPixelMap()
                    for (left in listOf(true, false)) {
                        val edge = pixels.outerRim(left, grade)
                        val description = "${stock.id} Lv.$grade at ${pixels.width}px, left=$left"
                        assertTrue(
                            "$description • expected rim colour, got $edge",
                            when (grade) {
                                2 -> edge.blue > edge.red + .16f
                                3 -> edge.blue >= edge.red && edge.blue - edge.red < .22f
                                4 -> edge.blue > edge.red + .12f
                                else -> edge.red > edge.blue + .17f
                            },
                        )
                        if (grade >= 3) {
                            val inset = (16f * pixels.width / 360).roundToInt()
                            val paper = pixels[if (left) inset else pixels.width - 1 - inset, pixels.height / 2]
                            assertTrue(
                                "$description • legible edge on dark stock",
                                brightness(edge) > brightness(paper) + .35f,
                            )
                        }
                    }
                }
            }
        }
    }

    private fun PixelMap.outerRim(left: Boolean, level: Int): Color {
        val scale = width / 360f
        val middle = height / 2
        fun sample(x: Int) = this[if (left) x else width - 1 - x, middle]
        if (level == 2) return sample((5f * scale).toInt())
        return ((2 * scale).toInt()..(6 * scale).roundToInt()).map(::sample).maxBy(::brightness)
    }

    private fun brightness(color: Color) = color.red + color.green + color.blue
}
