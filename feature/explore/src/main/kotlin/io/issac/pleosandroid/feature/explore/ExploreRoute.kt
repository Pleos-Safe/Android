package io.issac.pleosandroid.feature.explore

import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.ui.Modifier
import androidx.hilt.lifecycle.viewmodel.compose.hiltViewModel
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.compose.LifecycleResumeEffect
import androidx.lifecycle.compose.LocalLifecycleOwner
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.repeatOnLifecycle
import io.issac.pleosandroid.core.navigation.AppRoute
import io.issac.pleosandroid.core.navigation.LocalNavigationRouter

@Composable
internal fun ExploreRouteContent(
    route: AppRoute,
    modifier: Modifier = Modifier,
    viewModel: ExploreViewModel = hiltViewModel(),
) {
    val state by viewModel.state.collectAsStateWithLifecycle()
    val router = LocalNavigationRouter.current
    val lifecycle = LocalLifecycleOwner.current.lifecycle
    val isActive by rememberUpdatedState { router.isActive(route) }
    val permission = rememberLauncherForActivityResult(ActivityResultContracts.RequestPermission()) {
        if (isActive() && lifecycle.currentState.isAtLeast(Lifecycle.State.RESUMED)) {
            viewModel.handleIntent(ExploreIntent.LocationPermissionChanged)
        }
    }
    LifecycleResumeEffect(viewModel, route) {
        if (isActive()) viewModel.handleIntent(ExploreIntent.Resume)
        onPauseOrDispose { viewModel.handleIntent(ExploreIntent.Pause) }
    }
    LaunchedEffect(viewModel, lifecycle, router, route) {
        lifecycle.repeatOnLifecycle(Lifecycle.State.STARTED) {
            viewModel.effect.collect { effect ->
                if (!isActive()) return@collect
                when (effect) {
                    ExploreEffect.Back -> router.pop()

                    ExploreEffect.RequestLocationPermission -> if (viewModel.canInteract()) {
                        permission.launch(PLEOS_LOCATION_PERMISSION)
                    }
                }
            }
        }
    }
    ExploreScreen(state, viewModel::handleIntent, modifier)
}

private const val PLEOS_LOCATION_PERMISSION = "pleos.car.permission.FUSED_LOCATION"
