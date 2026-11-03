package io.issac.pleosandroid.feature.badges

import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import io.issac.pleosandroid.core.navigation.LocalNavigationRouter

@Composable
internal fun BadgesRouteContent(
    modifier: Modifier = Modifier,
) {
    val router = LocalNavigationRouter.current
    BadgesScreen(onBack = { router.pop() }, modifier = modifier)
}
