package io.issac.pleosandroid.feature.records.navigation

import android.Manifest
import android.content.pm.PackageManager
import android.os.Build
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.hilt.lifecycle.viewmodel.compose.hiltViewModel
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.compose.LocalLifecycleOwner
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.repeatOnLifecycle
import io.issac.pleosandroid.core.designsystem.component.PleosButton
import io.issac.pleosandroid.core.navigation.AppRoute
import io.issac.pleosandroid.core.navigation.BadgesRoute
import io.issac.pleosandroid.core.navigation.DrivingDataStatusRoute
import io.issac.pleosandroid.core.navigation.DrivingReportRoute
import io.issac.pleosandroid.core.navigation.LocalNavigationRouter
import io.issac.pleosandroid.core.navigation.MainRoute
import io.issac.pleosandroid.core.navigation.RecordingSettingsRoute
import io.issac.pleosandroid.core.navigation.RecordsRoute
import io.issac.pleosandroid.core.navigation.SafetyCollectionRoute
import io.issac.pleosandroid.feature.records.component.RecordBody
import io.issac.pleosandroid.feature.records.component.RecordsPage
import io.issac.pleosandroid.feature.records.history.RecordsEffect
import io.issac.pleosandroid.feature.records.history.RecordsIntent
import io.issac.pleosandroid.feature.records.history.RecordsScreen
import io.issac.pleosandroid.feature.records.history.RecordsViewModel
import io.issac.pleosandroid.feature.records.report.DrivingDataStatusScreen
import io.issac.pleosandroid.feature.records.report.DrivingReportScreen
import io.issac.pleosandroid.feature.records.report.DrivingReportViewModel
import io.issac.pleosandroid.feature.records.report.ReportIntent
import io.issac.pleosandroid.feature.records.settings.RecordingSettingsScreen

@Composable
internal fun RecordsRouteContent(route: AppRoute, modifier: Modifier) {
    val router by rememberUpdatedState(LocalNavigationRouter.current)
    if (route == RecordsRoute || route == RecordingSettingsRoute) {
        val viewModel: RecordsViewModel = hiltViewModel()
        val state by viewModel.state.collectAsStateWithLifecycle()
        val lifecycle = LocalLifecycleOwner.current.lifecycle
        LaunchedEffect(viewModel, route, lifecycle) {
            lifecycle.repeatOnLifecycle(Lifecycle.State.RESUMED) {
                viewModel.effect.collect { effect ->
                    if (!router.isActive(route)) return@collect
                    when (effect) {
                        is RecordsEffect.Navigate -> router.navigate(effect.route)
                    }
                }
            }
        }
        if (route == RecordsRoute) {
            RecordsScreen(state, viewModel::handleIntent, { router.navigate(MainRoute) }, modifier)
        } else {
            val context = LocalContext.current
            val permission = rememberLauncherForActivityResult(ActivityResultContracts.RequestMultiplePermissions()) {
                viewModel.handleIntent(RecordsIntent.SetEnabled(true))
            }
            RecordingSettingsScreen(state, { intent ->
                val missingPermissions = buildList {
                    if (context.checkSelfPermission(Manifest.permission.ACCESS_FINE_LOCATION) !=
                        PackageManager.PERMISSION_GRANTED
                    ) {
                        add(Manifest.permission.ACCESS_FINE_LOCATION)
                        add(Manifest.permission.ACCESS_COARSE_LOCATION)
                    }
                    if (Build.VERSION.SDK_INT >= 33 &&
                        context.checkSelfPermission(Manifest.permission.POST_NOTIFICATIONS) !=
                        PackageManager.PERMISSION_GRANTED
                    ) {
                        add(Manifest.permission.POST_NOTIFICATIONS)
                    }
                }
                if (intent is RecordsIntent.SetEnabled && intent.enabled && missingPermissions.isNotEmpty()) {
                    permission.launch(missingPermissions.toTypedArray())
                } else {
                    viewModel.handleIntent(intent)
                }
            }, { router.pop() }, modifier)
        }
        return
    }
    val id = when (route) {
        is DrivingReportRoute -> route.journeyId
        is DrivingDataStatusRoute -> route.journeyId
        else -> return
    }
    val viewModel: DrivingReportViewModel = hiltViewModel()
    LaunchedEffect(id) { viewModel.handleIntent(ReportIntent.Load(id)) }
    val state by viewModel.state.collectAsStateWithLifecycle()
    val journey = state.journey
    if (state.loading || state.error || journey == null) {
        RecordsPage("운전습관 기록", modifier, onBack = { router.pop() }) {
            RecordBody(
                when {
                    state.loading -> "기록을 불러오고 있어요."
                    state.error -> "기록을 불러오지 못했어요."
                    else -> "이 여정의 기록이 없어요."
                },
            )
            if (state.error) PleosButton("다시 시도", { viewModel.handleIntent(ReportIntent.Retry) })
        }
    } else if (route is DrivingDataStatusRoute) {
        DrivingDataStatusScreen(journey, state.demo, {
            router.pop()
        }, { router.navigate(DrivingReportRoute(id)) }, modifier)
    } else {
        DrivingReportScreen(
            journey,
            state.demo,
            { router.pop() },
            { router.navigate(DrivingDataStatusRoute(id)) },
            { router.replaceAll(BadgesRoute, SafetyCollectionRoute) },
            modifier,
        )
    }
}
