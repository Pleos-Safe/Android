package io.issac.pleosandroid.feature.nav

import androidx.compose.animation.AnimatedContentTransitionScope
import androidx.compose.animation.AnimatedContentTransitionScope.SlideDirection
import androidx.compose.animation.ContentTransform
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.togetherWith
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.navigation3.runtime.NavEntry
import androidx.navigation3.runtime.NavKey
import androidx.navigation3.scene.Scene
import androidx.navigation3.ui.NavDisplay
import io.issac.pleosandroid.core.designsystem.component.PleosTab
import io.issac.pleosandroid.core.designsystem.theme.PleosMotion

internal const val TAB_METADATA_KEY = "pleos.navigation.tab"
private const val TAB_SLIDE_DIVISOR = 12
private const val BACKGROUND_SLIDE_DIVISOR = 4

@Composable
fun PleosNavDisplay(entries: List<NavEntry<NavKey>>, onBack: () -> Unit, modifier: Modifier = Modifier) {
    NavDisplay(
        entries = entries,
        onBack = onBack,
        modifier = modifier,
        transitionSpec = { screenTransition(isPop = false) },
        popTransitionSpec = { screenTransition(isPop = true) },
        predictivePopTransitionSpec = { _ -> screenTransition(isPop = true) },
    )
}

private fun AnimatedContentTransitionScope<Scene<NavKey>>.screenTransition(isPop: Boolean): ContentTransform {
    val from = initialState.entries.lastOrNull()?.metadata?.get(TAB_METADATA_KEY) as? PleosTab
    val to = targetState.entries.lastOrNull()?.metadata?.get(TAB_METADATA_KEY) as? PleosTab
    if (from != null && to != null && from != to) {
        val direction = if (to > from) SlideDirection.Start else SlideDirection.End
        val duration = PleosMotion.TAB_DURATION_MILLIS
        val easing = PleosMotion.navigationEasing
        val enter = slideIntoContainer(direction, tween(duration, easing = easing)) { it / TAB_SLIDE_DIVISOR } +
            fadeIn(tween(duration, easing = easing))
        val exit = slideOutOfContainer(direction, tween(duration, easing = easing)) { it / TAB_SLIDE_DIVISOR } +
            fadeOut(tween(duration, easing = easing))
        return enter togetherWith exit
    }

    val direction = if (isPop) SlideDirection.End else SlideDirection.Start
    val duration = PleosMotion.NAVIGATION_DURATION_MILLIS
    val easing = PleosMotion.navigationEasing
    val enter = slideIntoContainer(direction, tween(duration, easing = easing)) {
        if (isPop) {
            it /
                BACKGROUND_SLIDE_DIVISOR
        } else {
            it
        }
    }
    val exit = slideOutOfContainer(direction, tween(duration, easing = easing)) {
        if (isPop) {
            it
        } else {
            it /
                BACKGROUND_SLIDE_DIVISOR
        }
    }
    return enter togetherWith exit
}
