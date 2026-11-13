package io.issac.pleosandroid.feature.home

import android.graphics.Bitmap
import androidx.annotation.StringRes
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.requiredSize
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.mutableStateOf
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.asAndroidBitmap
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.semantics.SemanticsActions
import androidx.compose.ui.test.assertHeightIsEqualTo
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.assertIsEnabled
import androidx.compose.ui.test.assertWidthIsEqualTo
import androidx.compose.ui.test.captureToImage
import androidx.compose.ui.test.getUnclippedBoundsInRoot
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onNodeWithContentDescription
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performSemanticsAction
import androidx.compose.ui.text.TextLayoutResult
import androidx.compose.ui.unit.Density
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.test.platform.app.InstrumentationRegistry
import io.issac.pleosandroid.core.designsystem.component.PleosHeader
import io.issac.pleosandroid.core.designsystem.component.PleosTab
import io.issac.pleosandroid.core.designsystem.component.pleosShellBackground
import io.issac.pleosandroid.core.designsystem.theme.PleosWindowTheme
import io.issac.pleosandroid.core.domain.place.DrivingSafetyMessage
import io.issac.pleosandroid.core.domain.place.PlaceMapPosition
import io.issac.pleosandroid.core.domain.place.PlaceNavigationOperation
import io.issac.pleosandroid.feature.home.driving.DrivingOverviewScreen
import io.issac.pleosandroid.feature.home.driving.DrivingRecommendation
import io.issac.pleosandroid.feature.home.driving.DrivingRegionMap
import java.io.File
import org.junit.Assert.assertTrue
import org.junit.Assume.assumeTrue
import org.junit.Rule
import org.junit.Test

/** Captures UI fixtures on a large display without starting vehicle or navigation services. */
class DrivingOverviewCaptureTest {
    @get:Rule
    val compose = createComposeRule()

    @Test
    fun compactMetricsKeepMapAndRecommendationVisibleAtLargeFontSizes() {
        val fontScale = mutableStateOf(1f)
        val operation = mutableStateOf(PlaceNavigationOperation.Applied)
        val safetyMessage = mutableStateOf<DrivingSafetyMessage?>(null)
        val context = InstrumentationRegistry.getInstrumentation().targetContext
        compose.setContent {
            val density = LocalDensity.current
            CompositionLocalProvider(LocalDensity provides Density(density.density, fontScale.value)) {
                PleosWindowTheme(Modifier.requiredSize(520.dp, 760.dp)) {
                    Column(Modifier.fillMaxSize().testTag(CAPTURE_TAG)) {
                        PleosHeader(PleosTab.Travel, {}, {}, restricted = true)
                        DrivingOverviewScreen(
                            isMoving = true,
                            modifier = Modifier.weight(1f),
                            progress = JourneyProgressDisplay(2_400, 198_000),
                            destination = "해운대 해변 방면",
                            safetyMessage = safetyMessage.value,
                            map = { DrivingRegionMap(CapturePosition, VisitPackCounts, true, it) },
                            recommendation = {
                                DrivingRecommendation(
                                    placeName = "광안리 해변",
                                    hasDestination = true,
                                    operation = operation.value,
                                    onRequest = {},
                                    onRecheck = {},
                                    distanceKm = 1.8,
                                )
                            },
                        )
                    }
                }
            }
        }
        val cases = listOf(
            PlaceNavigationOperation.Applied to null,
            PlaceNavigationOperation.Idle to DrivingSafetyMessage.Acceleration,
        )
        for ((targetOperation, targetSafety) in cases) {
            for (targetFontScale in listOf(1f, 1.5f)) {
                compose.runOnIdle {
                    fontScale.value = targetFontScale
                    operation.value = targetOperation
                    safetyMessage.value = targetSafety
                }
                val map = compose.onNodeWithContentDescription("대한민국 주행 위치 지도", substring = true)
                    .assertIsDisplayed().getUnclippedBoundsInRoot()
                assertTrue("Map canvas collapsed at font scale $targetFontScale: $map", map.bottom - map.top >= 80.dp)
                for (label in listOfNotNull(
                    "해운대 해변 방면",
                    "거리 ${context.getString(R.string.journey_distance_km, 2.4)}",
                    "주행 ${context.getString(R.string.journey_duration_minutes_seconds, 3, 18)}",
                    context.getString(R.string.driving_recommendation_applied)
                        .takeIf { targetOperation == PlaceNavigationOperation.Applied },
                    context.getString(R.string.driving_overview_acceleration).takeIf { targetSafety != null },
                    "광안리 해변",
                )) {
                    compose.onNodeWithText(label, useUnmergedTree = true).assertIsDisplayed()
                }
                val action = compose.onNodeWithText(context.getString(R.string.place_map_add_waypoint))
                    .assertIsDisplayed().assertIsEnabled().getUnclippedBoundsInRoot()
                val viewport = compose.onNodeWithTag(CAPTURE_TAG).getUnclippedBoundsInRoot()
                assertTrue(
                    "Recommendation action is clipped at font scale $targetFontScale",
                    action.bottom <= viewport.bottom,
                )
            }
        }
    }

    @Test
    fun captureOverviewStates() {
        assumeTrue(InstrumentationRegistry.getArguments().getString("drivingOverviewCapture") == "true")

        val moving = Capture("01-moving-destination")
        val onRoute = moving.copy(hasDestination = true, action = R.string.place_map_add_waypoint)
        val cases = listOf(
            moving,
            onRoute.copy(filename = "02-stopped-waypoint", isMoving = false),
            moving.copy(filename = "03-location-unavailable", position = null, placeName = null, action = null),
            onRoute.copy(
                filename = "04-recommendation-pending",
                operation = PlaceNavigationOperation.Requesting,
                message = R.string.driving_recommendation_requesting,
                action = null,
            ),
            onRoute.copy(
                filename = "05-recommendation-rejected",
                operation = PlaceNavigationOperation.Rejected,
                message = R.string.driving_recommendation_rejected,
            ),
            onRoute.copy(
                filename = "06-recommendation-applied",
                operation = PlaceNavigationOperation.Applied,
                message = R.string.driving_recommendation_applied,
                placeName = "청사포 정거장",
            ),
            onRoute.copy(
                filename = "07-recommendation-unconfirmed",
                operation = PlaceNavigationOperation.Unconfirmed,
                message = R.string.driving_recommendation_unconfirmed,
                action = R.string.place_map_check_route,
            ),
            onRoute.copy(filename = "08-acceleration", safetyMessage = DrivingSafetyMessage.Acceleration),
            onRoute.copy(filename = "09-braking", safetyMessage = DrivingSafetyMessage.Braking),
        )
        val current = mutableStateOf(moving)
        val width = mutableStateOf(1216.dp)
        val height = mutableStateOf(760.dp)
        val context = InstrumentationRegistry.getInstrumentation().targetContext
        val output = File(context.filesDir, "screenshots/driving-overview")
        check(output.isDirectory || output.mkdirs())

        compose.setContent {
            PleosWindowTheme(Modifier.requiredSize(width.value, height.value)) {
                val capture = current.value
                Column(Modifier.fillMaxSize().testTag(CAPTURE_TAG).pleosShellBackground()) {
                    PleosHeader(PleosTab.Travel, {}, {}, restricted = true)
                    Box(Modifier.weight(1f)) {
                        DrivingOverviewScreen(
                            isMoving = capture.isMoving,
                            safetyMessage = capture.safetyMessage,
                            map = { modifier ->
                                DrivingRegionMap(capture.position, VisitPackCounts, capture.isMoving, modifier)
                            },
                            recommendation = {
                                DrivingRecommendation(
                                    placeName = capture.placeName,
                                    hasDestination = capture.hasDestination,
                                    operation = capture.operation,
                                    onRequest = {},
                                    onRecheck = {},
                                )
                            },
                        )
                    }
                }
            }
        }

        for ((captureWidth, captureHeight) in listOf(
            1216.dp to 760.dp,
            520.dp to 760.dp,
            1680.dp to 1210.dp,
            841.dp to 1210.dp,
        )) {
            for (capture in cases) {
                compose.runOnIdle {
                    current.value = capture
                    width.value = captureWidth
                    height.value = captureHeight
                }
                assertState(capture, context::getString)
                saveCapture(output, capture.filename, captureWidth, captureHeight)
            }
        }
    }

    @Test
    fun captureRegionalVisitPackLevels() {
        assumeTrue(InstrumentationRegistry.getArguments().getString("regionalVisitPackCapture") == "true")
        val width = mutableStateOf(1216.dp)
        val height = 760.dp
        val context = InstrumentationRegistry.getInstrumentation().targetContext
        val output = File(context.filesDir, "screenshots/driving-overview")
        check(output.isDirectory || output.mkdirs())
        val regions = listOf(
            Triple("KR-11", R.string.driving_region_map_seoul, 1),
            Triple("KR-41", R.string.driving_region_map_gyeonggi, 4),
            Triple("KR-42", R.string.driving_region_map_gangwon, 7),
            Triple("KR-47", R.string.driving_region_map_gyeongbuk, 10),
            Triple("KR-48", R.string.driving_region_map_gyeongnam, 13),
            Triple("KR-26", R.string.driving_region_map_busan, 16),
        )
        val counts = regions.associate { (id, _, count) -> id to count }
        compose.setContent {
            PleosWindowTheme(Modifier.requiredSize(width.value, height)) {
                Column(Modifier.fillMaxSize().testTag(CAPTURE_TAG).pleosShellBackground()) {
                    PleosHeader(PleosTab.Travel, {}, {}, restricted = true)
                    DrivingOverviewScreen(
                        isMoving = true,
                        modifier = Modifier.weight(1f),
                        map = { DrivingRegionMap(CapturePosition, counts, true, it) },
                        recommendation = {
                            DrivingRecommendation(
                                placeName = "광안리 해변",
                                hasDestination = true,
                                operation = PlaceNavigationOperation.Idle,
                                onRequest = {},
                                onRecheck = {},
                            )
                        },
                    )
                }
            }
        }

        for (captureWidth in listOf(1216.dp, 520.dp)) {
            compose.runOnIdle { width.value = captureWidth }
            saveCapture(output, "visit-pack-levels", captureWidth, height)
            for ((index, region) in regions.withIndex()) {
                val (_, name, count) = region
                val description = context.getString(
                    R.string.driving_region_map_region_pack_description,
                    context.getString(name),
                    count,
                    index + 1,
                )
                compose.onNodeWithContentDescription(description, substring = true).assertIsDisplayed()
            }
            val legend = compose.onNodeWithText(context.getString(R.string.driving_region_map_visit_pack_scale))
            legend.assertIsDisplayed()
            val layouts = mutableListOf<TextLayoutResult>()
            legend.performSemanticsAction(SemanticsActions.GetTextLayoutResult) { it(layouts) }
            val details = layouts.joinToString { result ->
                val lineBounds = (0 until result.lineCount).joinToString { line ->
                    "(${result.getLineLeft(line)},${result.getLineTop(line)})-" +
                        "(${result.getLineRight(line)},${result.getLineBottom(line)})"
                }
                "size=${result.size}, paragraph=${result.multiParagraph.width}x${result.multiParagraph.height}, " +
                    "lines=${result.lineCount}/${result.layoutInput.maxLines}, " +
                    "overflowWidth=${result.didOverflowWidth}, overflowHeight=${result.didOverflowHeight}, " +
                    "constraints=${result.layoutInput.constraints}, density=${result.layoutInput.density}, " +
                    "lineBounds=[$lineBounds]"
            }
            assertTrue(
                "Visit-pack legend must remain readable at ${captureWidth.value}dp: " +
                    "${layouts.size} layouts [$details]",
                layouts.isNotEmpty() && layouts.all { it.fitsVisibleBounds() },
            )
        }
    }

    private fun saveCapture(output: File, filename: String, width: Dp, height: Dp) {
        val surface = compose.onNodeWithTag(CAPTURE_TAG)
        surface.assertWidthIsEqualTo(width).assertHeightIsEqualTo(height)
        val image = surface.captureToImage().asAndroidBitmap()
        File(output, "$filename-${width.value.toInt()}.png").outputStream().use { stream ->
            check(image.compress(Bitmap.CompressFormat.PNG, 100, stream))
        }
    }

    private fun TextLayoutResult.fitsVisibleBounds(): Boolean {
        if (didOverflowHeight || multiParagraph.didExceedMaxLines) return false
        // A wrapped Text can keep a wider paragraph container. Check the actual drawn lines.
        return (0 until lineCount).all { line ->
            getLineLeft(line) >= -1f && getLineRight(line) <= size.width + 1f &&
                getLineTop(line) >= -1f && getLineBottom(line) <= size.height + 1f
        }
    }

    private fun assertState(capture: Capture, label: (Int) -> String) {
        val heading = if (capture.isMoving) {
            R.string.journey_restriction_moving_title
        } else {
            R.string.journey_restriction_stopped_title
        }
        val location = if (capture.position != null) {
            R.string.driving_region_map_current_position
        } else {
            R.string.driving_region_map_location_unavailable
        }
        compose.onNodeWithText(label(heading)).assertIsDisplayed()
        compose.onNodeWithText(label(R.string.driving_region_map_title)).assertIsDisplayed()
        compose.onNodeWithText(label(location)).assertIsDisplayed()
        capture.message?.let { compose.onNodeWithText(label(it)).assertIsDisplayed() }

        for (action in RecommendationActions) {
            val node = compose.onNodeWithText(label(action))
            if (capture.action == action) node.assertIsDisplayed().assertIsEnabled() else node.assertDoesNotExist()
        }

        val observation = compose.onNodeWithText(label(R.string.driving_overview_observation), useUnmergedTree = true)
        val safetyBody = when (capture.safetyMessage) {
            DrivingSafetyMessage.Acceleration -> R.string.driving_overview_acceleration
            DrivingSafetyMessage.Braking -> R.string.driving_overview_braking
            null -> null
        }
        if (safetyBody == null) {
            observation.assertDoesNotExist()
        } else {
            observation.assertIsDisplayed()
            compose.onNodeWithText(label(safetyBody), useUnmergedTree = true).assertIsDisplayed()
        }
    }

    private data class Capture(
        val filename: String,
        val isMoving: Boolean = true,
        val position: PlaceMapPosition? = CapturePosition,
        val placeName: String? = "해운대 해수욕장",
        val hasDestination: Boolean = false,
        val operation: PlaceNavigationOperation = PlaceNavigationOperation.Idle,
        @param:StringRes val message: Int? = null,
        @param:StringRes val action: Int? = R.string.place_map_set_destination,
        val safetyMessage: DrivingSafetyMessage? = null,
    )

    private companion object {
        const val CAPTURE_TAG = "driving-overview-capture"
        val CapturePosition = PlaceMapPosition(latitude = 35.1631, longitude = 129.1604)
        val VisitPackCounts = mapOf("KR-26" to 1, "KR-48" to 4)
        val RecommendationActions = listOf(
            R.string.place_map_set_destination,
            R.string.place_map_add_waypoint,
            R.string.place_map_check_route,
        )
    }
}
