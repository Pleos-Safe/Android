package io.issac.pleosandroid.feature.badges.navigation

import androidx.compose.foundation.layout.padding
import androidx.compose.ui.Modifier
import dagger.Binds
import dagger.Module
import dagger.hilt.InstallIn
import dagger.hilt.components.SingletonComponent
import dagger.multibindings.IntoSet
import io.issac.pleosandroid.core.navigation.AppRoute
import io.issac.pleosandroid.core.navigation.BadgesRoute
import io.issac.pleosandroid.core.navigation.EnhancementRoute
import io.issac.pleosandroid.core.navigation.NavEntryFactory
import io.issac.pleosandroid.core.navigation.NavEntryResult
import io.issac.pleosandroid.core.navigation.OpenPackRoute
import io.issac.pleosandroid.core.navigation.PackShelfRoute
import io.issac.pleosandroid.feature.badges.album.AlbumDetailRoute
import io.issac.pleosandroid.feature.badges.album.PackOddsRoute
import io.issac.pleosandroid.feature.badges.album.WorkshopRoute
import javax.inject.Inject

internal class BadgesNavEntryFactory @Inject constructor() : NavEntryFactory {
    override fun create(route: AppRoute): NavEntryResult? = when (route) {
        BadgesRoute, EnhancementRoute, PackShelfRoute, PackOddsRoute,
        is AlbumDetailRoute, is OpenPackRoute, is WorkshopRoute,
        ->
            NavEntryResult { padding -> BadgesRouteContent(route, Modifier.padding(padding)) }

        else -> null
    }
}

@Module
@InstallIn(SingletonComponent::class)
internal abstract class BadgesNavigationModule {
    @Binds
    @IntoSet
    abstract fun bindBadgesNavEntryFactory(factory: BadgesNavEntryFactory): NavEntryFactory
}
