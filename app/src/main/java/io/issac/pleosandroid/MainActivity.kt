package io.issac.pleosandroid

import android.content.Intent
import android.graphics.Color
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.SystemBarStyle
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.material3.Scaffold
import androidx.compose.ui.Modifier
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.lifecycleScope
import androidx.lifecycle.repeatOnLifecycle
import dagger.hilt.android.AndroidEntryPoint
import io.issac.pleosandroid.core.designsystem.theme.PleosWindowTheme
import io.issac.pleosandroid.core.domain.driving.repository.DrivingRecorder
import io.issac.pleosandroid.core.navigation.NavEntryFactory
import io.issac.pleosandroid.driving.DrivingRecordingService
import io.issac.pleosandroid.navigation.PleosAppNavigation
import javax.inject.Inject
import kotlinx.coroutines.launch

@AndroidEntryPoint
class MainActivity : ComponentActivity() {
    @Inject
    lateinit var navEntryFactories: Set<@JvmSuppressWildcards NavEntryFactory>

    @Inject lateinit var drivingRecorder: DrivingRecorder

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        lifecycleScope.launch {
            // Re-promote with the location type after returning from a runtime permission dialog.
            repeatOnLifecycle(Lifecycle.State.RESUMED) {
                drivingRecorder.enabled.collect { enabled ->
                    if (enabled) {
                        try {
                            startForegroundService(Intent(this@MainActivity, DrivingRecordingService::class.java))
                        } catch (_: IllegalStateException) {
                            drivingRecorder.monitoringUnavailable()
                        } catch (_: SecurityException) {
                            drivingRecorder.monitoringUnavailable()
                        }
                    }
                }
            }
        }
        val darkBars = SystemBarStyle.dark(Color.TRANSPARENT)
        enableEdgeToEdge(statusBarStyle = darkBars, navigationBarStyle = darkBars)
        setContent {
            PleosWindowTheme {
                Scaffold(modifier = Modifier.fillMaxSize()) { innerPadding ->
                    PleosAppNavigation(factories = navEntryFactories, contentPadding = innerPadding)
                }
            }
        }
    }
}
