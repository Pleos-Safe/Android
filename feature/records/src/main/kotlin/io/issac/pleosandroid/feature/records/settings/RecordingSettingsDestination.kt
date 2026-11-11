package io.issac.pleosandroid.feature.records.settings

import android.Manifest
import android.content.Context
import android.content.pm.PackageManager
import android.os.Build
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import io.issac.pleosandroid.core.navigation.LocalNavigationRouter
import io.issac.pleosandroid.core.navigation.RecordingSettingsRoute
import io.issac.pleosandroid.feature.records.history.RecordsIntent
import io.issac.pleosandroid.feature.records.history.RecordsState

@Composable
internal fun RecordingSettingsDestination(
    state: RecordsState,
    onIntent: (RecordsIntent) -> Unit,
    canInteract: () -> Boolean,
    modifier: Modifier = Modifier,
) {
    val context = LocalContext.current
    val router by rememberUpdatedState(LocalNavigationRouter.current)
    val currentOnIntent by rememberUpdatedState(onIntent)
    val currentCanInteract by rememberUpdatedState(canInteract)
    val permission = rememberLauncherForActivityResult(ActivityResultContracts.RequestMultiplePermissions()) {
        if (router.isActive(RecordingSettingsRoute) && currentCanInteract()) {
            currentOnIntent(RecordsIntent.SetEnabled(true))
        }
    }
    RecordingSettingsScreen(
        state,
        onIntent = { intent ->
            if (router.isActive(RecordingSettingsRoute) && currentCanInteract()) {
                val missingPermissions = context.missingRecordingPermissions()
                if (intent is RecordsIntent.SetEnabled && intent.enabled && missingPermissions.isNotEmpty()) {
                    permission.launch(missingPermissions)
                } else {
                    currentOnIntent(intent)
                }
            }
        },
        onBack = { router.pop() },
        modifier = modifier,
    )
}

private fun Context.missingRecordingPermissions(): Array<String> = buildList {
    if (checkSelfPermission(Manifest.permission.ACCESS_FINE_LOCATION) != PackageManager.PERMISSION_GRANTED) {
        add(Manifest.permission.ACCESS_FINE_LOCATION)
        add(Manifest.permission.ACCESS_COARSE_LOCATION)
    }
    if (Build.VERSION.SDK_INT >= 33 &&
        checkSelfPermission(Manifest.permission.POST_NOTIFICATIONS) != PackageManager.PERMISSION_GRANTED
    ) {
        add(Manifest.permission.POST_NOTIFICATIONS)
    }
}.toTypedArray()
