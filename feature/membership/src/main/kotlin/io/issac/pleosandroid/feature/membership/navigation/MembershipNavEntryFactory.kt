package io.issac.pleosandroid.feature.membership.navigation

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
import io.issac.pleosandroid.core.navigation.ShopRoute
import io.issac.pleosandroid.feature.membership.MembershipDestination
import javax.inject.Inject

internal class MembershipNavEntryFactory @Inject constructor() : NavEntryFactory {
    override fun create(route: AppRoute): NavEntryResult? = when (route) {
        ShopRoute, MembershipSettingsRoute -> NavEntryResult { padding ->
            val router = LocalNavigationRouter.current
            MembershipDestination(
                managing = route == MembershipSettingsRoute,
                onBack = { router.pop() },
                onManage = { router.navigate(MembershipSettingsRoute) },
                onShop = { router.navigate(ShopRoute) },
                onRecordingSettings = { router.navigate(RecordingSettingsRoute) },
                modifier = Modifier.padding(padding),
            )
        }

        else -> null
    }
}

@Module
@InstallIn(SingletonComponent::class)
internal abstract class MembershipNavigationModule {
    @Binds
    @IntoSet
    abstract fun entry(factory: MembershipNavEntryFactory): NavEntryFactory
}
