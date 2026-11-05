package io.issac.pleosandroid.navigation

import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.material3.Button
import androidx.compose.material3.Text
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.test.junit4.StateRestorationTester
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.test.ext.junit.runners.AndroidJUnit4
import io.issac.pleosandroid.core.navigation.AppRoute
import io.issac.pleosandroid.core.navigation.LocalNavigationRouter
import io.issac.pleosandroid.core.navigation.MainRoute
import io.issac.pleosandroid.core.navigation.NavEntryFactory
import io.issac.pleosandroid.core.navigation.NavEntryResult
import io.issac.pleosandroid.feature.nav.PleosNavHost
import kotlinx.serialization.Serializable
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith

@RunWith(AndroidJUnit4::class)
class PleosNavigationTest {
    @get:Rule
    val compose = createComposeRule()

    @Test
    fun restoresDestinationAndEntryStateThenNavigatesBack() {
        val restoration = StateRestorationTester(compose)
        val factory = NavEntryFactory { route ->
            when (route) {
                NavigationTestRoot -> NavEntryResult {
                    val router = LocalNavigationRouter.current
                    Button(onClick = { router.navigate(NavigationTestDetail("trip-1")) }) {
                        Text("Open detail")
                    }
                }

                is NavigationTestDetail -> NavEntryResult {
                    val router = LocalNavigationRouter.current
                    var count by rememberSaveable { mutableIntStateOf(0) }
                    Column {
                        Text("${route.id}: $count")
                        Button(onClick = { count++ }) { Text("Increment") }
                        Button(onClick = { router.pop() }) { Text("Back") }
                    }
                }

                else -> null
            }
        }
        restoration.setContent {
            PleosNavHost(factories = setOf(factory), startRoute = NavigationTestRoot)
        }

        compose.onNodeWithText("Open detail").performClick()
        compose.onNodeWithText("Increment").performClick()
        compose.onNodeWithText("trip-1: 1").assertExists()

        restoration.emulateSavedInstanceStateRestore()

        compose.onNodeWithText("trip-1: 1").assertExists()
        compose.onNodeWithText("Back").performClick()
        compose.onNodeWithText("Open detail").assertExists()
    }

    @Test
    fun mainEntryReceivesUpdatedFactoryContent() {
        val label = mutableStateOf("Initial vehicle data")
        compose.setContent {
            val currentLabel = label.value
            val factory = NavEntryFactory { route ->
                if (route == MainRoute) NavEntryResult { Text(currentLabel) } else null
            }
            PleosAppNavigation(factories = setOf(factory), contentPadding = PaddingValues())
        }
        compose.onNodeWithText("Initial vehicle data").assertExists()

        compose.runOnIdle { label.value = "Updated vehicle data" }

        compose.onNodeWithText("Updated vehicle data").assertExists()
        compose.onNodeWithText("Initial vehicle data").assertDoesNotExist()
    }
}

@Serializable
internal data object NavigationTestRoot : AppRoute

@Serializable
internal data class NavigationTestDetail(val id: String) : AppRoute
