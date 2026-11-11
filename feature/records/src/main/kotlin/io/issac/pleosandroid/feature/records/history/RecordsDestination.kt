package io.issac.pleosandroid.feature.records.history

import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.ui.Modifier
import androidx.hilt.lifecycle.viewmodel.compose.hiltViewModel
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.compose.LocalLifecycleOwner
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.repeatOnLifecycle
import io.issac.pleosandroid.core.navigation.AppRoute
import io.issac.pleosandroid.core.navigation.DrivingPointsWeekRoute
import io.issac.pleosandroid.core.navigation.LocalNavigationRouter
import io.issac.pleosandroid.core.navigation.MainRoute
import io.issac.pleosandroid.core.navigation.RecordsRoute
import io.issac.pleosandroid.feature.records.points.DrivingPointsWeekScreen
import io.issac.pleosandroid.feature.records.settings.RecordingSettingsDestination

@Composable
internal fun RecordsDestination(
    route: AppRoute,
    modifier: Modifier = Modifier,
    viewModel: RecordsViewModel = hiltViewModel(),
) {
    val router by rememberUpdatedState(LocalNavigationRouter.current)
    val state by viewModel.state.collectAsStateWithLifecycle()
    val lifecycle = LocalLifecycleOwner.current.lifecycle
    LaunchedEffect(viewModel, route, lifecycle) {
        lifecycle.repeatOnLifecycle(Lifecycle.State.STARTED) {
            viewModel.effect.collect { effect ->
                if (!router.isActive(route) || !viewModel.canInteract()) return@collect
                when (effect) {
                    is RecordsEffect.Navigate -> router.navigate(effect.route)
                }
            }
        }
    }
    when (route) {
        RecordsRoute -> RecordsScreen(state, viewModel::handleIntent, { router.navigate(MainRoute) }, modifier)

        DrivingPointsWeekRoute -> DrivingPointsWeekScreen(
            state,
            onBack = { router.pop() },
            onRetry = { viewModel.handleIntent(RecordsIntent.Retry) },
            modifier = modifier,
        )

        else -> RecordingSettingsDestination(state, viewModel::handleIntent, viewModel::canInteract, modifier)
    }
}
