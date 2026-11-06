package io.issac.pleosandroid.feature.main

import android.graphics.Bitmap
import androidx.annotation.StringRes
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.size
import androidx.compose.runtime.mutableStateOf
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.asAndroidBitmap
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.test.assertHeightIsEqualTo
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.assertIsEnabled
import androidx.compose.ui.test.assertWidthIsEqualTo
import androidx.compose.ui.test.captureToImage
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.unit.dp
import androidx.test.platform.app.InstrumentationRegistry
import io.issac.pleosandroid.core.designsystem.component.PleosHeader
import io.issac.pleosandroid.core.designsystem.component.PleosTab
import io.issac.pleosandroid.core.designsystem.component.pleosShellBackground
import io.issac.pleosandroid.core.designsystem.theme.PleosTheme
import io.issac.pleosandroid.feature.main.map.DrivingRegionMap
import io.issac.pleosandroid.feature.main.map.PlaceMapPosition
import io.issac.pleosandroid.feature.main.map.PlaceNavigationOperation
import java.io.File
import org.junit.Assume.assumeTrue
import org.junit.Rule
import org.junit.Test

/** Captures UI fixtures on a large display without starting vehicle or navigation services. */
class DrivingOverviewCaptureTest {
    @get:Rule
    val compose = createComposeRule()

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
            onRoute.copy(filename = "08-safety-good", safetyMessage = DrivingSafetyMessage.Good),
            onRoute.copy(filename = "09-safety-violation", safetyMessage = DrivingSafetyMessage.Violation),
        )
        val current = mutableStateOf(moving)
        val width = mutableStateOf(1216.dp)
        val context = InstrumentationRegistry.getInstrumentation().targetContext
        val output = File(context.filesDir, "screenshots/driving-overview")
        check(output.isDirectory || output.mkdirs())

        compose.setContent {
            PleosTheme {
                val capture = current.value
                Column(Modifier.size(width.value, 760.dp).testTag(CAPTURE_TAG).pleosShellBackground()) {
                    PleosHeader(PleosTab.Travel, {}, {}, restricted = true)
                    Box(Modifier.weight(1f)) {
                        DrivingOverviewScreen(
                            isMoving = capture.isMoving,
                            safetyMessage = capture.safetyMessage,
                            map = { modifier ->
                                DrivingRegionMap(capture.position, BadgeRegions, capture.isMoving, modifier)
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

        for (captureWidth in listOf(1216.dp, 520.dp)) {
            for (capture in cases) {
                compose.runOnIdle {
                    current.value = capture
                    width.value = captureWidth
                }
                assertState(capture, context::getString)
                val surface = compose.onNodeWithTag(CAPTURE_TAG)
                surface.assertWidthIsEqualTo(captureWidth).assertHeightIsEqualTo(760.dp)
                val image = surface.captureToImage().asAndroidBitmap()
                File(output, "${capture.filename}-${captureWidth.value.toInt()}.png").outputStream().use { stream ->
                    check(image.compress(Bitmap.CompressFormat.PNG, 100, stream))
                }
            }
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

        val demo = compose.onNodeWithText(label(R.string.driving_overview_safety_demo), useUnmergedTree = true)
        val safetyBody = when (capture.safetyMessage) {
            DrivingSafetyMessage.Good -> R.string.driving_overview_safety_good
            DrivingSafetyMessage.Violation -> R.string.driving_overview_safety_violation
            null -> null
        }
        if (safetyBody == null) {
            demo.assertDoesNotExist()
        } else {
            demo.assertIsDisplayed()
            compose.onNodeWithText(label(safetyBody), useUnmergedTree = true).assertIsDisplayed()
        }
    }

    private data class Capture(
        val filename: String,
        val isMoving: Boolean = true,
        val position: PlaceMapPosition? = PlaceMapPosition(latitude = 35.1631, longitude = 129.1604),
        val placeName: String? = "해운대 해수욕장",
        val hasDestination: Boolean = false,
        val operation: PlaceNavigationOperation = PlaceNavigationOperation.Idle,
        @param:StringRes val message: Int? = null,
        @param:StringRes val action: Int? = R.string.place_map_set_destination,
        val safetyMessage: DrivingSafetyMessage? = null,
    )

    private companion object {
        const val CAPTURE_TAG = "driving-overview-capture"
        val BadgeRegions = setOf("KR-26", "KR-48")
        val RecommendationActions = listOf(
            R.string.place_map_set_destination,
            R.string.place_map_add_waypoint,
            R.string.place_map_check_route,
        )
    }
}
