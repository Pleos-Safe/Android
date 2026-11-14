package io.issac.pleosandroid.navigation

import android.app.UiAutomation
import android.content.ComponentName
import android.content.Intent
import android.graphics.Bitmap
import android.os.SystemClock
import androidx.test.core.app.ActivityScenario
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import dagger.hilt.android.testing.HiltAndroidRule
import dagger.hilt.android.testing.HiltAndroidTest
import io.issac.pleosandroid.MainActivity
import java.io.File
import org.junit.Assume.assumeTrue
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith

/** Opt in with `-e pleosAdjacentProbe true` on a parked Pleos emulator with built-in navigation. */
@RunWith(AndroidJUnit4::class)
@HiltAndroidTest
class AdjacentNavigationProbeTest {
    @get:Rule(order = -1)
    val hilt = HiltAndroidRule(this)

    @Test
    fun captureAdjacentNavigationRequest() {
        assumeTrue(InstrumentationRegistry.getArguments().getString("pleosAdjacentProbe") == "true")
        val instrumentation = InstrumentationRegistry.getInstrumentation()
        val automation = instrumentation.getUiAutomation(
            UiAutomation.FLAG_DONT_SUPPRESS_ACCESSIBILITY_SERVICES,
        )
        val output = File(instrumentation.targetContext.filesDir, "screenshots")
        check(output.isDirectory || output.mkdirs())

        ActivityScenario.launch(MainActivity::class.java).use { scenario ->
            check(automation.awaitForeground(instrumentation.targetContext.packageName)) {
                "The source activity did not come to the foreground."
            }
            automation.capture(File(output, "adjacent-before.png"))

            scenario.onActivity { activity ->
                val launch = Intent.makeMainActivity(BuiltInNavigation)
                    .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_LAUNCH_ADJACENT)
                activity.startActivity(launch)
            }

            val opened = automation.awaitForeground(BuiltInNavigation.packageName)
            automation.capture(File(output, "adjacent-after.png"))
            // Foreground confirms launch. Inspect the capture to determine whether both apps are visible.
            check(opened) { "The navigation app did not come to the foreground." }
        }
    }

    private fun UiAutomation.awaitForeground(packageName: String): Boolean {
        val deadline = SystemClock.uptimeMillis() + 15_000
        while (SystemClock.uptimeMillis() < deadline) {
            if (rootInActiveWindow?.packageName?.toString() == packageName) return true
            SystemClock.sleep(100)
        }
        return false
    }

    private fun UiAutomation.capture(output: File) {
        val screenshot = checkNotNull(takeScreenshot()) { "Could not capture the display." }
        try {
            output.outputStream().use { stream ->
                check(screenshot.compress(Bitmap.CompressFormat.PNG, 100, stream))
            }
        } finally {
            screenshot.recycle()
        }
    }

    private companion object {
        val BuiltInNavigation = ComponentName(
            "ai.umos.maps.android.navigation.app",
            "ai.umos.maps.android.navigation.app.MainActivity",
        )
    }
}
