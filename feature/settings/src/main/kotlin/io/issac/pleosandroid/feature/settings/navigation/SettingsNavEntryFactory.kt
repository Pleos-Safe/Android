package io.issac.pleosandroid.feature.settings.navigation

import androidx.compose.foundation.layout.padding
import androidx.compose.ui.Modifier
import dagger.Binds
import dagger.Module
import dagger.hilt.InstallIn
import dagger.hilt.components.SingletonComponent
import dagger.multibindings.IntoSet
import io.issac.pleosandroid.core.navigation.AppRoute
import io.issac.pleosandroid.core.navigation.LocalNavigationRouter
import io.issac.pleosandroid.core.navigation.MembershipSettingsRoute
import io.issac.pleosandroid.core.navigation.NavEntryFactory
import io.issac.pleosandroid.core.navigation.NavEntryResult
import io.issac.pleosandroid.core.navigation.RecordingSettingsRoute
import io.issac.pleosandroid.core.navigation.SettingsRoute
import io.issac.pleosandroid.feature.settings.SettingsScreen
import javax.inject.Inject

internal class SettingsNavEntryFactory @Inject constructor() : NavEntryFactory {
    override fun create(route: AppRoute): NavEntryResult? = when (route) {
        SettingsRoute -> NavEntryResult { padding ->
            val router = LocalNavigationRouter.current
            SettingsScreen(
                onMembershipSettings = { router.navigate(MembershipSettingsRoute) },
                onRecordingSettings = { router.navigate(RecordingSettingsRoute) },
                onBack = { router.pop() },
                modifier = Modifier.padding(padding),
            )
        }

        else -> null
    }
}

@Module
@InstallIn(SingletonComponent::class)
internal abstract class SettingsNavigationModule {
    @Binds
    @IntoSet
    abstract fun entry(factory: SettingsNavEntryFactory): NavEntryFactory
}
