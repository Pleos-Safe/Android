package io.issac.pleosandroid

import android.graphics.Color
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.SystemBarStyle
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.material3.Scaffold
import androidx.compose.ui.Modifier
import dagger.hilt.android.AndroidEntryPoint
import io.issac.pleosandroid.core.designsystem.theme.PleosWindowTheme
import io.issac.pleosandroid.core.navigation.NavEntryFactory
import io.issac.pleosandroid.navigation.PleosAppNavigation
import javax.inject.Inject

@AndroidEntryPoint
class MainActivity : ComponentActivity() {
    @Inject
    lateinit var navEntryFactories: Set<@JvmSuppressWildcards NavEntryFactory>

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
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
